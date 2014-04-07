/*
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

import java.sql.Date;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer.FillOutsideLine;
import org.opendatakit.sensors.service.BaseActivity;
import org.achartengine.renderer.XYSeriesRenderer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/* 
 * Activities that communicate with ODK Sensors need to extend org.opendatakit.sensors.service.BaseActivity, which provides the methods 
 * to interface with the Sensors framework. Typically, methods from BaseActivity are invoked in the following sequence:
 * 
 * 1) launchSensorDiscovery(): this starts the sensor discovery process in ODKSensors. This allows the activity to discover the ID of 
 * the sensor it needs to communicate to. If the activity stores the sensorID persistently, this might just be a 1-time thing.
 * 2) sensorConnect(...): this establishes a connection with the sensor.
 * 3) configure(...): Sensors often have configurable parameters, this call allows configuration of these parameters. This is an 
 * optional call.
 * 4) startSensor(...): ODKSensors starts collecting data from the sensor after this method call.
 * 5) getSensorData(...): Activities call this method periodically to get sensor data from the framework.
 * 6) stopSensor(...): ODKSensors stops collecting data from the sensor after this method call.
 *  
 */
public class HeartrateDriverActivity extends BaseActivity {
 
	private static final String TAG = "SensorDriverActivity";
	
////////////////////// Connection and Sensor related Variables and Constants //////////////////////

	private static final String HR_SENSOR_ID_STR = "EKG_SENSOR_ID";	
	private static final int SENSOR_CONNECTION_COUNTER = 10;
	private static final int REQUEST_ENABLE_BT = 2;
	
	//each physical sensor has a unique sensorID. Activities use this sensorID to communicate with sensors via the framework.
	private String sensorID = null;
	private boolean isConnectedToSensor = false;
	private boolean isStarted = false;
	private DataProcessor sensorDataProcessor;
	private ConnectionThread connectionThread;
	
	
/////////////////////// Variables for res/layout/plot.xml /////////////////////////////////////////	
	
	private TextView heartRateField;
	private TextView activityStatusField;
    private LinearLayout plot;
	
    
/////////////////////////// Heartrate Detection Variables & Constants ///////////////////////////////
    
	private static final int VOLTAGE_SAMPLE_SIZE = 50; // size of voltage array sent from HeartrateDriverImpl.java
	private static final int DETECTION_TIME = 7500; 
	private static final int NO_HEARTBEAT_DETECTED = -200;
	private static final int HEARTBEAT_DETECTED = 200;
	private static final String[] HEART_CONDITION_OPTIONS = {
        "Normal", "Bradycardia", "Tachycardia", "Abnormal QRS Complex", "Premature Atrial Contraction"};
    
	private static int heartRate = 0;
	private static int qrs_duration = 0;
	private static int regularity = 0;
	private static int p_wave = 0;
	private int qrs_duration_width_count = 0;
	private static int condition;
    // voltage array from HeartrateDRiverImpl
	private static int[] voltageValuesArray;
	// peak detection value and array
	private static int heartBeatValue = NO_HEARTBEAT_DETECTED;
	private static int[] heartBeatArray = new int[DETECTION_TIME];
	// ecg value and array displayed and stored in the database
	private static int ecgValue = 0;
	private static int[] ecgWaveformArray = new int[DETECTION_TIME];
    private static int index = 0;
	// Heartrate array
	private static int[] hrArray = new int[200];
	private static int hrIndex = 0;
	// qrs duration array
	private static int[] qrsArray = new int[200];
	private static int qrsIndex = 0;
	
	private static int seqNo = 0;
	
	// For filtering and analysis
	private Lowpass bp =  new Lowpass();
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
	
	
	
	// currently not used
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
	
	

///////////////////////// ECG Plot Constants and Variables /////////////////////////////////
	
	private static final int SAMPLE_RATE = 250; // 250 samples per second
	private static final int SAMPLE_SIZE = 1000; //200 sample_size and 50 pdu_size
	private static final int RANGE_MIN = -100;
	private static final int RANGE_MAX = 100;
	private static final int DOMAIN_MIN = 0;
	private static final int DOMAIN_MAX = 1000; 
	private static final int DOMAIN_STEP_VALUE = (int) ((SAMPLE_SIZE / SAMPLE_RATE) / 0.04); // each square is 0.04sec
	private static final int RANGE_STEP_VALUE = (int) (RANGE_MAX - RANGE_MIN) / 10;
	
