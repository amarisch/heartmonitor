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

	private static ListView listview;
	
	private static final String TAG = "DatabaseActivity";
	public static DatabaseHelper dbhelper;
	public static ArrayAdapter<Patient> adapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.databaseview);
		listview=(ListView)findViewById(android.R.id.list);

		dbhelper = new DatabaseHelper(this);

		Log.d(TAG, "GET ALL APTIENTS");
		List<Patient> values = dbhelper.getAllPatients();
		
		if (values != null) {
			Log.d(TAG, "size of list: " + values.size());
		}
		Log.d(TAG, "DONE GETTING ALL");
		
		// Use the SimpleCursorAdapter to show the
		// elements in a ListView
		adapter = new ArrayAdapter<Patient>(this,
				android.R.layout.simple_list_item_1, values);
		listview.setAdapter(adapter);

	}

	public void addUser(View view) {
		
    	Intent j = new Intent(this,FormActivity.class);
        startActivity(j);
	}

	public void deleteFirstUser(View view) {

		ArrayAdapter adapter = (ArrayAdapter) getListAdapter();
		Patient pat = null;

		if (getListAdapter().getCount() > 0) {
			pat = (Patient) getListAdapter().getItem(0);
			dbhelper.deletePatient(pat);
			adapter.remove(pat);
		}
	}
	
	public void searchUser(View view) {
		
		EditText text = (EditText) findViewById(R.id.editText1);
		List<Patient> values = dbhelper.searchPatients(text.getText().toString());
		
		adapter = new ArrayAdapter<Patient>(this,
				android.R.layout.simple_list_item_1, values);
		listview.setAdapter(adapter);
	}

	@Override
	protected void onResume() {
		super.onResume();
		dbhelper.open();
		
		listview.setAdapter(adapter);
		listview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Patient pat = (Patient) parent.getAdapter().getItem(position);
				Intent in = new Intent(DatabaseActivity.this,PatientProfileActivity.class);
				in.putExtra("patient", pat);
			}
		});
	}

	@Override
	protected void onPause() {
		dbhelper.close();
		super.onPause();
	}
	

}