package de.samply.manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AccessibleJobManager {

    public static void main(String[] args) {
        SpringApplication.run(AccessibleJobManager.class, args);
    }

}
