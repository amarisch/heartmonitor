package org.opendatakit.sensors.drivers.bt.heart;


import java.util.List;

import android.app.ListActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;

public class DatabaseActivity extends ListActivity {

	private static final String TAG = "DatabaseActivity";
	public static PatientOperations patientDBoperation;
	public static ArrayAdapter<Patient> adapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.databaseview);

		patientDBoperation = new PatientOperations(this);
		patientDBoperation.open();

		Log.d(TAG, "GET ALL APTIENTS");
		List<Patient> values = patientDBoperation.getAllPatients();
		Log.d(TAG, "DONE GETTING ALL");
		
		// Use the SimpleCursorAdapter to show the
		// elements in a ListView
		adapter = new ArrayAdapter<Patient>(this,
				android.R.layout.simple_list_item_1, values);
		setListAdapter(adapter);
	}

	public void addUser(View view) {

		ArrayAdapter<Patient> adapter = (ArrayAdapter<Patient>) getListAdapter();

		EditText text = (EditText) findViewById(R.id.editText1);
		Patient pat = patientDBoperation.addPatient(text.getText().toString(), new int[] {1,2,3});

		adapter.add(pat);

	}

	public void deleteFirstUser(View view) {

		ArrayAdapter adapter = (ArrayAdapter) getListAdapter();
		Patient pat = null;

		if (getListAdapter().getCount() > 0) {
			pat = (Patient) getListAdapter().getItem(0);
			patientDBoperation.deletePatient(pat);
			adapter.remove(pat);
		}

	}

	@Override
	protected void onResume() {
		patientDBoperation.open();
		super.onResume();
	}

	@Override
	protected void onPause() {
		patientDBoperation.close();
		super.onPause();
	}

}