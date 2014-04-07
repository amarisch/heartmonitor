package org.opendatakit.sensors.drivers.bt.heart;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class Patient implements Parcelable {

	private long id;
	private String name;
	private String gender;
	private String birthdate;

	public Patient() {
	}
	
	public Patient(String name) {
		this.name = name;
	}	
	
	
	public void setId(long id) {
		this.id = id;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setGender(String gender) {
		this.gender = gender;
	}

	public void setBirthdate(String birthdate) {
		this.birthdate = birthdate;
	}
	
	public long getId() {
		return id;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getGender() {
		return this.gender;
	}
	
	public String getBirthdate() {
		return this.birthdate;
	}
	
	// This method determines what will be displayed in listview
	public String toString() {
		return name;
	}
	
    /** Used to give additional hints on how to process the received parcel.*/
    @Override
    public int describeContents() {
	// ignore for now
	return 0;
    }

	@Override
	public void writeToParcel(Parcel pc, int flags) {
		pc.writeLong(id);
		pc.writeString(name);
		pc.writeString(gender);
		pc.writeString(birthdate);
	}

   /** Static field used to regenerate object, individually or as arrays */
	public static final Parcelable.Creator<Patient> CREATOR = new Parcelable.Creator<Patient>() {
		public Patient createFromParcel(Parcel pc) {
			return new Patient(pc);
		}
		public Patient[] newArray(int size) {
			return new Patient[size];
		}
	};

   /**Ctor from Parcel, reads back fields IN THE ORDER they were written */
   public Patient(Parcel pc){
       id = pc.readLong();
       name =  pc.readString();;
       gender = pc.readString();
       birthdate = pc.readString();;
  }
}
