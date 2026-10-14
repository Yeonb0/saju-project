package com.sajuppugi.fortune.talisman.application;

import com.sajuppugi.fortune.talisman.port.TalismanAssetStore;
import com.sajuppugi.fortune.talisman.port.TalismanRenderJobRepository;
import com.sajuppugi.fortune.talisman.port.TalismanRenderJobRepository.Lease;
import com.sajuppugi.fortune.talisman.port.TalismanRenderer;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/** Executes rendering outside database transactions; only final state transitions are transactional. */
@Service
@ConditionalOnBean({TalismanRenderer.class, TalismanAssetStore.class})
public class TalismanRenderService {
    private final TalismanRenderJobRepository jobs;
    private final TalismanRepository talismans;
    private final TalismanRenderer renderer;
    private final TalismanAssetStore assets;
    private final TalismanRenderProperties properties;
    private final Clock clock;

    public TalismanRenderService(TalismanRenderJobRepository jobs, TalismanRepository talismans,
            TalismanRenderer renderer, TalismanAssetStore assets, TalismanRenderProperties properties, Clock clock) {
        this.jobs = jobs;
        this.talismans = talismans;
        this.renderer = renderer;
        this.assets = assets;
        this.properties = properties;
        this.clock = clock;
    }

    public void render(Lease lease) {
        try {
            var talisman = talismans.findById(lease.talismanId()).orElseThrow();
            var rendered = renderer.render(talisman);
            var keys = assets.store(talisman, rendered);
            jobs.complete(lease, keys, clock.instant());
        } catch (RuntimeException failure) {
            jobs.fail(lease, failureCode(failure), clock.instant(), properties.maxAttempts(), properties.retryDelay());
        }
    }

    private String failureCode(RuntimeException failure) {
        String name = failure.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_]", "");
        return (name.isBlank() ? "RENDER_FAILED" : "RENDER_" + name).substring(0,
                Math.min(100, (name.isBlank() ? "RENDER_FAILED" : "RENDER_" + name).length()));
    }
}
