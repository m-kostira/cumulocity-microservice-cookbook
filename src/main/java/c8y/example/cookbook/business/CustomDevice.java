package c8y.example.cookbook.business;

public class CustomDevice {		
	
	private String manufacturer;
	private String model;	
	
	public CustomDevice() {				
	}

	public CustomDevice(String manufacturer, String model) {
		super();
		this.manufacturer = manufacturer;
		this.model = model;
	}

	public String getManufacturer() {
		return manufacturer;
	}
	
	public void setManufacturer(String manufacturer) {
		this.manufacturer = manufacturer;
	}
	
	public String getModel() {
		return model;
	}
	
	public void setModel(String model) {
		this.model = model;
	}
}
