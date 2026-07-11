package com.markit.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

  Optional<UserJpaEntity> findByEmail(String email);

  Optional<UserJpaEntity> findByGoogleSub(String googleSub);

  boolean existsByEmail(String email);
}
