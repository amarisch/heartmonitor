package org.opendatakit.sensors.drivers.bt.heart;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
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
				radioGenderGroup.check(R.id.female);
			} else { 
				radioGenderGroup.check(R.id.male);
			}
		}
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "ON PAUSE");
		dbhelper.close();
		super.onPause();
	}
	
	public void save(View view) {
		int selectedId = radioGenderGroup.getCheckedRadioButtonId();
		Log.d(TAG, "botton id: " + selectedId);
		if (nameField.getText().length() == 0 || dob.getText().length() == 0 
				|| selectedId == -1) {
			
			Toast.makeText(getApplicationContext(), "Please complete the form", Toast.LENGTH_LONG).show();
		
		} else {
			// get selected radio button from radioGroup
			// find the radiobutton by returned id
			radioGenderButton = (RadioButton) findViewById(selectedId);
			
			if(pat == null) {
				
				String name = nameField.getText().toString();
				List<Patient> others = dbhelper.searchPatients(name);
				if (others.size() != 0) {
					sameNameAlert();
				} else {
					pat = new Patient();
					
					pat.setName((String) nameField.getText().toString());
					pat.setBirthdate((String) dob.getText().toString());
					pat.setGender((String) radioGenderButton.getText().toString());
					
					dbhelper.createPatient(pat);
					
					gotoPatientProfileActivity();
				}
			} else {
				pat.setName((String) nameField.getText().toString());
				pat.setBirthdate((String) dob.getText().toString());
				pat.setGender((String) radioGenderButton.getText().toString());

				// update patient info in the database
				dbhelper.updatePatient(pat);
				
				finish();
			}
		}
	}
	
	private void sameNameAlert() {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
				this);
 
			// set title
			//alertDialogBuilder.setTitle("Your Title");
 
			// set dialog message
			alertDialogBuilder
				.setMessage("Patient profile with this name already exist in the database. " +
						"Would you like to search for it in Patient list?")
				.setCancelable(false)
				.setPositiveButton("Yes",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						finish();
					}
				  })
				.setNegativeButton("No",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						// if this button is clicked, just close
						// the dialog box and do nothing
						dialog.cancel();
					}
				});
 
				// create alert dialog
				AlertDialog alertDialog = alertDialogBuilder.create();
 
				// show it
				alertDialog.show();
	}
	
	private void gotoPatientProfileActivity() {
		Intent launchNext = new Intent(this, PatientProfileActivity.class);
		launchNext.putExtra("patient", pat);
		startActivity(launchNext);
	}
}
