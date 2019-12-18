package c8y.example.cookbook.business;

import java.util.List;

import org.svenson.JSONTypeHint;

public class SensorArray {
	
	private List<Sensor> sensors;
	
	public SensorArray() {
		
	}
	
	public SensorArray(List<Sensor> sensors) {
		super();
		this.sensors = sensors;
	}

	public List<Sensor> getSensors() {
		return sensors;
	}

	public void setSensors(List<Sensor> sensors) {
		this.sensors = sensors;
	}
	
	
}
