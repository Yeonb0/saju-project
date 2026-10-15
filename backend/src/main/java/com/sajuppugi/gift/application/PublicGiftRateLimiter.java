package com.sajuppugi.gift.application;
import com.sajuppugi.common.api.*;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.*; import java.time.temporal.ChronoUnit; import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service public class PublicGiftRateLimiter {
 private final JdbcTemplate jdbc; private final Clock clock; public PublicGiftRateLimiter(JdbcTemplate j,Clock c){jdbc=j;clock=c;}
 public void check(String token,String ip){ Instant w=clock.instant().truncatedTo(ChronoUnit.MINUTES); Integer n=jdbc.queryForObject("INSERT INTO public_gift_rate_limits(subject_hash,window_started,request_count) VALUES(?,?,1) ON CONFLICT(subject_hash,window_started) DO UPDATE SET request_count=public_gift_rate_limits.request_count+1 RETURNING request_count",Integer.class,hash(token+"|"+(ip==null?"":ip)),java.sql.Timestamp.from(w)); if(n==null||n>30)throw new ApiException(ErrorCode.TOO_MANY_REQUESTS); }
 private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
