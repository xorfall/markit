package com.markit.identity.infrastructure.persistence;

import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import com.markit.identity.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the domain {@link UserRepository} port. */
@Repository
public class UserRepositoryAdapter implements UserRepository {

  private final SpringDataUserRepository jpa;

  UserRepositoryAdapter(SpringDataUserRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return jpa.findByEmail(email.value()).map(UserRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<User> findByGoogleSub(String googleSub) {
    return jpa.findByGoogleSub(googleSub).map(UserRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<User> findById(UserId id) {
    return jpa.findById(id.value()).map(UserRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByEmail(Email email) {
    return jpa.existsByEmail(email.value());
  }

  @Override
  public void save(User user) {
    jpa.save(
        new UserJpaEntity(
            user.id().value(),
            user.email().value(),
            user.passwordHash(),
            user.googleSub(),
            user.createdAt()));
  }

  private static User toDomain(UserJpaEntity e) {
    return User.rehydrate(
        UserId.of(e.getId()),
        new Email(e.getEmail()),
        e.getPasswordHash(),
        e.getGoogleSub(),
        e.getCreatedAt());
  }
}
