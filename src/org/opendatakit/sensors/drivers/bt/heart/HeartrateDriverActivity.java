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

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.LayoutManager;
import com.androidplot.ui.SizeLayoutType;
import com.androidplot.ui.SizeMetrics;
import com.androidplot.ui.XLayoutStyle;
import com.androidplot.ui.YLayoutStyle;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYGraphWidget;
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
 
	private static final String HR_SENSOR_ID_STR = "EKG_SENSOR_ID";
	private static final String TAG = "SensorDriverActivity";
	private static final int SENSOR_CONNECTION_COUNTER = 10;
	
	//each physical sensor has a unique sensorID. Activities use this sensorID to communicate with sensors via the framework.
	private String sensorID = null;
	
	private boolean isConnectedToSensor = false;
	
	private boolean isStarted = false;
	
	private volatile int heartRate;
	
	private TextView heartRateField, conditionField;
	
	private volatile int[] voltageValues;
	
	private DataProcessor sensorDataProcessor;
	
//	private Button connectButton, startButton;
	
	private ConnectionThread connectionThread;

	private static final int SAMPLE_SIZE = 1000; //200 sample_size and 50 pdu_size
	private static final int RANGE_MIN = 300;
	private static final int RANGE_MAX = 700;
	private static final int REQUEST_ENABLE_BT = 2;
    // main plot
    private XYPlot dynamicPlot = null;
    // data series for EKG
    private SimpleXYSeries heartSeries = null;
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.plot);
		
		//heartRateField = (TextView) findViewById(R.id.heartRateField);
		//conditionField = (TextView) findViewById(R.id.conditionField);
		
		// Shown now as a placeholder. remove later
		//heartRateField.setText(String.valueOf(heartRate));
		//conditionField.setText(String.valueOf(heartRate));
		
		// Initialize AndroidPlot setting
		plot_init();

		SharedPreferences appPreferences = getPreferences(MODE_PRIVATE);
		
		//restore the sensorID if we stored it earlier
		if(appPreferences.contains(HR_SENSOR_ID_STR)) {
			sensorID = appPreferences.getString(HR_SENSOR_ID_STR, null);
			
			if(sensorID != null) {
				Log.d(TAG,"restored sensorID: " + sensorID);
			}
		}	
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.options_menu, menu);
	    return true;
	}
	
	protected void onResume() {
		super.onResume();
		
		sensorDataProcessor = new DataProcessor();
		//sensorDataProcessor.execute();
		
/*		connectButton.setEnabled(false);
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
*/			
	}
	
    protected void onPause() {
        super.onPause();
		sensorDataProcessor.cancel(true);
    }
	
	public void connectAction() {

		if(sensorID == null) {
			//BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			//Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			
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
	
	public void startAction() {
		
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
		
//		startButton.setEnabled(false);
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
		intent.putExtra(HeartrateDriverImpl.VOLTAGE_VALUES, voltageValues);
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
		// disable the connection if it already exist
		if(connectionThread != null && connectionThread.isAlive()) {
			connectionThread.stopConnectionThread();
		}
		
		// disable the connectButton
		runOnUiThread(new Runnable() {
			public void run() {
//				connectButton.setEnabled(false);
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
						voltageValues = aBundle.getIntArray(HeartrateDriverImpl.VOLTAGE_VALUES);
						
				        
				        for (int i = 0; i < voltageValues.length; i++) {
					    
							// write to the end of the screen, start from the beginning
					        if (heartSeries.size() >= SAMPLE_SIZE) {
					        	// erase data
					        	while (heartSeries.size() > 0) {
					        		heartSeries.removeFirst();
					        	}
					        	
					        	//heartSeries.removeFirst();
					        }
					        
					        Log.d(TAG, "heartVOl: " + voltageValues[i]);
				        	heartSeries.addLast(null, voltageValues[i]);				        
							dynamicPlot.redraw();
				        
				        }
				        

						//update UI
						//Log.d(TAG, "heartrate: " + heartRate);
						//Log.d(TAG, "beatcount: " + beatCount);
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
					Thread.sleep(150);
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
/*
						if(!isConnectedToSensor) {
							connectButton.setEnabled(true);
							startButton.setEnabled(false);							
						}
						else {
							connectButton.setEnabled(false);
							startButton.setEnabled(true);							
						}
*/
					}
				});

			}
			catch(RemoteException rex) {
				rex.printStackTrace();
			}
		}
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
/*        case R.id.preferences:
        	doPreferences();
            return true;
        case R.id.menu_special_keys:
            doDocumentKeys();
            return true;
        case R.id.menu_remote:
        	sendStartCommand();
        	return true;
*/
        }        
        return false;
    }
    
    public void plot_init() {
		// initialize our XYPlot reference:
        dynamicPlot = (XYPlot) findViewById(R.id.dynamicPlot);
        //dynamicPlot.setBorderStyle(XYPlot.BorderStyle.NONE, null, null);
        dynamicPlot.getGraphWidget().getBackgroundPaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getGridBackgroundPaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getDomainGridLinePaint().setColor(Color.RED);
        dynamicPlot.getGraphWidget().getRangeGridLinePaint().setColor(Color.RED);
        
        // Domain
        dynamicPlot.getGraphWidget().setDomainLabelPaint(null);
        dynamicPlot.getGraphWidget().setDomainOriginLinePaint(null);
        dynamicPlot.setDomainStep(XYStepMode.SUBDIVIDE, 20); // need to modify the step value later
        dynamicPlot.setDomainValueFormat(new DecimalFormat("0"));
        
        //Range
        //dynamicPlot.getGraphWidget().getRangeLabelPaint().setColor(Color.TRANSPARENT);
        //dynamicPlot.getGraphWidget().getRangeOriginLabelPaint().setColor(Color.TRANSPARENT);
        dynamicPlot.getGraphWidget().setRangeLabelPaint(null);
        dynamicPlot.getGraphWidget().setRangeOriginLinePaint(null);
        dynamicPlot.setRangeStep(XYStepMode.SUBDIVIDE, 10); // need to modify the step value later
        dynamicPlot.setRangeValueFormat(new DecimalFormat("0"));
        
        //Remove legend
        dynamicPlot.getLayoutManager().remove(dynamicPlot.getLegendWidget());
        dynamicPlot.getLayoutManager().remove(dynamicPlot.getDomainLabelWidget());
        dynamicPlot.getLayoutManager().remove(dynamicPlot.getRangeLabelWidget());
        dynamicPlot.getLayoutManager().remove(dynamicPlot.getTitleWidget());
        
        dynamicPlot.getGraphWidget().setSize(new SizeMetrics(
                0, SizeLayoutType.FILL,
                0, SizeLayoutType.FILL));
        
        heartSeries = new SimpleXYSeries("EKG");
        // Use index value as xVal, instead of explicit, user provided xVals.
        heartSeries.useImplicitXVals();
        
        // Create a formatter to use for drawing a series using LineAndPointRenderer:
        LineAndPointFormatter series1Format = new LineAndPointFormatter(
                Color.rgb(0, 0, 0),   // line color
                null,                   // point color
                null, 					// fill color 
                null);                  // PointLabelFormatter
        
        dynamicPlot.setRangeBoundaries(RANGE_MIN, RANGE_MAX, BoundaryMode.FIXED);
        //dynamicPlot.setRangeBoundaries(400, 650, BoundaryMode.FIXED);
        dynamicPlot.setDomainBoundaries(0, SAMPLE_SIZE, BoundaryMode.FIXED);
        dynamicPlot.addSeries(heartSeries, series1Format);
     

        XYGraphWidget g = dynamicPlot.getGraphWidget();

 /*       dynamicPlot.getGraphWidget().setGridPaddingLeft(0);
        dynamicPlot.getGraphWidget().setGridPaddingRight(0);
        dynamicPlot.getGraphWidget().setMarginLeft(0);
        dynamicPlot.getGraphWidget().setMarginRight(0);
        g.setPadding(0, 0, 0, 0);
        g.setMargins(0, 0, 0, 0);
*/
        dynamicPlot.setPlotMargins(0, 0, 0, 0);
        dynamicPlot.setPlotPadding(0, 0, 0, 0);

//        g.setRangeLabelWidth(0);
//        g.setDomainLabelWidth(0);

//        dynamicPlot.setDomainLabel("Time"); // x-axis title
//        dynamicPlot.getDomainLabelWidget().pack();
//        dynamicPlot.setRangeLabel("Voltage"); // y-axis title
//        dynamicPlot.getRangeLabelWidget().pack();
        
//		connectButton = (Button)findViewById(R.id.connectButton);
//		startButton = (Button)findViewById(R.id.startButton);
		
    }
}
