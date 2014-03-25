package org.opendatakit.sensors.drivers.bt.heart;


import java.util.Arrays;
import java.util.List;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

public class DatabaseActivity extends ListActivity {

	private ListView listview;
	
	private static final String TAG = "DatabaseActivity";
	public static PatientOperations patientDBoperation;
	public static ArrayAdapter<Patient_old> adapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.databaseview);
		listview=(ListView)findViewById(android.R.id.list);

		patientDBoperation = new PatientOperations(this);
		patientDBoperation.open();

		Log.d(TAG, "GET ALL APTIENTS");
		List<Patient_old> values = patientDBoperation.getAllPatients();
		
		if (values != null) {
			Log.d(TAG, "size of list: " + values.size());
		}
		Log.d(TAG, "DONE GETTING ALL");
		
		// Use the SimpleCursorAdapter to show the
		// elements in a ListView
		adapter = new ArrayAdapter<Patient_old>(this,
				android.R.layout.simple_list_item_1, values);
		listview.setAdapter(adapter);

	}

	public void addUser(View view) {
		
		ArrayAdapter<Patient_old> adapter = (ArrayAdapter<Patient_old>) getListAdapter();
		EditText text = (EditText) findViewById(R.id.editText1);
		Patient_old pat = patientDBoperation.addPatient(text.getText().toString());
		
		adapter.add(pat);

	}

	public void deleteFirstUser(View view) {

		ArrayAdapter adapter = (ArrayAdapter) getListAdapter();
		Patient_old pat = null;

		if (getListAdapter().getCount() > 0) {
			pat = (Patient_old) getListAdapter().getItem(0);
			patientDBoperation.deletePatient(pat);
			adapter.remove(pat);
		}
	}
	
	public void searchUser(View view) {
		
		EditText text = (EditText) findViewById(R.id.editText1);
		List<Patient_old> values = patientDBoperation.searchPatients(text.getText().toString());
		
		adapter = new ArrayAdapter<Patient_old>(this,
				android.R.layout.simple_list_item_1, values);
		listview.setAdapter(adapter);
	}

	@Override
	protected void onResume() {
		super.onResume();
		patientDBoperation.open();
		
		listview.setAdapter(adapter);
		listview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Patient_old pat = (Patient_old) parent.getAdapter().getItem(position);
				Log.d(TAG, "CLICKED PATIENT: " + pat.getName());
				
				Log.d(TAG, "CLICKED PATIENT: " + pat.getecg().length);
				//Log.d(TAG, "CLICKED PATIENT: " + Arrays.toString(pat.getecg()));
				
                Intent in = new Intent(DatabaseActivity.this,ViewActivity.class);
                in.putExtra("xyseries", pat.getecg());
                // change back 
                //heartRate = computeAverageHR();
                // placeholder change
                Log.d(TAG, "CLICKED PATIENT: " + pat.gethr());
                in.putExtra("heartrate", pat.gethr());
                //condition = detectHeartCondition();
                in.putExtra("condition", pat.getcondition());
                startActivity(in);

			}
		});
	}

	@Override
	protected void onPause() {
		patientDBoperation.close();
		super.onPause();
	}

}