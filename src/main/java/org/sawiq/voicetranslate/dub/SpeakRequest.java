package org.sawiq.voicetranslate.dub;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import org.sawiq.voicetranslate.net.Http;
import org.sawiq.voicetranslate.net.ServiceException;

/** POST /speak to an engine on this computer, which answers with 48 kHz mono PCM16. */
final class SpeakRequest {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int NO_VOICE_FOR_LANGUAGE = 422;
    private static final int NOT_READY = 503;

    private SpeakRequest() {
    }

    /**
     * @param address the engine's base URL, or null while it is not running
     * @param engine  how the failures name the engine, for the log and the action bar
     */
    static short[] send(String address, JsonObject body, String engine) throws ServiceException {
        if (address == null) {
            throw new ServiceException(ServiceException.Kind.NOT_READY, "The " + engine + " is not running");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(address + "/speak"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = Http.LOCAL.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            throw new ServiceException(ServiceException.Kind.NETWORK, "The " + engine + " did not answer", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ServiceException.Kind.NETWORK, "interrupted", exception);
        }
        return switch (response.statusCode()) {
            case 200 -> toSamples(response.body());
            case NOT_READY -> throw new ServiceException(ServiceException.Kind.NOT_READY,
                    "The " + engine + " is still preparing the voice");
            case NO_VOICE_FOR_LANGUAGE -> new short[0];
            default -> throw new ServiceException(ServiceException.Kind.BAD_RESPONSE,
                    "The " + engine + " answered HTTP " + response.statusCode());
        };
    }

    private static short[] toSamples(byte[] bytes) {
        short[] samples = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
        return samples;
    }
}
