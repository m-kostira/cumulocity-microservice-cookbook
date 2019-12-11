package c8y.example.cookbook;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionsInitializedEvent;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.model.util.ExtensibilityConverter;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.Platform;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.inventory.InventoryFilter;
import com.cumulocity.sdk.client.inventory.ManagedObjectCollection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import c8y.example.cookbook.business.CustomDevice;
import c8y.example.cookbook.util.ExtendedInventoryFilter;

@Component
public class InventoryCookbook {
	
	private static final Logger log = LoggerFactory.getLogger(InventoryCookbook.class);
	
	@Autowired
	MicroserviceSubscriptionsService subscriptionsService;
	
	@Autowired
    InventoryApi inventoryApi;
	
	@EventListener
	public void onSubscriptionsInitialized(MicroserviceSubscriptionsInitializedEvent event) {
		try {
			log.info("Fetching from inventory..");
			simpleFetchFromIventory();
					
			log.info("Inserting in inventory..");
			addToInventory();
			
			log.info("Fetching from inventory with a filter on fragment type/Java class");
			// get all objects of class CustomDevice
			InventoryFilter filter = new InventoryFilter().byFragmentType(CustomDevice.class);
			filteredFetchFromIventory(filter);
			
			// see the section on Query Language at https://cumulocity.com/guides/reference/inventory/  
			log.info("Fetching from inventory with a query filter..");
			String fragmentTypeString =  ExtensibilityConverter.classToStringRepresentation(CustomDevice.class);
			InventoryFilter queryFilter = new ExtendedInventoryFilter().byQuery(String.format("%s.manufacturer eq 'Acme Corp'", fragmentTypeString));
			filteredFetchFromIventory(queryFilter);			
			
			// this will return no matches
			log.info("Fetching from inventory with a query filter..");
			queryFilter = new ExtendedInventoryFilter().byQuery(String.format("%s.manufacturer eq 'AmeriCorp'", fragmentTypeString));
			filteredFetchFromIventory(queryFilter);
			
			log.info("Updating existing object in inventory");
			updateInInventory();
			
		} catch (Exception e) {
			log.error("Error", e);
		} finally {
			log.info("Deleting from inventory..");
			deleteFromInventory();
		}
	}
		
	private void updateInInventory() {
		InventoryFilter filter = new InventoryFilter().byFragmentType(CustomDevice.class);
		subscriptionsService.runForEachTenant( ()->{
			ManagedObjectCollection managedObjectCollection = inventoryApi.getManagedObjectsByFilter(filter);
			
			// get a single managed object
			ManagedObjectRepresentation mor = managedObjectCollection.get().elements(1).iterator().next();
			
			// create a new one to replace it; we could also update the one we fetched and pass it to inventoryApi.update()
			ManagedObjectRepresentation updated = new ManagedObjectRepresentation();
			updated.set(new CustomDevice("Acme Corp", "foobar 15"));
			
			// set the new object's database id to the one we're updating
			updated.setId(new GId(mor.getId().getValue()));
			
			inventoryApi.update(updated);
		});	
	}

	private void deleteFromInventory() {
		InventoryFilter filter = new InventoryFilter().byFragmentType(CustomDevice.class);
		subscriptionsService.runForEachTenant( ()->{			
			ManagedObjectCollection managedObjectCollection = inventoryApi.getManagedObjectsByFilter(filter);
			Iterable<ManagedObjectRepresentation> mos = managedObjectCollection.get().allPages();
			for (ManagedObjectRepresentation mor : mos) {
				log.info(String.format("Deleting in tenant %s; managed object: %s ", 
						subscriptionsService.getTenant(),
						mor.getId().getValue()));
				inventoryApi.delete(mor.getId());
            }
		});	
	}


	private void addToInventory() {		
		subscriptionsService.runForEachTenant(()-> {
			CustomDevice device = new CustomDevice("Acme Corp", "foobar 12");
			ManagedObjectRepresentation mor = new ManagedObjectRepresentation();
			
			// serializes the device Java object as a property of the managed object
			// the Java class will be the name of the property with underscores: 
			// c8y_example_cookbook_business_CustomDevice
			mor.set(device);	
			
			try {
				mor = inventoryApi.create(mor);			
			
				log.info(String.format("Created object in tenant %s; managed object: %s ", 
						subscriptionsService.getTenant(),
						new ObjectMapper().writeValueAsString(mor)
						));
			
			} catch (JsonProcessingException e) {
				log.error("Error writing JSON string", e);
			} catch (Exception e) {
				log.error("Error creating ManagedObject in inventory", e);
			}
		});
	}
	
	private void filteredFetchFromIventory(InventoryFilter filter) {
		subscriptionsService.runForEachTenant( ()->{			
			String tenant= subscriptionsService.getTenant();			
			ManagedObjectCollection managedObjectCollection = inventoryApi.getManagedObjectsByFilter(filter);
			Iterator<ManagedObjectRepresentation> itor = managedObjectCollection.get().allPages().iterator();
            while (itor.hasNext()) {
                ManagedObjectRepresentation managedObjectRepresentation = itor.next();
                try {
					log.info(String.format("Fetched with filter a managed object with id %s from tenant %s, object: %s", 
							managedObjectRepresentation.getId().getValue(), 
							tenant,							
							new ObjectMapper().writeValueAsString(managedObjectRepresentation)));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
				}
            }
		});		
	}

	
	private void simpleFetchFromIventory() {
		subscriptionsService.runForEachTenant( ()->{
			String tenant= subscriptionsService.getTenant();			
			ManagedObjectCollection managedObjectCollection = inventoryApi.getManagedObjects();
			int numObjectsToRetrieve = 1;			
			Iterator<ManagedObjectRepresentation> itor = managedObjectCollection.get().elements(numObjectsToRetrieve).iterator();					
            while (itor.hasNext()) {
                ManagedObjectRepresentation managedObjectRepresentation = itor.next();
                try {
					log.info(String.format("Fteched managed object with id %s from tenant %s", 
							managedObjectRepresentation.getId().getValue(), 
							tenant,
							new ObjectMapper().writeValueAsString(managedObjectRepresentation)));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
				}
            }
		});		
	}
}
