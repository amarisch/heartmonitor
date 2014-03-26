package org.opendatakit.sensors.drivers.bt.heart;

import java.util.List;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PatientProfileActivity extends ListActivity {

	private static final String TAG = "PatientProfileActivity";
	private Patient pat;
	private static ListView listview;

	public static DatabaseHelper dbhelper;
	public static ArrayAdapter<ECG_Data> adapter;
	
 	protected void onCreate(Bundle savedInstanceState) {
	 	super.onCreate(savedInstanceState);
		// automatically set orientation to landscape
        setContentView(R.layout.patientprofileactivitylayout);
		listview = (ListView)findViewById(android.R.id.list);
		TextView nameField = (TextView)findViewById(R.id.nameField);
		TextView genderField = (TextView)findViewById(R.id.genderField);
		TextView dobField = (TextView)findViewById(R.id.dobField);
		
		dbhelper = new DatabaseHelper(this);

        pat = (Patient) getIntent().getSerializableExtra("patient");
        
        nameField.setText(pat.getName());
        genderField.setText(pat.getGender());
        dobField.setText(pat.getBirthdate());
		
		Log.d(TAG, "GET ALL ecg data");
		List<ECG_Data> data = dbhelper.getAllECG_data(pat);
		
		if (data != null) {
			Log.d(TAG, "size of list: " + data.size());
		}
		Log.d(TAG, "DONE GETTING ALL");
		
		// Use the SimpleCursorAdapter to show the
		// elements in a ListView
		adapter = new ArrayAdapter<ECG_Data>(this,
				android.R.layout.simple_list_item_1, data);
		listview.setAdapter(adapter);
 	}
 	
	@Override
	protected void onResume() {
		super.onResume();
		dbhelper.open();
		
		listview.setAdapter(adapter);
		listview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				ECG_Data dat = (ECG_Data) parent.getAdapter().getItem(position);
				Log.d(TAG, "CLICKED DATA: " + dat.getDate());
				
                Intent in = new Intent(PatientProfileActivity.this,ViewActivity.class);
                in.putExtra("xyseries", dat.waveform_to_array());
                in.putExtra("heartrate", dat.getHeartrate());
                in.putExtra("qrs_duration", dat.getQrs_duration());                
                in.putExtra("regularity", dat.getRegularity());  
                startActivity(in);
			}
		});
	}

	@Override
	protected void onPause() {
		dbhelper.close();
		super.onPause();
	}
 	
	public void edit(View view) {
		
    	Intent j = new Intent(this,FormActivity.class);
    	j.putExtra("patient", pat);
        startActivity(j);

	}
	
}
