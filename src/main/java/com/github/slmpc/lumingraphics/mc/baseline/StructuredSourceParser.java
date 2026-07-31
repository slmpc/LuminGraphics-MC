package com.github.slmpc.lumingraphics.mc.baseline;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public final class StructuredSourceParser {
    private static final Set<String> LOOM_ACCESS_WIDENED_CONSTRUCTORS =
            Set.of("GlBuffer", "GlTexture", "GlTextureView");
    private static final Pattern TYPE_NAME =
            Pattern.compile("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*");

    private StructuredSourceParser() {}

    public static String summary(String expectedType, byte[] bytes) throws Exception {
        ParsedSource source = parse(expectedType, bytes, false);
        long fields = source.type().getMembers().stream().filter(VariableTree.class::isInstance).count();
        long constructors = source.type().getMembers().stream().filter(MethodTree.class::isInstance)
                .map(MethodTree.class::cast).filter(method -> method.getName().contentEquals("<init>")).count();
        long methods = source.type().getMembers().stream().filter(MethodTree.class::isInstance)
                .map(MethodTree.class::cast).filter(method -> !method.getName().contentEquals("<init>")).count();
        return qualifiedName(source.unit(), expectedType) + "|fields=" + fields
                + "|constructors=" + constructors + "|methods=" + methods;
    }

    public static String semanticSignature(String expectedType, byte[] bytes) throws Exception {
        ParsedSource source = parse(expectedType, bytes, true);
        TypeNormalizer types = new TypeNormalizer(source.unit());
        List<String> members = new ArrayList<>();
        for (var member : source.type().getMembers()) {
            if (member instanceof VariableTree field) {
                members.add("F:" + semanticModifiers(field.getModifiers().getFlags(), false) + ":"
                        + types.normalize(field.getType())
                        + ":" + field.getName());
            } else if (member instanceof MethodTree method) {
                String parameters = method.getParameters().stream()
                        .map(parameter -> parameterModifiers(parameter) + ":" + types.normalize(parameter.getType()))
                        .collect(Collectors.joining(","));
                boolean constructor = method.getName().contentEquals("<init>");
                String returnType = constructor ? "<constructor>" : types.normalize(method.getReturnType());
                boolean accessWidened = constructor && LOOM_ACCESS_WIDENED_CONSTRUCTORS.contains(expectedType);
                members.add("M:" + semanticModifiers(method.getModifiers().getFlags(), accessWidened)
                        + ":" + method.getName()
                        + ":" + returnType + "(" + parameters + ")");
            }
        }
        members.sort(Comparator.naturalOrder());
        return qualifiedName(source.unit(), expectedType) + "|kind=" + source.type().getKind()
                + "|modifiers=" + semanticModifiers(source.type().getModifiers().getFlags(), false)
                + "|" + String.join("|", members);
    }

    private static ParsedSource parse(String expectedType, byte[] bytes, boolean allowRecoverableBodyErrors) throws Exception {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject source = new SimpleJavaFileObject(
                URI.create("string:///" + expectedType + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        };
        JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(
                null, null, diagnostics, List.of("-proc:none", "--release", "25"), null, List.of(source));
        CompilationUnitTree unit = task.parse().iterator().next();
        String errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString).collect(Collectors.joining("; "));
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!errors.isEmpty() && (!allowRecoverableBodyErrors || !hasBalancedBraces(text))) {
            throw new IllegalArgumentException("malformed Java source for " + expectedType + ": " + errors);
        }
        ClassTree type = unit.getTypeDecls().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
                .filter(candidate -> candidate.getSimpleName().contentEquals(expectedType)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "signature mismatch: expected top-level type " + expectedType));
        return new ParsedSource(unit, type);
    }

    private static boolean hasBalancedBraces(String source) {
        int depth = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            if (current == '}' && --depth < 0) return false;
        }
        return depth == 0;
    }

    private static String qualifiedName(CompilationUnitTree unit, String type) {
        return (unit.getPackageName() == null ? "" : unit.getPackageName() + ".") + type;
    }

    private static String semanticModifiers(Iterable<? extends Modifier> flags, boolean accessWidened) {
        List<String> values = new ArrayList<>();
        flags.forEach(flag -> {
            boolean widenedVisibility = accessWidened && (flag == Modifier.PUBLIC || flag == Modifier.PROTECTED);
            values.add(widenedVisibility ? "loom-accessible" : flag.toString());
        });
        values.sort(Comparator.naturalOrder());
        return String.join(",", values);
    }

    private static String parameterModifiers(VariableTree parameter) {
        return semanticModifiers(parameter.getModifiers().getFlags().stream()
                .filter(flag -> flag != Modifier.FINAL).toList(), false);
    }

    private static final class TypeNormalizer {
        private final Map<String, String> imports = new HashMap<>();
        private final String packageName;

        private TypeNormalizer(CompilationUnitTree unit) {
            packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            unit.getImports().stream().filter(importTree -> !importTree.isStatic()).forEach(importTree -> {
                String qualified = importTree.getQualifiedIdentifier().toString();
                if (!qualified.endsWith(".*")) {
                    imports.put(qualified.substring(qualified.lastIndexOf('.') + 1), qualified);
                }
            });
        }

        private String normalize(Object value) {
            if (value == null) return "";
            String text = value.toString()
                    .replaceAll("@[\\w.]+(?:\\([^)]*\\))?", "")
                    .replaceAll("\\s+", "");
            Matcher matcher = TYPE_NAME.matcher(text);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(resolve(matcher.group())));
            }
            return matcher.appendTail(result).toString();
        }

        private String resolve(String name) {
            int separator = name.indexOf('.');
            String first = separator < 0 ? name : name.substring(0, separator);
            String imported = imports.get(first);
            if (imported != null) return imported + (separator < 0 ? "" : name.substring(separator));
            if (name.indexOf('.') >= 0 && Character.isLowerCase(name.charAt(0))) return name;
            if (!packageName.isEmpty() && Character.isUpperCase(name.charAt(0))) return packageName + "." + name;
            return name;
        }
    }

    private record ParsedSource(CompilationUnitTree unit, ClassTree type) {}
}
