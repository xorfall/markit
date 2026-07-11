package com.markit.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Walking-skeleton smoke test: the full Spring context boots and wires against real Postgres,
 * RabbitMQ, and Elasticsearch (via Testcontainers {@code @ServiceConnection}). Runs in {@code verify}
 * (failsafe), not the fast {@code test} phase, since it needs Docker.
 */
@SpringBootTest
@Testcontainers
class ContextLoadsIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

  @Container
  @ServiceConnection
  static RabbitMQContainer rabbitmq =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

  @Container
  @ServiceConnection
  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.3"))
          .withEnv("xpack.security.enabled", "false");

  @Test
  void contextLoads() {
    // The application context starting with all three dependencies wired is the assertion.
  }
}
