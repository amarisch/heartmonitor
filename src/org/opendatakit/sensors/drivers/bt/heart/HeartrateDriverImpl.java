/*
 * Copyright (C) 2013 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.sensors.drivers.bt.heart;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opendatakit.sensors.SensorDataPacket;
import org.opendatakit.sensors.SensorDataParseResponse;
import org.opendatakit.sensors.SensorParameter;
import org.opendatakit.sensors.drivers.AbstractDriverBaseV2;

import android.os.Bundle;
import android.util.Log;

/*
 * Sensor drivers are implemented as Android Services and are accessed by ODK Sensors over the service interface. 
 * The generic classes that handle Android Service semantics are already implemented and available in the 
 * org.opendatakit.sensors.drivers package of the Sensors framework. 
 * 
 * Sensor driver developers are responsible for implementing a class (known as the driverImpl class) that decodes 
 * raw sensor data buffers received from sensors into higher level key-value pairs, and encodes key-value pairs 
 * into a buffer in a sensor-specific format that is sent to the sensor. The Sensors framework mediates the communications 
 * between physical sensors and their drivers. The driverImpl class needs to either implement the 
 * org.opendatakit.sensors.Driver interface or extend the org.opendatakit.sensors.drivers.AbstractDriverBaseV2 class (a convenience
 * class that implements the Driver interface) provided by ODKSensors. Please look at org.opendatakit.sensors.Driver.java for
 * a description of each method in the interface. 
 * 
 * ODK Sensors uses some metadata defined in the AndroidManifest.xml of the Android Application that implements a sensor driver
 * to discover and instantiate drivers. Please look at the manifest file of this project for meta-data elements that need to 
 * be defined by driver developers.
 *  
 */

/*
 * This file contains the driverImpl class for a sensor driver that communicates with Zephyr Heartrate monitors. The meta-data element 
 * called "ODK_sensors_driverImplClassname" in the manifest file refers to this class.
 * 
 */

public class HeartrateDriverImpl extends AbstractDriverBaseV2  {

	
	private static final String TAG = "HeartrateSensor";
	
	public static final String VOLTAGE_VALUES = "VV";
	public  static final String HEART_RATE = "HR";
	
	//REMOVE LATER
	public static final String QRS_VALUES = "QRS";
	
	private static final int PAYLOAD_SIZE = 128; //including crc
	private static final int SYNC_BYTE = 0xaa; //10101010
	private static final int MAX_SYNC_BYTES = 10;
	private static final int VOLTAGE_SAMPLE_SIZE = 50; // sending 50 voltage readings at once 
	private static final int MSG_DATA_BEGIN_INDEX = 3;
	private static final int MSG_DATA_END_INDEX = 3 + VOLTAGE_SAMPLE_SIZE * 2;

	
	//message types
	private static final int MSG_READING = 1; //1 temp reading per msg
	
	int syncCounter = 0;
	int payloadCounter = 0;
	byte[] payloadBuffer;
	
	private enum ParsingState {
		SYNCING,
		SYNCED,
		PARSING_PAYLOAD
	}
	
	private ParsingState state = ParsingState.SYNCING;

	private enum WaveState {
		P,
		Q,
		R,
		S,
		T
	}
	
	private WaveState wave_state = WaveState.P;
	
	private Bandpass bp =  new Bandpass();
	private Average av = new Average();
	private Integral inte = new Integral();
	private Differential dif = new Differential();
	private Highpass hp = new Highpass();
	private Lowpass lp = new Lowpass();
	private DetectHeartrate dt = new DetectHeartrate();
	
	private Low_Smooth ls = new Low_Smooth();
	private High_Smooth hs = new High_Smooth();
	private Bandstop bs =  new Bandstop();
	
	
	public class Low_Smooth {
		public int prev = 0;
	}
	
	public class High_Smooth {
		public int prev = 0;
	}
	
