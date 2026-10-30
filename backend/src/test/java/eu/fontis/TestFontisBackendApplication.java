package eu.fontis;

import org.springframework.boot.SpringApplication;

public class TestFontisBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(FontisBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
