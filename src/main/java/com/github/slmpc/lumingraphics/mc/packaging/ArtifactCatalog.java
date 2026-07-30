package com.github.slmpc.lumingraphics.mc.packaging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;

final class ArtifactCatalog {
    static final String VERSION = "0.1.0";
    static final List<Coordinate> EXPECTED = List.of(
            new Coordinate("com.github.slmpc.lumingraphics", "lumin-graphics-core"),
            new Coordinate("com.github.slmpc.lumingraphics", "lumin-graphics-render"),
            new Coordinate("com.github.slmpc.lumingraphics", "lumin-graphics-text"),
            new Coordinate("com.github.slmpc.lumingraphics", "lumin-graphics-ui"),
            new Coordinate("com.github.slmpc.prismrhi", "prism-rhi-core"),
            new Coordinate("com.github.slmpc.prismrhi", "prism-rhi-backend-opengl-common"),
            new Coordinate("com.github.slmpc.prismrhi", "prism-rhi-backend-opengl41"),
            new Coordinate("com.github.slmpc.prismrhi", "prism-rhi-backend-opengl-dsa"));

    private ArtifactCatalog() {}

    static Artifact load(Path repository, Coordinate coordinate) throws Exception {
        Path directory = repository.resolve(coordinate.group().replace('.', '/'))
                .resolve(coordinate.artifact()).resolve(VERSION).normalize();
        if (!directory.startsWith(repository.normalize())) {
            throw new IOException("Coordinate escaped isolated repository: " + coordinate);
        }
        String baseName = coordinate.artifact() + '-' + VERSION;
        Path jar = directory.resolve(baseName + ".jar");
        Path module = directory.resolve(baseName + ".module");
        Path pom = directory.resolve(baseName + ".pom");
        if (!Files.isRegularFile(jar) || !Files.isRegularFile(module) || !Files.isRegularFile(pom)) {
            throw new IOException("Incomplete isolated publication for " + coordinate + " under " + directory);
        }
        verifyPom(pom, coordinate);
        String sha = verifyModule(module, coordinate, jar.getFileName().toString());
        byte[] bytes = Files.readAllBytes(jar);
        String actual = ArchiveContents.sha256(bytes);
        if (!sha.equals(actual)) {
            throw new IOException("Published SHA-256 mismatch for " + coordinate + ": " + actual + " != " + sha);
        }
        return new Artifact(coordinate, jar, bytes, sha);
    }

    private static String verifyModule(Path module, Coordinate coordinate, String jarName) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(module, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonObject component = root.getAsJsonObject("component");
        requireJson(component, "group", coordinate.group(), module);
        requireJson(component, "module", coordinate.artifact(), module);
        requireJson(component, "version", VERSION, module);
        for (JsonElement variantElement : root.getAsJsonArray("variants")) {
            JsonObject variant = variantElement.getAsJsonObject();
            if (!"runtimeElements".equals(variant.get("name").getAsString())) {
                continue;
            }
            JsonArray files = variant.getAsJsonArray("files");
            for (JsonElement fileElement : files) {
                JsonObject file = fileElement.getAsJsonObject();
                if (jarName.equals(file.get("name").getAsString())) {
                    return file.get("sha256").getAsString();
                }
            }
        }
        throw new IOException("Runtime JAR metadata missing from " + module);
    }

    private static void requireJson(JsonObject object, String key, String expected, Path source) throws IOException {
        if (object == null || !object.has(key) || !expected.equals(object.get(key).getAsString())) {
            throw new IOException("Unexpected " + key + " in " + source);
        }
    }

    private static void verifyPom(Path pom, Coordinate coordinate) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        var xpath = XPathFactory.newInstance().newXPath();
        assertPomValue(xpath, document, "/*[local-name()='project']/*[local-name()='groupId']/text()", coordinate.group(), pom);
        assertPomValue(xpath, document, "/*[local-name()='project']/*[local-name()='artifactId']/text()", coordinate.artifact(), pom);
        assertPomValue(xpath, document, "/*[local-name()='project']/*[local-name()='version']/text()", VERSION, pom);
    }

    private static void assertPomValue(javax.xml.xpath.XPath xpath, Document document, String expression,
                                       String expected, Path source) throws Exception {
        String value = (String) xpath.evaluate(expression, document, XPathConstants.STRING);
        if (!expected.equals(value)) {
            throw new IOException("Unexpected POM coordinate in " + source + ": " + value);
        }
    }

    record Coordinate(String group, String artifact) {
        String id() {
            return group + ':' + artifact + ':' + VERSION;
        }
    }

    record Artifact(Coordinate coordinate, Path jar, byte[] bytes, String sha256) {}
}
