package c8y.example.cookbook;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cumulocity.microservice.security.service.RoleService;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.Platform;
import com.cumulocity.sdk.client.PlatformParameters;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.inventory.PagedManagedObjectCollectionRepresentation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
public class RESTCookbook {
	
	private static final Logger log = LoggerFactory.getLogger(RESTCookbook.class);
	
	@Autowired
	private MicroserviceSubscriptionsService subscriptionsService;
		
	@Autowired
    InventoryApi inventoryApi;	
	
	@Autowired
	private RoleService roleService;
	
	@Autowired
	@Qualifier("userPlatform")
	private Platform userPlatform;
	
	@GetMapping("/health")
	public String health() {
		return "Service is up and running!";
	}
	
	@GetMapping(path = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getManagedObjects(@RequestParam(value = "pageSize") Optional<Integer> pageSize,
			@RequestParam(value = "currentPage") Optional<Integer> currentPage) throws JsonProcessingException {

		if (!currentPage.isPresent() || !pageSize.isPresent()) {
			Iterable<ManagedObjectRepresentation> managedObjectsIterable = inventoryApi.getManagedObjects().get(2000).allPages();
			List<ManagedObjectRepresentation> managedObjects = StreamSupport.stream(managedObjectsIterable.spliterator(), false).collect(Collectors.toList());
					
			log.info(String.format("Fetched all managed objects, %d total", 
						managedObjects.size()));
			
			return ResponseEntity.status(HttpStatus.OK).body(managedObjects);
		} else {
			
			PagedManagedObjectCollectionRepresentation collection = inventoryApi.getManagedObjects().get();
			
			List<ManagedObjectRepresentation> managedObjects = inventoryApi.getManagedObjects().getPage(collection, currentPage.get(), pageSize.get()).getManagedObjects();
			
			return ResponseEntity.status(HttpStatus.OK).body(managedObjects);
		}
	}	
	
	@GetMapping(path = "/inventory/{managedObjectId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getManagedObject(@PathVariable(value = "managedObjectId") String managedObjectId) throws JsonProcessingException {
		
		ManagedObjectRepresentation managedObject = inventoryApi.get(GId.asGId(managedObjectId));
		log.info(String.format("Fetched managed object: %s", 
				new ObjectMapper().writeValueAsString(managedObject)));
		
		return ResponseEntity.status(HttpStatus.OK).body(managedObject);
	}
	
	@GetMapping(path = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getCurrentUserInfo() throws JsonProcessingException {
		
		PlatformParameters userPlatformParameters = getUserPlatformParameters();
		
		String userInfoString = String.format("User info: host: %s; tenantId: %s; user: %s; auth string: %s; roles: %s", 
			userPlatformParameters.getHost(),
			userPlatformParameters.getTenantId(),
			userPlatformParameters.getUser(),
			userPlatformParameters.getCumulocityCredentials().getAuthenticationString(),
			roleService.getUserRoles());		
		
		return ResponseEntity.status(HttpStatus.OK).body(userInfoString);
	}
	
	@GetMapping(path = "/inventory/{tenantId}/{managedObjectId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getManagedObjectFromTenant(
			@PathVariable(value = "tenantId") String tenantId,
			@PathVariable(value = "managedObjectId") String managedObjectId) throws JsonProcessingException {
		
		if(!getUserPlatformParameters().getTenantId().equals(tenantId)) {
			return  ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(String.format("User's tenant id %s does not match request tenantId %s", getUserPlatformParameters().getTenantId(), tenantId));
		}
		
		if (!subscriptionsService.getCredentials(tenantId).isPresent()) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(String.format("Tenant not subscribed: %s", tenantId));
		}
		
		if(!userHasCookbookAdminRole()) {			 
			return  ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body("Insufficient Permissions: user does not have required permission to access this API");
		}	
		
		ManagedObjectRepresentation managedObject = subscriptionsService.callForTenant(tenantId, ()->{
			return inventoryApi.get(GId.asGId(managedObjectId));
		});
		
		log.info(String.format("Fetched from tenant %s managed object: %s",
				tenantId,
				new ObjectMapper().writeValueAsString(managedObject)));
		
		return ResponseEntity.status(HttpStatus.OK).body(managedObject);
	}

	private PlatformParameters getUserPlatformParameters() {
		if (!(userPlatform instanceof PlatformParameters)) {
			throw new IllegalStateException("userPlatform not instanceof PlatformParameters");
		}
		PlatformParameters userPlatformParameters = (PlatformParameters) userPlatform;
		
		return userPlatformParameters;
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
