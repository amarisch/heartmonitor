package org.opendatakit.sensors.drivers.bt.heart;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {
	   // Logcat tag
    private static final String LOG = "DatabaseHelper";
 
    // Database Version
    private static final int DATABASE_VERSION = 1;
 
    // Database Name
    private static final String DATABASE_NAME = "PatientData";
    
    // Table Names
    private static final String TABLE_PATIENT = "patients";
    private static final String TABLE_ECG_DATA = "ecg_data";
    private static final String TABLE_PATIENT_ECG_DATA = "patients_ecg_data";		
 
    // Common column names
    private static final String KEY_ID = "id";
 
    // Patients Table - column names
    private static final String KEY_NAME = "name";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_BIRTHDATE = "birthdate";
 
    // ECG Data Table - column names
    private static final String KEY_DATE = "date";
    private static final String KEY_ECG_WAVEFORM = "ecg_waveform";
    private static final String KEY_HEARTRATE = "heartrate";
    private static final String KEY_QRS_DURATION = "qrs_duration";
    private static final String KEY_REGULARITY = "regularity";
    private static final String KEY_P_WAVE = "p_wave";
 
    // Patients_ECG Data Table - column names
    private static final String KEY_PATIENT_ID = "patient_id";
    private static final String KEY_ECG_DATA_ID = "ecg_data_id";
	private static String[] TABLE_PATIENT_ECG_DATA_COLUMNS = { KEY_ID, KEY_PATIENT_ID, KEY_ECG_DATA_ID }; 

    
    // Table Create Statements
    // Todo table create statement
    private static final String CREATE_TABLE_PATIENT = "CREATE TABLE "
            + TABLE_PATIENT + "(" + KEY_ID + " INTEGER PRIMARY KEY," + KEY_NAME
            + " TEXT," + KEY_GENDER + " INTEGER," + KEY_BIRTHDATE
            + " TEXT" + ")";
 
    // Tag table create statement
    private static final String CREATE_TABLE_ECG_DATA = "CREATE TABLE " + TABLE_ECG_DATA
            + "(" + KEY_ID + " INTEGER PRIMARY KEY," + KEY_DATE + " DATETIME,"
            + KEY_ECG_WAVEFORM + " TEXT," + KEY_HEARTRATE + " INTEGER,"
            + KEY_QRS_DURATION + " INTEGER," + KEY_REGULARITY + " INTEGER,"
            + KEY_P_WAVE + " INTEGER" + ")";
 
    // todo_tag table create statement
    private static final String CREATE_TABLE_PATIENT_ECG_DATA = "CREATE TABLE "
            + TABLE_PATIENT_ECG_DATA + "(" + KEY_ID + " INTEGER PRIMARY KEY,"
            + KEY_PATIENT_ID + " INTEGER," + KEY_ECG_DATA_ID + " INTEGER" + ")";
    
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

	@Override
	public void onCreate(SQLiteDatabase db) {
        // creating required tables
        db.execSQL(CREATE_TABLE_PATIENT);
        db.execSQL(CREATE_TABLE_ECG_DATA);
        db.execSQL(CREATE_TABLE_PATIENT_ECG_DATA);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // on upgrade drop older tables
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PATIENT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ECG_DATA);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PATIENT_ECG_DATA);
 
        // create new tables
        onCreate(db);
	}
	
	/*
	 * Creating a patient
	 */
	public long createPatient(Patient pat) {
	    SQLiteDatabase db = this.getWritableDatabase();
	 
	    ContentValues values = new ContentValues();
	    values.put(KEY_NAME, pat.getName());
	    values.put(KEY_GENDER, pat.getGender());
	    values.put(KEY_BIRTHDATE, pat.getBirthdate());
	 
	    // insert row
	    long patient_id = db.insert(TABLE_PATIENT, null, values);
	    
	    return patient_id;
	}
	
	/*
	 * add an ECG data entry to a patient
	 */
	public long addecgData(Patient pat, ECG_Data data) {
	    SQLiteDatabase db = this.getWritableDatabase();
	 
	    ContentValues values = new ContentValues();
	    values.put(KEY_DATE, data.getDate());
	    values.put(KEY_ECG_WAVEFORM, data.getEcg_waveform());
	    values.put(KEY_HEARTRATE, data.getHeartrate());
	    values.put(KEY_QRS_DURATION, data.getQrs_duration());
	    values.put(KEY_REGULARITY, data.getRegularity());
	    values.put(KEY_P_WAVE, data.getP_wave());
	 
	    // insert row
	    long ecg_data_id = db.insert(TABLE_ECG_DATA, null, values);
	    
	    createPatientECGData(pat.getId(), ecg_data_id);
	    
	    return ecg_data_id;
	}
	
    /*
     * Creating patient_ecg_data
     */
    public long createPatientECGData(long patient_id, long ecg_data_id) {
        SQLiteDatabase db = this.getWritableDatabase();
 
        ContentValues values = new ContentValues();
        values.put(KEY_PATIENT_ID, patient_id);
        values.put(KEY_ECG_DATA_ID, ecg_data_id);
 
        long id = db.insert(TABLE_PATIENT_ECG_DATA, null, values);
 
        return id;
    }
	
	/*
	 * get an ecg waveform
	 */
	public ECG_Data getEcgData(long ecg_data_id) {
	    SQLiteDatabase db = this.getReadableDatabase();
	 
	    String selectQuery = "SELECT  * FROM " + TABLE_ECG_DATA + " WHERE "
	            + KEY_ID + " = " + ecg_data_id;
	 
	    Log.e(LOG, selectQuery);
	 
	    Cursor c = db.rawQuery(selectQuery, null);
	 
	    if (c != null)
	        c.moveToFirst();
	 
	    ECG_Data data = new ECG_Data();
	    data.setId(c.getInt(c.getColumnIndex(KEY_ID)));
	    data.setDate((c.getString(c.getColumnIndex(KEY_DATE))));
	    data.setEcg_waveform(c.getString(c.getColumnIndex(KEY_ECG_WAVEFORM)));
	    data.setHeartrate(c.getInt(c.getColumnIndex(KEY_HEARTRATE)));
	    data.setQrs_duration(c.getInt(c.getColumnIndex(KEY_QRS_DURATION)));
	    data.setRegularity(c.getInt(c.getColumnIndex(KEY_REGULARITY)));
	    data.setP_wave(c.getInt(c.getColumnIndex(KEY_P_WAVE)));
	    
	    return data;
	}
	
    /*
     * getting all ECG_datas for one patient
     */
    public List<ECG_Data> getAllECG_data(Patient pat) {
        List<ECG_Data> data = new ArrayList<ECG_Data>();
        
        SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(TABLE_PATIENT_ECG_DATA, TABLE_PATIENT_ECG_DATA_COLUMNS,
				KEY_PATIENT_ID + "='" + pat.getId() + "'", null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			long ecg_data_id = cursor.getInt(2);
			ECG_Data single = getEcgData(ecg_data_id);
			data.add(single);
			cursor.moveToNext();
		}
		
		return data;
    }
    
    /**
     * Deleting a patient and all his/her ecg data
     */
    public void deletePatient(Patient pat) {
        SQLiteDatabase db = this.getWritableDatabase();
 
        // get all ECG data under this patient
        List<ECG_Data> alldata = getAllECG_data(pat);
 
            // delete all todos
        for (ECG_Data data : alldata) {
            // delete todo
        	deleteEcgData(data.getId());
        }
 
        // now delete the patient in TABLE_PATIENT and TABLE_PATIENT_ECG_DATA
        db.delete(TABLE_PATIENT_ECG_DATA, KEY_PATIENT_ID + " = ?",
                new String[] { String.valueOf(pat.getId()) });
        db.delete(TABLE_PATIENT, KEY_ID + " = ?",
                new String[] { String.valueOf(pat.getId()) });
    }
	
    /**
     * Deleting an ECG_Data
     */
    public void deleteEcgData(long ecg_data_id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_ECG_DATA, KEY_ID + " = ?",
                new String[] { String.valueOf(ecg_data_id) });
    }
    
}
