package c8y.example.cookbook;

import java.util.Iterator;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionAddedEvent;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionRemovedEvent;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionsInitializedEvent;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.Platform;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.inventory.ManagedObjectCollection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SubscriptionsCookbook {

	@Autowired
	private MicroserviceSubscriptionsService subscriptionsService;
	
	@Autowired
	private Platform c8yPlatform;
	
	private static final Logger log = LoggerFactory.getLogger(SubscriptionsCookbook.class);

	/**
	 * Executes after dependencies have been autowired
	 */
	@PostConstruct
	public void init() {	
		listSubscriptions(); 
	}

	@EventListener
	public void onSubscriptionAdded(MicroserviceSubscriptionAddedEvent event) {
		log.info("subscription added for tenant: " + event.getCredentials().getTenant());
		runBusinessLogicForSingleTenant(event.getCredentials().getTenant());
	};
	
	@EventListener
	public void onSubscriptionRemoved(MicroserviceSubscriptionRemovedEvent event) {
		log.info("subscription removed for tenant: " + event.getTenant());
	};

	@EventListener
	public void onSubscriptionsInitialized(MicroserviceSubscriptionsInitializedEvent event) {
		log.info("Subscriptions have been initialized on application startup");
		listSubscriptions();
		runBusinessLogicForAllTenants();
		
		try {
			runBusinessLogicOutOfContext();			
		} catch (Exception e) {
			log.error("Error, not in any tenant scope"); // results in "java.lang.IllegalStateException: Not within any context!"
		}
	}

	private void listSubscriptions() {
		if (subscriptionsService.getAll().isEmpty()) {
			log.info("No tenants are subscribed to this application");
		} else {
			for (MicroserviceCredentials microserviceCredentials : subscriptionsService.getAll()) {
				log.info(String.format("The tenant %s is subscribed to this application ",  microserviceCredentials.getTenant()));
			}
		}		
	};
	
	private void runBusinessLogicForAllTenants() {
		subscriptionsService.runForEachTenant( ()->{
			/*
			 * runForEachTenant() works some magic behind the scenes. Code that is wrapped in  
			 * runForEachTenant() will actually use a different instance of Platform (and InventoryApi) 
			 * for each tenant. Under the hood, this is implemented using Spring's custom
			 * scopes functionality (see https://www.baeldung.com/spring-custom-scope)
			 * 
			 */
			String tenant= subscriptionsService.getTenant();		 
			InventoryApi tenantInventoryApi = c8yPlatform.getInventoryApi();
			ManagedObjectCollection managedObjectCollection = tenantInventoryApi.getManagedObjects();
			Iterator<ManagedObjectRepresentation> itor = managedObjectCollection.get().elements(1).iterator();
			while (itor.hasNext()) {
                ManagedObjectRepresentation managedObjectRepresentation = itor.next();
                try {
					log.info(String.format("Fteched managed object with id %s from tenant %s: %s",
							managedObjectRepresentation.getId().getValue(), 
							tenant,
							new ObjectMapper().writeValueAsString(managedObjectRepresentation)));
				} catch (JsonProcessingException e) {
					log.error("Error writing JSON string", e);
				}
            }
		});
	}
	
	/**
	 * This will throw an exception unless it is wrapped in one of:  
	 * 
	 * MicroserviceSubscriptionsService.runForEachTenant()
	 * MicroserviceSubscriptionsService.runForTenant() 
	 * MicroserviceSubscriptionsService.callForTenant()
	 * ...
	 * 
	 * Or wrapped in: 
	 * 
	 * ContextService.runWithinContext(MicroserviceCredentials c,  Runnable task)
	 * ...
	 * 
	 */
	private void runBusinessLogicOutOfContext() {
		InventoryApi tenantInventoryApi = c8yPlatform.getInventoryApi();
		ManagedObjectCollection managedObjectCollection = tenantInventoryApi.getManagedObjects();
		Iterator<ManagedObjectRepresentation> itor = managedObjectCollection.get().elements(1).iterator();
		while (itor.hasNext()) {
            ManagedObjectRepresentation managedObjectRepresentation = itor.next();
            try {
				log.info(String.format("Fteched managed object with id %s : %s",
						managedObjectRepresentation.getId().getValue(),						
						new ObjectMapper().writeValueAsString(managedObjectRepresentation)));
			} catch (JsonProcessingException e) {
				log.error("Error writing JSON string", e);
			}
        }	
	}
	
	/**
	 * @param tenant Tenant id, e.g. 't174774'
	 */
	private void runBusinessLogicForSingleTenant(String tenant) {		
		subscriptionsService.runForTenant(tenant,  ()->{
			InventoryApi tenantInventoryApi = c8yPlatform.getInventoryApi();
			ManagedObjectCollection managedObjectCollection = tenantInventoryApi.getManagedObjects();
			Iterator<ManagedObjectRepresentation> itor = managedObjectCollection.get().elements(1).iterator();
            while (itor.hasNext()) {
                ManagedObjectRepresentation managedObjectRepresentation = itor.next();
                try {
					log.info(String.format("Fteched managed object with id %s from tenant %s: %s",
							managedObjectRepresentation.getId().getValue(), 
							tenant,
							new ObjectMapper().writeValueAsString(managedObjectRepresentation)));
				} catch (JsonProcessingException e) {
					log.error("Error writing JSON string", e);
				}
            }
		});
	}

	
}
