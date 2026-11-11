package com.b2borderflow.core.integration;

import com.b2borderflow.core.logging.CorrelationId;
import com.b2borderflow.core.logging.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Общий GET к mock-сервису. 404 — нормальный бизнес-ответ, 5xx и сетевые сбои —
 * повод для повтора, остальные 4xx не повторяются. Тело ответа никогда не пишется в лог.
 */
@Component
public class MockHttpClient {

    public ExternalRawResponse get(RestClient restClient, String uriTemplate, Object... uriVariables) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(CorrelationIdFilter.HEADER, CorrelationId.current())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return new ExternalRawResponse(200, body == null ? Map.of() : body);
        } catch (HttpClientErrorException.NotFound notFound) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("httpStatus", 404);
            payload.put("body", safeBody(notFound.getResponseBodyAsString()));
            return new ExternalRawResponse(404, payload);
        } catch (HttpClientErrorException clientError) {
            throw new NonRetryableExternalException(
                    clientError.getStatusCode().value(), "HTTP " + clientError.getStatusCode().value());
        } catch (HttpServerErrorException serverError) {
            throw new RetryableExternalException(
                    serverError.getStatusCode().value(), "HTTP " + serverError.getStatusCode().value());
        } catch (ResourceAccessException accessException) {
            throw new RetryableExternalException(0, accessException.getMessage());
        } catch (RestClientException ex) {
            throw new RetryableExternalException(0, ex.getMessage());
        }
    }

    private static String safeBody(String body) {
        if (body == null) {
            return null;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 2000) {
            return body;
        }
        return new String(bytes, 0, 2000, StandardCharsets.UTF_8);
    }
}
