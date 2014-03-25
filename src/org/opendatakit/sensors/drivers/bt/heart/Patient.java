package org.opendatakit.sensors.drivers.bt.heart;

public class Patient {

	private int id;
	private String name;
	private int gender; // 0=male; 1=female
	private String birthdate;

	public void setId(int id) {
		this.id = id;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setGender(int gender) {
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
	
	public int getGender() {
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
