package c8y.example.cookbook;

import com.cumulocity.microservice.autoconfigure.MicroserviceApplication;

import org.springframework.boot.SpringApplication;

@MicroserviceApplication
public class CookBookApp {
	
    public static void main(String[] args) {
    	SpringApplication.run(CookBookApp.class, args);
    }    

}
