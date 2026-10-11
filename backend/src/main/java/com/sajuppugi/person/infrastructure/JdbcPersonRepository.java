package com.sajuppugi.person.infrastructure;

import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.reading.domain.RelationType;
import com.sajuppugi.person.domain.Person;
import com.sajuppugi.person.port.PersonRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPersonRepository implements PersonRepository {
    private final JdbcTemplate jdbc;
    private final PersonDataProtector protector;

    public JdbcPersonRepository(JdbcTemplate jdbc, PersonDataProtector protector) {
        this.jdbc = jdbc;
        this.protector = protector;
    }

    @Override
    public List<Person> findActiveByOwner(UUID ownerUserId) {
        return jdbc.query("""
                SELECT * FROM people WHERE owner_user_id = ? AND deleted_at IS NULL
                ORDER BY CASE profile_type WHEN 'SELF' THEN 0 ELSE 1 END, created_at, id
                """, this::map, ownerUserId);
    }

    @Override
    public Optional<Person> findActiveOwned(UUID ownerUserId, UUID personId) {
        return jdbc.query("SELECT * FROM people WHERE owner_user_id = ? AND id = ? AND deleted_at IS NULL",
                this::map, ownerUserId, personId).stream().findFirst();
    }

    @Override
    public boolean activeSelfExists(UUID ownerUserId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM people
                WHERE owner_user_id = ? AND profile_type = 'SELF' AND deleted_at IS NULL
                """, Integer.class, ownerUserId) > 0;
    }

    @Override
    public int countActiveOthers(UUID ownerUserId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM people
                WHERE owner_user_id = ? AND profile_type = 'OTHER' AND deleted_at IS NULL
                """, Integer.class, ownerUserId);
    }

    @Override
    public void insert(Person person) {
        BirthInput birth = person.birthInput();
        jdbc.update("""
                INSERT INTO people (id, owner_user_id, profile_type, display_name_enc, birth_date_enc,
                birth_time_enc, calendar_type, is_leap_month, birth_time_unknown, gender, relation_type,
                birth_fingerprint, third_party_confirmed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, person.id(), person.ownerUserId(), person.profileType().name(),
                protector.encrypt(person.displayName()), protector.encrypt(birth.birthDate().toString()),
                protector.encrypt(birth.birthTime() == null ? null : birth.birthTime().toString()),
                birth.calendarType().name(), birth.leapMonth(), birth.birthTimeUnknown(), birth.gender().name(),
                person.relationType() == null ? null : person.relationType().name(), fingerprint(birth),
                timestamp(person.thirdPartyConfirmedAt()), timestamp(person.createdAt()), timestamp(person.updatedAt()));
    }

    @Override
    public boolean update(Person person) {
        BirthInput birth = person.birthInput();
        return jdbc.update("""
                UPDATE people SET display_name_enc = ?, birth_date_enc = ?, birth_time_enc = ?,
                calendar_type = ?, is_leap_month = ?, birth_time_unknown = ?, gender = ?, relation_type = ?,
                birth_fingerprint = ?, third_party_confirmed_at = ?, updated_at = ?
                WHERE id = ? AND owner_user_id = ? AND deleted_at IS NULL
                """, protector.encrypt(person.displayName()), protector.encrypt(birth.birthDate().toString()),
                protector.encrypt(birth.birthTime() == null ? null : birth.birthTime().toString()),
                birth.calendarType().name(), birth.leapMonth(), birth.birthTimeUnknown(), birth.gender().name(),
                person.relationType() == null ? null : person.relationType().name(), fingerprint(birth),
                timestamp(person.thirdPartyConfirmedAt()), timestamp(person.updatedAt()), person.id(), person.ownerUserId()) == 1;
    }

    @Override
    public boolean softDelete(UUID ownerUserId, UUID personId, Instant deletedAt) {
        return jdbc.update("""
                UPDATE people SET deleted_at = ?, updated_at = ?
                WHERE owner_user_id = ? AND id = ? AND deleted_at IS NULL
                """, timestamp(deletedAt), timestamp(deletedAt), ownerUserId, personId) == 1;
    }

    private Person map(ResultSet rs, int row) throws SQLException {
        boolean unknown = rs.getBoolean("birth_time_unknown");
        String relation = rs.getString("relation_type");
        BirthInput birth = new BirthInput(LocalDate.parse(protector.decrypt(rs.getBytes("birth_date_enc"))),
                unknown ? null : LocalTime.parse(protector.decrypt(rs.getBytes("birth_time_enc"))), unknown,
                BirthInput.CalendarType.valueOf(rs.getString("calendar_type")), rs.getBoolean("is_leap_month"),
                BirthInput.Gender.valueOf(rs.getString("gender")));
        return new Person(rs.getObject("id", UUID.class), rs.getObject("owner_user_id", UUID.class),
                Person.ProfileType.valueOf(rs.getString("profile_type")),
                protector.decrypt(rs.getBytes("display_name_enc")), birth,
                relation == null ? null : RelationType.valueOf(relation), instant(rs, "third_party_confirmed_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private String fingerprint(BirthInput birth) {
        return protector.fingerprint(String.join("|", birth.birthDate().toString(),
                birth.birthTime() == null ? "UNKNOWN" : birth.birthTime().toString(), birth.calendarType().name(),
                Boolean.toString(birth.leapMonth()), birth.gender().name()));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
