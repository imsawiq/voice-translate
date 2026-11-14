package org.sawiq.voicetranslate.dub;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;
import org.sawiq.voicetranslate.audio.Pcm;
import org.sawiq.voicetranslate.net.Http;
import org.sawiq.voicetranslate.net.ServiceException;

/** The voice engine on this computer: Chatterbox, copying each player's voice from their own speech. */
public final class LocalVoiceSynthesizer implements VoiceSynthesizer {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final Supplier<String> engineAddress;

    /** @param engineAddress the running voice engine's base URL, or null while it is not running */
    public LocalVoiceSynthesizer(Supplier<String> engineAddress) {
        this.engineAddress = engineAddress;
    }

    @Override
    public short[] speak(String text, String language, VoiceSample voice) throws ServiceException {
        String address = this.engineAddress.get();
        if (address == null) {
            throw new ServiceException(ServiceException.Kind.NOT_READY, "The voice engine is not running");
        }
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("language", language);
        if (voice != null) {
            body.addProperty("speaker", voice.key());
            body.addProperty("reference", Base64.getEncoder().encodeToString(Pcm.toLittleEndianBytes(voice.samples())));
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
            throw new ServiceException(ServiceException.Kind.NETWORK, "The voice engine did not answer", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ServiceException.Kind.NETWORK, "interrupted", exception);
        }
        if (response.statusCode() == 503) {
            throw new ServiceException(ServiceException.Kind.NOT_READY, "The voice engine is still loading");
        }
        if (response.statusCode() != 200) {
            throw new ServiceException(ServiceException.Kind.BAD_RESPONSE,
                    "The voice engine answered HTTP " + response.statusCode());
        }
        byte[] bytes = response.body();
        short[] samples = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
        return samples;
    }
}