	private XYSeries voltageSeries;
	private XYSeries qrsLines;
    private static XYMultipleSeriesDataset dataset;
    private static XYMultipleSeriesRenderer renderer;
    private static XYSeriesRenderer rendererSeries;
    private static XYSeriesRenderer qrsrendererSeries;
    private static GraphicalView waveform;

    
///////////////////////// Database Variable ///////////////////////////////////////////////////////
	
    public static DatabaseHelper dbhelper;
    private Patient pat;
    
    
    
//////////////////////////////////////////// Alert dialog Variables ///////////////////////////////
    private static AlertDialog alertDialog;
    
    
//////////////////////////////// Preferences Variables & Constants ///////////////////////////////

    private SharedPreferences mPrefs;
    // View Setting
    private static final String VIEW_KEY = "viewsetting";
    private int viewId = 1;
    
    private static final int[][] VIEW_SCHEMES = {
        {-50, 50}, {-100, 100}, {-200, 200}};
	

////////////////////////////// End of Variables and Constants Declaration /////////////////////////
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// automatically set orientation to landscape
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);		
		setContentView(R.layout.plot);
		plot = (LinearLayout) findViewById(R.id.ecgplot);
		heartRateField = (TextView) findViewById(R.id.heartRateField);
		activityStatusField = (TextView) findViewById(R.id.activityStatusField);
		
		// Preferences setting
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        readPrefs();
		
		// Initialize Plot setting
		plot_init();
		
		// Initialize DatabaseHelper
		dbhelper = new DatabaseHelper(this);

		// Restore previous sensorID
		restore_sensorID();

	}

	/*
	 * Initializes the ecgplot
	 */
	public void plot_init() {
        dataset = new XYMultipleSeriesDataset();
        renderer = new XYMultipleSeriesRenderer();
        renderer.setApplyBackgroundColor(true);
        renderer.setBackgroundColor(Color.argb(255, 255, 255, 255));
        renderer.setAxisTitleTextSize(16);
        renderer.setChartTitleTextSize(20);
        renderer.setLabelsTextSize(15);
        renderer.setLegendTextSize(15);
        renderer.setMargins(new int[] { 20, 30, 15, 0 });
        renderer.setZoomButtonsVisible(true);
        renderer.setPointSize(10);
        renderer.setShowGrid(true);
        renderer.setGridColor(Color.RED);
        
        /* 25small squares/sec
         * we are fitting 1000 samples(4 sec) in a screen
         * we need to have 100 small squares
         */
        renderer.setXLabels(100);
        
        /* 25small squares/sec
         * we are fitting 1000 samples(4 sec) in a screen
         * we need to have 100 small squares
         */
        renderer.setYLabels(50);
        
        // x and y axis labels
        renderer.setShowLabels(false);

        renderer.setYAxisMin(RANGE_MIN);
        renderer.setYAxisMax(RANGE_MAX);
        renderer.setXAxisMin(DOMAIN_MIN);
        renderer.setXAxisMax(DOMAIN_MAX);
        renderer.setPanLimits(new double[] { DOMAIN_MIN, DOMAIN_MAX, 0, 0 });
        renderer.setZoomLimits(new double[] { DOMAIN_MIN, DOMAIN_MAX, 0, 0 });

        rendererSeries = new XYSeriesRenderer();
        rendererSeries.setColor(Color.BLACK);
        rendererSeries.setLineWidth(2);

        qrsrendererSeries = new XYSeriesRenderer();
        qrsrendererSeries.setColor(Color.BLUE);
        qrsrendererSeries.setLineWidth(2);
        
        renderer.addSeriesRenderer(rendererSeries);
        renderer.addSeriesRenderer(qrsrendererSeries);
        
        voltageSeries = new XYSeries("ECG waveform");
        dataset.addSeries(voltageSeries);
        
        qrsLines = new XYSeries("qrs peaks");
        dataset.addSeries(qrsLines);


        waveform = ChartFactory.getLineChartView(this, dataset, renderer);
        waveform.refreshDrawableState();
        waveform.repaint();

        plot.addView(waveform);
        
        
    }
	
	/*
	 * If sensorID(of a specific foneAstra) is stored earlier, this function restores the
	 * sensorID, so the application doesn't have to go through device discovery again 
	 */
    private void restore_sensorID() {
		SharedPreferences appPreferences = getPreferences(MODE_PRIVATE);
		
		//restore the sensorID if we stored it earlier
		if(appPreferences.contains(HR_SENSOR_ID_STR)) {
			sensorID = appPreferences.getString(HR_SENSOR_ID_STR, null);
			
			if(sensorID != null) {
				Log.d(TAG,"restored sensorID: " + sensorID);
			}
		}	
	}
	
	protected void onResume() {
		super.onResume();
		
		dbhelper.open();
		pat = (Patient) getIntent().getParcelableExtra("patient");
		
    	readPrefs();
    	updatePrefs();
    	
    	
    	activityStatusField.setText(String.valueOf("detecting, place both hands on the pads"));
    	
		if(!isConnectedToSensor) {
			activityStatusField.setText(String.valueOf("not connected"));	
		} else if (isStarted) { 
			sensorDataProcessor = new DataProcessor();
			sensorDataProcessor.execute();
			activityStatusField.setText(String.valueOf("detecting, place both hands on the pads"));
		} else {
			activityStatusField.setText(String.valueOf("connected:" + sensorID));	
		}			
	}
	
    protected void onPause() {
        dbhelper.close();
        super.onPause();
    }
	
    /*
     * Connects to a FoneAstra device
     */
	public void connectAction() {

		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(mBluetoothAdapter == null) {
			Toast.makeText(getApplicationContext(), "Device doesn't support Bluetooth", Toast.LENGTH_LONG).show();
		}
	    
	    if(!mBluetoothAdapter.isEnabled()) {
	    	Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); 
	    	startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
	    	//Toast.makeText(getApplicationContext(), "Enabling Bluetooth!!", Toast.LENGTH_LONG).show();
	    }
		
		if(sensorID == null) {
			//launch the framework's discovery process if we don't already have a sensor ID.
			//the discovery process allows users to discover sensors and assign drivers to them.
			//launchSensorDiscovery basically starts an activity for result in the framework, 
			//so the discovery results are returned in the onActivityResult method below. 
			super.launchSensorDiscovery();
			return;
		}

		if (!isConnectedToSensor) {
			//establish a connection to the physical sensor if we aren't connected already.
			connectToSensor();
			Log.d(TAG, "connect to sensor succeed");
		}
	}
	
	/*
	 * Starts/restarts data collection and plotting
	 */
	public void startAction() {
		
		sensorDataProcessor = new DataProcessor();
		sensorDataProcessor.execute();
		
		try {
			//startSensor needs to be called after connecting to the physical sensor. 
			//sensor data can be received from the framework after this.

			super.startSensor(sensorID);
			index = 0;
			hrIndex = 0;
			qrsIndex = 0;
			voltageSeries.clear();
			qrsLines.clear();
			waveform.repaint();
			
        	//Toast.makeText(this, "Sensoring Started", Toast.LENGTH_SHORT).show();
        	
			Log.d(TAG, "startSensor succeed");
		}
		catch(RemoteException rex) {
			rex.printStackTrace();
		}
		
		activityStatusField.setText(String.valueOf("detecting, place both hands on the pads"));
		isStarted = true;
		
	}

	
	@Override
	public void onDestroy() {
		
		if (sensorID != null) {
			try {
				//call stopSensor to stop receiving data from the framework.
				stopSensor(sensorID);
				isStarted = false;
			}
			catch(RemoteException rex) {
				rex.printStackTrace();
			}
		}
		
		//amongst other things, the super.onDestroy terminates the connection between the activity and the Sensors framrwork. 
		super.onDestroy();
	}

	/*
	 * Sensor data received from ODK Sensors is returned in a result intent.
	 */
	private void returnSensorDataToCaller() {
		Intent intent = new Intent();
		intent.putExtra(HeartrateDriverImpl.SEQ_NUM, seqNo);
		intent.putExtra(HeartrateDriverImpl.VOLTAGE_VALUES, voltageValuesArray);
		setResult(RESULT_OK, intent);
		
		finish();
	}
	
	public void onConfigurationChanged(Configuration newConfig) {
	    super.onConfigurationChanged(newConfig);
	}
	
	/*
	 * sensorID is returned in the onActivityResult after sensor discovery completes.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		super.onActivityResult(requestCode, resultCode, data);

		Log.d(TAG, "onActivityResult resultCode" + resultCode
				+ "  and  requestCode" + requestCode);

		//the result code is set by ODK Sensors.
		if (requestCode == SENSOR_DISCOVERY_RETURN) {
			// from addSensorActvitity
			if (resultCode == RESULT_OK) {
				// Get sensor id from result
				if (data.hasExtra("sensor_id")) {
					sensorID = data.getStringExtra("sensor_id");

					// update sensor id stored in preferences
					SharedPreferences.Editor prefsEditor = getPreferences(MODE_PRIVATE).edit();
					prefsEditor.putString(HR_SENSOR_ID_STR, sensorID);
					prefsEditor.commit();

					// connect to sensor
					connectToSensor();
				} else {
					Log.d(TAG, "activity result returned without sensorID");
				}
			}
		}
	}
	
	protected void showdialog() {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
 
	    // Get the layout inflater
	    LayoutInflater inflater = this.getLayoutInflater();
	    
		// set title
		alertDialogBuilder.setTitle("Would you like to save this trace?");
		 
		// set dialog message
		alertDialogBuilder
			.setView(inflater.inflate(R.layout.dialoglayout, null))
			//.setMessage("Name and Genger")
			//.setCancelable(false)
			.setPositiveButton("Discard",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					// if this button is clicked, close the dialog
					index = 0;
					hrIndex = 0;
					qrsIndex = 0;
					alertDialog.dismiss();
					sensorDataProcessor = new DataProcessor();
					sensorDataProcessor.execute();
				}
			  })
			.setNegativeButton("Save",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					// if this button is clicked, the trace is saved to the database
					// and the view activity will pop up
					Dialog f = (Dialog) dialog;

	                heartRate = computeAverageHR();
	                qrs_duration = computeAverage_qrs_duration();
	                regularity = evaluateRegularity();
	                p_wave = evaluatePwave();

	                // add new data into patient database
					dbhelper.addecgData(pat, new ECG_Data(getDateTime(), 
							Arrays.toString(ecgWaveformArray), heartRate, qrs_duration, regularity, p_wave));
					
					// start View Activity
					open_ViewActivity();
					
					alertDialog.dismiss();
				}
			});
		 
			// create alert dialog
			alertDialog = alertDialogBuilder.create();
		 
			// show it
			alertDialog.show();
	}

///////////////////////////////////////////////////////////////////////////////////////////////////
//                  Functions that analyze different Heart-related components                    //
/////////////////////////////////////////////////////////////////////////////////////////////////// 	

	private void process_raw_voltage_values() {
		
		for (int i = 0; i < voltageValuesArray.length; i++) {
		    
			/*
			 * After a length of DETECTION_TIME, a dialog will pop up to save the trace
			 * Done sampling
			 */
        	if (index >= DETECTION_TIME) {
                Log.d(TAG, "qrsindex: " + qrsIndex);
                Log.d(TAG, "hrindex: " + hrIndex);
                sensorDataProcessor.cancel(true);
        		// show dialog for the option of saving the trace to the database
        		showdialog();
                return;
        	}
        	
        	if (index % DOMAIN_MAX == 0) {
        		voltageSeries.clear();
        		qrsLines.clear();
        	}

    		int processed_voltage_value = apply_algorithms_for_heartrate_detection(voltageValuesArray[i]);

    		
    		heartBeatValue = detect_heartrate(processed_voltage_value);
    		
    		
    		qrs_duration_detection(processed_voltage_value);
    		if (qrs_duration != 0) {
    			qrsArray[qrsIndex++] = qrs_duration;
    		}
    		qrs_duration = 0;
    		
    		
    		/* Apply filters for display */
    		ecgValue = apply_filters_for_display(voltageValuesArray[i] - 512);
        	
        	voltageSeries.add(index % DOMAIN_MAX, ecgValue);
        	qrsLines.add((index % DOMAIN_MAX) - 5, heartBeatValue); // minus 5 for allignment purposes
        	
        	if (index < DETECTION_TIME) {
        		ecgWaveformArray[index] = ecgValue;
        		heartBeatArray[index] = heartBeatValue;
        	}
        	index++;
        	
        	waveform.repaint();
        	
	        //Log.d(TAG, "heartVOl: " + voltageValues[i]);
        	
			//update heartrate field on the display
			Log.d(TAG, "heartrate: " + heartRate + ", qrs_duration: " + qrs_duration);
			//Log.d(TAG, "beatcount: " + beatCount);
			if (heartRate == 0) {
				heartRateField.setText(String.valueOf("Detecting Heartrate"));
			} else {
				heartRateField.setText(String.valueOf(heartRate));
			}
			//timeField.setText(String.valueOf(beatCount));
        }
	}
	
	private void qrs_duration_detection(int qrs) {
		int slope = differentiation_algorithm(qrs, dif2);
		if (slope > 0) {
			qrs_duration_width_count++;
		} else {
			
			/* 
			 * Store qrs duration data into qrs_duration array
			 * at least 40ms qrs duration, anything below is probably noise or from T wave
			 */
			if (qrs_duration_width_count > 10) {
				// duration (in ms) is equal to qrswidth_count * 4ms
				qrs_duration = qrs_duration_width_count * 4;
				//Log.d(TAG,"qrs width: " + qrswidth_count);
			}
			qrs_duration_width_count = 0;
		}
	}
	
	// for calculating heartrate
	private int apply_algorithms_for_heartrate_detection(int voltage) {
		int value = differentiation_algorithm(voltage, dif);
		value = value * value; // square function
		value = integration_algorithm(value);
		//Log.d(TAG, "QRS: " + qrs);
		return value;
		
	}
	
	// filters used for display
	private int apply_filters_for_display(int voltage) {
		voltage = lowpass_filter(voltage);
		return voltage;
	}
	
	private int lowpass_filter(int raw) {
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
	
	private int differentiation_algorithm(int raw, Differential dif) {
		int y, i;
		
		y = (raw << 1) + dif.x_derv[3] - dif.x_derv[1] - (dif.x_derv[0] << 1);
		y >>= 3;
		
		for (i = 0; i < 3; ++i)
			dif.x_derv[i] = dif.x_derv[i+1];
		dif.x_derv[3] = raw;
		
		return y;
	}

	private int integration_algorithm(int raw) {
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

/*
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
*/	
	
	
	/*
	 * If need to get system time, use
	 * long time = System.currentTimeMillis();
	 */
	
	private int prev_heartrate = 0;
	private double prev_rr_interval = 0;
	private int rr_irregularity = 0;

	// calculate heartrate and returns whether a heartbeat is detected
	private int detect_heartrate(int data) {
		// wait until the fingers have been on the metal plate for awhile before heartrate detection
		if (seqNo < 8) {
			return NO_HEARTBEAT_DETECTED;
		}
		
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
					
					prev_heartrate = heartRate;
					
					// heartrate adjustment to prevent drastic changes
					if (prev_heartrate != 0)
						h = (int) ((h)*0.50 + 0.50*prev_heartrate);
   	  
					if (seqNo >= 20) {
						heartRate = (int) h;
						if (heartRate != 0)
							hrArray[hrIndex++] = heartRate;
					}
				}
	      
				Log.d(TAG, "out. t = " + dt.t + ", thresh = " + dt.threshold + ", peak = " + dt.peak + ", hr = " + heartRate);
	      
				dt.t = 0;
				return HEARTBEAT_DETECTED;
			}
		}
		return NO_HEARTBEAT_DETECTED;
	}
	
	private int computeAverageHR() {
		int sum = 0;
		for (int i = 0; i < hrIndex; i++) {
			sum += hrArray[i];
		}
		// duration is the number of counts * 4ms
		if (hrIndex != 0)
			return sum / hrIndex;
		else 
			return 0;
	}
	
	private int computeAverage_qrs_duration() {
		int sum = 0;
		for (int i = 0; i < qrsIndex; i++) {
			sum += qrsArray[i];
		}
		// duration is the number of counts * 4ms
		if (qrsIndex != 0)
			return sum / qrsIndex;
		else 
			return 0;

	}

	
	private int evaluateRegularity() {
		return 0;
	}
	
	private int evaluatePwave() {
		return 0;
	}
	
	/*
	private int detectHeartCondition() {
		if (heartRate < 60) {
			// Bradycardia condition
			return 1;
		} else if (heartRate > 100) {
			// Tachycardia condition
			return 2;
		} else if (qrs_duration > 110) { // normal is 60-110ms
			return 3;
		} else {
			return 0;
		}
	}
	*/
	
