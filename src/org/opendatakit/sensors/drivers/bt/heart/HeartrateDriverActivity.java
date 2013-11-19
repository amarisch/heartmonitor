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

import java.text.DecimalFormat;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import org.opendatakit.sensors.service.BaseActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;

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
 
	private static final String HR_SENSOR_ID_STR = "ZEPHYR_SENSOR_ID";
	private static final String TAG = "SensorDriverActivity";
	private static final int SENSOR_CONNECTION_COUNTER = 10;
	
	//each physical sensor has a unique sensorID. Activities use this sensorID to communicate with sensors via the framework.
	private String sensorID = null;
	
	private boolean isConnectedToSensor = false;
	
	private boolean isStarted = false;
	
	private volatile int heartRate, beatCount;
	
	private DataProcessor sensorDataProcessor;
	
	private Button connectButton, startButton;
	
	private ConnectionThread connectionThread;

	private static final int SAMPLE_SIZE = 250;
	
    // main plot
    private XYPlot dynamicPlot = null;
    // data series for EKG
    private SimpleXYSeries heartSeries = null;
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.plot);
		
		 
        // get handles to our View defined in layout.xml:
        dynamicPlot = (XYPlot) findViewById(R.id.dynamicPlot);
        heartSeries = new SimpleXYSeries("EKG");
        // Use index value as xVal, instead of explicit, user provided xVals.
        heartSeries.useImplicitXVals();
        
        dynamicPlot.setRangeBoundaries(0, 250, BoundaryMode.FIXED);
        dynamicPlot.setDomainBoundaries(0, 200, BoundaryMode.FIXED);
        dynamicPlot.addSeries(heartSeries, new LineAndPointFormatter());
        dynamicPlot.setDomainStepValue(1);
        dynamicPlot.setTicksPerRangeLabel(3);
        dynamicPlot.setDomainLabel("Time Index");
        dynamicPlot.getDomainLabelWidget().pack();
        dynamicPlot.setRangeLabel("Voltage");
        dynamicPlot.getRangeLabelWidget().pack();
        
		connectButton = (Button)findViewById(R.id.connectButton);
		startButton = (Button)findViewById(R.id.startButton);
		
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
		
		sensorDataProcessor = new DataProcessor();
		//sensorDataProcessor.execute();
		
		connectButton.setEnabled(false);
		startButton.setEnabled(false);		
		
		if(!isConnectedToSensor) {
			connectButton.setEnabled(true);
			startButton.setEnabled(false);	
		}
		else if (isStarted) { 
			connectButton.setEnabled(false);
			startButton.setEnabled(false);	;
		}
		else {
			connectButton.setEnabled(false);
			startButton.setEnabled(true);	
		}
			
	}
	
    protected void onPause() {
        super.onPause();
		sensorDataProcessor.cancel(true);
    }
	
	public void connectAction(View view) {

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
	
	public void startAction(View view) {
		
		try {
			//startSensor needs to be called after connecting to the physical sensor. 
			//sensor data can be received from the framework after this.
			sensorDataProcessor.execute();
			super.startSensor(sensorID);
			Log.d(TAG, "startSensor succeed");
		}
		catch(RemoteException rex) {
			rex.printStackTrace();
		}
		
		startButton.setEnabled(false);
		isStarted = true;
		
	}

	
	@Override
	public void onDestroy() {
		
		try {
			//call stopSensor to stop receiving data from the framework.
			stopSensor(sensorID);
			isStarted = false;
		}
		catch(RemoteException rex) {
			rex.printStackTrace();
		}
		
		//amongst other things, the super.onDestroy terminates the connection between the activity and the Sensors framrwork. 
		super.onDestroy();
	}

	/*
	 * Sensor data received from ODK Sensors is returned in a result intent.
	 */
	private void returnSensorDataToCaller() {
		Intent intent = new Intent();
		intent.putExtra(HeartrateDriverImpl.HEART_RATE, heartRate);
		intent.putExtra(HeartrateDriverImpl.BEAT_COUNT, beatCount);
		setResult(RESULT_OK, intent);
		
		finish();
	}
	
	public void onConfigurationChanged(Configuration newConfig) {
	    super.onConfigurationChanged(newConfig);

	    // Checks the orientation of the screen
	    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
	        Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
	    } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
	        Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
	    }
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
	
	/*
	 * The Zephyr heart rate monitor is a bluetooth enabled sensor. so we connect in a thread, waiting for the connection to get established.
	 */
	private void connectToSensor() {
		if(connectionThread != null && connectionThread.isAlive()) {
			connectionThread.stopConnectionThread();
		}
		
		runOnUiThread(new Runnable() {
			public void run() {
				connectButton.setEnabled(false);
			}
		});

		connectionThread = new ConnectionThread();
		connectionThread.start();
	}
	
	private void processData() {

		if (!isConnectedToSensor) {
			return;
		}				

		runOnUiThread(new Runnable() {
			public void run() {
				List<Bundle> sensorDataBundles = null;

				try {
					Log.d(TAG,"getSensorData");
					
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
						
						heartRate = aBundle.getInt(HeartrateDriverImpl.HEART_RATE);
						beatCount = aBundle.getInt(HeartrateDriverImpl.BEAT_COUNT);
						
						// get rid the oldest sample in history:
				        if (heartSeries.size() > SAMPLE_SIZE) {
				        	heartSeries.removeFirst();
				        }
				        heartSeries.addLast(null, beatCount);
						dynamicPlot.redraw();
						//update UI
						Log.d(TAG, "heartrate: " + heartRate);
						Log.d(TAG, "beatcount: " + beatCount);
						//heartRateField.setText(String.valueOf(heartRate));
						//timeField.setText(String.valueOf(beatCount));
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
					Thread.sleep(1000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return null;
		}
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
							connectButton.setEnabled(true);
							startButton.setEnabled(false);							
						}
						else {
							connectButton.setEnabled(false);
							startButton.setEnabled(true);							
						}
					}
				});

			}
			catch(RemoteException rex) {
				rex.printStackTrace();
			}
		}
	}
}
