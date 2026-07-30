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
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public final class StructuredSourceParser {
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
        List<String> members = new ArrayList<>();
        for (var member : source.type().getMembers()) {
            if (member instanceof VariableTree field) {
                members.add("F:" + modifiers(field.getModifiers().getFlags()) + ":" + normalize(field.getType())
                        + ":" + field.getName());
            } else if (member instanceof MethodTree method) {
                String parameters = method.getParameters().stream()
                        .map(parameter -> parameterModifiers(parameter) + ":" + normalize(parameter.getType()))
                        .collect(Collectors.joining(","));
                String returnType = method.getReturnType() == null ? "<constructor>" : normalize(method.getReturnType());
                members.add("M:" + modifiers(method.getModifiers().getFlags()) + ":" + method.getName()
                        + ":" + returnType + "(" + parameters + ")");
            }
        }
        members.sort(Comparator.naturalOrder());
        return qualifiedName(source.unit(), expectedType) + "|kind=" + source.type().getKind()
                + "|modifiers=" + modifiers(source.type().getModifiers().getFlags()) + "|" + String.join("|", members);
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

    private static String modifiers(Iterable<?> flags) {
        List<String> values = new ArrayList<>();
        flags.forEach(flag -> values.add(flag.toString()));
        values.sort(Comparator.naturalOrder());
        return String.join(",", values);
    }

    private static String parameterModifiers(VariableTree parameter) {
        return modifiers(parameter.getModifiers().getFlags().stream()
                .filter(flag -> flag != Modifier.FINAL).toList());
    }

    private static String normalize(Object value) {
        return value == null ? "" : value.toString()
                .replaceAll("@[\\w.]+(?:\\([^)]*\\))?", "")
                .replaceAll("\\s+", "");
    }

    private record ParsedSource(CompilationUnitTree unit, ClassTree type) {}
}
