package c8y.example.cookbook.controllers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cumulocity.microservice.settings.repository.CurrentApplicationSettingsApi;
import com.cumulocity.microservice.settings.service.MicroserviceSettingsService;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.inventory.PagedManagedObjectCollectionRepresentation;
import com.fasterxml.jackson.core.JsonProcessingException;

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
