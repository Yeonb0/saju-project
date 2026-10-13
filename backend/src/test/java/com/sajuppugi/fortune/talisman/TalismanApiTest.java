package com.sajuppugi.fortune.talisman;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajuppugi.fortune.talisman.port.TalismanAssetAccess;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TalismanApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TalismanAssetAccess assetAccess;

    @Test
    void listsOnlyOwnedTalismansAndHidesPrivateObjectKeys() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID older = insert(owner, "PENDING", Instant.parse("2026-10-13T01:00:00Z"));
        UUID newer = insert(owner, "READY", Instant.parse("2026-10-13T02:00:00Z"));
        insert(other, "READY", Instant.parse("2026-10-13T03:00:00Z"));

        String first = mvc.perform(get("/api/v1/talismans").param("size", "1").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(newer.toString()))
                .andExpect(jsonPath("$.data.items[0].originalObjectKey").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.data.nextCursor");
        mvc.perform(get("/api/v1/talismans").param("size", "1").param("cursor", cursor)
                        .with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(older.toString()))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        mvc.perform(get("/api/v1/talismans/{id}", older).with(user(other.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TALISMAN_NOT_FOUND"));
    }

    @Test
    void signsOnlyReadyOwnedOriginalForTenMinutes() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID pending = insert(owner, "PENDING", Instant.parse("2026-10-13T01:00:00Z"));
        UUID ready = insert(owner, "READY", Instant.parse("2026-10-13T02:00:00Z"));
        when(assetAccess.signedDownloadUrl("private/original/" + ready, Duration.ofMinutes(10)))
                .thenReturn(URI.create("https://signed.invalid/file"));

        mvc.perform(post("/api/v1/talismans/{id}/download-url", pending)
                        .with(user(owner.toString())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TALISMAN_NOT_READY"));
        mvc.perform(post("/api/v1/talismans/{id}/download-url", ready)
                        .with(user(owner.toString())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://signed.invalid/file"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600));
    }

    private UUID insert(UUID owner, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        UUID reading = UUID.randomUUID();
        boolean ready = status.equals("READY");
        jdbc.update("""
                INSERT INTO talismans (id, owner_user_id, source_reading_id, fortune_type, element, animal,
                    phrase, description, content_version, status, original_object_key, thumbnail_object_key,
                    share_object_key, failure_code, created_at, ready_at)
                VALUES (?, ?, ?, 'LOVE', 'WOOD', 'TIGER', '문구', '설명', 'test-v1', ?, ?, ?, ?, NULL, ?, ?)
                """, id, owner, reading, status,
                ready ? "private/original/" + id : null, ready ? "private/thumbnail/" + id : null,
                ready ? "public/share/" + id : null, Timestamp.from(createdAt),
                ready ? Timestamp.from(createdAt.plusSeconds(1)) : null);
        jdbc.update("""
                INSERT INTO talisman_ownerships (talisman_id, owner_user_id, source, acquired_at)
                VALUES (?, ?, 'PURCHASE', ?)
                """, id, owner, Timestamp.from(createdAt));
        return id;
    }
}
