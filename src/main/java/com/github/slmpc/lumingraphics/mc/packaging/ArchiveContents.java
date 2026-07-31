package com.github.slmpc.lumingraphics.mc.packaging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ArchiveContents {
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_ENTRY_BYTES = 128 * 1024 * 1024;
    private final Map<String, byte[]> entries;

    private ArchiveContents(Map<String, byte[]> entries) {
        this.entries = Collections.unmodifiableMap(entries);
    }

    static ArchiveContents read(Path path) throws IOException {
        return read(Files.readAllBytes(path), path.toString());
    }

    static ArchiveContents read(byte[] bytes, String source) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int count = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) {
                    throw new IOException("Archive has too many entries: " + source);
                }
                byte[] content = entry.isDirectory() ? new byte[0] : readBounded(zip, source, entry.getName());
                if (entries.putIfAbsent(entry.getName(), content) != null) {
                    throw new IOException("Duplicate ZIP entry in " + source + ": " + entry.getName());
                }
            }
        }
        return new ArchiveContents(entries);
    }

    private static byte[] readBounded(InputStream input, String source, String name) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_ENTRY_BYTES) {
                throw new IOException("Oversized ZIP entry in " + source + ": " + name);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    byte[] required(String name) throws IOException {
        byte[] value = entries.get(name);
        if (value == null) {
            throw new IOException("Missing ZIP entry: " + name);
        }
        return value;
    }

    Set<String> names() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK lacks SHA-256", exception);
        }
    }
}
