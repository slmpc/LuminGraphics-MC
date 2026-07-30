package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record PipelineMetadata2612(
        Primitive primitive,
        int vertexStride,
        List<VertexAttribute> attributes,
        List<String> samplers,
        List<Uniform> uniforms
) {
    public PipelineMetadata2612 {
        attributes = List.copyOf(attributes);
        samplers = List.copyOf(samplers);
        uniforms = List.copyOf(uniforms);
    }

    public static PipelineMetadata2612 from(RenderPipeline pipeline) {
        VertexFormat format = pipeline.getVertexFormat();
        List<VertexAttribute> attributes = new ArrayList<>();
        List<VertexFormatElement> elements = format.getElements();
        for (int location = 0; location < elements.size(); location++) {
            VertexFormatElement element = elements.get(location);
            attributes.add(new VertexAttribute(location, format.getElementName(element),
                    elementType(element.type()), element.normalized(), element.count(), format.getOffset(element)));
        }
        List<Uniform> uniforms = pipeline.getUniforms().stream().map(uniform -> new Uniform(
                uniform.name(),
                switch (uniform.type()) {
                    case UNIFORM_BUFFER -> UniformKind.UNIFORM_BUFFER;
                    case TEXEL_BUFFER -> UniformKind.TEXEL_BUFFER;
                },
                uniform.textureFormat())).toList();
        return new PipelineMetadata2612(primitive(pipeline.getVertexFormatMode()), format.getVertexSize(),
                attributes, pipeline.getSamplers(), uniforms);
    }

    private static Primitive primitive(VertexFormat.Mode mode) {
        return switch (mode) {
            case LINES -> Primitive.LINES;
            case DEBUG_LINES -> Primitive.DEBUG_LINES;
            case DEBUG_LINE_STRIP -> Primitive.DEBUG_LINE_STRIP;
            case POINTS -> Primitive.POINTS;
            case TRIANGLES -> Primitive.TRIANGLES;
            case TRIANGLE_STRIP -> Primitive.TRIANGLE_STRIP;
            case TRIANGLE_FAN -> Primitive.TRIANGLE_FAN;
            case QUADS -> Primitive.QUADS;
        };
    }

    private static ElementType elementType(VertexFormatElement.Type type) {
        return switch (type) {
            case FLOAT -> ElementType.FLOAT;
            case UBYTE -> ElementType.UNSIGNED_BYTE;
            case BYTE -> ElementType.SIGNED_BYTE;
            case USHORT -> ElementType.UNSIGNED_SHORT;
            case SHORT -> ElementType.SIGNED_SHORT;
            case UINT -> ElementType.UNSIGNED_INT;
            case INT -> ElementType.SIGNED_INT;
        };
    }

    public enum Primitive { LINES, DEBUG_LINES, DEBUG_LINE_STRIP, POINTS, TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN, QUADS }
    public enum ElementType { FLOAT, UNSIGNED_BYTE, SIGNED_BYTE, UNSIGNED_SHORT, SIGNED_SHORT, UNSIGNED_INT, SIGNED_INT }
    public enum UniformKind { UNIFORM_BUFFER, TEXEL_BUFFER }
    public record VertexAttribute(int location, String name, ElementType type, boolean normalized, int count, int offset) { }
    public record Uniform(String name, UniformKind kind, @Nullable TextureFormat textureFormat) { }
}
