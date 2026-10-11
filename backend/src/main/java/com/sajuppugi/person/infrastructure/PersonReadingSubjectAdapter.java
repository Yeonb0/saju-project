package com.sajuppugi.person.infrastructure;

import com.sajuppugi.fortune.reading.port.ReadingSubjectPort;
import com.sajuppugi.person.port.PersonRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PersonReadingSubjectAdapter implements ReadingSubjectPort {
    private final PersonRepository repository;

    public PersonReadingSubjectAdapter(PersonRepository repository) {
        this.repository = repository;
    }

    @Override
    public OwnedSubject getOwnedSubject(UUID ownerUserId, UUID personId) {
        return repository.findActiveOwned(ownerUserId, personId)
                .map(person -> new OwnedSubject(person.id(), person.displayName(), person.birthInput()))
                .orElse(null);
    }
}
