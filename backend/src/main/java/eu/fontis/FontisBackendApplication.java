package eu.fontis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FontisBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FontisBackendApplication.class, args);
	}

}
