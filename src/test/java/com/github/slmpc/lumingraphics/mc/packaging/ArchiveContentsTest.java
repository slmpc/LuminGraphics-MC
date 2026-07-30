package com.github.slmpc.lumingraphics.mc.packaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

final class ArchiveContentsTest {
    @Test
    void verifiesFabricWrapperWhenOnlyGeneratedMetadataIsAdded() throws Exception {
        // Given
        ArchiveContents source = archive(Map.of("example/Marker.class", new byte[] {1, 2, 3}));
        ArchiveContents wrapped = archive(Map.of(
                "example/Marker.class", new byte[] {1, 2, 3},
                "fabric.mod.json", "{}".getBytes(StandardCharsets.UTF_8)));

        // When / Then
        assertDoesNotThrow(() -> wrapped.verifyFabricWrapper(source, "example.jar"));
    }

    @Test
    void rejectsFabricWrapperWhenPublishedBytesChange() throws Exception {
        // Given
        ArchiveContents source = archive(Map.of("example/Marker.class", new byte[] {1, 2, 3}));
        ArchiveContents wrapped = archive(Map.of(
                "example/Marker.class", new byte[] {1, 2, 4},
                "fabric.mod.json", "{}".getBytes(StandardCharsets.UTF_8)));

        // When / Then
        assertThrows(IOException.class, () -> wrapped.verifyFabricWrapper(source, "example.jar"));
    }

    @Test
    void rejectsFabricWrapperWhenUnexpectedEntryIsAdded() throws Exception {
        // Given
        ArchiveContents source = archive(Map.of("example/Marker.class", new byte[] {1, 2, 3}));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("example/Marker.class", new byte[] {1, 2, 3});
        entries.put("fabric.mod.json", "{}".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/services/example.Service", new byte[] {7});
        ArchiveContents wrapped = archive(entries);

        // When / Then
        assertThrows(IOException.class, () -> wrapped.verifyFabricWrapper(source, "example.jar"));
    }

    private static ArchiveContents archive(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return ArchiveContents.read(output.toByteArray(), "test fixture");
    }
}
