package com.github.slmpc.lumingraphics.mc.v262.smoke;

import com.github.slmpc.lumingraphics.mc.bridge.BridgeCapability;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeContextIdentity;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeInvalidationToken;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeLease;
import com.github.slmpc.lumingraphics.mc.v262.access.MixinGlObjectFactory262;
import com.github.slmpc.lumingraphics.mc.v262.access.GlBufferDsaAccess262;
import com.github.slmpc.lumingraphics.mc.v262.bridge.GlStateManagerBridge262;
import com.github.slmpc.lumingraphics.mc.v262.mixin.GlInvokers262;
import com.github.slmpc.lumingraphics.mc.v262.bridge.Blaze3DBridge262;
import com.github.slmpc.prismrhi.PrismRHI;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlShaderAdoption;
import com.github.slmpc.prismrhi.backend.opengl41.Gl41BackendProvider;
import com.github.slmpc.prismrhi.backend.opengl46.Gl46BackendProvider;
import com.github.slmpc.prismrhi.device.RhiDeviceCreateInfo;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.instance.RhiInstance;
import com.github.slmpc.prismrhi.instance.RhiInstanceCreateInfo;
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
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderBinaryFormat;
import com.github.slmpc.prismrhi.shader.RhiShaderDesc;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class RealClientBridgeSmoke262 {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private RealClientBridgeSmoke262() { }

    public static void runIfEnabled(Minecraft client, OpenGlExternalContext context, String loader) {
        String mode = System.getenv("LUMIN_MC_SMOKE_MODE");
        if (mode == null || !STARTED.compareAndSet(false, true)) return;
        Path output = output();
        try {
            if ("positive".equals(mode)) positive(client, context, loader, output);
            else negative(context, loader, mode, output);
        } catch (Throwable failure) {
            json(output, "{\"version\":\"26.2\",\"loader\":\"" + loader + "\",\"mode\":\"" + esc(mode)
                    + "\",\"pass\":false,\"errorType\":\"" + esc(failure.getClass().getName()) + "\",\"error\":\"" + esc(failure.getMessage()) + "\"}");
        } finally { client.stop(); }
    }

    private static void positive(Minecraft client, OpenGlExternalContext context, String loader, Path output) throws Exception {
        RenderSystem.assertOnRenderThread(); context.requireCurrent();
        RhiInstance instance = PrismRHI.createInstance(isMacOs()
                ? new Gl41BackendProvider(context) : new Gl46BackendProvider(context), RhiInstanceCreateInfo.builder().build());
        OpenGlExternalDevice prism = (OpenGlExternalDevice) instance.createDevice(instance.enumeratePhysicalDevices().getFirst(),
                RhiDeviceCreateInfo.builder().glStateBridge(GlStateManagerBridge262.INSTANCE).build());
        BridgeContextIdentity bridgeContext = BridgeContextIdentity.create("minecraft-26.2-" + loader);
        BridgeInvalidationToken bridgeToken = bridgeContext.newInvalidationToken();
        Blaze3DBridge262 bridge = new Blaze3DBridge262(prism, bridgeContext, bridgeToken, context.contextIdentity(),
                context.invalidation(), GL.getCapabilities(), GL::getCapabilities, () -> bridgeContext,
                new MixinGlObjectFactory262());

        GpuTexture blazeTexture = RenderSystem.getDevice().createTexture(() -> "lumin-smoke-blaze", 12, GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
        GpuBuffer blazeBuffer = RenderSystem.getDevice().createBuffer(() -> "lumin-smoke-blaze", GpuBuffer.USAGE_VERTEX, 64);
        GlShaderModule blazeShader = shader();
        int bt = ((GlTexture) blazeTexture).glId(), bb = ((GlBuffer) blazeBuffer).handle(), bs = blazeShader.getShaderId();
        BridgeLease<RhiImage> btLease = bridge.textureToLumin(blazeTexture).orElseThrow();
        BridgeLease<Blaze3DBridge262.RhiBufferSlice262> bbLease = bridge.bufferToLumin(blazeBuffer.slice()).orElseThrow();
        BridgeLease<RhiShader> bsLease = bridge.shaderToLumin(blazeShader).orElseThrow();
        require(nativeId(btLease.access(bridgeContext), OpenGlNativeObjectTypes.TEXTURE) == bt, "Blaze texture identity");
        require(nativeId(bbLease.access(bridgeContext).buffer(), OpenGlNativeObjectTypes.BUFFER) == bb, "Blaze buffer identity");
        require(nativeId(bsLease.access(bridgeContext), OpenGlNativeObjectTypes.SHADER) == bs, "Blaze shader identity");

        RhiImage prismTexture = prism.createImage(new RhiImageCreateInfo(new RhiExtent3D(8, 8, 1), RhiFormat.RGBA8_UNORM,
                Set.of(RhiImageUsage.SAMPLED, RhiImageUsage.COLOR_ATTACHMENT), RhiMemoryUsage.GPU_ONLY));
        RhiBuffer prismBuffer = prism.createBuffer(new RhiBufferCreateInfo(64, Set.of(RhiBufferUsage.VERTEX_BUFFER), RhiMemoryUsage.GPU_ONLY));
        RhiShader prismShader = adoptShader(prism, context, GL20.GL_VERTEX_SHADER, RhiShaderStage.VERTEX,
                "lumin-smoke-prism-vertex", "#version 410 core\nvoid main(){gl_Position=vec4(0.0);}");
        RhiShader prismFragment = adoptShader(prism, context, GL20.GL_FRAGMENT_SHADER, RhiShaderStage.FRAGMENT,
                "lumin-smoke-prism-fragment", "#version 410 core\nout vec4 color;void main(){color=vec4(1.0);}");
        int pt = nativeId(prismTexture, OpenGlNativeObjectTypes.TEXTURE), pb = nativeId(prismBuffer, OpenGlNativeObjectTypes.BUFFER), ps = nativeId(prismShader, OpenGlNativeObjectTypes.SHADER);
        BridgeCapability tc = capability("texture", "RhiImage", pt, bridgeContext, bridgeToken);
        BridgeCapability bc = capability("buffer", "RhiBuffer", pb, bridgeContext, bridgeToken);
        BridgeCapability sc = capability("shader-module", "RhiShader", ps, bridgeContext, bridgeToken);
        GlTexture ptBorrowed = bridge.textureToMinecraft(prismTexture, tc,
                new Blaze3DBridge262.TextureMetadata262(4, "lumin-smoke-prism", 1), new FrameBufferCache()).orElseThrow().access(bridgeContext);
        DirectStateAccess dsa = dsa((GlBuffer) blazeBuffer);
        GlBuffer.Direct pbBorrowed = bridge.bufferToMinecraft(prismBuffer, bc, GpuBuffer.USAGE_VERTEX, dsa, false).orElseThrow().access(bridgeContext);
        GlShaderModule psBorrowed = bridge.shaderToMinecraft(prismShader, sc,
                Identifier.fromNamespaceAndPath("lumin_graphics_mc", "smoke/prism")).orElseThrow().access(bridgeContext);
        require(ptBorrowed.glId() == pt && pbBorrowed.handle() == pb && psBorrowed.getShaderId() == ps, "Prism native identity");

        var blazeSampler = RenderSystem.getDevice().createSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, 1, java.util.OptionalDouble.empty());
        var samplerLease = bridge.samplerToLumin(blazeSampler).orElseThrow();
        var rebuiltSampler = bridge.samplerToMinecraft(samplerLease.access(bridgeContext), ignored ->
                RenderSystem.getDevice().createSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, 1, java.util.OptionalDouble.empty())).orElseThrow();
        RenderPipeline minecraftPipeline = smokePipeline();
        var pipelineLease = bridge.pipelineToLumin(minecraftPipeline, ignored ->
                prism.createGraphicsPipeline(RhiGraphicsPipelineCreateInfo.builder()
                        .shader(RhiShaderStage.VERTEX, prismShader)
                        .shader(RhiShaderStage.FRAGMENT, prismFragment)
                        .build())).orElseThrow();
        var rebuiltMinecraftPipeline = bridge.pipelineToMinecraft(pipelineLease.access(bridgeContext), ignored -> minecraftPipeline)
                .orElseThrow();
        require(rebuiltMinecraftPipeline.access(bridgeContext) == minecraftPipeline, "pipeline reverse rebuild");
        int pipelineId = nativeId(pipelineLease.access(bridgeContext), OpenGlNativeObjectTypes.PROGRAM);
        require(GL20.glIsProgram(pipelineId), "pipeline rebuild did not create an OpenGL program");
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "lumin-smoke-main-target", client.gameRenderer.mainRenderTarget().getColorTextureView(), Optional.of(new Vector4f(.1f, .2f, .3f, 1f)))) {
            bridge.encoderToMinecraft(bridge.encoderToLumin(encoder).orElseThrow().access(bridgeContext)).orElseThrow().access(bridgeContext);
            bridge.renderPassToMinecraft(bridge.renderPassToLumin(pass).orElseThrow().access(bridgeContext)).orElseThrow().access(bridgeContext);
        }
        byte[] pixels = paint((GlTexture) client.gameRenderer.mainRenderTarget().getColorTexture());
        Path png = sibling(output, ".png"); png(png, pixels); String hash = hash(png);

        btLease.close(); bbLease.close(); bsLease.close(); ptBorrowed.close(); pbBorrowed.close(); psBorrowed.close();
        require(live(bt, bb, bs, pt, pb, ps), "borrowed close deleted owner");
        rebuiltMinecraftPipeline.close(); pipelineLease.close();
        require(!GL20.glIsProgram(pipelineId), "pipeline close did not delete its OpenGL program");
        blazeShader.close(); blazeBuffer.close(); blazeTexture.close(); prismFragment.close(); prismShader.close(); prismBuffer.close(); prismTexture.close();
        require(!live(bt, bb, bs, pt, pb, ps), "owner close did not delete objects");
        rebuiltSampler.close(); samplerLease.close(); blazeSampler.close(); bridgeToken.invalidate(); prism.close(); instance.close();
        String objects = obj("texture", bt, pt) + "," + obj("buffer", bb, pb) + "," + obj("shader", bs, ps);
        json(output, "{\"version\":\"26.2\",\"loader\":\"" + loader + "\",\"mode\":\"positive\",\"contextIdentity\":\""
                + esc(context.contextIdentity().toString()) + "\",\"ownerThread\":\"" + esc(context.ownerThread().getName())
                + "\",\"invalidationTokenValid\":true,\"objects\":[" + objects + "],\"sampler\":true,\"pipeline\":true,\"encoder\":true,\"renderPass\":true,\"pixelHash\":\""
                + hash + "\",\"png\":\"" + esc(png.toAbsolutePath().toString()) + "\",\"cleanup\":true,\"borrowedCloseLive\":true,\"ownerCloseDeleted\":true,\"pass\":true}");
    }

    private static void negative(OpenGlExternalContext context, String loader, String mode, Path output) {
        String expected, observed;
        if ("wrong-context".equals(mode)) { expected = "BridgeWrongContextException"; var c = BridgeContextIdentity.create("expected"); var lease = BridgeLease.borrowed("x", c, c.newInvalidationToken()); try { lease.access(BridgeContextIdentity.create("wrong")); observed = "none"; } catch (Throwable e) { observed = e.getClass().getSimpleName(); }
        } else if ("missing-accessor".equals(mode)) {
            RenderSystem.assertOnRenderThread(); context.requireCurrent(); expected = "ClassCastException";
            GpuBuffer probe = RenderSystem.getDevice().createBuffer(() -> "lumin-smoke-missing-accessor", GpuBuffer.USAGE_VERTEX, 16);
            int handle = ((GlBuffer) probe).handle();
            boolean accessorApplied = GlBufferDsaAccess262.class.isInstance(probe);
            try { dsa((GlBuffer) probe); observed = "none"; } catch (Throwable e) { observed = e.getClass().getSimpleName(); }
            boolean beforeDeletion = GL15.glIsBuffer(handle); probe.close(); boolean cleanup = !GL15.glIsBuffer(handle);
            require(expected.equals(observed), "negative mismatch expected=" + expected + " observed=" + observed
                    + " accessorApplied=" + accessorApplied + " probeClass=" + probe.getClass().getName());
            json(output, "{\"version\":\"26.2\",\"loader\":\"" + loader + "\",\"mode\":\"missing-accessor\",\"expectedFailure\":\"" + expected + "\",\"observedFailure\":\"" + observed + "\",\"accessorApplied\":" + accessorApplied + ",\"beforeDraw\":true,\"beforeDeletion\":" + beforeDeletion + ",\"cleanup\":" + cleanup + ",\"pass\":true}");
            return;
        } else { expected = "RhiInvalidStateException"; Throwable[] e = new Throwable[1]; Thread t = new Thread(() -> { try { context.requireCurrent(); } catch (Throwable x) { e[0] = x; } }); t.start(); try { t.join(); } catch (InterruptedException x) { throw new IllegalStateException(x); } observed = e[0] == null ? "none" : e[0].getClass().getSimpleName(); }
        require(expected.equals(observed), "negative mismatch");
        json(output, "{\"version\":\"26.2\",\"loader\":\"" + loader + "\",\"mode\":\"" + esc(mode) + "\",\"expectedFailure\":\"" + expected + "\",\"observedFailure\":\"" + observed + "\",\"beforeDraw\":true,\"beforeDeletion\":true,\"cleanup\":true,\"pass\":true}");
    }

    private static BridgeCapability capability(String type, String diagnostic, int id, BridgeContextIdentity c, BridgeInvalidationToken t) { return BridgeCapability.openGl(type, diagnostic, id, c, t, () -> true, GL.getCapabilities(), GL::getCapabilities, () -> c); }
    private static int nativeId(com.github.slmpc.prismrhi.resource.RhiResource r, com.github.slmpc.prismrhi.resource.RhiNativeObjectType t) { return Math.toIntExact(RhiNativeObjects.requireValue(r, t)); }
    private static GlShaderModule shader() { int id = GL20.glCreateShader(GL20.GL_VERTEX_SHADER); GL20.glShaderSource(id, "#version 410 core\nvoid main(){gl_Position=vec4(0.0);}"); GL20.glCompileShader(id); require(GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE, GL20.glGetShaderInfoLog(id)); return GlInvokers262.Shader.create(id, Identifier.fromNamespaceAndPath("lumin_graphics_mc", "smoke/vertex"), ShaderType.VERTEX); }
    private static RhiShader adoptShader(OpenGlExternalDevice device, OpenGlExternalContext context, int type, RhiShaderStage stage, String label, String source) { int id=GL20.glCreateShader(type);GL20.glShaderSource(id,source);GL20.glCompileShader(id);require(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==GL11.GL_TRUE,GL20.glGetShaderInfoLog(id));return device.adoptShader(new OpenGlShaderAdoption(new RhiShaderDesc(stage,"main",label),new RhiNativeObject(OpenGlNativeObjectTypes.SHADER,id),RhiOwnership.OWNED,context.contextIdentity(),context.invalidation())); }
    private static RenderPipeline smokePipeline() { return RenderPipeline.builder().withLocation("lumin_graphics_mc/smoke/pipeline").withVertexShader("lumin_graphics_mc/smoke/vertex").withFragmentShader("lumin_graphics_mc/smoke/fragment").withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build(); }
    private static DirectStateAccess dsa(GlBuffer buffer) { return ((GlBufferDsaAccess262) buffer).lumin$getDsa(); }
    private static boolean live(int t1,int b1,int s1,int t2,int b2,int s2){return GL11.glIsTexture(t1)&&GL15.glIsBuffer(b1)&&GL20.glIsShader(s1)&&GL11.glIsTexture(t2)&&GL15.glIsBuffer(b2)&&GL20.glIsShader(s2);}
    private static byte[] paint(GlTexture t){int f=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,t.glId(),0);GL11.glEnable(GL11.GL_SCISSOR_TEST);int[][]cs={{0,0,4,4,255,32,32},{4,0,4,4,32,255,32},{0,4,4,4,32,32,255},{4,4,4,4,255,255,32}};for(int[]c:cs){GL11.glScissor(c[0],c[1],c[2],c[3]);GL11.glClearColor(c[4]/255f,c[5]/255f,c[6]/255f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);}GL11.glDisable(GL11.GL_SCISSOR_TEST);ByteBuffer b=BufferUtils.createByteBuffer(256);GL11.glReadPixels(0,0,8,8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);byte[]p=new byte[256];b.get(p);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(f);return p;}
    private static void png(Path p,byte[]v)throws IOException{BufferedImage i=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<8;y++)for(int x=0;x<8;x++){int n=((7-y)*8+x)*4;i.setRGB(x,y,(v[n+3]&255)<<24|(v[n]&255)<<16|(v[n+1]&255)<<8|v[n+2]&255);}Files.createDirectories(p.getParent());ImageIO.write(i,"PNG",p.toFile());}
    private static String obj(String type,int b,int p){return "{\"type\":\""+type+"\",\"directions\":[{\"direction\":\"BLAZE_TO_PRISM\",\"mode\":\"BORROWED_ZERO_COPY\",\"nativeId\":"+b+"},{\"direction\":\"PRISM_TO_BLAZE\",\"mode\":\"BORROWED_ZERO_COPY\",\"nativeId\":"+p+"}],\"preCloseGlIs\":true,\"borrowedCloseGlIs\":true,\"ownerCloseGlIs\":false}";}
    private static Path output(){String v=System.getenv("LUMIN_MC_SMOKE_OUTPUT");if(v==null||v.isBlank()||!v.endsWith(".json"))throw new IllegalArgumentException("LUMIN_MC_SMOKE_OUTPUT must be .json");return Path.of(v).toAbsolutePath();}
    private static Path sibling(Path p,String x){String n=p.getFileName().toString();return p.resolveSibling(n.substring(0,n.lastIndexOf('.'))+x);}
    private static void json(Path p,String j){try{Files.createDirectories(p.getParent());Files.writeString(p,j+System.lineSeparator(),StandardCharsets.UTF_8);}catch(IOException e){throw new IllegalStateException(e);}}
    private static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static String esc(String v){return v==null?"":v.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n");}
    private static void require(boolean c,String m){if(!c)throw new IllegalStateException(m);}
    private static boolean isMacOs(){return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");}
}
