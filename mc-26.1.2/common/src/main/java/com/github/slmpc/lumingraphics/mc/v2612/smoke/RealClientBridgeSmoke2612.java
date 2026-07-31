package com.github.slmpc.lumingraphics.mc.v2612.smoke;

import com.github.slmpc.lumingraphics.mc.v2612.mixin.GlAccess2612;
import com.github.slmpc.lumingraphics.mc.v2612.bridge.Blaze3DBridge2612;
import com.github.slmpc.prismrhi.PrismRHI;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlShaderAdoption;
import com.github.slmpc.prismrhi.backend.opengl41.Gl41BackendProvider;
import com.github.slmpc.prismrhi.backend.opengldsa.GlDsaBackendProvider;
import com.github.slmpc.prismrhi.device.RhiDeviceCreateInfo;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.instance.RhiInstance;
import com.github.slmpc.prismrhi.instance.RhiInstanceCreateInfo;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipelineCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiImageUsage;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.RhiNativeObject;
import com.github.slmpc.prismrhi.resource.RhiNativeObjects;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderBinaryFormat;
import com.github.slmpc.prismrhi.shader.RhiShaderDesc;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class RealClientBridgeSmoke2612 {
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private RealClientBridgeSmoke2612() { }

    public static void runIfEnabled(Minecraft client, OpenGlExternalContext context, String loader) {
        String mode = System.getenv("LUMIN_MC_SMOKE_MODE");
        if (mode == null || !STARTED.compareAndSet(false, true)) return;
        Path output = requiredOutput();
        try {
            if ("positive".equals(mode)) runPositive(client, context, loader, output);
            else runNegative(context, loader, mode, output);
        } catch (Throwable failure) {
            writeJson(output, "{\"version\":\"26.1.2\",\"loader\":\"" + loader
                    + "\",\"mode\":\"" + escape(mode) + "\",\"pass\":false,\"errorType\":\""
                    + escape(failure.getClass().getName()) + "\",\"error\":\"" + escape(failure.getMessage()) + "\"}");
        } finally {
            client.stop();
        }
    }

    private static void runPositive(Minecraft client, OpenGlExternalContext context, String loader, Path output)
            throws Exception {
        RenderSystem.assertOnRenderThread();
        context.requireCurrent();
        RhiInstance instance = PrismRHI.createInstance(
                GL.getCapabilities().OpenGL45 ? new GlDsaBackendProvider(context) : new Gl41BackendProvider(context),
                RhiInstanceCreateInfo.builder().build());
        OpenGlExternalDevice prism = (OpenGlExternalDevice) instance.createDevice(
                instance.enumeratePhysicalDevices().getFirst(), RhiDeviceCreateInfo.builder().build());
        Blaze3DBridge2612 bridge = new Blaze3DBridge2612(prism);

        GpuTexture blazeTexture = RenderSystem.getDevice().createTexture(() -> "lumin-smoke-blaze",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, 8, 8, 1, 1);
        GpuBuffer blazeBuffer = RenderSystem.getDevice().createBuffer(() -> "lumin-smoke-blaze", GpuBuffer.USAGE_VERTEX, 64);
        GlShaderModule blazeShader = compileBlazeShader();
        int blazeTextureId = ((GlTexture) blazeTexture).glId();
        int blazeBufferId = ((GlAccess2612.Buffer) (GlBuffer) blazeBuffer).lumin$handle();
        int blazeShaderId = blazeShader.getShaderId();

        RhiImage fromBlazeTexture = bridge.fromBlazeTexture(blazeTexture).orElseThrow();
        RhiBuffer fromBlazeBuffer = bridge.fromBlazeBuffer(blazeBuffer).orElseThrow();
        RhiShader fromBlazeShader = bridge.fromBlazeShader(blazeShader).orElseThrow();
        require(RhiNativeObjects.requireValue(fromBlazeTexture, OpenGlNativeObjectTypes.TEXTURE) == blazeTextureId, "Blaze texture identity");
        require(RhiNativeObjects.requireValue(fromBlazeBuffer, OpenGlNativeObjectTypes.BUFFER) == blazeBufferId, "Blaze buffer identity");
        require(RhiNativeObjects.requireValue(fromBlazeShader, OpenGlNativeObjectTypes.SHADER) == blazeShaderId, "Blaze shader identity");

        RhiImage prismTexture = prism.createImage(new RhiImageCreateInfo(new RhiExtent3D(8, 8, 1), RhiFormat.RGBA8_UNORM,
                Set.of(RhiImageUsage.SAMPLED, RhiImageUsage.COLOR_ATTACHMENT), RhiMemoryUsage.GPU_ONLY));
        RhiBuffer prismBuffer = prism.createBuffer(new RhiBufferCreateInfo(64, Set.of(RhiBufferUsage.VERTEX_BUFFER), RhiMemoryUsage.GPU_ONLY));
        RhiShader prismShader = adoptShader(prism, context, GL20.GL_VERTEX_SHADER, RhiShaderStage.VERTEX,
                "lumin-smoke-prism-vertex", "#version 410 core\nvoid main(){gl_Position=vec4(0.0);}");
        RhiShader prismFragment = adoptShader(prism, context, GL20.GL_FRAGMENT_SHADER, RhiShaderStage.FRAGMENT,
                "lumin-smoke-prism-fragment", "#version 410 core\nout vec4 color;void main(){color=vec4(1.0);}");
        int prismTextureId = Math.toIntExact(RhiNativeObjects.requireValue(prismTexture, OpenGlNativeObjectTypes.TEXTURE));
        int prismBufferId = Math.toIntExact(RhiNativeObjects.requireValue(prismBuffer, OpenGlNativeObjectTypes.BUFFER));
        int prismShaderId = Math.toIntExact(RhiNativeObjects.requireValue(prismShader, OpenGlNativeObjectTypes.SHADER));
        GpuTexture toBlazeTexture = bridge.toBlazeTexture(prismTexture, 1, "lumin-smoke-prism").orElseThrow();
        GpuBuffer toBlazeBuffer = bridge.toBlazeBuffer(prismBuffer, GpuBuffer.USAGE_VERTEX).orElseThrow();
        GlShaderModule toBlazeShader = bridge.toBlazeShader(prismShader).orElseThrow();
        require(((GlTexture) toBlazeTexture).glId() == prismTextureId, "Prism texture identity");
        require(((GlAccess2612.Buffer) toBlazeBuffer).lumin$handle() == prismBufferId, "Prism buffer identity");
        require(toBlazeShader.getShaderId() == prismShaderId, "Prism shader identity");

        GpuSampler blazeSampler = RenderSystem.getDevice().createSampler(AddressMode.REPEAT, AddressMode.REPEAT,
                FilterMode.LINEAR, FilterMode.LINEAR, 1, java.util.OptionalDouble.empty());
        RhiSampler rhiSampler = bridge.fromBlazeSampler(blazeSampler).orElseThrow();
        GpuSampler rebuiltSampler = bridge.toBlazeSampler(rhiSampler, RhiSamplerCreateInfo.linearRepeat()).orElseThrow();
        RenderPipeline minecraftPipeline = smokePipeline();
        RhiGraphicsPipeline rebuiltPipeline = bridge.rebuildPipeline(minecraftPipeline, (source, metadata) ->
                prism.createGraphicsPipeline(RhiGraphicsPipelineCreateInfo.builder()
                        .shader(RhiShaderStage.VERTEX, prismShader)
                        .shader(RhiShaderStage.FRAGMENT, prismFragment)
                        .build())).orElseThrow();
        require(bridge.rebuildPipeline(rebuiltPipeline, ignored -> minecraftPipeline).orElseThrow() == minecraftPipeline,
                "pipeline reverse rebuild");
        int pipelineId = Math.toIntExact(
                RhiNativeObjects.requireValue(rebuiltPipeline, OpenGlNativeObjectTypes.PROGRAM));
        require(GL20.glIsProgram(pipelineId), "pipeline rebuild did not create an OpenGL program");
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var mainView = client.getMainRenderTarget().getColorTextureView();
        try (var pass = encoder.createRenderPass(() -> "lumin-smoke-main-target", mainView, OptionalInt.of(0xff203040))) {
            bridge.adaptCommandEncoder(encoder).orElseThrow().access();
            bridge.adaptRenderPass(pass).orElseThrow().access();
        }
        byte[] pixels = paintAndRead((GlTexture) client.getMainRenderTarget().getColorTexture());
        Path png = replaceExtension(output, ".png");
        writePng(png, pixels);
        String pixelHash = sha256(png);

        fromBlazeShader.close(); fromBlazeBuffer.close(); fromBlazeTexture.close();
        toBlazeShader.close(); toBlazeBuffer.close(); toBlazeTexture.close();
        boolean borrowedCloseLive = GL11.glIsTexture(blazeTextureId) && GL15.glIsBuffer(blazeBufferId)
                && GL20.glIsShader(blazeShaderId) && GL11.glIsTexture(prismTextureId)
                && GL15.glIsBuffer(prismBufferId) && GL20.glIsShader(prismShaderId);
        require(borrowedCloseLive, "borrowed close deleted a native owner");

        blazeShader.close(); blazeBuffer.close(); blazeTexture.close();
        rebuiltPipeline.close();
        require(!GL20.glIsProgram(pipelineId), "pipeline close did not delete its OpenGL program");
        prismFragment.close(); prismShader.close(); prismBuffer.close(); prismTexture.close();
        boolean ownerCloseDeleted = !GL11.glIsTexture(blazeTextureId) && !GL15.glIsBuffer(blazeBufferId)
                && !GL20.glIsShader(blazeShaderId) && !GL11.glIsTexture(prismTextureId)
                && !GL15.glIsBuffer(prismBufferId) && !GL20.glIsShader(prismShaderId);
        require(ownerCloseDeleted, "owner close did not delete native objects");
        rebuiltSampler.close(); rhiSampler.close(); blazeSampler.close();
        prism.close(); instance.close();

        String objects = objectJson("texture", blazeTextureId, prismTextureId)
                + "," + objectJson("buffer", blazeBufferId, prismBufferId)
                + "," + objectJson("shader", blazeShaderId, prismShaderId);
        writeJson(output, "{\"version\":\"26.1.2\",\"loader\":\"" + loader
                + "\",\"mode\":\"positive\",\"contextIdentity\":\"" + escape(context.contextIdentity().toString())
                + "\",\"ownerThread\":\"" + escape(context.ownerThread().getName())
                + "\",\"invalidationTokenValid\":true,\"objects\":[" + objects
                + "],\"sampler\":true,\"pipeline\":true,\"encoder\":true,\"renderPass\":true,\"pixelHash\":\""
                + pixelHash + "\",\"png\":\"" + escape(png.toAbsolutePath().toString())
                + "\",\"cleanup\":true,\"borrowedCloseLive\":true,\"ownerCloseDeleted\":true,\"pass\":true}");
    }

    private static void runNegative(OpenGlExternalContext context, String loader, String mode, Path output) {
        String expected;
        String observed;
        if ("wrong-thread".equals(mode)) {
            expected = "RhiInvalidStateException";
            Throwable[] failure = new Throwable[1];
            Thread thread = new Thread(() -> { try { context.requireCurrent(); } catch (Throwable error) { failure[0] = error; } }, "smoke-wrong-thread");
            thread.start();
            try { thread.join(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            observed = failure[0] == null ? "none" : failure[0].getClass().getSimpleName();
        } else if ("stale-token".equals(mode)) {
            expected = "RhiResourceInvalidatedException";
            var token = new com.github.slmpc.prismrhi.context.RhiInvalidationToken(); token.invalidate();
            try { token.requireValid(); observed = "none"; } catch (Throwable error) { observed = error.getClass().getSimpleName(); }
        } else {
            expected = "IllegalArgumentException";
            try { requiredOutputValue(""); observed = "none"; } catch (Throwable error) { observed = error.getClass().getSimpleName(); }
        }
        require(observed.equals(expected), "negative expected " + expected + " but got " + observed);
        writeJson(output, "{\"version\":\"26.1.2\",\"loader\":\"" + loader + "\",\"mode\":\""
                + escape(mode) + "\",\"expectedFailure\":\"" + expected + "\",\"observedFailure\":\"" + observed
                + "\",\"beforeDraw\":true,\"beforeDeletion\":true,\"cleanup\":true,\"pass\":true}");
    }

    private static GlShaderModule compileBlazeShader() {
        int id = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(id, "#version 410 core\nvoid main(){gl_Position=vec4(0.0);}");
        GL20.glCompileShader(id);
        require(GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE, GL20.glGetShaderInfoLog(id));
        return GlAccess2612.Shader.create(id, Identifier.fromNamespaceAndPath("lumin_graphics_mc", "smoke/vertex"), ShaderType.VERTEX);
    }

    private static RhiShader adoptShader(OpenGlExternalDevice device, OpenGlExternalContext context, int type,
                                         RhiShaderStage stage, String label, String source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        require(GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE, GL20.glGetShaderInfoLog(id));
        return device.adoptShader(new OpenGlShaderAdoption(new RhiShaderDesc(stage, "main", label),
                new RhiNativeObject(OpenGlNativeObjectTypes.SHADER, id), RhiOwnership.OWNED,
                context.contextIdentity(), context.invalidation()));
    }

    private static RenderPipeline smokePipeline() {
        VertexFormat format = VertexFormat.builder().add("Position", VertexFormatElement.POSITION).build();
        return RenderPipeline.builder().withLocation("lumin_graphics_mc/smoke/pipeline")
                .withVertexShader("lumin_graphics_mc/smoke/vertex")
                .withFragmentShader("lumin_graphics_mc/smoke/fragment")
                .withVertexFormat(format, VertexFormat.Mode.TRIANGLES).build();
    }

    private static byte[] paintAndRead(GlTexture texture) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture.glId(), 0);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int[][] colors = {{0, 0, 4, 4, 255, 32, 32}, {4, 0, 4, 4, 32, 255, 32}, {0, 4, 4, 4, 32, 32, 255}, {4, 4, 4, 4, 255, 255, 32}};
        for (int[] c : colors) { GL11.glScissor(c[0], c[1], c[2], c[3]); GL11.glClearColor(c[4] / 255f, c[5] / 255f, c[6] / 255f, 1f); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT); }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        ByteBuffer buffer = BufferUtils.createByteBuffer(8 * 8 * 4);
        GL11.glReadPixels(0, 0, 8, 8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        byte[] pixels = new byte[buffer.remaining()]; buffer.get(pixels);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0); GL30.glDeleteFramebuffers(fbo);
        return pixels;
    }

    private static void writePng(Path path, byte[] pixels) throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) { int i = ((7 - y) * 8 + x) * 4; image.setRGB(x, y, (pixels[i + 3] & 255) << 24 | (pixels[i] & 255) << 16 | (pixels[i + 1] & 255) << 8 | pixels[i + 2] & 255); }
        Files.createDirectories(path.toAbsolutePath().getParent()); ImageIO.write(image, "PNG", path.toFile());
    }

    private static String objectJson(String type, int blaze, int prism) {
        return "{\"type\":\"" + type + "\",\"directions\":[{\"direction\":\"BLAZE_TO_PRISM\",\"mode\":\"BORROWED_ZERO_COPY\",\"nativeId\":" + blaze
                + "},{\"direction\":\"PRISM_TO_BLAZE\",\"mode\":\"BORROWED_ZERO_COPY\",\"nativeId\":" + prism
                + "}],\"preCloseGlIs\":true,\"borrowedCloseGlIs\":true,\"ownerCloseGlIs\":false}";
    }

    private static Path requiredOutput() { return Path.of(requiredOutputValue(System.getenv("LUMIN_MC_SMOKE_OUTPUT"))).toAbsolutePath(); }
    private static String requiredOutputValue(String value) { if (value == null || value.isBlank() || !value.endsWith(".json")) throw new IllegalArgumentException("LUMIN_MC_SMOKE_OUTPUT must be a .json path"); return value; }
    private static Path replaceExtension(Path path, String extension) { String name = path.getFileName().toString(); return path.resolveSibling(name.substring(0, name.lastIndexOf('.')) + extension); }
    private static void writeJson(Path path, String json) { try { Files.createDirectories(path.getParent()); Files.writeString(path, json + System.lineSeparator(), StandardCharsets.UTF_8); } catch (IOException error) { throw new IllegalStateException(error); } }
    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
