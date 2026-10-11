package com.sajuppugi.common.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PurchaseRequestBindingService {
    private final JdbcPurchaseRequestBindings bindings;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PurchaseRequestBindingService(JdbcPurchaseRequestBindings bindings, ObjectMapper mapper, Clock clock) {
        this.bindings = bindings;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void bind(UUID actor, String key, String operation, Object request) {
        if (key == null || key.isBlank() || key.length() > 512) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        String keyHash = hash(key);
        String requestHash = hash(operation + "\n" + canonical(mapper.valueToTree(request)));
        try {
            bindings.insert(actor, keyHash, requestHash, clock.instant());
        } catch (DuplicateKeyException duplicate) {
            // Read only after the losing insert transaction has rolled back (PostgreSQL aborts it).
            String original = bindings.find(actor, keyHash)
                    .orElseThrow(() -> new IllegalStateException("Purchase request binding is missing"));
            if (!original.equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, canonical(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
