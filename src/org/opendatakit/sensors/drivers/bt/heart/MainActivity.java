package org.opendatakit.sensors.drivers.bt.heart;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;


public class MainActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mainactivitylayout);
	}
	
	public void viewPatients(View view) {
    	Intent j = new Intent(this,DatabaseActivity.class);
        startActivity(j);
	}
	
	public void addNewPatient(View view) {
    	Intent j = new Intent(this,FormActivity.class);
        startActivity(j);
	}

	public void runSensor(View view) {
    	Intent j = new Intent(this,HeartrateDriverActivity.class);
        startActivity(j);
	}
	
	public void runSensor_recorded(View view) {
		showdialog();
	}

	public void settings(View view) {
		
	}
	
	protected void showdialog() {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
 
	    // Get the layout inflater
	    LayoutInflater inflater = this.getLayoutInflater();
	    
		alertDialogBuilder.setMessage("You must create a patient profile in order to proceed."
				 + " Would you like to create a profile?");
		
		// set dialog message
		alertDialogBuilder
			.setView(inflater.inflate(R.layout.dialoglayout, null))
			//.setMessage("Name and Genger")
			//.setCancelable(false)
			.setPositiveButton("Yes",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					
			    	Intent j = new Intent(MainActivity.this,FormActivity.class);
			        startActivity(j);
				}
			  })
			.setNegativeButton("No",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					dialog.dismiss();
				}
			});
		 
			// create alert dialog
			AlertDialog alertDialog = alertDialogBuilder.create();
		 
			// show it
			alertDialog.show();
	}
	
	public void onDestroy() {
		super.onDestroy();
	}
}
