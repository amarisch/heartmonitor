package org.opendatakit.sensors.drivers.bt.heart;

import java.io.Serializable;

public class Patient implements Serializable {

	private int id;
	private String name;
	private String gender;
	private String birthdate;

	public Patient() {
	}
	
	public Patient(String name) {
		this.name = name;
	}	
	
	
	public void setId(int id) {
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
}
