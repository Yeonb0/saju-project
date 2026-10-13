package com.sajuppugi.fortune.talisman.port;

import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import com.sajuppugi.fortune.talisman.port.TalismanRenderer.RenderedAssets;

/** Private object storage boundary. Implementations must never return a raw object URL. */
public interface TalismanAssetStore {
    AssetKeys store(Talisman talisman, RenderedAssets assets);
}
