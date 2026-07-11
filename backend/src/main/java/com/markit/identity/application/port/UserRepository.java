package com.markit.identity.application.port;

import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import com.markit.identity.domain.UserId;
import java.util.Optional;

/** Persistence port for the Identity aggregate (implemented by an infrastructure adapter). */
public interface UserRepository {

  Optional<User> findByEmail(Email email);

  Optional<User> findById(UserId id);

  boolean existsByEmail(Email email);

  void save(User user);
}
