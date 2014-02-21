package org.opendatakit.sensors.drivers.bt.heart;


import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class PatientOperations {

	// Database fields
	private DataBaseWrapper dbHelper;
	private String[] PATIENT_TABLE_COLUMNS = { DataBaseWrapper.PATIENT_ID, DataBaseWrapper.PATIENT_NAME };
	private SQLiteDatabase database;

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

		long patId = database.insert(DataBaseWrapper.PATIENTS, null, values);

		// now that the student is created return it ...
		Cursor cursor = database.query(DataBaseWrapper.PATIENTS,
				PATIENT_TABLE_COLUMNS, DataBaseWrapper.PATIENT_ID + " = "
						+ patId, null, null, null, null);

		cursor.moveToFirst();

		Patient newComment = parsePatient(cursor);
		cursor.close();
		return newComment;
	}

	public void deletePatient(Patient comment) {
		long id = comment.getId();
		System.out.println("Comment deleted with id: " + id);
		database.delete(DataBaseWrapper.PATIENTS, DataBaseWrapper.PATIENT_ID
				+ " = " + id, null);
	}

	public List getAllPatients() {
		List students = new ArrayList();

		Cursor cursor = database.query(DataBaseWrapper.PATIENTS,
				PATIENT_TABLE_COLUMNS, null, null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Patient student = parsePatient(cursor);
			students.add(student);
			cursor.moveToNext();
		}

		cursor.close();
		return students;
	}

	private Patient parsePatient(Cursor cursor) {
		Patient patient = new Patient();
		patient.setId((cursor.getInt(0)));
		patient.setName(cursor.getString(1));
		return patient;
	}
}