package c8y.example.cookbook.business;

import java.util.List;

public class SensorAssembly {
	
	private Sensor sensor;

	public SensorAssembly() {
	}
	
	public SensorAssembly(Sensor sensor) {
		super();
		this.sensor = sensor;
	}

	public Sensor getSensor() {
		return sensor;
	}

	public void setSensor(Sensor sensor) {
		this.sensor = sensor;
	}
	
	
}