///////////////////////////////////////////////////////////////////////////////////////////////////
//                           DataProcessing and Connection Functions                             //
///////////////////////////////////////////////////////////////////////////////////////////////////  

	/*
	 * The main function of this activity. Receives values from calling getSensorData
	 * Responsible for displaying the data to the users and putting the data to corresponding
	 * data structures for processing and storage of the data
	 */
	private void processData() {

		if (!isConnectedToSensor) {
			return;
		}				

		runOnUiThread(new Runnable() {
			public void run() {
				List<Bundle> sensorDataBundles = null;

				try {
//					Log.d(TAG,"getSensorData");
					
					//call the getSensorData method periodically to get sensor data as key-value pairs from ODKSenors
					sensorDataBundles = getSensorData(sensorID, 1);
				}
				catch(RemoteException rex) {
					rex.printStackTrace();
					Log.d(TAG, "getSensorData Failed");
				}

				if(sensorDataBundles != null) {
					for(Bundle aBundle : sensorDataBundles) {
						
						//retrieve sensor data from each bundle and store it locally. 
						
						// Unfiltered heart voltage values
						voltageValuesArray = aBundle.getIntArray(HeartrateDriverImpl.VOLTAGE_VALUES);
						seqNo = aBundle.getInt(HeartrateDriverImpl.SEQ_NUM);

						Log.d(TAG, "VOLTAGE LENGTH: " + voltageValuesArray.length);
						
						/*
						 * IMPORTANT FUNCTION: filters voltage values for display; processes voltage values for various detections
						 */
						process_raw_voltage_values();
						
					}
				}
			}
		});
	}
	
	private class DataProcessor extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			while (!isCancelled()) {
				processData();
				try {
					Thread.sleep(150);
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
			return null;
		}
	}
	
	/*
	 * Heart rate monitor is a bluetooth enabled sensor. so we connect in a thread, waiting for the connection to get established.
	 */
	private void connectToSensor() {
		if(connectionThread != null && connectionThread.isAlive()) {
			connectionThread.stopConnectionThread();
		}
		
		runOnUiThread(new Runnable() {
			public void run() {
				activityStatusField.setText(String.valueOf("connecting..."));
			}
		});

		connectionThread = new ConnectionThread();
		connectionThread.start();
	}
	
	private class ConnectionThread extends Thread {
		private String TAG = "ConnectionThread";
		private boolean isConnThreadRunning = false;		

		public ConnectionThread() {
			super("Printer Connection Thread");
		}

		@Override
		public void start() {
			isConnThreadRunning = true;
			super.start();
		}

		public void stopConnectionThread() {
			isConnThreadRunning = false;

			try {
				this.interrupt();
				isConnectedToSensor = false;
				Thread.sleep(250);
			}
			catch(InterruptedException iex) {
				Log.d(TAG,"stopConnectionThread got interrupted");
			}
		}

		public void run() {
			int connectCntr = 0;

			try {
				//this initiates connection establishment in ODK sensors.
				sensorConnect(sensorID, false);

				while (isConnThreadRunning && (connectCntr++ < SENSOR_CONNECTION_COUNTER)) {
					try {

						if(isConnected(sensorID)) {
							isConnectedToSensor = true;
							break;
						}
						Log.d(TAG,"connectThread waiting to connect to sensor");
						Thread.sleep(1000);
					}
					catch(InterruptedException iex) {
						Log.d(TAG, "interrupted");
					}
				}

				Log.d(TAG,"connectThread connect status: " + isConnectedToSensor);
				
				runOnUiThread(new Runnable() {
					public void run() {
						if(!isConnectedToSensor) {
							activityStatusField.setText(String.valueOf("not connected"));						
						}
						else {
							activityStatusField.setText(String.valueOf("connected:" + sensorID));						
						}
					}
				});

			}
			catch(RemoteException rex) {
				rex.printStackTrace();
			}
		}
	}
	
	
