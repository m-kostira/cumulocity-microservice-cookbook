package c8y.example.cookbook;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

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
