package org.opendatakit.sensors.drivers.bt.heart;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class FormActivity extends Activity {

	private static final String TAG = "FormActivity";
	private Patient pat;

	private RadioGroup radioGenderGroup;
	private RadioButton radioGenderButton;
	private Button save;
	private EditText dob;
	private EditText nameField;
	
	public static DatabaseHelper dbhelper;
	
 	protected void onCreate(Bundle savedInstanceState) {
	 	super.onCreate(savedInstanceState);

        setContentView(R.layout.formactivitylayout);
		
        radioGenderGroup = (RadioGroup) findViewById(R.id.radioGender);
		nameField = (EditText) findViewById(R.id.nameField);
		dob = (EditText) findViewById(R.id.dob);
		save = (Button) findViewById(R.id.save);
        
		dbhelper = new DatabaseHelper(this);

		Log.d(TAG, "GET ALL ecg data");
 	}
 	
	@Override
	protected void onResume() {
		super.onResume();
		dbhelper.open();
		pat = (Patient) getIntent().getParcelableExtra("patient");
		if (pat != null) {
			nameField.setText(pat.getName());
			dob.setText(pat.getBirthdate());
			if (pat.getGender().equals("female")) {
				Log.d(TAG, "female");
			}
		}
	}

	@Override
	protected void onPause() {
		dbhelper.close();
		super.onPause();
	}
	
	public void save(View view) {
		int selectedId = radioGenderGroup.getCheckedRadioButtonId();
		if (nameField.getText().length() == 0 || dob.getText().length() == 0 
				|| selectedId == -1) {
			
			Toast.makeText(getApplicationContext(), "Please complete the form", Toast.LENGTH_LONG).show();
		
		} else {
			// get selected radio button from radioGroup
			// find the radiobutton by returned id
			radioGenderButton = (RadioButton) findViewById(selectedId);
			
			if(pat == null) {
				pat = new Patient();
				
				pat.setName((String) nameField.getText().toString());
				pat.setBirthdate((String) dob.getText().toString());
				pat.setGender((String) radioGenderButton.getText().toString());
				
				dbhelper.createPatient(pat);	
			} else {
				pat.setName((String) nameField.getText().toString());
				pat.setBirthdate((String) dob.getText().toString());
				pat.setGender((String) radioGenderButton.getText().toString());

				// update patient info in the database
				dbhelper.updatePatient(pat);
			}
			
			gotoPatientProfileActivity();
		}
	}
	
	private void gotoPatientProfileActivity() {
    	Intent j = new Intent(this,PatientProfileActivity.class);
    	j.putExtra("patient", pat);
        startActivity(j);
	}
}
