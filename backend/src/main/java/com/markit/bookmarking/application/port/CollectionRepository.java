package com.markit.bookmarking.application.port;

import com.markit.bookmarking.domain.Collection;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.identity.domain.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the {@link Collection} aggregate. Every read is scoped by owner so cross-user
 * access is impossible by construction (R-SEC-01).
 */
public interface CollectionRepository {

  Optional<Collection> findByIdAndOwner(CollectionId id, UserId owner);

  List<Collection> findByOwner(UserId owner);

  void save(Collection collection);

  void delete(Collection collection);
}
