package com.sajuppugi.fortune.talisman.port;

import java.net.URI;
import java.time.Duration;

/** Issues short-lived access only for private object keys after application-level ownership checks. */
public interface TalismanAssetAccess {
    URI signedDownloadUrl(String objectKey, Duration lifetime);
}
