package org.opendatakit.sensors.drivers.bt.heart;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class PatientOperations {

	// Database fields
	private DataBaseWrapper dbHelper;
	private static String[] PATIENT_TABLE_COLUMNS = { DataBaseWrapper.PATIENT_ID, DataBaseWrapper.PATIENT_NAME, DataBaseWrapper.PATIENT_ECG, 
														DataBaseWrapper.PATIENT_HR, DataBaseWrapper.PATIENT_CONDITION };
	private static  SQLiteDatabase database;

	public PatientOperations(Context context) {
		dbHelper = new DataBaseWrapper(context);
	}

	public void open() throws SQLException {
		database = dbHelper.getWritableDatabase();
	}

	public void close() {
		dbHelper.close();
	}

	
	public Patient addPatient(String name) {

		ContentValues values = new ContentValues();

		values.put(DataBaseWrapper.PATIENT_NAME, name);
		
		long patId = database.insert(DataBaseWrapper.TABLE_PATIENTS, null, values);

/*		// now that the student is created return it ...
		Cursor cursor = database.query(DataBaseWrapper.TABLE_PATIENTS,
				PATIENT_TABLE_COLUMNS, DataBaseWrapper.PATIENT_ID + " = "
						+ patId, null, null, null, null);

		cursor.moveToFirst();

		Patient newComment = parsePatient(cursor);
		cursor.close();
*/
		Patient newComment = new Patient();
		return newComment;
	}
	
	public Patient addPatient_complete(String name, int[] ecg, int hr, String condition) {

		ContentValues values = new ContentValues();

		values.put(DataBaseWrapper.PATIENT_NAME, name);
		values.put(DataBaseWrapper.PATIENT_ECG, Arrays.toString(ecg));
		values.put(DataBaseWrapper.PATIENT_HR, hr);
		values.put(DataBaseWrapper.PATIENT_CONDITION, condition);
		
		long patId = database.insert(DataBaseWrapper.TABLE_PATIENTS, null, values);

/*		// now that the student is created return it ...
		Cursor cursor = database.query(DataBaseWrapper.TABLE_PATIENTS,
				PATIENT_TABLE_COLUMNS, DataBaseWrapper.PATIENT_ID + " = "
						+ patId, null, null, null, null);

		cursor.moveToFirst();

		Patient newComment = parsePatient(cursor);
		cursor.close();
*/
		Patient newComment = new Patient();
		return newComment;
	}

	public static void deletePatient(Patient comment) {
		long id = comment.getId();
		System.out.println("Comment deleted with id: " + id);
		database.delete(DataBaseWrapper.TABLE_PATIENTS, DataBaseWrapper.PATIENT_ID
				+ " = " + id, null);
	}

	public List<Patient> getAllPatients() {
		List<Patient> patients = new ArrayList<Patient>();

		Cursor cursor = database.query(DataBaseWrapper.TABLE_PATIENTS,
				PATIENT_TABLE_COLUMNS, null, null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Patient patient = parsePatient(cursor);
			patients.add(patient);
			cursor.moveToNext();
		}

		cursor.close();
		return patients;
	}

	public List<Patient> searchPatients(String name) {
		List<Patient> patients = new ArrayList<Patient>();

		Cursor cursor = database.query(DataBaseWrapper.TABLE_PATIENTS,
				PATIENT_TABLE_COLUMNS, DataBaseWrapper.PATIENT_NAME + "='" + name + "'", null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Patient patient = parsePatient(cursor);
			patients.add(patient);
			cursor.moveToNext();
		}

		cursor.close();
		return patients;
	}
	
	private static Patient parsePatient(Cursor cursor) {
		Patient patient = new Patient();
		patient.setId((cursor.getInt(0)));
		patient.setName(cursor.getString(1));
		patient.setecg(cursor.getString(2));
		patient.sethr(cursor.getInt(3));
		patient.setcondition(cursor.getString(4));
		return patient;
	}
}