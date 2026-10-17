package com.sajuppugi.share;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.reading.application.ReadingQueryService;
import com.sajuppugi.fortune.talisman.application.TalismanQueryService;
import com.sajuppugi.fortune.talisman.domain.Talisman;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public links store only deliberately shareable result snapshots, never people or purchase data. */
@Service
public class PublicResultShareService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final ReadingQueryService readings;
    private final TalismanQueryService talismans; private final Clock clock;
    public PublicResultShareService(JdbcTemplate jdbc, ObjectMapper mapper, ReadingQueryService readings,
            TalismanQueryService talismans, Clock clock) {
        this.jdbc=jdbc; this.mapper=mapper; this.readings=readings; this.talismans=talismans; this.clock=clock;
    }
    @Transactional(timeout=5)
    public Created reading(UUID owner, UUID readingId) {
        var reading=readings.get(owner, readingId);
        return create(owner, Kind.READING, readingId, mapper.valueToTree(new ReadingSnapshot(reading.fortuneType().name(),
                reading.contentVersion(), reading.sections().sections())));
    }
    @Transactional(timeout=5)
    public Created talisman(UUID owner, UUID talismanId) {
        Talisman t=talismans.get(owner,talismanId);
        if(t.status()!=Talisman.Status.READY) throw new ApiException(ErrorCode.TALISMAN_NOT_READY);
        return create(owner, Kind.TALISMAN, talismanId, mapper.valueToTree(new TalismanSnapshot(t.fortuneType().name(),
                t.element().name(),t.animal().name(),t.phrase(),t.description(),t.composition(),t.contentVersion())));
    }
    @Transactional(timeout=5)
    public Created create(UUID owner, Kind kind, UUID source, JsonNode snapshot) {
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS), expires=now.plus(30,ChronoUnit.DAYS); String token=token();
        UUID id=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public_result_shares(id,public_token_hash,owner_user_id,source_kind,source_id,snapshot,created_at,expires_at)
                VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (source_kind,source_id) DO UPDATE SET
                public_token_hash=EXCLUDED.public_token_hash, owner_user_id=EXCLUDED.owner_user_id, snapshot=EXCLUDED.snapshot,
                created_at=EXCLUDED.created_at, expires_at=EXCLUDED.expires_at, revoked_at=NULL
                """,
                id,hash(token),owner,kind.name(),source,json(snapshot),Timestamp.from(now),Timestamp.from(expires));
        UUID actual=jdbc.queryForObject("SELECT id FROM public_result_shares WHERE source_kind=? AND source_id=?",UUID.class,kind.name(),source);
        return new Created(actual,token,expires,kind,snapshot);
    }
    @Transactional(readOnly=true,timeout=5)
    public PublicSnapshot get(String token) {
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}")) throw new ApiException(ErrorCode.SHARE_NOT_FOUND);
        var rows=jdbc.query("SELECT source_kind,snapshot,expires_at,revoked_at FROM public_result_shares WHERE public_token_hash=?",
                (rs,n)->new Stored(Kind.valueOf(rs.getString(1)),read(rs.getString(2)),rs.getTimestamp(3).toInstant(),rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant()),hash(token));
        Stored s=rows.stream().findFirst().orElseThrow(()->new ApiException(ErrorCode.SHARE_NOT_FOUND));
        if(s.revoked()!=null) throw new ApiException(ErrorCode.SHARE_REVOKED); if(!s.expires().isAfter(clock.instant())) throw new ApiException(ErrorCode.SHARE_EXPIRED);
        return new PublicSnapshot(s.kind(),s.snapshot(),s.expires());
    }
    @Transactional(timeout=5) public void revoke(UUID owner, UUID id) {
        int changed=jdbc.update("UPDATE public_result_shares SET revoked_at=? WHERE id=? AND owner_user_id=? AND revoked_at IS NULL",Timestamp.from(clock.instant()),id,owner);
        if(changed==0) throw new ApiException(ErrorCode.SHARE_NOT_FOUND);
    }
    private String token(){byte[] v=new byte[32];RANDOM.nextBytes(v);return Base64.getUrlEncoder().withoutPadding().encodeToString(v);}
    private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String json(JsonNode n){try{return mapper.writeValueAsString(n);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode read(String v){try{return mapper.readTree(v);}catch(Exception e){throw new IllegalStateException(e);}}
    public enum Kind { READING, TALISMAN }
    public record Created(UUID shareId,String token,Instant expiresAt,Kind kind,JsonNode snapshot) {}
    public record PublicSnapshot(Kind kind,JsonNode snapshot,Instant expiresAt) {}
    private record Stored(Kind kind,JsonNode snapshot,Instant expires,Instant revoked) {}
    private record ReadingSnapshot(String fortuneType,String contentVersion,Object sections) {}
    private record TalismanSnapshot(String fortuneType,String element,String animal,String phrase,String description,Object composition,String contentVersion) {}
}
