# Cumulocity Microservices Cookbook Using The Java SDK

This cookbook shows some Java snippets for some common scenarios that arise when developing Cumulocity microservices. The snippets are mostly based on the default Java SDK for Cumulocity. 

The cookbook comes with a complete, working Spring Boot microservice that includes all the snippets and more, available at https://github.com/m-kostira/cumulocity-microservice-cookbook.

Prerequisites:
- C8y microservices overview (https://cumulocity.com/guides/microservice-sdk/concept/)
- Java SDK overview and Hello World step-by-step (https://cumulocity.com/guides/microservice-sdk/java/)

## Service credentials and tenant subscription

When your (multi-tenant) microservice starts, the c8y SDK will automatically retrieve the list of tenants that subscribe to the microservice. It will also retrieve the service credentials for each tenant, which are needed to make calls to the c8y platform APIs. Your code can be notified when this happends by listening for particular events (this relies on Spring events, so your class needs to be annotated with @Component/@Service/@RestController).

**Note**: Most of the snppiets in the cookbook, (and the example microservice) deal with a multi-tenant microservice. In case of a single tenant microservice (per_tenant isolation), there is only one tenant and one set of service credentials. These are made available to the deployed microservice as the environment variables C8Y_TENANT, C8Y_USER and C8Y_PASSWORD. See https://cumulocity.com/guides/microservice-sdk/concept/#environment-variables for details.

**Detecting when a tenant has subscribed to the microservice** 
This will also execute on microservice startup once for each active subscription.

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

**Detecting when the service credentials for all active subscriptions are available**
This will execute only once on microservice startup.

```
	@EventListener
	public void onSubscriptionsInitialized(MicroserviceSubscriptionsInitializedEvent event) {
		log.info("Subscriptions have been initialized on application startup");		
		...
	}
	
```

**Detecting when a tenant is unsubscribed**

```
@EventListener
	public void onSubscriptionRemoved(MicroserviceSubscriptionRemovedEvent event) {
		log.info("subscription removed for tenant: " + event.getTenant());
	};
	
```

You can autowire `MicroserviceSubscriptionsService` to **get the list of service credentials**:

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

## Make calls to c8y platform APIs

The SDK provides wrappers for the most important c8y APIs, which are available as Spring components. You need to **autowire the platform API** you will use (e.g. `com.cumulocity.sdk.client.inventory.InventoryApi` or `com.cumulocity.sdk.client.Platform`). 

```
@Autowired
InventoryApi inventoryApi;
	
```

To use the APIs, you need to have valid c8y credentials. Generally, there are two options. For one, you can **use the service credentials** of the microservice (for multi-tenant microservices, there is a separate set of credentials for each tenant).  

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

```
@EventListener
public void onSubscriptionsInitialized(MicroserviceSubscriptionsInitializedEvent event) {
	MicroserviceCredentials tenantCredentials = subscriptionsService.getAll().iterator().next();
	subscriptionsService.runForTenant(tenantCredentials.getTenant(), ()-> {
		ManagedObjectCollection managedObjectCollection = inventoryApi.getManagedObjects();
	}
}							

```

The methods `MicroserviceSubscriptionsService.runForTenant()` and `MicroserviceSubscriptionsService.runForEachTenant()`
switch to the particular tenant scope and service credentials. Those methods works some magic behind the scenes. Code that is wrapped in  runForEachTenant() will actually use a different instance of `Platform` (and `InventoryApi`) for each tenant. Under the hood, this is implemented using Spring's custom scopes functionality (see https://www.baeldung.com/spring-custom-scope)

In addition to using service credentials, there is the option to make **API requests using the credentials of the user, whose REST request is being serviced**. The SDK will handle this automatically for code that is called from a `@RestController` request handler method.

## Inventory basics

The inventory is where you generally store all your data in the form of **managed objects** (JSON documents with arbitrary structure). To perform CRUD operations on the inventory using the SDK, inject an instance of  `com.cumulocity.sdk.client.inventory.InventoryApi` or `com.cumulocity.sdk.client.Platform` into your component. 

**Creating an object in the inventory**

```
CustomDevice device = new CustomDevice("Acme Corp", "foobar 12");
ManagedObjectRepresentation mor = new ManagedObjectRepresentation();
		
mor.set(device);	
				
mor = inventoryApi.create(mor); // the id of newly created ManagedObjectRepresentation will now be populated
String createdObjectId = mor.getId().getValue();

```
`ManagedObjectRepresentation.set()` serializes the CustomDevice Java object as a property of the managed object. The Java class will be the name of the property with underscores, e.g. c8y_example_cookbook_business_CustomDevice. Note that in this way we can store multiple Java objects in the same inventory object. Below is how the managed object will look as JSON stored in the inventory:

```
{
	...
	"self": "https://t60662491.adamos-preprod.com/inventory/managedObjects/4605",
	"id": "4605",
	"c8y_example_cookbook_business_CustomDevice": {
		"model": "foobar 12",
		"manufacturer": "Acme Corp"
	}
}

```

At a later time, you can **read the managed object from the inventory and convert back to a Java object**:

```
InventoryFilter filter = new InventoryFilter().byFragmentType(CustomDevice.class);
ManagedObjectCollection managedObjectCollection = inventoryApi.getManagedObjectsByFilter(filter);
ManagedObjectRepresentation mor = managedObjectCollection.get().elements(1).iterator().next();
CustomDevice customDevice = mor.get(CustomDevice.class);

```

**Fetching and updating an object in the inventory**:

```
ManagedObjectRepresentation updated = new ManagedObjectRepresentation();
updated.set(new CustomDevice("Acme Corp", "foobar 15"));

// set the new object's database id to the one we're updating
updated.setId(new GId(existingObjectId));

inventoryApi.update(updated);

```

**Deleting from the inventory** is done by supplying the id of the managed object you want to delete.

```
InventoryFilter filter = new InventoryFilter().byFragmentType(CustomDevice.class);
ManagedObjectCollection managedObjectCollection = inventoryApi.getManagedObjectsByFilter(filter);
Iterable<ManagedObjectRepresentation> mos = managedObjectCollection.get().allPages();

for (ManagedObjectRepresentation mor : mos) {
	log.info(String.format("Deleting in tenant %s; managed object: %s ", 
			subscriptionsService.getTenant(),
			mor.getId().getValue()));
	inventoryApi.delete(mor.getId());
}	
```

## Inventory advanced serialization 

What if you want to store more complex objects in the inventory, for example an SensorArray object which contains a collection Sensor objects of different subtypes? This is not possible out of the box. In particular, the default serialization fails when a class has a property that is polymorphic, e.g. the property type is a superclass/interface and the object is a subclass/implementor. 

The workaround for these cases is to use custom serialization. In our case, this is a solution based on the Jackson JSON library, which encodes the concrete object class name as part of the inventory object. The class `c8y.example.cookbook.util.ManagedObjectPOJOMapper` (included with the cookbook code) provides utility methods for writing and reading plain old Java objects (POJOs) as managed objects in the inventory. Again, it's possible to write multiple Java objects to the same managed object, provided that the Java objects are of different types.

```
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

Sensor sensor = fetchedSensorArray.getSensors().get(0);  // type is TemperatureSensor

log.info(String.format("Fetched sensor: %s ", 
		new ObjectMapper().writeValueAsString(sensor)));
```

Note that deserializing JSON that comes from an untrusted source might not be a good idea from a security standpoint. Therefore the `ManagedObjectPOJOMapper.DEFAULT` can be used in such cases. It relies on the presence of annotations in the polymorphic classes that will be deserialized. See the class documentation for more info.

Note: The Java microservice SDK default serialization uses the Svenson library to serialize Java objects to inventory JSON documents. Workarounds for cases like the above exist, but are cumbersome or incomplete (see https://code.google.com/archive/p/svenson/wikis/ParsingJSON.wiki)

## Exposing a REST API

Servicing inbound HTTP requests is as easy as defining a Spring RestController with request handler methods:

```
@RestController
@RequestMapping("/api")
public class RESTCookbook {
	@GetMapping("/health")
	public String health() {
		return "Service is up and running!";
	}
}
```

Usually, to service a request your microservice needs to fetch some data from the c8y platform. You can do that in two ways: 
- authenticating as the microservice service user (service user)
- authenticating as the cumulocity user that initiated the request to your microservice (request user)

The distinction is important because the service user and the request user may have different permissions. Also, a multitenant microservice may pull data from a different tenant than the request user's tenant.

**Platform requests as a service user**. As before, you need to autowire the components provided by the SDK, such as `com.cumulocity.sdk.client.inventory.InventoryApi` or `com.cumulocity.sdk.client.Platform`. Then you need to wrap calls to those components in `MicroserviceSubscriptionsService.runForTenant()` or `MicroserviceSubscriptionsService.runForEachTenant()`.

```
@GetMapping(path = "/inventory/{tenantId}/{managedObjectId}", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> getManagedObjectFromTenant(
		@PathVariable(value = "tenantId") String tenantId,
		@PathVariable(value = "managedObjectId") String managedObjectId) throws JsonProcessingException {
	
	
	if (!subscriptionsService.getCredentials(tenantId).isPresent()) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(String.format("Tenant not subscribed: %s", tenantId));
	}
		
	ManagedObjectRepresentation managedObject = subscriptionsService.callForTenant(tenantId, ()->{
		return inventoryApi.get(GId.asGId(managedObjectId));
	});
	
	log.info(String.format("Fetched from tenant %s managed object: %s",
			tenantId,
			new ObjectMapper().writeValueAsString(managedObject)));
	
	return ResponseEntity.status(HttpStatus.OK).body(managedObject);
}
```

**Platform requests as the request user**. Unlike above, you don't need to wrap API calls in `MicroserviceSubscriptionsService.runForTenant()` or `MicroserviceSubscriptionsService.runForEachTenant()`. For code inside a RestController handler, the SDK automatically switches to the context of the request user:

```
@GetMapping(path = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> getManagedObjects() {

	Iterable<ManagedObjectRepresentation> managedObjectsIterable = inventoryApi.getManagedObjects().get(2000).allPages();
	List<ManagedObjectRepresentation> managedObjects = StreamSupport.stream(managedObjectsIterable.spliterator(), false).collect(Collectors.toList());
			
	log.info(String.format("Fetched all managed objects, %d total", 
				managedObjects.size()));
	
	return ResponseEntity.status(HttpStatus.OK).body(managedObjects);	
}	

```

**Getting request user info**. To get info about the request user in your RestController, autowire an instance of `com.cumulocity.sdk.client.Platform` and annotate it with `@Qualifier("userPlatform")`. Now, in your request handler methods, you can query the `Platform` for the info of the user that is making the request.

```
@RestController
@RequestMapping("/api")
public class RESTCookbook {
		
	@Autowired
	@Qualifier("userPlatform")
	private Platform userPlatform;	

	@GetMapping(path = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getCurrentUserInfo() throws JsonProcessingException {
		
		if (!(userPlatform instanceof PlatformParameters)) {
			throw new IllegalStateException("userPlatform not instanceof PlatformParameters");
		}
		PlatformParameters userPlatformParameters = (PlatformParameters) userPlatform;
				
		String userInfoString = String.format("User info: host: %s; tenantId: %s; user: %s; auth string: %s; roles: %s", 
			userPlatformParameters.getHost(),
			userPlatformParameters.getTenantId(),
			userPlatformParameters.getUser(),
			userPlatformParameters.getCumulocityCredentials().getAuthenticationString(),
			roleService.getUserRoles());		
		
		return ResponseEntity.status(HttpStatus.OK).body(userInfoString);
	}
}
```

**Checking request user permissions** 

Autowire a `com.cumulocity.microservice.security.service.RoleService` in your RestController. Then, in the scope of a request handler method, you can query `RoleService` for the permissions of the cumulocity user making the request. Again, the SDK does some behind the scene magic to associate the `RoleService` with the request user. You can use `RoleService` only from code that is handling an http request, such as the method `getManagedObjectFromTenant` below; otherwise you would get `java.lang.IllegalStateException: Not within any context!`. 

```
@RestController
@RequestMapping("/api")
public class RESTCookbook {
	
	@Autowired
	private RoleService roleService;	
	
	@GetMapping(path = "/inventory/{tenantId}/{managedObjectId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getManagedObjectFromTenant(
			@PathVariable(value = "tenantId") String tenantId,
			@PathVariable(value = "managedObjectId") String managedObjectId) throws JsonProcessingException {
		
		if(!userHasCookbookAdminRole()) {			 
			return  ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body("Insufficient Permissions: user does not have required permission to access this API: ROLE_COOKBOOK_ADMIN");
		}			
		...
	}
	
	private Boolean userHasCookbookAdminRole() {

		for(String userRole : roleService.getUserRoles()){
			if(userRole.equals("ROLE_COOKBOOK_ADMIN")) {
				return true;
			}
		}
		
		return false;
	}
	

}
```

**Providing custom roles** It's a common use case for a c8y micrservice to declare additional user roles, specific to that particular microservice. You can do so by adding them to the `cumulocity.json` manifest, under the fragment `roles`: 

```
{
  "apiVersion":"1",
  "version":"@project.version@",
  "provider": {
    "name":"Cumulocity GmbH"
  },
  "isolation":"MULTI_TENANT",  
  "roles":[
  	"ROLE_COOKBOOK_READ",
  	"ROLE_COOKBOOK_ADMIN"
  ],
  ...
}
```

## Interacting with Cumulocity without the Cumulocity Java SDK
If there is some functionality you need to use that is not provided by the Java SDK, you can always make HTTP requests to the platform directly. Below is an example using Apache HTTPClient to retrieve the service user credentials:

```
@Component
public class HTTPClientCookbook {

	private static final Logger log = LoggerFactory.getLogger(HTTPClientCookbook.class);
	
	@Value("${C8Y.baseURL}")
	private String c8yUrl;
	
	@Value("${C8Y.bootstrap.tenant}")
	private String tenantId;
	
	@Value("${C8Y.bootstrap.user}")
	private String serviceBootstrapUser;
	
	@Value("${C8Y.bootstrap.password}")
	private String serviceBootstrapPassword;
	
	private HttpClient httpClient;
	
	@PostConstruct
	private void init() throws Exception {
		
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(tenantId + '/' + serviceBootstrapUser, serviceBootstrapPassword));
        			
		httpClient = HttpClients.custom()
	    .setDefaultCredentialsProvider(credsProvider)
        .setConnectionTimeToLive(20, TimeUnit.SECONDS)
        .setMaxConnTotal(400).setMaxConnPerRoute(400)
        .setDefaultRequestConfig(RequestConfig.custom()
                .setSocketTimeout(30000).setConnectTimeout(5000).build())
        .setRetryHandler(new DefaultHttpRequestRetryHandler(5, true))        
        .build();
		
		
		ArrayNode serviceCredentials = getServiceCredentials();
		log.info(String.format("Service credentials: %s", serviceCredentials));
	}

	private ArrayNode getServiceCredentials() throws ClientProtocolException, IOException {
		HttpGet request = new HttpGet(c8yUrl + "/application/currentApplication/subscriptions");		
		request.setHeader("Content-type", "application/json");
		
		HttpResponse response = httpClient.execute(request);
		
		if (response.getStatusLine().getStatusCode() != 200) {
			throw new IOException(String.format("Error getting service subscriptions,response: status code: %d " + "body:%s",
					response.getStatusLine().getStatusCode(), EntityUtils.toString(response.getEntity())));
		}
				
		JsonNode tree = new ObjectMapper().readTree(EntityUtils.toString(response.getEntity()));
		ArrayNode users = (ArrayNode) tree.at("/users");
		
		return users;
	}
	
}
```


## Configuring your microservice

**Spring Boot application.properties**

Almost every application needs some configuration options to be provided at startup. For Spring Boot, these options are usually provided in `src/main/resources/application.properties` as key-value pairs:

```
application.name=microservice-cookbook
server.port=80

C8Y.bootstrap.initialDelay=3000
```

Here, 'server.port' tells Spring Boot to start the microservice on the default HTTP port 80.
`C8Y.bootstrap.initialDelay` is a property which tells the Cumulocity SDK how long to wait (in milliseconds) before it retrieves the microservice credentials using the microservice bootstrap user; the longer you set this, the longer it will be before you get the `MicroserviceSubscriptionAdded` events. As discussed in the official `Cumulocity Java SDK Guide`, there are some parameters which you need to specify in order to run and test your microservice locally:

```
C8Y.baseURL=<tenant URL, e.g. https://mytenant.cumulocity.com>
C8Y.bootstrap.tenant=<tenant id, e.g. t1438548>
C8Y.bootstrap.user=servicebootstrap_microservice-cookbook
C8Y.bootstrap.password=<password for the service bootstrap user>
```

These are used by the Java SDK to connect to the platform.

** Reading property values **
You can, in fact, read these properties in your application code. The most straight-forward way to do this is to have your class annotated as a Spring `Component` and injecting the property value using the annotation `@Value`:

```
@Component
public class ConfigurationCookbook {
	private static final Logger log = LoggerFactory.getLogger(ConfigurationCookbook.class);
		
	@Value("${C8Y.baseURL}")
	private String baseURL;
	
	@PostConstruct
	public void init() {
		log.info("Base url is " + baseURL);
	}
}
```

Here, the @PostConstruct annotation tells Spring to execute the method after all the dependencies of the component have been injected. Note that if you were to try to read `baseURL` in the class constructor, it would be null - because Spring has not injected it yet. You could also inject values in your class contructor directly using this syntax:

```
@Component
public class ConfigurationCookbook {
	private static final Logger log = LoggerFactory.getLogger(ConfigurationCookbook.class);
		
	private String baseURL;
	
	 @Inject
	 public ConfigurationCookbook(@Value("${C8Y.baseURL}") String baseURL) {
		 this.baseURL = baseURL;
		 log.info("Base url is " + baseURL);
	 }	
}

```

Using constructor injection is cleaner and is recommended for required dependencies, but you have to be careful not to have circular dependencies between your components.

You can of course supply and read your custom properties. 

Reading with a default value, if one is not supplied:

```
@Value("${myapp.myCustomProp:20}")
private int myCustomProp;	
```

**Spring profiles**

Spring Boot has a **profiles** feature, which allows you to easily run the application with a different set of properties based on the active profile. If you don't explicitly specify a profile, the `default` profile will be loaded. The `default` profile loads the default properties, stored in `src/main/resources/application.properties`. You can activate additional profiles, which will load additional properites files, where you can override the default property values and supply additional properties.

Let's say you want to configure your application to run on port 8080 when running locally, because port 80 is used by another process. To do so, you need to override the `server.port` property. We will define a `local` profile and activate it by passing the command line argument `--spring.profiles.active=local` (In Eclipse, this is can be entered in the Run/Debug configuration dialog -> `Arguments` tab-> `Program arguments`). When the profile is active, it will automatically load properties from the file `application-<profile name>.properties`. This means that in our case, we will need to create an `application-local.properties` file, containing:

```
server.port=8080
```

You can activate multiple profiles: `--spring.profiles.active=local,dev`. Note that the order matters - properties in the `local` profile will override properties in the default profile, and properties in the `dev` profile will override `local` profile properties.

You may have different environments: dev, test, prod, each associated with a different tenant. It is possible to have a separate profile and properties for each environment, e.g.

application-dev.properties:
```
C8Y.baseURL=https://dev.cumulocity.com
C8Y.bootstrap.tenant=devTenantId
C8Y.bootstrap.user=servicebootstrap_microservice-cookbook
C8Y.bootstrap.password=<dev bootstrap password>
```

application-test.properties:
```
C8Y.baseURL=https://test.cumulocity.com
C8Y.bootstrap.tenant=testTenantId
C8Y.bootstrap.user=servicebootstrap_microservice-cookbook
C8Y.bootstrap.password=<test bootstrap password>
```

Then you could have activate a different profile when you want to test your service against a particular environment. 

There is a problem with this approach, howerever - you are storing credentials in a document which is under source control, which is a bad practice from a security standpoint, and it's also fragile because bootstrap credentials might change. A better approach is presented in the next section.

**Externalising properties**

Let's say you want to supply credentials to your service, in order to test locally. You can do so in the properties file, but to avoid having the credentials under source control, you can also supply properties as command line arguments or even as environment variables. Spring Boot loads the application's properties not only from the `application.properties` file, but also from a number of other locations, in a very specific precedence order:
https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config

For our purposes, the most common ways to supply properties are the following (highest precedence first):

1. Command line arguments
2. OS environment variables
3. In application.properties 

Thus, we can supply credentials as command line arguments:

```
--C8Y.baseURL=https://dev.cumulocity.com --C8Y.bootstrap.tenant=devTenantId --C8Y.bootstrap.user=servicebootstrap_microservice-cookbook --C8Y.bootstrap.password=iH1JZBJN0UK1rn5d50Dd1ntfl6E3a3Bi
```

**Environment variables** are also a useful way to supply properties. Note - most operating systems disallow period-separated key names and it's standard practice to use underscores and capital case, as in `C8Y_BASEURL`. Spring is pretty smart about that, so if you try to lookup a property `c8y.baseURL`, the framework will resolve it to the environment variable `C8Y_BASEURL`. 

In fact, the Cumulocity platform injects a number of envrionment variables into the microservice runtime (the Docker container inside which the microservice runs). When you deploy a microservice to the platform, these environment variables are injected

- `C8Y_BOOTSTRAP_TENANT`, `C8Y_BOOTSTRAP_USER`, `C8Y_BOOTSTRAP_PASSWORD` (for multitenant microsertvices)

- `C8Y_TENANT`, `C8Y_USER`, `C8Y_PASSWORD` (for per-tenant microservices)

Thus, you don't really need these properties (`C8Y.baseURL`, etc.) in your `application.properties` file, because they will be supplied by the platform; you only need them for running your microservice locally. In that way, your microservice can be deployed to different tenants without the need to package it with different bootstrap properties every time.

**Properties in tenant options**

Often, you want to provide your microservice to a client, who may need to change a configuration property. With properties in `application.properties`, this means you have to update the file and repackage the microservice, which is impractical. Environment variables are not an option, since you have no control over them once the microservice is deployed. Even if you did, there would be no way to change a property at runtime. For these use cases, it's recommended to store your properties in the Cumulocity platform itself and the recommended place to do so is the **tenant options**. 

The tenant options are category-key-value tuples, that can be created, read, updated and deleted via the Cumulocity REST API (see the docs for details). As such, they are accessible to microservices. 

The convention is to store settings for your microservice in a tenant options, whose category is the same as the microservice **context path**. See the docs for more details, but basically, unless you specify otherwise, the context path is equal to the application name. So if the application is under `https://my-tenant.cumulocity.com/service/my-app`, then you would store settings by executing

`POST https://my-tenant.cumulocity.com/tenant/options`

with request body:

```
{
    "category": "my-app",
    "key": "<setting name>",
    "value": "<setting value>"
}
```

To read those settings from your microservice, execute

`GET /application/currentApplication/settings`

Authenticate as the service bootstrap user, to get the settings for the owner tenant (in the case of a multitenant microservice). Authenticate as a service user for a particular tenant, to get the settings for that tenant.  

The added benefit of storing settings in this way is that you can store sensitive information and make the platform encrypt it. This way, regular users will see the setting values encrypted in the tenant options, but it will still be available decrypted to the microservice user. To make a setting encrypted, prefix the key with `credentials`:

`POST https://my-tenant.cumulocity.com/tenant/options`

```
{
    "category": "my-app",
    "key": "credetials.apiPassword",
    "value": "<setting value>"
}
```

The Java Cumulocity SDK provides some convenience classes that do that for you and let you easily obtain the microservice settings. Keep in mind the SDK caches the settings for 10  minutes, so you won't see updates immediately.

```
@RestController
@RequestMapping("/configuration")
public class ConfigurationRestController {

	@Autowired
	private Environment environment;

	@Autowired
	private MicroserviceSettingsService settingsService;
	
	/**
	 * @return All available settings
	 */
	@GetMapping(path = "/environment", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getConfigurationProperties(
			@RequestParam(value = "propertyName", required = false) String propertyName) throws JsonProcessingException {
		try {
			
			if (propertyName != null) {
				String property = environment.getProperty(propertyName);
				return ResponseEntity.status(HttpStatus.OK).body(property);
			}
			
			MutablePropertySources propSrcs = ((AbstractEnvironment) environment).getPropertySources();
			Map<String, String> props = StreamSupport.stream(propSrcs.spliterator(), false)
			        .filter(ps -> ps instanceof EnumerablePropertySource)
			        .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
			        .flatMap(Arrays::<String>stream)		        
			        .distinct()
			        .collect(Collectors.toMap(Function.identity(), environment::getProperty));
			
			return ResponseEntity.status(HttpStatus.OK).body(props);
		
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(String.format("Error %s", e.getMessage()));
		}
		
	}
	
	
	/**
	 * @return Settings in tenant options 
	 */
	@GetMapping(path = "/tenantOptions", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getTenantOptionsConfigurationProperty(
			@RequestParam(value = "propertyName", required = false) String propertyName) throws JsonProcessingException {
	
		if (propertyName != null) {
			String property = settingsService.get(propertyName);
			
			return ResponseEntity.status(HttpStatus.OK).body(property);
		}
		
		Map<String, String> props = settingsService.getAll();
		
		return ResponseEntity.status(HttpStatus.OK).body(props);		
	}
	
}
```

Note that for multitenant microservices, `MicroserviceSettingsService.get()` and `MicroserviceSettingsService.getAll()` can return different values depending on the current tenant scope, i.e. if they are wrapped in `MicroserviceSubscriptionsService.runForTenant()`. For example, `MicroserviceSettingsService.getAll()` will return the settings for the current tenant, or for the owner tenant (bootstrap tenant) if there is no current tenant.
