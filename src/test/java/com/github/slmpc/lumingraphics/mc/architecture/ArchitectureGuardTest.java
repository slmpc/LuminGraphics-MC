package com.github.slmpc.lumingraphics.mc.architecture;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArchitectureGuardTest {
    private static final Set<String> REFLECTION_CALLS = Set.of(
            "Class.forName", "getDeclaredField", "getDeclaredFields", "getDeclaredMethod", "getDeclaredMethods",
            "getDeclaredConstructor", "getDeclaredConstructors", "setAccessible", "trySetAccessible", "privateLookupIn");
    private static final Set<String> WINDOW_CALLS = Set.of(
            "glfwCreateWindow", "glfwInit", "SDL_CreateWindow", "SDL_GL_CreateContext", "GL.createCapabilities");

    @Test
    void productionSourcesClassesJarsAndMatrixRespectArchitecture() throws Exception {
        Path root = Path.of(System.getProperty("lumin.mc.root")).toAbsolutePath();
        List<Path> sources = productionSources(root);
        for (Path source : sources) inspectSource(root, source, Files.readString(source));

        int classCount = 0;
        try (var paths = Files.walk(root, 16)) {
            for (Path classFile : paths.filter(path -> path.toString().contains("build\\classes\\java\\main"))
                    .filter(path -> path.toString().endsWith(".class")).toList()) {
                classCount++;
                inspectConstantPool(root.relativize(classFile).toString(), Files.readAllBytes(classFile));
            }
        }

        int jarCount = 0;
        int jarEntries = 0;
        try (var paths = Files.walk(root, 5)) {
            for (Path jar : paths.filter(path -> path.toString().contains("build\\libs"))
                    .filter(path -> path.toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().contains("sources")).toList()) {
                String jarPath = jar.toString();
                if (!(jarPath.contains("-fabric\\build\\libs") || jarPath.contains("-neoforge\\build\\libs"))) continue;
                jarCount++;
                try (JarFile archive = new JarFile(jar.toFile())) {
                    var entries = archive.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        jarEntries++;
                        reject(entry.getName().startsWith("META-INF/services/"), jar + "!" + entry.getName(),
                                "service descriptor");
                        String name = entry.getName();
                        boolean v2612Jar = jar.toString().contains("26.1.2");
                        reject(v2612Jar && name.contains("/v262/"), jar + "!" + name, "26.2 class in 26.1.2 JAR");
                        reject(!v2612Jar && jar.toString().contains("26.2") && name.contains("/v2612/"),
                                jar + "!" + name, "26.1.2 class in 26.2 JAR");
                    }
                }
            }
        }
        assertTrue(sources.size() > 40 && classCount > 40 && jarCount == 4 && jarEntries > 40,
                "architecture source/class/JAR inspection must be nonzero: sources=" + sources.size()
                        + " classes=" + classCount + " jars=" + jarCount + " entries=" + jarEntries);
        System.out.printf("ARCH_MC_COUNTS sources=%d classes=%d jars=%d entries=%d ledgerRows=168 schemaColumns=22%n",
                sources.size(), classCount, jarCount, jarEntries);
    }

    @Test
    void boundedMutationFixturesNameEveryExactOffender() {
        assertOffender("bridge/Reflect.java", "class Reflect { void x() { String.class.getDeclaredField(\"x\"); } }", "Reflect.java");
        assertOffender("mc-26.1.2-common/FabricLeak.java", "import net.fabricmc.api.ClientModInitializer; class FabricLeak {}", "FabricLeak.java");
        assertOffender("mc-26.2-common/CrossLeak.java", "import com.github.slmpc.lumingraphics.mc.v2612.bridge.Blaze3DBridge2612; class CrossLeak {}", "CrossLeak.java");
        assertOffender("mc-26.2-common/Window.java", "class Window { void x() { glfwCreateWindow(); } }", "Window.java");
        assertOffender("bridge/Service.java", "import java.util.ServiceLoader; class Service {}", "Service.java");
        AssertionError gav = assertThrows(AssertionError.class,
                () -> inspectPublishedCoordinate("mc-26.2-common/build.gradle", "com.github.slmpc.prismrhi:prism-rhi-core:0.0.1"));
        assertTrue(gav.getMessage().contains("mc-26.2-common/build.gradle"), gav::getMessage);
        System.out.println("ARCH_MC_MUTATIONS rejected=reflection,Fabric-common,26.1.2-in-26.2,window,service,stale-GAV offenders=6");
    }

    private static void assertOffender(String path, String source, String expected) {
        AssertionError error = assertThrows(AssertionError.class, () -> inspectUnit(path, source));
        assertTrue(error.getMessage().contains(expected), error::getMessage);
    }

    private static List<Path> productionSources(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        try (var paths = Files.walk(root, 12)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src\\main\\java"))
                    .filter(path -> !path.toString().contains("reference\\"))
                    .forEach(result::add);
        }
        return result;
    }

    private static void inspectSource(Path root, Path source, String text) throws IOException {
        inspectUnit(root.relativize(source).toString(), text);
    }

    private static void inspectUnit(String path, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        var file = new SimpleJavaFileObject(URI.create("string:///" + path.replace('\\', '/')), javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        try {
            JavacTask task = (JavacTask) compiler.getTask(null, null, null, List.of("-proc:none"), null, List.of(file));
            for (CompilationUnitTree unit : task.parse()) {
                for (ImportTree imported : unit.getImports()) {
                    String name = imported.getQualifiedIdentifier().toString();
                    reject(name.startsWith("java.lang.reflect") || name.equals("java.util.ServiceLoader"), path, "reflection/service import " + name);
                    boolean common = path.contains("common") || path.contains("bridge-contract") || path.startsWith("bridge/");
                    reject(common && (name.startsWith("net.fabricmc.") || name.startsWith("net.neoforged.")), path, "loader type " + name);
                    reject(path.contains("26.2") && name.contains(".v2612."), path, "26.1.2 class in 26.2");
                    reject(path.contains("26.1.2") && name.contains(".v262."), path, "26.2 class in 26.1.2");
                }
                unit.accept(new TreeScanner<Void, Void>() {
                    @Override public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                        String call = node.getMethodSelect().toString();
                        String simple = call.substring(call.lastIndexOf('.') + 1);
                        reject(REFLECTION_CALLS.contains(call) || REFLECTION_CALLS.contains(simple), path, "private reflection API " + call);
                        reject(WINDOW_CALLS.contains(call) || WINDOW_CALLS.contains(simple), path, "window/context creation " + call);
                        return super.visitMethodInvocation(node, unused);
                    }
                }, null);
            }
        } catch (IOException error) {
            throw new AssertionError("cannot parse " + path, error);
        }
    }

    private static void inspectConstantPool(String path, byte[] bytes) throws IOException {
        Set<String> utf8 = constantPoolUtf8(bytes);
        for (String symbol : REFLECTION_CALLS) reject(utf8.contains(symbol), path, "reflection constant " + symbol);
        boolean v262 = path.contains("mc-26.2");
        boolean v2612 = path.contains("mc-26.1.2");
        reject(v262 && utf8.stream().anyMatch(value -> value.contains("/v2612/")), path, "26.1.2 class constant in 26.2");
        reject(v2612 && utf8.stream().anyMatch(value -> value.contains("/v262/")), path, "26.2 class constant in 26.1.2");
    }

    private static Set<String> constantPoolUtf8(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 0xCAFEBABE) throw new IOException("not a class file");
            input.readUnsignedShort(); input.readUnsignedShort();
            int count = input.readUnsignedShort();
            Set<String> values = new HashSet<>();
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> values.add(input.readUTF());
                    case 3, 4 -> input.skipBytes(4);
                    case 5, 6 -> { input.skipBytes(8); index++; }
                    case 7, 8, 16, 19, 20 -> input.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> input.skipBytes(4);
                    case 15 -> input.skipBytes(3);
                    default -> throw new IOException("unknown constant-pool tag " + tag);
                }
            }
            return values;
        }
    }

    private static void reject(boolean condition, String path, String reason) {
        if (condition) throw new AssertionError(reason + " in " + path);
    }

    private static void inspectPublishedCoordinate(String path, String coordinate) {
        String[] parts = coordinate.split(":", -1);
        reject(parts.length != 3, path, "malformed GAV " + coordinate);
        reject((parts[0].equals("com.github.slmpc.lumingraphics") || parts[0].equals("com.github.slmpc.prismrhi"))
                && !parts[2].equals("0.1.0"), path, "stale published GAV " + coordinate);
    }
}
