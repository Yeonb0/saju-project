package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.talisman.application.TalismanAddonPurchaseService;
import com.sajuppugi.fortune.talisman.application.TalismanAddonQuoteService;
import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.Animal;
import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import com.sajuppugi.fortune.talisman.port.TalismanRenderJobRepository;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
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

    @Test void concurrentClaimAndLostResponseRetryDebitOnceAndLeaveOnePendingTalisman() throws Exception {
        UUID user = UUID.randomUUID(), reading = UUID.randomUUID(), result = UUID.randomUUID(), purchase = UUID.randomUUID();
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
        jobs.enqueue(talisman.id(), now);
        var killed = jobs.claimReady(now, 3, 1, java.time.Duration.ofMillis(1)).getFirst();
        var resumed = jobs.claimReady(now.plusSeconds(1), 3, 1, java.time.Duration.ofMinutes(1)).getFirst();
        jobs.complete(killed, new AssetKeys("stale", "stale", "stale"), now.plusSeconds(1));
        assertThat(talismans.findById(talisman.id()).orElseThrow().status()).isEqualTo(Talisman.Status.PENDING);
        assertThat(resumed.fence()).isGreaterThan(killed.fence());
    }
}
