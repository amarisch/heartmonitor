package org.opendatakit.sensors.drivers.bt.heart;

public class Patient {

	private int id;
	private String name;
	private int gender; // 0=male; 1=female

	public long getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public int getGender() {
		return this.gender;
	}

	public void setGender(int gender) {
		this.gender = gender;
	}

	@Override
	public String toString() {
		return name;
	}
}