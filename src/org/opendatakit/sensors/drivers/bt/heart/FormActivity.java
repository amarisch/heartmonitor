package org.opendatakit.sensors.drivers.bt.heart;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class FormActivity extends Activity {

	private static final String TAG = "FormActivity";
	private Patient pat;

	public static DatabaseHelper dbhelper;
	
 	protected void onCreate(Bundle savedInstanceState) {
	 	super.onCreate(savedInstanceState);
		// automatically set orientation to landscape
        setContentView(R.layout.formactivitylayout);

		TextView nameField = (TextView)findViewById(R.id.nameField);
		TextView genderField = (TextView)findViewById(R.id.genderField);
		TextView dobField = (TextView)findViewById(R.id.dobField);
		
		dbhelper = new DatabaseHelper(this);

        pat = (Patient) getIntent().getSerializableExtra("patient");
        
        if (pat != null) {
            nameField.setText(pat.getName());
            genderField.setText(pat.getGender());
            dobField.setText(pat.getBirthdate());	
        }
		
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
 = (ArrayAdapter<Patient_old>) getListAdapter();
	EditText text = (EditText) findViewById(R.id.editText1);
 = dbhelper.createPatient(new Patient(text.getText().toString()));

	adapter.add(pat);
	
}