	public static final int SAMPLEFILTER_TAP_NUM1 = 53;
	public class Bandstop {
		public int[] history = new int[SAMPLEFILTER_TAP_NUM1];
		public int last_index;
		public int[] filter_taps = new int[]{
				 2,
				  14,
				  19,
				  27,
				  30,
				  28,
				  19,
				  6,
				  -8,
				  -18,
				  -21,
				  -14,
				  0,
				  15,
				  26,
				  26,
				  14,
				  -8,
				  -32,
				  -46,
				  -42,
				  -14,
				  36,
				  98,
				  159,
				  203,
				  219,
				  203,
				  159,
				  98,
				  36,
				  -14,
				  -42,
				  -46,
				  -32,
				  -8,
				  14,
				  26,
				  26,
				  15,
				  0,
				  -14,
				  -21,
				  -18,
				  -8,
				  6,
				  19,
				  28,
				  30,
				  27,
				  19,
				  14,
				  2
				};
	}
	

	
	/*

	FIR filter designed with
	 http://t-filter.appspot.com

	sampling frequency: 250 Hz

	fixed point precision: 10 bits

	* 0 Hz - 10 Hz
	  gain = 1
	  desired ripple = 5 dB
	  actual ripple = n/a

	* 15 Hz - 125 Hz
	  gain = 0
	  desired attenuation = -10 dB
	  actual attenuation = n/a

	*/

	public static final int SAMPLEFILTER_TAP_NUM = 139;
	public class Bandpass {
		public int[] history = new int[SAMPLEFILTER_TAP_NUM];
		public int last_index;
		public int[] filter_taps = new int[]{
				  -1,
				  0,
				  0,
				  -1,
				  -1,
				  -1,
				  -1,
				  -1,
				  -1,
				  -1,
				  0,
				  0,
				  0,
				  1,
				  1,
				  2,
				  3,
				  4,
				  4,
				  5,
				  6,
				  7,
				  8,
				  9,
				  10,
				  11,
				  11,
				  11,
				  11,
				  11,
				  11,
				  10,
				  9,
				  8,
				  7,
				  5,
				  3,
				  1,
				  -1,
				  -3,
				  -5,
				  -7,
				  -9,
				  -11,
				  -12,
				  -13,
				  -13,
				  -13,
				  -13,
				  -11,
				  -9,
				  -7,
				  -4,
				  0,
				  4,
				  9,
				  14,
				  19,
				  24,
				  30,
				  35,
				  41,
				  46,
				  50,
				  54,
				  58,
				  60,
				  63,
				  64,
				  64,
				  64,
				  63,
				  60,
				  58,
				  54,
				  50,
				  46,
				  41,
				  35,
				  30,
				  24,
				  19,
				  14,
				  9,
				  4,
				  0,
				  -4,
				  -7,
				  -9,
				  -11,
				  -13,
				  -13,
				  -13,
				  -13,
				  -12,
				  -11,
				  -9,
				  -7,
				  -5,
				  -3,
				  -1,
				  1,
				  3,
				  5,
				  7,
				  8,
				  9,
				  10,
				  11,
				  11,
				  11,
				  11,
				  11,
				  11,
				  10,
				  9,
				  8,
				  7,
				  6,
				  5,
				  4,
				  4,
				  3,
				  2,
				  1,
				  1,
				  0,
				  0,
				  0,
				  -1,
				  -1,
				  -1,
				  -1,
				  -1,
				  -1,
				  -1,
				  0,
				  0,
				  -1
		};
	}
	
	
	// Data filtering stuff
/*	public class Bandpass {
		
		public int xnt = 0;
		public int xm1 = 0;
		public int xm2 = 0;
		public int ynt = 0;
		public int ym1 = 0;
		public int ym2 = 0;
	}
*/	
	private static final int AVE_NUM = 7; //# of samples used for average filter
	
	public class Average {
		public int[] x = new int[AVE_NUM - 1];
	}
	
	public class Integral {
		public int[] x = new int[32];
		public int ptr = 0;
		public long sum = 0;
	}
	
	public class Differential {
		public int[] x_derv = new int[4];
	}

	public class Highpass {
		public int y1 = 0;
		public int[] x = new int[66];
		public int n = 32;
	}
	
