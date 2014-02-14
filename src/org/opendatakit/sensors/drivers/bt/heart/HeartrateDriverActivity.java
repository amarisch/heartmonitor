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
import java.util.List;
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

import android.bluetooth.BluetoothAdapter;
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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
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
	
	
	//REMOVE LATER
	private volatile int[] qrsValues;
	
	private DataProcessor sensorDataProcessor;
	
//	private Button connectButton, startButton;
	
	private ConnectionThread connectionThread;
	
	private static final int REQUEST_ENABLE_BT = 2;

	// For AChartEngine main plot
	private XYSeries voltageSeries;
    private static XYMultipleSeriesDataset dataset;
    private static XYMultipleSeriesRenderer renderer;
    private static XYSeriesRenderer rendererSeries;
    private static GraphicalView waveform;

    // plot of the ecg
    private LinearLayout plot;
    private static int index = 0;
    
    
    // Preference settings
    private SharedPreferences mPrefs;
    
    private static final String VIEW_KEY = "viewsetting";
    private int viewId = 1;
    
    private static final int[][] VIEW_SCHEMES = {
        {-50, 50}, {-100, 100}, {-200, 200}};
    
	// Constants for the plot display
	private static final int SAMPLE_RATE = 250; // 250 samples per second
	private static final int SAMPLE_SIZE = 1000; //200 sample_size and 50 pdu_size
	private static final int RANGE_MIN = -100; //300;
	private static final int RANGE_MAX = 100; //700;
	private static final int DOMAIN_MIN = 0;
	private static final int DOMAIN_MAX = 1000; 
	private static final int DOMAIN_STEP_VALUE = (int) ((SAMPLE_SIZE / SAMPLE_RATE) / 0.04); // each square is 0.04sec
	private static final int RANGE_STEP_VALUE = (int) (RANGE_MAX - RANGE_MIN) / 10;
	
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// automatically set orientation to landscape
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        readPrefs();
		
		setContentView(R.layout.plot);
		
		plot = (LinearLayout) findViewById(R.id.lygforce);
		
		heartRateField = (TextView) findViewById(R.id.heartRateField);
		//conditionField = (TextView) findViewById(R.id.conditionField);
		
		// Initialize Plot setting
		plot_init();

		preference_settings();

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

    // update changes made to preferences
    private void updatePrefs() {
        setView();

        plot.removeAllViews();  //This remove previous graph
        plot.addView(waveform); //This loads the graph again
    }

    // set the new view
	private void setView() {
        int[] scheme = VIEW_SCHEMES[viewId];
        renderer.setYAxisMin(scheme[0]);
        renderer.setYAxisMax(scheme[1]);
        Log.d(TAG,"set view: " + scheme[0] + ", " + scheme[1]);
	}


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
        renderer.setShowLabels(true);

        renderer.setYAxisMin(RANGE_MIN);
        renderer.setYAxisMax(RANGE_MAX);
        renderer.setXAxisMin(DOMAIN_MIN);
        renderer.setXAxisMax(DOMAIN_MAX);
        //renderer.setPanLimits(new double[] { DOMAIN_MIN, DOMAIN_MAX, 0, 0 });
        //renderer.setZoomLimits(new double[] { DOMAIN_MIN, DOMAIN_MAX, 0, 0 });

        rendererSeries = new XYSeriesRenderer();
        rendererSeries.setColor(Color.BLACK);
        rendererSeries.setLineWidth(2);

        renderer.addSeriesRenderer(rendererSeries);

        voltageSeries = new XYSeries("ECG waveform");

        dataset.addSeries(voltageSeries);

        waveform = ChartFactory.getLineChartView(this, dataset, renderer);
        waveform.refreshDrawableState();
        waveform.repaint();

        plot.addView(waveform);
        
        
    }
	
	// To store different sensor IDs
    private void preference_settings() {
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
		sensorDataProcessor.execute();
		
    	readPrefs();
    	updatePrefs();
		
    	//startAction();
    	
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

			super.startSensor(sensorID);
			
        	Toast.makeText(this, "Sensoring Started", Toast.LENGTH_SHORT).show();
        	
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
						
						heartRate = aBundle.getInt(HeartrateDriverImpl.HEART_RATE);
						voltageValues = aBundle.getIntArray(HeartrateDriverImpl.VOLTAGE_VALUES);
						
						// REMOVE later
						qrsValues = aBundle.getIntArray(HeartrateDriverImpl.QRS_VALUES);
						
						
/*
 * Voltage values being wrapped around and old values are removed
 * 
 */							
				        for (int i = 0; i < voltageValues.length; i++) {
						    
				        	if (index % DOMAIN_MAX == 0) {
				        		voltageSeries.clear();
				        	}
				        	// write to the end of the screen, start from the beginning
				        	voltageSeries.add(index % DOMAIN_MAX, voltageValues[i]);
				        	index++;

			                //waveform.repaint(index - 1, 50, (index -1) % 750, -50);
				        	waveform.repaint();
				        	
					        //Log.d(TAG, "heartVOl: " + voltageValues[i]);


				        
				        }
						
						
/*
 * Voltage values being wrapped around and old values being kept
 * 
 */						
/*				        for (int i = 0; i < voltageValues.length; i++) {
					    
				        	if (index / (DOMAIN_MAX - 40) >= 1) {
				        		voltageSeries.remove(0);
				        	}
				        	// write to the end of the screen, start from the beginning
				        	voltageSeries.add(index % DOMAIN_MAX, voltageValues[i]);
				        	index++;
				        	
				        	
			                //renderer.setXAxisMax(renderer.getXAxisMax() + 30);
			                //renderer.setXAxisMin(renderer.getXAxisMin() + 30);

			                //waveform.repaint(index - 1, 50, (index -1) % 750, -50);
				        	waveform.repaint();
				        	
					        //Log.d(TAG, "heartVOl: " + voltageValues[i]);


				        
				        }
*/				        
						
						
/*
 * Display moves with voltage values
 * 
 */
/*						Log.d(TAG, "length: " + voltageValues.length);
						
				        for (int i = 0; i < voltageValues.length; i++) {
						    
				        	if (index >= DOMAIN_MAX - 40) {
				                renderer.setXAxisMin(renderer.getXAxisMin() + 1);
				                renderer.setXAxisMax(renderer.getXAxisMax() + 1);
				        	}
				        	
				        	voltageSeries.add(index, voltageValues[i]);
				        	index++;


			                //waveform.repaint(index - 1, 50, (index -1) % 750, -50);
				        	waveform.repaint();
				        	
					        //Log.d(TAG, "heartVOl: " + voltageValues[i]);			        
				        }
*/
						
						//update UI
						Log.d(TAG, "heartrate: " + heartRate);
						//Log.d(TAG, "beatcount: " + beatCount);
						if (heartRate == 0) {
							heartRateField.setText(String.valueOf("Detecting Heartrate"));
						} else {
							heartRateField.setText(String.valueOf(heartRate));
						}
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
        case R.id.preferences:
        	doPreferences();
        	//registerForContextMenu(view_setting);
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
    
	private void doPreferences() {
        startActivity(new Intent(this, SetPreferenceActivity.class));
    }
}
