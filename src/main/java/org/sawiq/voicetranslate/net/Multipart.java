package org.sawiq.voicetranslate.net;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A multipart/form-data body, for uploading audio to transcription APIs. */
public final class Multipart {
    private final String boundary = "----VoiceTranslate" + UUID.randomUUID().toString().replace("-", "");
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    public Multipart field(String name, String value) {
        write("--" + this.boundary + "\r\n");
        write("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(value + "\r\n");
        return this;
    }

    public Multipart file(String name, String fileName, String contentType, byte[] content) {
        write("--" + this.boundary + "\r\n");
        write("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n");
        write("Content-Type: " + contentType + "\r\n\r\n");
        this.body.writeBytes(content);
        write("\r\n");
        return this;
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + this.boundary;
    }

    public byte[] build() {
        write("--" + this.boundary + "--\r\n");
        return this.body.toByteArray();
    }

    private void write(String text) {
        this.body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
