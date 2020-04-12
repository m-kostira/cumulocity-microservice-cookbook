package c8y.example.cookbook;

import java.util.Arrays;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import com.cumulocity.microservice.settings.service.MicroserviceSettingsService;

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
