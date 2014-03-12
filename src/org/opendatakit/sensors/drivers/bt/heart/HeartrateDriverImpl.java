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
	public  static final String QRS_DURATION = "QRS_DURATION";
	
	//REMOVE LATER
	public static final String QRS_VALUES = "QRS";
	
	private static final int PAYLOAD_SIZE = 128; //including crc
	private static final int SYNC_BYTE = 0xaa; //10101010
	private static final int MAX_SYNC_BYTES = 10;
	private static final int VOLTAGE_SAMPLE_SIZE = 50; // sending 50 voltage readings at once 
	private static final int MSG_DATA_BEGIN_INDEX = 3;
	private static final int MSG_DATA_END_INDEX = 3 + VOLTAGE_SAMPLE_SIZE * 2;

	// qrs peaks index array
	private static int[] qrsPeakIndex = new int[10];
	
	
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
	
	private Lowpass bp =  new Lowpass();
	private Average av = new Average();
	private Integral inte = new Integral();
	private Differential dif = new Differential();
	private Differential dif2 = new Differential();
	private DetectHeartrate dt = new DetectHeartrate();

	
	/*
	  Lowpass filter with sampling frequency: 250 Hz

	* 0 Hz - 4 Hz
	  gain = 1
	  desired ripple = 5 dB
	  actual ripple = 1.7239660392741425 dB

	* 20 Hz - 125 Hz
	  gain = 0
	  desired attenuation = -80 dB
	  actual attenuation = -87.54790793759072 dB
	*/
	public static final int SAMPLEFILTER_TAP_NUM = 51;
	public class Lowpass {
		public int[] history = new int[SAMPLEFILTER_TAP_NUM];
		public int last_index;
		public int[] filter_taps = new int[]{
				  0,
				  0,
				  0,
				  0,
				  0,
				  0,
				  0,
				  1,
				  1,
				  2,
				  4,
				  6,
				  9,
				  13,
				  17,
				  22,
				  28,
				  34,
				  40,
				  46,
				  52,
				  58,
				  62,
				  66,
				  68,
				  68,
				  68,
				  66,
				  62,
				  58,
				  52,
				  46,
				  40,
				  34,
				  28,
				  22,
				  17,
				  13,
				  9,
				  6,
				  4,
				  2,
				  1,
				  1,
				  0,
				  0,
				  0,
				  0,
				  0,
				  0,
				  0
		};
	}
	
	
	
	
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

	
	public class DetectHeartrate {
		  public int threshold = 0, stage = 0, peak = 0;
		  public int t = 0;
		  public int[] hr = new int[5];
	}
	
	
	public HeartrateDriverImpl() {
		super();
		
		// data reporting parameters. These are the key-value pairs returned by this driver.
		sensorParams.add(new SensorParameter(HEART_RATE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Heart Rate"));
		sensorParams.add(new SensorParameter(QRS_DURATION, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "QRS Duration"));
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
	
	private int seqNo = 0;
	
	private void processCompletePacket(List<Bundle> parsedDataBundles) {
		//mt, seqNo, msg, crc
//		Log.d(TAG,"processCompletePacket payloadCounter: " + payloadCounter);
		//StringBuffer strBuff = new StringBuffer();
		//String logStr;
		
		int msgType = payloadBuffer[0] & 0xff;
		int seqLow = payloadBuffer[1] & 0xff; // sequence number low byte
		int seqHi = payloadBuffer[2] & 0xff; // sequence number high byte
		seqNo = seqHi << 8 | seqLow; // 2 byte sequence number
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
				Log.d(TAG, "SEQ #: " + seqNo);
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

	private int qrswidth_count = 0;
	private int qrs_duration = 0;
	
	Bundle getVoltSamples(byte[] voltSamples) {
		// data_array is a buffer to store the voltage values
		int[] data_array = new int[VOLTAGE_SAMPLE_SIZE];
		
		//REMOVE LATER
		int[] qrs_array = new int[VOLTAGE_SAMPLE_SIZE];
		
		int counter = 0;
		
		for(int i = MSG_DATA_BEGIN_INDEX; i < MSG_DATA_END_INDEX - 1; i+=2) {

			int voltage = (voltSamples[i + 1] << 8) + (voltSamples[i] & 0xff);
//			Log.d(TAG,"voltage: " + voltage + "(" + (voltSamples[i] & 0xff) + " + " + (voltSamples[i + 1] << 8));
			
			/* heartrate detection
			 * Fist apply algorithms to detect qrs
			 * then detect the timing of qrs occurrence
			 */
			int result = -200; // a random low number to distinguish from the high# to indicate qrs pulse
			int qrs = qrs_calculation(voltage);
			// wait until the fingers have been on the metal plate for awhile before heartrate detection
			if (seqNo >= 8) { 
				result = detect_heartrate(qrs);
			}
			

			qrs_array[counter] = result;
			
			
			/* Filters for display */
			voltage = apply_filters(voltage - 512);
			qrs_duration_detection(qrs);

			data_array[counter] = voltage;
			
			counter++;
		}
		Bundle sample = new Bundle();

		
		sample.putIntArray(VOLTAGE_VALUES, data_array);
		sample.putIntArray(QRS_VALUES, qrs_array);
		
		sample.putInt(HEART_RATE, heartrate);		
		sample.putInt(QRS_DURATION, qrs_duration);
		qrs_duration = 0;



		return sample;
	}
	
	private void qrs_duration_detection(int qrs) {
		int slope = apply_diff(qrs, dif2);
		if (slope > 0) {

			qrswidth_count++;

		} else {
			
			/* 
			 * Store qrs duration data into qrs_duration array
			 * at least 40ms qrs duration, anything below is probably noise or from T wave
			 */
			if (qrswidth_count > 10) {
				// duration (in ms) is equal to qrswidth_count * 4ms
				qrs_duration = qrswidth_count * 4;
				//Log.d(TAG,"qrs width: " + qrswidth_count);
			}
			qrswidth_count = 0;
			
		}
	}
	
	// for calculating QRS and heartrate
	private int qrs_calculation(int voltage) {
		int qrs = apply_diff(voltage, dif);
		qrs = qrs * qrs;
		qrs = apply_integ(qrs);
		//Log.d(TAG, "QRS: " + qrs);
		return qrs;
		
	}
	
	private int apply_filters(int voltage) {
		voltage = apply_lowpass(voltage);
		return voltage;
	}
	
	private int apply_lowpass(int raw) {
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
	
	private int apply_diff(int raw, Differential dif) {
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
	
	
	
	/*
	 * If need to get system time, use
	 * long time = System.currentTimeMillis();
	 */
	
	private int heartrate = 0;
	private int prev_heartrate = 0;
	private double prev_rr_interval = 0;
	private int rr_irregularity = 0;
	
	private int detect_heartrate(int data) {

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
			} else if (data < 0.50*dt.peak) {
				dt.stage = 0;
				dt.threshold = (int) ((0.125 * (dt.peak * 0.50)) + 0.875 * dt.threshold);

			
				// time in sec between adjacent R waves
				double rr_interval =  dt.t * 0.004;
				
				/*
				 * check regularity of RR Interval
				 * Considered irregular if the difference between 2 RR intervals is greater than 120ms
				 */
				if (prev_rr_interval != 0 && Math.abs(rr_interval - prev_rr_interval) > 0.12) {
					rr_irregularity++;
				}
				prev_rr_interval = rr_interval;
				
				// multiple d_t by 60 seconds to find bpm
				double h = 60 / rr_interval;
				Log.d(TAG,"RR Interval: " + rr_interval + ", d_t in sec: " + (double) rr_interval/1000 + ", h: " + h);
	      
	      
				// only take the heartrate value if it's in a reasonable range: less than 300bpm
				if (h < 300) {
					
					prev_heartrate = heartrate;
					
					// heartrate adjustment to prevent drastic changes
					if (prev_heartrate != 0)
						h = (int) ((h)*0.50 + 0.50*prev_heartrate);
   	  
					if (seqNo >= 20)
						heartrate = (int) h;
					
				}
	      
				Log.d(TAG, "out. t = " + dt.t + ", thresh = " + dt.threshold + ", peak = " + dt.peak + ", hr = " + heartrate);
	      
				dt.t = 0;
				return -50;
			}
		}
		return -200;
	}
}

