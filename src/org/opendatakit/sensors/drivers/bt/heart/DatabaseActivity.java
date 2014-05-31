package org.opendatakit.sensors.drivers.bt.heart;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

public class DatabaseActivity extends ListActivity {

	private static ListView listview;
	
	private static final String TAG = "DatabaseActivity";
	public static DatabaseHelper dbhelper;
    private static final String DATABASE_NAME = "PatientData";
	public static ArrayAdapter<Patient> adapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.databaseview);
		listview=(ListView)findViewById(android.R.id.list);

		dbhelper = new DatabaseHelper(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		dbhelper.open();
		
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
		Log.d(TAG, "set adapter");
		
		listview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Patient pat = (Patient) parent.getAdapter().getItem(position);
				Log.d(TAG, "get patient:" + pat.getName());
				Intent in = new Intent(DatabaseActivity.this,PatientProfileActivity.class);
				in.putExtra("patient", pat);
				startActivity(in);
			}
		});
	}
	
	public void addUser(View view) {
		
    	Intent j = new Intent(this,FormActivity.class);
        startActivity(j);
	}

/*	public void deleteFirstUser(View view) {

		ArrayAdapter<Patient> adapter = (ArrayAdapter<Patient>) getListAdapter();
		Patient pat = null;

		if (getListAdapter() == null) {
			Log.d(TAG, "adapter null");
		} else {
		
		if (getListAdapter().getCount() > 0) {
			pat = (Patient) getListAdapter().getItem(0);
			if (pat == null) {
				Log.d(TAG, "patient null");
			}

			//dbhelper.deletePatient(pat);
			adapter.remove(pat);
		}
		}
	}
*/	
	
	public void searchUser(View view) {
		
		EditText text = (EditText) findViewById(R.id.editText1);
		List<Patient> values = dbhelper.searchPatients(text.getText().toString());
		
		Log.d(TAG, "search result: " + values.size());
		
		adapter = new ArrayAdapter<Patient>(this,
				android.R.layout.simple_list_item_1, values);
		listview.setAdapter(adapter);
	}

	private void exportDB(){
		File sd = Environment.getExternalStorageDirectory();
		Log.d(TAG, sd.getAbsolutePath());
		File data = Environment.getDataDirectory();
		Log.d(TAG, data.getAbsolutePath());
		FileChannel source=null;
		FileChannel destination=null;
		String currentDBPath = "/data/"+ "org.opendatakit.sensors.drivers.bt.heart" +"/databases/" + DATABASE_NAME;
		String backupDBPath = DATABASE_NAME;
		File currentDB = new File(data, currentDBPath);
		File backupDB = new File(sd, backupDBPath);
		try {
			source = new FileInputStream(currentDB).getChannel();
			destination = new FileOutputStream(backupDB).getChannel();
			destination.transferFrom(source, 0, source.size());
			source.close();
			destination.close();
			Toast.makeText(this, "DB Exported!", Toast.LENGTH_LONG).show();
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		File dbFile = getDatabasePath(DATABASE_NAME);
		Log.d(TAG, dbFile.getAbsolutePath());
		
	}
	
	
	@Override
	protected void onPause() {
		Log.d(TAG, "ON PAUSE");
		dbhelper.close();
		super.onPause();
	}
	
	
///////////////////////////////////////////////////////////////////////////////////////////////////
//							Functions related to the options menu                                //
///////////////////////////////////////////////////////////////////////////////////////////////////  	

	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.db_view_options_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.export:
			
				exportDB();
				
				return true;

			}        
		
		return false;
	}
	

}