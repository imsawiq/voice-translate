package org.sawiq.voicetranslate.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one HTTP client the mod uses, so connections to the speech and
 * translation services are pooled and every request has a timeout.
 *
 * <p>The system proxy is honoured: players behind one would otherwise get
 * nothing but timeouts.</p>
 */
public final class Http {
    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .build();

    /** Loopback traffic to the local engine never goes through a proxy. */
    public static final HttpClient LOCAL = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();

    private static final int MAX_ERROR_BODY = 300;

    private Http() {
    }

    public static HttpResponse<String> send(HttpClient client, HttpRequest request) throws ServiceException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new ServiceException(ServiceException.Kind.NETWORK, describe(exception), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ServiceException.Kind.NETWORK, "interrupted", exception);
        }
    }

    /** Sends and parses a JSON answer, turning HTTP errors into the kind of failure they mean. */
    public static JsonElement sendForJson(HttpClient client, HttpRequest request, String service) throws ServiceException {
        HttpResponse<String> response = send(client, request);
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw ServiceException.fromStatus(service, status, shorten(response.body()));
        }
        try {
            return JsonParser.parseString(response.body());
        } catch (RuntimeException exception) {
            throw new ServiceException(ServiceException.Kind.BAD_RESPONSE,
                    service + " sent something that is not JSON: " + shorten(response.body()), exception);
        }
    }

    public static String formEncode(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** A base URL as typed by a player, without the trailing slash, ready to append a path to. */
    public static URI endpoint(String baseUrl, String path) throws ServiceException {
        String base = baseUrl.strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        try {
            URI uri = URI.create(base + path);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("not http(s)");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ServiceException.Kind.CONFIGURATION, "Not a valid address: " + baseUrl, exception);
        }
    }

    public static String shorten(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return flat.length() <= MAX_ERROR_BODY ? flat : flat.substring(0, MAX_ERROR_BODY) + "...";
    }

    private static String describe(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
