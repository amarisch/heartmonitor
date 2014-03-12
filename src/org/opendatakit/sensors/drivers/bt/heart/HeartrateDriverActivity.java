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
 
	private static final String HR_SENSOR_ID_STR = "EKG_SENSOR_ID";
	private static final String TAG = "SensorDriverActivity";
	private static final int SENSOR_CONNECTION_COUNTER = 10;
	private static final int DETECTION_TIME = 7500; // CHANGE BACK TO 7500(30sec)
	
	//each physical sensor has a unique sensorID. Activities use this sensorID to communicate with sensors via the framework.
	private String sensorID = null;
	
	private boolean isConnectedToSensor = false;
	
	private boolean isStarted = false;
	
	private static int heartRate;
	
	private static int qrs_duration ;
	
	private static int condition;
	
	private static final String[] HEART_CONDITION_OPTIONS = {
        "Normal", "Bradycardia", "Tachycardia", "Abnormal QRS Complex", "Premature Atrial Contraction"};
	
	private TextView heartRateField;
	
	private TextView activityStatusField;
	
	private static int[] voltageValues;
	
	private static int[] voltageArray = new int[DETECTION_TIME];
	
	private static int[] hrArray = new int[200];
	private static int hrIndex = 0;
	
	private static int[] qrsArray = new int[200];
	private static int qrsIndex = 0;
	
	//REMOVE LATER
	private volatile int[] qrsValues;
	
	private DataProcessor sensorDataProcessor;
	
	private ConnectionThread connectionThread;
	
	private static final int REQUEST_ENABLE_BT = 2;

	// For AChartEngine main plot
	private XYSeries voltageSeries;
	private XYSeries qrsLines;
    private static XYMultipleSeriesDataset dataset;
    private static XYMultipleSeriesRenderer renderer;
    private static XYSeriesRenderer rendererSeries;
    private static XYSeriesRenderer qrsrendererSeries;
    private static GraphicalView waveform;

    
    // database
	public static PatientOperations patientDBoperation;
    
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
		
		plot = (LinearLayout) findViewById(R.id.ecgplot);
		
		heartRateField = (TextView) findViewById(R.id.heartRateField);
		activityStatusField = (TextView) findViewById(R.id.activityStatusField);

		
		// Initialize Plot setting
		plot_init();

		// Restore previous sensorID
		restore_sensorID();
		
		// Initialize and open patient database
		patientDBoperation = new PatientOperations(this);
		patientDBoperation.open();

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

	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.options_menu, menu);
	    return true;
	}
	
	protected void onResume() {
		super.onResume();
		
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
        super.onPause();
		sensorDataProcessor.cancel(true);
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
		intent.putExtra(HeartrateDriverImpl.QRS_DURATION, qrs_duration);
		intent.putExtra(HeartrateDriverImpl.HEART_RATE, heartRate);
		intent.putExtra(HeartrateDriverImpl.VOLTAGE_VALUES, voltageValues);
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
						qrs_duration = aBundle.getInt(HeartrateDriverImpl.QRS_DURATION);
						
						if (heartRate != 0)
							hrArray[hrIndex++] = heartRate;
						
						if (qrs_duration != 0) {
							qrsArray[qrsIndex++] = qrs_duration;
						}
						
						
						voltageValues = aBundle.getIntArray(HeartrateDriverImpl.VOLTAGE_VALUES);
						
						// REMOVE later
						qrsValues = aBundle.getIntArray(HeartrateDriverImpl.QRS_VALUES);
						
						
/*
 * Voltage values being wrapped around and old values are removed
 * 
 */							
						Log.d(TAG, "VOLTAGE LENGTH: " + voltageValues.length);
						for (int i = 0; i < voltageValues.length; i++) {
						    
							/*
							 * After a length of DETECTION_TIME, a dialog will pop up to save the trace
							 * Done sampling
							 */
				        	if (index >= DETECTION_TIME) {
				                Log.d(TAG, "qrsindex: " + qrsIndex);
				                Log.d(TAG, "hrindex: " + hrIndex);
				        		// show dialog for the option of saving the trace to the database
				        		showdialog();
				 
				                return;
				        	}
				        	
				        	if (index % DOMAIN_MAX == 0) {
				        		voltageSeries.clear();
				        		qrsLines.clear();
				        	}
				        	
/*				        	if (index != 0 && index % 250 == 0) {
				                qrsLines.add((index - 0.000001) % DOMAIN_MAX, -100);
				                qrsLines.add(index % DOMAIN_MAX, 100);
				                qrsLines.add((index + 0.000001) % DOMAIN_MAX, -100);
				        	}
*/
				        	qrsLines.add(index % DOMAIN_MAX, qrsValues[i]);
				        	
				        	// write to the end of the screen, start from the beginning
				        	voltageSeries.add(index % DOMAIN_MAX, voltageValues[i]);
				        	
				        	if (index < DETECTION_TIME) {
				        		voltageArray[index] = voltageValues[i];
				        	}
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
			}
		});
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
					dialog.dismiss();
				}
			  })
			.setNegativeButton("Save",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					// if this button is clicked, the trace is saved to the database
					// and the view activity will pop up
					Dialog f = (Dialog) dialog;
					EditText text = (EditText) f.findViewById(R.id.name);

	                heartRate = computeAverageHR();
	                qrs_duration = computeAverage_qrs_duration();
	                condition = detectHeartCondition();
	                
	                // add new data into patient database
					Patient pat = patientDBoperation.addPatient_complete(text.getText().toString(), voltageArray, heartRate, HEART_CONDITION_OPTIONS[condition]);
					
					dialog.dismiss();
					
					// start View Activity
					open_ViewActivity();
					
				}
			});
		 
			// create alert dialog
			AlertDialog alertDialog = alertDialogBuilder.create();
		 
			// show it
			alertDialog.show();
	}


	private int computeAverageHR() {
		int sum = 0;
		for (int i = 0; i < hrIndex; i++) {
			sum += hrArray[i];
		}
		// duration is the number of counts * 4ms
		return sum / hrIndex;
	}
	
	private int computeAverage_qrs_duration() {
		int sum = 0;
		for (int i = 0; i < qrsIndex; i++) {
			sum += qrsArray[i];
		}
		// duration is the number of counts * 4ms
		return sum / qrsIndex;
	}
	
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
	
	/*
	 * Options Menu
	 */
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
        heartRate = computeAverageHR();
        qrs_duration = computeAverage_qrs_duration();
        condition = detectHeartCondition();
        Intent i = new Intent(this,ViewActivity.class);
        i.putExtra("xyseries", voltageArray);
        i.putExtra("heartrate", heartRate);
        i.putExtra("qrs_duration", qrs_duration);
        i.putExtra("condition", HEART_CONDITION_OPTIONS[condition]);
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
	
}
