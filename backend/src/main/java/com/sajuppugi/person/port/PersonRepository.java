package com.sajuppugi.person.port;

import com.sajuppugi.person.domain.Person;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository {
    List<Person> findActiveByOwner(UUID ownerUserId);
    Optional<Person> findActiveOwned(UUID ownerUserId, UUID personId);
    boolean activeSelfExists(UUID ownerUserId);
    int countActiveOthers(UUID ownerUserId);
    void insert(Person person);
    boolean update(Person person);
    boolean softDelete(UUID ownerUserId, UUID personId, java.time.Instant deletedAt);
}
