package com.sajuppugi.fortune.talisman.application;

import com.sajuppugi.fortune.talisman.port.TalismanAssetStore;
import com.sajuppugi.fortune.talisman.port.TalismanRenderJobRepository;
import com.sajuppugi.fortune.talisman.port.TalismanRenderer;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.talisman.render.enabled", havingValue = "true")
@ConditionalOnBean({TalismanRenderer.class, TalismanAssetStore.class})
public class TalismanRenderScheduler {
    private static final Logger log = LoggerFactory.getLogger(TalismanRenderScheduler.class);
    private final TalismanRenderJobRepository jobs;
    private final TalismanRenderService renders;
    private final TalismanRenderProperties properties;
    private final Clock clock;

    public TalismanRenderScheduler(TalismanRenderJobRepository jobs, TalismanRenderService renders,
            TalismanRenderProperties properties, Clock clock) {
        this.jobs = jobs;
        this.renders = renders;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.talisman.render.poll-delay:30s}",
            initialDelayString = "${app.talisman.render.poll-delay:30s}")
    public void poll() {
        for (var lease : jobs.claimReady(clock.instant(), properties.maxAttempts(), properties.batchSize(),
                properties.leaseDuration())) {
            try { renders.render(lease); }
            catch (RuntimeException failure) {
                log.warn("Talisman render execution failed: talismanId={}, exception={}", lease.talismanId(),
                        failure.getClass().getSimpleName());
            }
        }
    }
}