///////////////////////////////////////////////////////////////////////////////////////////////////
//                          Functions related to the options menu                                //
///////////////////////////////////////////////////////////////////////////////////////////////////  	
	
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.options_menu, menu);
	    return true;
	}
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.connect:
        	
        	connectAction();

            return true;
        case R.id.start:
        	
        	startAction();
        	
            return true;
        case R.id.view:
        	
        	open_ViewActivity();
            
        	return true;
        case R.id.patientList:
            
        	open_DatabaseActivity();
            
            return true;            
        case R.id.preferences:
  
        	doPreferences();
        	//registerForContextMenu(view_setting);
        	
        	return true;
        }        
        return false;
    }
    
    
///////////////////////////////////////////////////////////////////////////////////////////////////
//           Functions in this section are for opening various activity classes                  //
///////////////////////////////////////////////////////////////////////////////////////////////////      
    
    /*
     * Opens DatabaseActivity
     */
    private void open_DatabaseActivity() {
    	Intent j = new Intent(this,DatabaseActivity.class);
        startActivity(j);
    }
    
    /*
     * Opens ViewActivity
     */
    private void open_ViewActivity() {
        Intent i = new Intent(this,ViewActivity.class);
        i.putExtra("xyseries", ecgWaveformArray);
        i.putExtra("heartrate", heartRate);
        i.putExtra("qrs_duration", qrs_duration);
        i.putExtra("regularity", regularity);
        i.putExtra("p_wave", p_wave);
        startActivity(i);
    }
    

