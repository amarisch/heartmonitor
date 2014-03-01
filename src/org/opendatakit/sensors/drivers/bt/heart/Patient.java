package org.opendatakit.sensors.drivers.bt.heart;

public class Patient {

	private int id;
	private String name;
	private int gender; // 0=male; 1=female
	private String ecg;

	public long getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	// string of ecg is parse and casted to integer array when this function is called
	public int[] getecg() {
		String[] s = ecg.split(",");
		 int[] numbers = new int[s.length];
		 for (int curr = 0; curr < s.length; curr++)
		     numbers[curr] = Integer.parseInt(s[curr]);

		return numbers;
	}

	// store ecg in string format
	public void setecg(String ecg) {
		this.ecg = ecg;
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

	// This method determines what will be displayed in listview
	public String toString() {
		return name;
	}
}