	public class Lowpass {
		public int y1 = 0;
		public int y2 = 0;
		public int[] x = new int[26];
		public int n = 12;
	}
	
	public class DetectHeartrate {
		  public int threshold = 0, stage = 0, peak = 0;
		  public int t = 0;
		  public int[] hr = new int[5];
	}
	
	public HeartrateDriverImpl() {
		super();
		
		// data reporting parameters. These are the key-value pairs returned by this driver.
		sensorParams.add(new SensorParameter(HEART_RATE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Heart Rate"));
		sensorParams.add(new SensorParameter(VOLTAGE_VALUES, SensorParameter.Type.INTEGERARRAY, SensorParameter.Purpose.DATA, "Voltage values"));
		Log.d(TAG," constructed" );
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.opendatakit.sensors.drivers.AbstractDriverBaseV2#getSensorData(long, java.util.List, byte[])
	 */

	@Override
	public SensorDataParseResponse getSensorData(long maxNumReadings, List<SensorDataPacket> rawData, byte[] remainingData) {
		List<Bundle> allData = new ArrayList<Bundle>();		
		//Log.d(TAG," sensor driver get dataV2. sdp list sz: " + rawData.size());
		List<Byte> dataBuffer = new ArrayList<Byte>();

		
//		Log.d(TAG,"getSensorData: rawData " + rawData.size());

		
		// Copy over the remaining bytes
		if(remainingData != null) {
			for (Byte b : remainingData) {
				dataBuffer.add(b);
			}
		}
//		Log.d(TAG,"dataBuffer size before load from remaining: " + dataBuffer.size());		
		
		// Add the new raw data
		for(SensorDataPacket pkt: rawData) {
			byte [] payload = pkt.getPayload();
//			Log.d(TAG, " sdp length: " + payload.length); // length of the byte array
			
			for (int i = 0; i < payload.length; i++) {
				dataBuffer.add(payload[i]);
			}			
		}
//		Log.d(TAG,"dataBuffer size after load: " + dataBuffer.size());	

/*		
		// if we did not get a full packet, we wait to receive the next one
		if (dataBuffer.size() < PAYLOAD_SIZE + MAX_SYNC_BYTES) {
			// Copy data back into remaining buffer
			byte[] newRemainingData = new byte[dataBuffer.size()];
			for (int i = 0; i < dataBuffer.size(); i++) {
				newRemainingData[i] = dataBuffer.get(i);
			}
			return new SensorDataParseResponse(allData, newRemainingData);
		}
*/
		parseData(dataBuffer, allData);

		return new SensorDataParseResponse(allData, null);	
	}	

	
	private void parseData(List<Byte> dataBuffer, List<Bundle> parsedDataBundles) {
		
//		Log.d(TAG,"parseData. dataBuffer len: " + dataBuffer.size());
		while(dataBuffer.size() > 0) {
			byte aByte = dataBuffer.remove(0);
			int maskedByte = aByte & 0xff;
		
			switch(state) {
			case SYNCING:
//				Log.d(TAG,"SYNCING: " + maskedByte);
				if(maskedByte == SYNC_BYTE) {			
					++syncCounter;			
				} else {
					syncCounter = 0;
				}
					
				if(syncCounter >= 5) {
					syncCounter = 0;
					state = ParsingState.SYNCED;
				}
				break;
			case SYNCED:
				//might have more sync bytes. ignore them
//				Log.d(TAG,"SYNCED");
				if(maskedByte != SYNC_BYTE) {
					payloadBuffer = new byte[PAYLOAD_SIZE];
					payloadBuffer[payloadCounter++] = aByte;
					state = ParsingState.PARSING_PAYLOAD;
				}
				break;
			case PARSING_PAYLOAD:
				if(payloadCounter == PAYLOAD_SIZE) {
					//we have a complete packet. process it.										
					processCompletePacket(parsedDataBundles);
					payloadCounter = 0;
					state = ParsingState.SYNCING;
				}
				else {
					payloadBuffer[payloadCounter++] = aByte;
				}
				break;
			}
		}
	}
	
	private void processCompletePacket(List<Bundle> parsedDataBundles) {
		//mt, seqNo, msg, crc
//		Log.d(TAG,"processCompletePacket payloadCounter: " + payloadCounter);
		//StringBuffer strBuff = new StringBuffer();
		//String logStr;
		
		int msgType = payloadBuffer[0] & 0xff;
		int seqLow = payloadBuffer[1] & 0xff; // sequence number low byte
		int seqHi = payloadBuffer[2] & 0xff; // sequence number high byte
		int seqNo = seqHi << 8 | seqLow; // 2 byte sequence number
		int receivedCRC = payloadBuffer[PAYLOAD_SIZE - 1] & 0xff;
		byte calcCRC = 0;
		
		for(int i = 0; i < (PAYLOAD_SIZE -1); i++) {
			//strBuff.append((payloadBuffer[i] & 0xff) + " ");
			calcCRC = (byte) (calcCRC ^ payloadBuffer[i]);
		}
		//strBuff.append(receivedCRC);
		
		int maskedCalcCRC = calcCRC & 0xff;
		//String str = "pkt no: " + seqNo + " crc rcvd: " + receivedCRC + " crc calculated: " + maskedCalcCRC;			
		
		//Log.d(TAG,"crc rcvd: " + receivedCRC);
		//Log.d(TAG,"crc calculated: " + maskedCalcCRC);
		
		if(maskedCalcCRC == receivedCRC) {
			switch(msgType) {
			case MSG_READING:
//				Log.d(TAG,"Got voltage READING msg");
//				Log.d(TAG, "SEQ #: " + seqNo);
				Bundle tempReading = getVoltSamples(payloadBuffer);
				parsedDataBundles.add(tempReading);
				break;
			default: 
				Log.d(TAG,"unknown msgType received: " + msgType);
			}
		}
		else {
			Log.d(TAG,"FAILED");
		}
		
		//logStr += str;
		//Log.d(TAG,logStr);
		//Log.d(TAG,strBuff.toString());
	}

	Bundle getVoltSamples(byte[] voltSamples) {
		// data_array is a buffer to store the voltage values
		int[] data_array = new int[VOLTAGE_SAMPLE_SIZE];
		
		//REMOVE LATER
		int[] qrs_array = new int[VOLTAGE_SAMPLE_SIZE];
		
		int counter = 0;
		
		for(int i = MSG_DATA_BEGIN_INDEX; i < MSG_DATA_END_INDEX - 1; i+=2) {
			//Log.d(TAG,"lower: " + (voltSamples[i] & 0xff));
			//Log.d(TAG,"upper: " + (voltSamples[i + 1] << 8));
			int voltage = (voltSamples[i + 1] << 8) + (voltSamples[i] & 0xff);
//			Log.d(TAG,"voltage: " + voltage + "(" + (voltSamples[i] & 0xff) + " + " + (voltSamples[i + 1] << 8));
			
			// REMOVE LATER
			qrs_array[counter] = voltage - 412; //lowpass_smooth(voltage - 512);
			
			
			
			voltage = apply_filters(voltage - 512);
			
			
			
/*			
			// dampen the noises of small waves by averaging the voltage with 0
			if (voltage < 5 && voltage > -5) {
				if (counter == 0)
					data_array[counter] = voltage / 2;
				else
					data_array[counter] = (voltage + data_array[counter - 1]) / 2;
			} else {
				data_array[counter] = voltage;
			}
*/			
			data_array[counter] = voltage;
			counter++;
		}
		Bundle sample = new Bundle();

		//sample.putString(DataSeries.SAMPLE, tempstr);
		sample.putIntArray(VOLTAGE_VALUES, data_array);
		sample.putInt(HEART_RATE, heartrate);
		
		//REMOVE LATER
		sample.putIntArray(QRS_VALUES, qrs_array);

		return sample;
	}
	
	// for calculating QRS and heartrate
	private int qrs_detection(int voltage) {
		int qrs = apply_diff(voltage);
		qrs = qrs * qrs;
		qrs = apply_integ(qrs);

		//Log.d(TAG, "QRS: " + qrs);
		detect_heartrate(qrs * qrs);
		return qrs;
		
	}
	
	private int apply_filters(int voltage) {

		//int qrs = data[buf_ind] - ADC_OFFSET;
		//filter_data[buf_ind] = apply_avg(apply_bandpass(qrs));
		//voltage = apply_bandpass(voltage);
		//voltage = apply_avg(voltage);
		//voltage = apply_highpass(voltage);
		//voltage = highpass_smooth(voltage);
		voltage = apply_bandpass(voltage);
		//voltage = apply_lowpass(voltage);
		//voltage = apply_lowpass(voltage);
		
		int qrs = qrs_detection(voltage);
		
//		voltage = highpass_smooth(voltage);
		//voltage = lowpass_smooth(voltage);
//		voltage = apply_avg(voltage);
		//voltage = apply_lowpass(voltage);
		//voltage = apply_lowpass(voltage);
		//voltage = band_stop(voltage);
		
		return voltage;
		//return voltage + 512;
	}

	private int band_stop(int raw) {
		  bs.history[bs.last_index++] = raw;
		  if(bs.last_index == SAMPLEFILTER_TAP_NUM1)
			  bs.last_index = 0;
		  
		  long acc = 0;
		  int index = bs.last_index, i;
		  for(i = 0; i < SAMPLEFILTER_TAP_NUM1; ++i) {
		    index = index != 0 ? index-1 : SAMPLEFILTER_TAP_NUM1-1;
		    acc += (long)bs.history[index] * bs.filter_taps[i];
		  }
		  return (int) (acc >> 10);		
	}
	
	private int highpass_smooth(int raw) {
		hs.prev = (int) (raw - (hs.prev + 0.15 * (raw - hs.prev)));
		return hs.prev;
	}
	
	private int lowpass_smooth(int raw) {
		ls.prev = (int) (ls.prev + 0.15 * (raw - ls.prev));
		return ls.prev;
	}
	
	private int apply_bandpass(int raw) {
		  bp.history[bp.last_index++] = raw;
		  if(bp.last_index == SAMPLEFILTER_TAP_NUM)
			  bp.last_index = 0;
		  
		  long acc = 0;
		  int index = bp.last_index, i;
		  for(i = 0; i < SAMPLEFILTER_TAP_NUM; ++i) {
		    index = index != 0 ? index-1 : SAMPLEFILTER_TAP_NUM-1;
		    acc += (long)bp.history[index] * bp.filter_taps[i];
		  }
		  return (int) (acc >> 10);
	}
	
	
/*	private int apply_bandpass(int raw) {
		bp.xnt = raw;
		
		bp.ynt = (bp.ym1 + bp.ym1 >> 1 + bp.ym1 >> 2 + bp.ym1 >> 3) +
				(bp.ym2 >> 1 + bp.ym2 >> 2 + bp.ym2 >> 3 +
				bp.ym2 >> 5 + bp.ym2 >> 6) + bp.xnt - bp.xm2;
		bp.xm2 = bp.xm1;
		bp.xm1 = bp.xnt;
		bp.ym2 = bp.ym1;
		bp.ym1 = bp.ynt;
		return bp.ynt;
	}
*/	
	private int apply_lowpass(int raw) {
		int y0;
		
		lp.x[lp.n] = lp.x[lp.n + 13] = raw;
		y0 = (lp.y1 << 1) - lp.y2 + lp.x[lp.n] - (lp.x[lp.n + 6] << 1) + lp.x[lp.n + 12];
		lp.y2 = lp.y1;
		lp.y1 = y0;
		y0 >>= 5;
		if (--lp.n < 0)
			lp.n = 12;
		
		return y0;
	}

	private int apply_highpass(int raw) {
		int y0;
		
		hp.x[hp.n] = hp.x[hp.n + 33] = raw;
		y0 = hp.y1 + hp.x[hp.n] - hp.x[hp.n + 32];
		hp.y1 = y0;
		if (--hp.n < 0)
			hp.n = 32;
		
		return hp.x[hp.n+16] - (y0 >> 5);
	}

	private int apply_diff(int raw) {
		int y, i;
		
		y = (raw << 1) + dif.x_derv[3] - dif.x_derv[1] - (dif.x_derv[0] << 1);
		y >>= 3;
		
		for (i = 0; i < 3; ++i)
			dif.x_derv[i] = dif.x_derv[i+1];
		dif.x_derv[3] = raw;
		
		return y;
	}

	private int apply_integ(int raw) {
		long ly;
		int y;
		
		if (++inte.ptr == 32)
			inte.ptr = 0;
		
		inte.sum -= inte.x[inte.ptr];
		inte.sum += raw;
		inte.x[inte.ptr] = raw;
		ly = inte.sum >> 5;
		if (ly > 32400)
			y = 32400;
		else
			y = (int) ly;
		
		return y;
	}

	private int apply_avg(int raw) {
		
		int x0 = raw;
		
		for (int i = 0; i < AVE_NUM - 1; i++) {
			x0 += av.x[i];
		}
		x0 = x0 / AVE_NUM;
		
		for (int i = AVE_NUM - 2; i > 0; i--)
			av.x[i] = av.x[i-1];
		av.x[0] = raw;

		return x0;
	}
	
	private int heartrate = 0;
	private long t_stop = 0;
	private long t_start = 0;
	private void detect_heartrate(int data) {

	  dt.t++;
	  
	  // if we go pass 4sec without reasonable heartrate detection, reset the values
	  if (dt.t > 1000) {
	    dt.stage = 0;
	    dt.threshold = 0;
	    dt.t = 0;
	  }
	  
	  if (dt.stage == 0 && dt.t > 20) {
	    if (data > dt.threshold) {
	      //printf("in ");
	      dt.stage = 1;
	      dt.peak = data;
	    }
	  } else if (dt.stage == 1) {
	    if (data > dt.peak) {
	      dt.peak = data;	
	    } else if (data < 0.5*dt.peak) {
	      dt.stage = 0;
	      dt.threshold = (int) ((0.125 * (dt.peak * 0.5)) + 0.875 * dt.threshold);

	      // time calls
	      t_stop = System.currentTimeMillis();                 // get stop time in milisec
	      //Log.d(TAG,"t_stop: " + t_stop + ", t_start: " + t_start);
	      
	      long d_t = t_stop - t_start;    // calculate delta t
	      
	      t_start = t_stop;                          // save stop time as next start time
	      double d_t_in_sec = (double) d_t/1000;
	      double h = 60.0/ d_t_in_sec;
	      //Log.d(TAG,"delta_t: " + d_t + ", d_t in sec: " + (double) d_t/1000 + ", h: " + h);
	      
	      
	      // only take the heartrate value if it's in a reasonable range: less than 300bpm
	      if (h < 300) {
	    	  h = (int) ((h)*0.125 + 0.875*dt.hr[4]);
	    	  //Log.d(TAG, "HEARTRATE: " + h);
	    	  if (h < dt.hr[0] + 5 && h > dt.hr[0] - 5) { // the new heartrate should differ 5 heartrates ago  by < 10
	    		  // average of 5 heartrates 
	    		  heartrate = (dt.hr[1] + dt.hr[2] + dt.hr[3] + dt.hr[4] + (int) h) / 5;
	    	  } else {
	    		  heartrate = 0; // heartrate still changing too much, don't display it yet
	    	  }
	    	  for (int i = 0; i < 4; ++i)
	    		  dt.hr[i] = dt.hr[i+1];
	    	  dt.hr[4] = (int)h;
	      }
	      
	      Log.d(TAG, "out. t = " + dt.t + ", thresh = " + dt.threshold + ", peak = " + dt.peak + ", hr = " + heartrate);
	      
	      dt.t = 0;
	    }
	  }
	}
}

