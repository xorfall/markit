package com.markit.bookmarking;

import static org.assertj.core.api.Assertions.assertThat;

import com.markit.bookmarking.application.BookmarkService;
import com.markit.bookmarking.application.CategoryService;
import com.markit.bookmarking.application.CollectionService;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.Category;
import com.markit.bookmarking.domain.Collection;
import com.markit.identity.application.port.AccessTokenService;
import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import com.markit.identity.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * R-SEC-01 / NFR-SEC-002 authorization matrix: user A must never reach user B's resources. Every
 * cross-user access resolves to a non-enumerating {@code 404}. Runs in {@code verify} (failsafe),
 * not the fast {@code test} phase, since it needs Docker (mirrors {@code ContextLoadsIT}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BookmarkingAuthorizationMatrixIT {

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
      new ElasticsearchContainer(
              DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.3"))
          .withEnv("xpack.security.enabled", "false");

  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;
  @Autowired UserRepository users;
  @Autowired AccessTokenService accessTokens;
  @Autowired CollectionService collections;
  @Autowired CategoryService categories;
  @Autowired BookmarkService bookmarks;

  private UserId userA;
  private Collection collectionB;
  private Category categoryB;
  private Bookmark bookmarkB;

  @BeforeEach
  void seedTwoUsers() {
    // Unique per run: @BeforeEach reruns for every test method against the same container,
    // and the email is UNIQUE — hardcoded addresses would collide on the second method.
    userA = registerUser("a-" + UUID.randomUUID() + "@example.com");
    UserId userB = registerUser("b-" + UUID.randomUUID() + "@example.com");

    collectionB = collections.create(userB, "B's collection");
    categoryB = categories.create(userB, collectionB.id(), "B's category");
    bookmarkB = bookmarks.add(userB, categoryB.id(), "https://b.example.com");
  }

  @Test
  void should_Return404_When_UserAReadsUserBsCollection() {
    ResponseEntity<String> response =
        get("/api/v1/collections/" + collectionB.id().asString() + "/categories", userA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void should_Return404_When_UserAReadsUserBsCategory() {
    ResponseEntity<String> response =
        get("/api/v1/categories/" + categoryB.id().asString() + "/bookmarks", userA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void should_Return404_When_UserAReadsUserBsBookmark() {
    ResponseEntity<String> response =
        get("/api/v1/bookmarks/" + bookmarkB.id().asString(), userA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private ResponseEntity<String> get(String path, UserId as) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessTokens.issue(as));
    return rest.exchange(
        "http://localhost:" + port + path,
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);
  }

  private UserId registerUser(String email) {
    UserId id = UserId.newId();
    users.save(User.registerWithPassword(id, new Email(email), "hash", Instant.now()));
    return id;
  }
}
