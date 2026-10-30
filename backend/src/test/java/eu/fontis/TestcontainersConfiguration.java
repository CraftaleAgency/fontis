package eu.fontis;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	// Same image as docker-compose.yml: Postgres with the pgvector extension.
	@Bean
	@ServiceConnection
	PostgreSQLContainer pgvectorContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17"));
	}

}
