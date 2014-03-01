package org.opendatakit.sensors.drivers.bt.heart;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DataBaseWrapper extends SQLiteOpenHelper {

	public static final String TABLE_PATIENTS = "Patients";
	public static final String PATIENT_ID = "_id";
	public static final String PATIENT_NAME = "_name";
	public static final String PATIENT_ECG = "_ecg";
//	public static final String PATIENT_GENDER = "_gender";
	
	private static final String DATABASE_NAME = "Patients.db";
	private static final int DATABASE_VERSION = 1;

	// creation SQLite statement
	private static final String DATABASE_CREATE = "create table " + TABLE_PATIENTS
			+ "(" + PATIENT_ID + " INTEGER PRIMARY KEY, " + PATIENT_NAME + " TEXT,"
		            + PATIENT_ECG + " STRING OF VALUES" + ")";

	public DataBaseWrapper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(DATABASE_CREATE);

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// you should do some logging in here
		// ..

		db.execSQL("DROP TABLE IF EXISTS " + TABLE_PATIENTS);
		onCreate(db);
	}

}