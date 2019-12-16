# cumulocity-microservice-java

This cookbook shows some Java snippets for some common scenarios that arise when developing Cumulocity microservices. The snippets are mostly based on the default Java SDK for Cumulocity. 

The cookbook comes with a runnable Maven project that includes all the snippets.

Prerequisites:
- C8y microservices overview (https://cumulocity.com/guides/microservice-sdk/concept/)
- Java SDK overview and Hello World step-by-step (https://cumulocity.com/guides/microservice-sdk/java/)

## Service credentials and tenant subscription

When your (multi-tenant) microservice starts, the c8y SDK will automatically retrieve the list of tenants that subscribe to the microservice. It will also retrieve the service credentials for each tenant, which are needed to make calls to the c8y platform APIs. Your code can be notified when this happends by listening for particular events (this relies on Spring events, so your class needs to be annotated with @Component/@Service/@RestController).

Detecting when a tenant has subscribed to the microservice. This will also execute once on microservice startup for all active subscriptions.
```
@Component
public class SubscriptionsCookbook {
    @EventListener
	public void onSubscriptionAdded(MicroserviceSubscriptionAddedEvent event) {
		log.info("subscription added for tenant: " + event.getCredentials().getTenant());
		...
	};
	...
}
```

Detecting when the service credentials for all active subscriptions are available. This will execute only once on microservice startup.

```
	@EventListener
	public void onSubscriptionsInitialized(MicroserviceSubscriptionsInitializedEvent event) {
		log.info("Subscriptions have been initialized on application startup");
		...
	}
```

Detecting when a tenant is unsubscribed:
```
@EventListener
	public void onSubscriptionRemoved(MicroserviceSubscriptionRemovedEvent event) {
		log.info("subscription removed for tenant: " + event.getTenant());
	};
```

You can autowire `MicroserviceSubscriptionsService` to get the list of service credentials:

```
@Component
public class SubscriptionsCookbook {
    @Autowired
    private MicroserviceSubscriptionsService subscriptionsService;
	
	private void listSubscriptions() {
		if (subscriptionsService.getAll().isEmpty()) {
			log.info("No tenants are subscribed to this application");
		} else {
			for (MicroserviceCredentials microserviceCredentials : subscriptionsService.getAll()) {
				log.info(String.format("The tenant %s is subscribed to this application ",  microserviceCredentials.getTenant()));
			}
		}		
	}
	...
}
```

To make platform API requests, autowire the needed API wrappers and use the `MicroserviceSubscriptionsService` to switch to the particular tenant scope.
`MicroserviceSubscriptionsService.runForEachTenant()` works some magic behind the scenes. Code that is wrapped in  runForEachTenant() will actually use a different instance of Platform (and InventoryApi) for each tenant. Under the hood, this is implemented using Spring's custom scopes functionality (see https://www.baeldung.com/spring-custom-scope)

```
@Component
public class SubscriptionsCookbook {
	@Autowired
	private MicroserviceSubscriptionsService subscriptionsService;
	@Autowired
	private Platform c8yPlatform;
	
	public void runBusinessLogicForAllTenants() {
		subscriptionsService.runForEachTenant( ()->{
			String tenant= subscriptionsService.getTenant();		 
			InventoryApi tenantInventoryApi = c8yPlatform.getInventoryApi();
			ManagedObjectCollection managedObjectCollection = tenantInventoryApi.getManagedObjects();
			...
		}	
	}
}

```

## Inventory basics

## Inventory advanced serialization 
What if you want to store more complex objects in the inventory, for example an SensorArray object which contains a collection Sensor instances of different types? This is not possible out of the box. In particular, the default serialization fails when a class has a property that is polymorphic, e.g. the property type is a superclass/interface and the object is a subclass/implementor. 

The workaround for these cases is to use custom serialization. In our case, this is a solution based on the Jackson JSON library, which encodes the concrete object types as part of the inventory object.

Note: The Java microservice SDK default serialization uses the Svenson library to serialize Java objects to inventory JSON documents. Workarounds for cases like the above exist, but are cumbersome or incomplete (see https://code.google.com/archive/p/svenson/wikis/ParsingJSON.wiki)

