package com.github.slmpc.lumingraphics.mc.v262.bridge;

import com.github.slmpc.prismrhi.command.PRhiPrimitiveTopology;
import com.github.slmpc.prismrhi.format.PRhiFormat;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;

public record PipelineMetadata262(String debugName, PRhiPrimitiveTopology topology,
                                  List<VertexBinding> vertexBindings, List<Binding> bindings,
                                  List<PRhiFormat> colorFormats) {
    public PipelineMetadata262 {
        vertexBindings = List.copyOf(vertexBindings);
        bindings = List.copyOf(bindings);
        colorFormats = List.copyOf(colorFormats);
    }

    public static PipelineMetadata262 from(RenderPipeline pipeline) {
        List<VertexBinding> vertices = new ArrayList<>();
        VertexFormat[] formats = pipeline.getVertexFormatBindings();
        for (int slot = 0; slot < formats.length; slot++) {
            VertexFormat format = formats[slot];
            if (format == null) continue;
            List<Attribute> attributes = format.getElements().stream()
                    .map(element -> new Attribute(element.name(), element.offset(),
                            BridgeTranslations262.format(element.format())))
                    .toList();
            vertices.add(new VertexBinding(slot, format.getVertexSize(), format.getStepRate(), attributes));
        }
        List<Binding> bindings = new ArrayList<>();
        for (int group = 0; group < pipeline.getBindGroupLayouts().size(); group++) {
            BindGroupLayout layout = pipeline.getBindGroupLayouts().get(group);
            for (String sampler : layout.getSamplers()) bindings.add(new Binding(group, sampler, "SAMPLER", null));
            for (BindGroupLayout.UniformDescription uniform : layout.getUniforms()) {
                bindings.add(new Binding(group, uniform.name(), uniform.type().name(),
                        uniform.gpuFormat() == null ? null : BridgeTranslations262.format(uniform.gpuFormat())));
            }
        }
        List<PRhiFormat> colors = new ArrayList<>();
        for (var state : pipeline.getColorTargetStates()) {
            colors.add(state == null ? PRhiFormat.UNDEFINED : BridgeTranslations262.format(state.format()));
        }
        return new PipelineMetadata262(pipeline.getLocation().toString(),
                BridgeTranslations262.topology(pipeline.getPrimitiveTopology()), vertices, bindings, colors);
    }

    public record VertexBinding(int slot, int stride, int stepRate, List<Attribute> attributes) {
        public VertexBinding { attributes = List.copyOf(attributes); }
    }
    public record Attribute(String name, int offset, PRhiFormat format) { }
    public record Binding(int group, String name, String kind, PRhiFormat texelFormat) { }
}
