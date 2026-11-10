package com.b2borderflow.core.idempotency;

import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Идемпотентность создания (api-contract.md, 5.1). Ключ, хеш тела и ответ
 * хранятся 24 часа; повтор с тем же ключом и телом возвращает исходный ответ.
 */
@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Optional<StoredResponse> find(UUID userId, UUID key, String endpoint, Object requestBody) {
        if (key == null) {
            return Optional.empty();
        }

        IdempotencyKey existing = repository.findByUserIdAndKey(userId, key.toString()).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        if (!existing.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            repository.delete(existing);
            return Optional.empty();
        }
        if (!existing.getEndpoint().equals(endpoint)
                || !existing.getRequestHash().equals(hash(requestBody))) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Ключ идемпотентности уже использован с другим запросом");
        }
        if (existing.getResponseStatus() == null || existing.getResponseBody() == null) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Запрос с этим ключом идемпотентности ещё выполняется");
        }
        return Optional.of(new StoredResponse(existing.getResponseStatus(), existing.getResponseBody()));
    }

    public void store(UUID userId, UUID key, String endpoint, Object requestBody,
                      int responseStatus, Object responseBody) {
        if (key == null) {
            return;
        }

        IdempotencyKey entity = new IdempotencyKey();
        entity.setUserId(userId);
        entity.setKey(key.toString());
        entity.setEndpoint(endpoint);
        entity.setRequestHash(hash(requestBody));
        entity.setResponseStatus(responseStatus);
        entity.setResponseBody(toMap(responseBody));
        entity.setExpiresAt(OffsetDateTime.now(clock).plus(TTL));
        repository.save(entity);
    }

    public <T> T convert(StoredResponse stored, Class<T> type) {
        return objectMapper.convertValue(stored.body(), type);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    private String hash(Object requestBody) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(requestBody);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Не удалось вычислить ключ идемпотентности");
        }
    }

    public record StoredResponse(int status, Map<String, Object> body) {
    }
}
