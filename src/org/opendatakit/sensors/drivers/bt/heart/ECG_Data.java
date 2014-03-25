package org.opendatakit.sensors.drivers.bt.heart;

public class ECG_Data {
	private int id;
	private String date;
	private String ecg_waveform;
	private int heartrate;
	private int qrs_duration;
	private int regularity;
	private int p_wave;

	public ECG_Data() { }
	
	public ECG_Data(int id, String date, String ecg_waveform, int heartrate,
			int qrs_duration, int regularity, int p_wave) {
		this.id = id;
		this.date = date;
		this.ecg_waveform = ecg_waveform;
		this.heartrate = heartrate;
		this.qrs_duration = qrs_duration;
		this.regularity = regularity;
		this.p_wave = p_wave;
	}
	
	public void setId(int id) {
		this.id = id;
	}

	public void setDate(String date) {
		this.date = date;
	}
	
	public void setEcg_waveform(String ecg_waveform) {
		this.ecg_waveform = ecg_waveform;
	}
	
	public void setHeartrate(int heartrate) {
		this.heartrate = heartrate;
	}
	
	public void setQrs_duration(int qrs_duration) {
		this.qrs_duration = qrs_duration;
	}
	
	public void setRegularity(int regularity) {
		this.regularity = regularity;
	}
	
	public void setP_wave(int p_wave) {
		this.p_wave = p_wave;
	}
	
	public long getId() {
		return this.id;
	}
	
	public String getDate() {
		return this.date;
	}
	
	public String getEcg_waveform() {
		return this.ecg_waveform;
	}
	// string of ecg is parse and casted to integer array when this function is called
	public int[] waveform_to_array() {
		ecg_waveform = ecg_waveform.replaceAll("\\[", "");
		ecg_waveform = ecg_waveform.replaceAll("\\]", "");
		ecg_waveform = ecg_waveform.replaceAll(" ", "");
		String[] s = ecg_waveform.split(",");
		 int[] numbers = new int[s.length];
		 for (int curr = 0; curr < s.length; curr++)
		     numbers[curr] = Integer.parseInt(s[curr]);

		return numbers;
	}
	
	public int getHeartrate() {
		return this.heartrate;
	}

	public int getQrs_duration() {
		return this.qrs_duration;
	}

	public int getRegularity() {
		return this.regularity;
	}

	public int getP_wave() {
		return this.p_wave;
	}
	
	// This method determines what will be displayed in listview
	public String toString() {
		return date;
	}

}
