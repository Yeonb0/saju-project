package com.sajuppugi.fortune.talisman.port;

import com.sajuppugi.fortune.talisman.domain.Talisman;

/** Renders approved product assets. No development placeholder renderer is registered in production. */
public interface TalismanRenderer {
    RenderedAssets render(Talisman talisman);

    record RenderedAssets(byte[] originalPng, byte[] thumbnailWebp, byte[] shareWebp) {
        public RenderedAssets {
            require(originalPng, "originalPng");
            require(thumbnailWebp, "thumbnailWebp");
            require(shareWebp, "shareWebp");
        }
        private static void require(byte[] value, String name) {
            if (value == null || value.length == 0) throw new IllegalArgumentException(name + " is required");
        }
    }
}
