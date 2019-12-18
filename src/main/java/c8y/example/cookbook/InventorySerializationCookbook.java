package c8y.example.cookbook;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionsInitializedEvent;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.util.ExtensibilityConverter;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.inventory.InventoryFilter;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import c8y.example.cookbook.business.CustomDevice;
import c8y.example.cookbook.business.HumiditySensor;
import c8y.example.cookbook.business.Sensor;
import c8y.example.cookbook.business.SensorArray;
import c8y.example.cookbook.business.SensorAssembly;
import c8y.example.cookbook.business.TemperatureSensor;
import c8y.example.cookbook.util.ExtendedInventoryFilter;
import c8y.example.cookbook.util.ManagedObjectPOJOMapper;

@Component
public class InventorySerializationCookbook {
	
	private static final Logger log = LoggerFactory.getLogger(InventorySerializationCookbook.class);
	
	@Autowired
	MicroserviceSubscriptionsService subscriptionsService;
	
	@Autowired
    InventoryApi inventoryApi;
	
	@EventListener
	public void onSubscriptionsInitialized(MicroserviceSubscriptionsInitializedEvent event) {
		serializeComplexObjectDefault();
		serializeComplexObjectCustom();
	}

	/**
	 * This will fail, as we have 
	 */
	private void serializeComplexObjectDefault() {
		try {			
			MicroserviceCredentials tenantCredentials = subscriptionsService.getAll().iterator().next();			
			subscriptionsService.runForTenant(tenantCredentials.getTenant(), ()-> {
				try {
					List<Sensor> sensors = Arrays.<Sensor>asList(new TemperatureSensor("foo"), new HumiditySensor("bar"));
					SensorArray sensorArray = new SensorArray(sensors);
					
					ManagedObjectRepresentation mor = new ManagedObjectRepresentation();
					mor.set(sensorArray);
					
					ManagedObjectRepresentation created = inventoryApi.create(mor);
					
					log.info(String.format("Created sensor array: %s ", 
								new ObjectMapper().writeValueAsString(created)));
					
					ManagedObjectRepresentation fetched = inventoryApi.get(created.getId());
					SensorArray fetchedSensorArray = fetched.get(SensorArray.class);

					log.info(String.format("Fetched sensor array: %s ", 
							new ObjectMapper().writeValueAsString(fetchedSensorArray)));
					
					// error, because type information is missing and fetchedSensorArray was not correctly deserialized
					Sensor sensor = fetchedSensorArray.getSensors().get(0); // throws java.lang.ClassCastException: java.util.HashMap cannot be cast to c8y.example.cookbook.business.Sensor
				
					log.info(String.format("Fetched sensor: %s ", 
							new ObjectMapper().writeValueAsString(sensor)));
				
				} catch (Exception e) {
					log.error("Error ", e);
				}
			});
			
		} catch (Exception e) {
			log.error("Error", e);
		} 
	}
	
	
	private void serializeComplexObjectCustom() {
		try {			
			MicroserviceCredentials tenantCredentials = subscriptionsService.getAll().iterator().next();			
			subscriptionsService.runForTenant(tenantCredentials.getTenant(), ()-> {
				try {
					List<Sensor> sensors = Arrays.<Sensor>asList(new TemperatureSensor("foo"), new HumiditySensor("bar"));
					SensorArray sensorArray = new SensorArray(sensors);
					ManagedObjectRepresentation mor = new ManagedObjectRepresentation();
					ManagedObjectPOJOMapper.TRUSTED_SOURCE.writePOJO(mor, sensorArray);
					
					ManagedObjectRepresentation created = inventoryApi.create(mor);
					
					log.info(String.format("Created sensor array: %s ", 
								new ObjectMapper().writeValueAsString(created)));
					
					ManagedObjectRepresentation fetched = inventoryApi.get(created.getId());
					SensorArray fetchedSensorArray = ManagedObjectPOJOMapper.TRUSTED_SOURCE
							.readPOJO(fetched, SensorArray.class);
					
					log.info(String.format("Fetched sensor array: %s ", 
							new ObjectMapper().writeValueAsString(fetchedSensorArray)));
					
					// error, because type information is missing and fetchedSensorArray was not correctly deserialized
					Sensor sensor = fetchedSensorArray.getSensors().get(0); // throws java.lang.ClassCastException: java.util.HashMap cannot be cast to c8y.example.cookbook.business.Sensor
					
					log.info(String.format("Fetched sensor: %s ", 
							new ObjectMapper().writeValueAsString(sensor)));
					
					
				} catch (Exception e) {
					log.error("Error ", e);
				}
			});
			
		} catch (Exception e) {
			log.error("Error", e);
		} 
	}
			
}
