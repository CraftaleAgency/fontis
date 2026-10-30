package eu.fontis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FontisBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