///////////////////////////////////////////////////////////////////////////////////////////////////
//                   Functions in this section are for Preferences settings                      //
///////////////////////////////////////////////////////////////////////////////////////////////////    
    
	private void doPreferences() {
        startActivity(new Intent(this, SetPreferenceActivity.class));
    }

    /*
     *  update changes made to preferences
     */
    private void updatePrefs() {
        setView();

        plot.removeAllViews();  //This remove previous graph
        plot.addView(waveform); //This loads the graph again
    }
    
	private void readPrefs() {
		viewId = readIntPref(VIEW_KEY, viewId, VIEW_SCHEMES.length - 1);
	}
	
    private int readIntPref(String key, int defaultValue, int maxValue) {
        int val;
        try {
            val = Integer.parseInt(
                mPrefs.getString(key, Integer.toString(defaultValue)));
            Log.d(TAG,"val: " + val);
        } catch (NumberFormatException e) {
            val = defaultValue;
        }
        val = Math.max(0, Math.min(val, maxValue));
        Log.d(TAG,"val: " + val);
        return val;
    }

    /*
     * set the new view in response to preference setting
     */
	private void setView() {
        int[] scheme = VIEW_SCHEMES[viewId];
        renderer.setYAxisMin(scheme[0]);
        renderer.setYAxisMax(scheme[1]);
        Log.d(TAG,"set view: " + scheme[0] + ", " + scheme[1]);
	}
	
///////////////////////////////////////////////////////////////////////////////////////////////////
//                                                                                               //
/////////////////////////////////////////////////////////////////////////////////////////////////// 
	
    /**
     * get datetime
     * */
    private String getDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = new Date(System.currentTimeMillis());
        return dateFormat.format(date);
    }
	
}
