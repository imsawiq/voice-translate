package org.sawiq.voicetranslate.engine;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pulls one program out of the uv download: a zip on Windows, a .tar.gz
 * elsewhere. Java reads zips itself; the tar format is simple enough to read
 * here rather than adding a library for it.
 */
final class Archives {
    private static final int TAR_BLOCK = 512;

    private Archives() {
    }

    static void extractFile(Path archive, boolean isZip, String fileName, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        boolean isFound = isZip ? extractFromZip(archive, fileName, target) : extractFromTarGz(archive, fileName, target);
        if (!isFound) {
            throw new IOException(fileName + " is not in " + archive.getFileName());
        }
        if (!isZip && !target.toFile().setExecutable(true)) {
            throw new IOException("Could not make " + target + " executable");
        }
    }

    private static boolean extractFromZip(Path archive, String fileName, Path target) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory() && baseName(entry.getName()).equals(fileName)) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean extractFromTarGz(Path archive, String fileName, Path target) throws IOException {
        try (InputStream input = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archive)))) {
            byte[] header = new byte[TAR_BLOCK];
            while (input.readNBytes(header, 0, TAR_BLOCK) == TAR_BLOCK) {
                String name = cString(header, 0, 100);
                if (name.isEmpty()) {
                    return false;
                }
                String sizeField = cString(header, 124, 12).strip();
                long size = sizeField.isEmpty() ? 0L : Long.parseLong(sizeField, 8);
                char type = (char) header[156];
                long padded = (size + TAR_BLOCK - 1) / TAR_BLOCK * TAR_BLOCK;
                if ((type == '0' || type == '\0') && baseName(name).equals(fileName)) {
                    try (InputStream body = new LimitedStream(input, size)) {
                        Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return true;
                }
                input.skipNBytes(padded);
            }
        }
        return false;
    }

    private static String cString(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /** Reads at most {@code remaining} bytes and leaves the underlying stream open. */
    private static final class LimitedStream extends InputStream {
        private final InputStream input;
        private long remaining;

        LimitedStream(InputStream input, long remaining) {
            this.input = input;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (this.remaining <= 0) {
                return -1;
            }
            int value = this.input.read();
            if (value >= 0) {
                this.remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (this.remaining <= 0) {
                return -1;
            }
            int read = this.input.read(buffer, offset, (int) Math.min(length, this.remaining));
            if (read > 0) {
                this.remaining -= read;
            }
            return read;
        }

        @Override
        public void close() {
            // The tar stream belongs to the caller.
        }
    }
}
