package eu.fontis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FontisBackendApplication {

	public static void main(String[] args) {
		// The JDK HTTP client reuses idle connections to the model server for up to 30 s by default; a
		// connection dropped silently in between (seen over a VPN) makes the next request fail or hang.
		if (System.getProperty("jdk.httpclient.keepalive.timeout") == null) {
			System.setProperty("jdk.httpclient.keepalive.timeout", "5");
		}
		SpringApplication.run(FontisBackendApplication.class, args);
	}

}
