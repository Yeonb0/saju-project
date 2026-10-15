package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.talisman.application.TalismanAddonPurchaseService;
import com.sajuppugi.fortune.talisman.application.TalismanAddonQuoteService;
import com.sajuppugi.gift.application.PublicGiftRateLimiter;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.Animal;
import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import com.sajuppugi.fortune.talisman.port.TalismanRenderJobRepository;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Real PostgreSQL unique-index and wallet-lock regression; no surrounding rollback. */
@SpringBootTest(properties = "app.wallet.purchases-enabled=true")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class TalismanAddonPurchasePostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TalismanAddonQuoteService quotes;
    @Autowired TalismanAddonPurchaseService purchases;
    @Autowired TalismanRepository talismans;
    @Autowired TalismanRenderJobRepository jobs;
    @Autowired PublicGiftRateLimiter publicLimits;
    private final Set<UUID> cleanupUsers = new HashSet<>();
    private final Set<UUID> cleanupResults = new HashSet<>();
    private final Set<UUID> cleanupTalismans = new HashSet<>();

    @AfterEach void cleanSyntheticRows() {
        for (UUID user : cleanupUsers) {
            jdbc.update("DELETE FROM talisman_addon_purchases WHERE owner_user_id = ?", user);
            jdbc.update("DELETE FROM talisman_render_jobs WHERE talisman_id IN (SELECT id FROM talismans WHERE owner_user_id = ?)", user);
            jdbc.update("DELETE FROM talisman_ownerships WHERE owner_user_id = ?", user);
            jdbc.update("DELETE FROM talismans WHERE owner_user_id = ?", user);
            jdbc.update("DELETE FROM readings WHERE owner_user_id = ?", user);
            jdbc.update("DELETE FROM reading_purchases WHERE buyer_user_id = ?", user);
            jdbc.update("DELETE FROM wallet_transaction_lines WHERE wallet_user_id = ?", user);
            jdbc.update("DELETE FROM wallet_purchase_commands WHERE wallet_user_id = ?", user);
            jdbc.update("DELETE FROM wallet_purchase_compensations WHERE wallet_user_id = ?", user);
            jdbc.update("DELETE FROM wallet_purchase_debits WHERE wallet_user_id = ?", user);
            jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ?", user);
            jdbc.update("DELETE FROM wallet_lots WHERE wallet_user_id = ?", user);
            jdbc.update("DELETE FROM wallets WHERE user_id = ?", user);
            jdbc.update("DELETE FROM purchase_quotes WHERE requester_user_id = ?", user);
            jdbc.update("DELETE FROM users WHERE id = ?", user);
        }
        for (UUID talisman : cleanupTalismans) {
            jdbc.update("DELETE FROM talisman_render_jobs WHERE talisman_id = ?", talisman);
            jdbc.update("DELETE FROM talisman_ownerships WHERE talisman_id = ?", talisman);
            jdbc.update("DELETE FROM talismans WHERE id = ?", talisman);
        }
        for (UUID result : cleanupResults) jdbc.update("DELETE FROM reading_results WHERE id = ?", result);
        jdbc.update("DELETE FROM public_gift_rate_limits");
        cleanupUsers.clear(); cleanupResults.clear(); cleanupTalismans.clear();
    }

    @Test void concurrentClaimAndLostResponseRetryDebitOnceAndLeaveOnePendingTalisman() throws Exception {
        UUID user = UUID.randomUUID(), reading = UUID.randomUUID(), result = UUID.randomUUID(), purchase = UUID.randomUUID();
        cleanupUsers.add(user); cleanupResults.add(result);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users(id,kakao_subject,nickname,status,created_at,updated_at) VALUES (?,?,'addon','ACTIVE',?,?)", user,"addon-"+user,Timestamp.from(now),Timestamp.from(now));
        jdbc.update("INSERT INTO wallets(user_id,paid_balance,updated_at) VALUES (?,20,?)",user,Timestamp.from(now));
        UUID topup=UUID.randomUUID(), lot=UUID.randomUUID();
        jdbc.update("INSERT INTO wallet_transactions(id,wallet_user_id,type,total_amount,reference_id,created_at) VALUES (?,?,'TOP_UP',20,?,?)",topup,user,UUID.randomUUID(),Timestamp.from(now));
        jdbc.update("INSERT INTO wallet_lots(id,wallet_user_id,balance_type,granted_amount,remaining_amount,created_at,expires_at) VALUES (?,?,'PAID',20,20,?,?)",lot,user,Timestamp.from(now),Timestamp.from(now.plusSeconds(3600)));
        jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id,lot_id,wallet_user_id,amount) VALUES (?,?,?,20)",topup,lot,user);
        UUID originalQuote=UUID.randomUUID();
        jdbc.update("INSERT INTO purchase_quotes(id,requester_user_id,product_id,product_code,category,price_currency,price_amount,active,catalog_version,context_hash,created_at,expires_at,purchase_id) VALUES (?,?,'b1000000-0000-0000-0000-000000000001','OVERALL_READING_ONLY','READING','TURTLE_SHELL',10,TRUE,'x',?, ?, ?, ?)",originalQuote,user,"0".repeat(64),Timestamp.from(now),Timestamp.from(now.plusSeconds(1800)),purchase);
        jdbc.update("INSERT INTO reading_purchases(id,buyer_user_id,product_id,quote_id,subject_person_id,status,created_at) VALUES (?,?,'b1000000-0000-0000-0000-000000000001',?,?,'FULFILLED',?)",purchase,user,originalQuote,UUID.randomUUID(),Timestamp.from(now));
        jdbc.update("INSERT INTO reading_results(id,generation_key,fortune_type,reference_date,facts_json,sections_json,calculation_version,generation_version,content_version,status,created_at,completed_at) VALUES (?,?, 'OVERALL',CURRENT_DATE,?, '{}','c','g','content-v1','SUCCEEDED',?,?)",result,result.toString().replace("-","").repeat(2),"{\"fiveElements\":{\"counts\":{\"WOOD\":0,\"FIRE\":2,\"EARTH\":2,\"METAL\":2,\"WATER\":2},\"dominant\":[],\"missing\":[\"WOOD\"]}}",Timestamp.from(now),Timestamp.from(now));
        jdbc.update("INSERT INTO readings(id,reading_result_id,owner_user_id,purchase_id,fortune_type,product_option,subject_person_id,subject_display_name,public_snapshot,calculation_version,generation_version,content_version,created_at) VALUES (?,?,?,?,'OVERALL','READING_ONLY',?,'addon','{}','c','g','content-v1',?)",reading,result,user,purchase,UUID.randomUUID(),Timestamp.from(now));
        UUID quote=quotes.issue(user,reading).quote().id();
        ExecutorService pool=Executors.newFixedThreadPool(2); try {
            var a=pool.submit(() -> purchases.purchase(user,reading,quote,new IdempotencyKey("a")));
            var b=pool.submit(() -> purchases.purchase(user,reading,quote,new IdempotencyKey("b")));
            assertThat(a.get(10,TimeUnit.SECONDS).talismanId()).isEqualTo(b.get(10,TimeUnit.SECONDS).talismanId());
        } finally { pool.shutdownNow(); }
        var retry=purchases.purchase(user,reading,quote,new IdempotencyKey("retry"));
        cleanupTalismans.add(retry.talismanId());
        assertThat(retry.reused()).isTrue();
        assertThat(jdbc.queryForObject("SELECT paid_balance FROM wallets WHERE user_id=?",Integer.class,user)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id=? AND type='PURCHASE'",Integer.class,user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talisman_addon_purchases WHERE reading_id=? AND state='PENDING_TALISMAN'",Integer.class,reading)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talismans WHERE source_reading_id=? AND status='PENDING'",Integer.class,reading)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talisman_render_jobs WHERE talisman_id=?",Integer.class,retry.talismanId())).isEqualTo(1);
    }

    @Test void expiredLeaseIsReclaimedAndKilledWorkersLateCompletionIsFenced() {
        Instant now = Instant.EPOCH;
        Talisman talisman = talismans.createPending(new Talisman(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                FortuneType.OVERALL, Element.WOOD, Animal.RAT, "p", "d", "v", Talisman.Status.PENDING,
                null, null, now, null));
        cleanupTalismans.add(talisman.id());
        jobs.enqueue(talisman.id(), now);
        var killed = jobs.claimReady(now, 3, 1, java.time.Duration.ofMillis(1)).getFirst();
        var resumed = jobs.claimReady(now.plusSeconds(1), 3, 1, java.time.Duration.ofMinutes(1)).getFirst();
        jobs.complete(killed, new AssetKeys("stale", "stale", "stale"), now.plusSeconds(1));
        assertThat(talismans.findById(talisman.id()).orElseThrow().status()).isEqualTo(Talisman.Status.PENDING);
        assertThat(resumed.fence()).isGreaterThan(killed.fence());
    }

    @Test void publicBearerLimitIsSharedAcrossConcurrentRequestsAndDoesNotStoreRawToken() throws Exception {
        String token = "a".repeat(43); String address = "198.51.100.42";
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 30; i++) futures.add(pool.submit(() -> publicLimits.check(token, address)));
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> publicLimits.check(token, address))
                .isInstanceOf(ApiException.class)
                .extracting(value -> ((ApiException) value).errorCode()).isEqualTo(com.sajuppugi.common.api.ErrorCode.TOO_MANY_REQUESTS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public_gift_rate_limits WHERE subject_hash LIKE ?", Integer.class,
                "%" + token.substring(0, 5) + "%")).isZero();
    }

    @Test void publicRateLimitRetentionPurgeIsBoundedAndDoesNotRemoveCurrentWindow() {
        String oldestDigest = UUID.randomUUID().toString().replace("-", "") + "b".repeat(32);
        String newerDigest = UUID.randomUUID().toString().replace("-", "") + "d".repeat(32);
        jdbc.update("INSERT INTO public_gift_rate_limits(subject_hash,window_started,request_count) VALUES (?, ?, 1)",
                oldestDigest, Timestamp.from(Instant.now().minusSeconds(4 * 3600)));
        jdbc.update("INSERT INTO public_gift_rate_limits(subject_hash,window_started,request_count) VALUES (?, ?, 1)",
                newerDigest, Timestamp.from(Instant.now().minusSeconds(3 * 3600)));
        publicLimits.check("c".repeat(43), "203.0.113.9");
        assertThat(publicLimits.purgeOlderThan(Instant.now().minusSeconds(2 * 3600), 1)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public_gift_rate_limits WHERE subject_hash IN (?, ?)", Integer.class,
                oldestDigest, newerDigest)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public_gift_rate_limits WHERE window_started >= ?", Integer.class,
                Timestamp.from(Instant.now().minusSeconds(2 * 3600)))).isGreaterThanOrEqualTo(1);
    }
}
