package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlAdoptedResource;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlBufferAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageViewAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlShaderAdoption;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageAspect;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiNativeObject;
import com.github.slmpc.prismrhi.resource.RhiNativeObjects;
import com.github.slmpc.prismrhi.resource.RhiNativeObjectType;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderDesc;
import com.github.slmpc.prismrhi.shader.RhiShaderHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class BridgeTestFixtures2612 {
    private BridgeTestFixtures2612() { }

    static final class Context implements BridgeContext2612 {
        final RhiContextIdentity identity = new RhiContextIdentity(2612, "test-2612");
        final RhiInvalidationToken token = new RhiInvalidationToken();
        boolean current = true;

        @Override public void requireCurrent() {
            token.requireValid();
            if (!current) throw new IllegalStateException("test context is not current");
        }
        @Override public RhiContextIdentity identity() { return identity; }
        @Override public RhiInvalidationToken invalidation() { return token; }
    }

    static final class Device implements InvocationHandler {
        final Context context;
        OpenGlImageAdoption imageAdoption;
        OpenGlImageViewAdoption viewAdoption;
        OpenGlBufferAdoption bufferAdoption;
        OpenGlShaderAdoption shaderAdoption;
        RhiSamplerCreateInfo samplerInfo;

        Device(Context context) { this.context = context; }

        OpenGlExternalDevice proxy() {
            return BridgeTestFixtures2612.proxy(
                    OpenGlExternalDevice.class, this, OpenGlExternalDevice.class);
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "adoptImage" -> adoptImage((OpenGlImageAdoption) args[0]);
                case "adoptImageView" -> adoptView((OpenGlImageViewAdoption) args[0]);
                case "adoptBuffer" -> adoptBuffer((OpenGlBufferAdoption) args[0]);
                case "adoptShader" -> adoptShader((OpenGlShaderAdoption) args[0]);
                case "createSampler" -> createSampler((RhiSamplerCreateInfo) args[0]);
                case "api" -> BackendApi.OPENGL_41;
                case "close" -> null;
                case "toString" -> "OpenGlExternalDevice[test]";
                default -> defaultValue(method.getReturnType());
            };
        }

        private RhiImage adoptImage(OpenGlImageAdoption adoption) {
            imageAdoption = adoption;
            return image(adoption.nativeObject().value(), adoption.createInfo().extent(),
                    adoption.createInfo().format(), adoption.contextIdentity(), adoption.invalidationToken());
        }

        private RhiImageView adoptView(OpenGlImageViewAdoption adoption) {
            viewAdoption = adoption;
            return imageView(adoption.createInfo().image(), context.identity, context.token);
        }

        private RhiBuffer adoptBuffer(OpenGlBufferAdoption adoption) {
            bufferAdoption = adoption;
            return buffer(adoption.nativeObject().value(), adoption.createInfo().size(),
                    adoption.contextIdentity(), adoption.invalidationToken());
        }

        private RhiShader adoptShader(OpenGlShaderAdoption adoption) {
            shaderAdoption = adoption;
            return shader(adoption.nativeObject().value(), adoption.desc(),
                    adoption.contextIdentity(), adoption.invalidationToken());
        }

        private RhiSampler createSampler(RhiSamplerCreateInfo info) {
            samplerInfo = info;
            return sampler(104, context.identity, context.token);
        }
    }

    static RhiImage image(long handle, RhiExtent3D extent, RhiFormat format,
                          RhiContextIdentity identity, RhiInvalidationToken token) {
        return resource(RhiImage.class, OpenGlNativeObjectTypes.TEXTURE, handle, identity, token,
                Map.of("extent", extent, "format", format));
    }

    static RhiImageView imageView(RhiImage image, RhiContextIdentity identity, RhiInvalidationToken token) {
        return resource(RhiImageView.class, OpenGlNativeObjectTypes.TEXTURE,
                RhiNativeObjects.requireValue(image, OpenGlNativeObjectTypes.TEXTURE), identity, token,
                Map.of("image", image, "format", image.format(), "aspects", Set.of(RhiImageAspect.COLOR)));
    }

    static RhiBuffer buffer(long handle, long size, RhiContextIdentity identity, RhiInvalidationToken token) {
        return resource(RhiBuffer.class, OpenGlNativeObjectTypes.BUFFER, handle, identity, token,
                Map.of("size", size));
    }

    static RhiSampler sampler(long handle, RhiContextIdentity identity, RhiInvalidationToken token) {
        return resource(RhiSampler.class, OpenGlNativeObjectTypes.SAMPLER, handle, identity, token, Map.of());
    }

    static RhiGraphicsPipeline pipeline(long handle, RhiContextIdentity identity, RhiInvalidationToken token) {
        return resource(RhiGraphicsPipeline.class, OpenGlNativeObjectTypes.PROGRAM, handle, identity, token, Map.of());
    }

    static RhiShader shader(long handle, RhiShaderDesc desc,
                            RhiContextIdentity identity, RhiInvalidationToken token) {
        return RhiShaderHandle.adopted(BackendApi.OPENGL_41, desc,
                new RhiNativeObject(OpenGlNativeObjectTypes.SHADER, handle), OpenGlNativeObjectTypes.SHADER,
                RhiOwnership.BORROWED, identity, identity, token, ignored -> { });
    }

    private static <T> T resource(Class<T> type, RhiNativeObjectType nativeType, long handle,
                                  RhiContextIdentity identity, RhiInvalidationToken token,
                                  Map<String, Object> values) {
        RhiNativeObject nativeObject = new RhiNativeObject(nativeType, handle);
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "api" -> BackendApi.OPENGL_41;
            case "ownership" -> RhiOwnership.BORROWED;
            case "contextIdentity" -> identity;
            case "invalidationToken" -> token;
            case "getNativeObject" -> nativeObject((RhiNativeObjectType) args[0], nativeObject);
            case "close" -> null;
            case "toString" -> type.getSimpleName() + "[test]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> values.getOrDefault(method.getName(), defaultValue(method.getReturnType()));
        };
        return proxy(type, handler, type, OpenGlAdoptedResource.class);
    }

    private static Optional<RhiNativeObject> nativeObject(RhiNativeObjectType requested,
                                                           RhiNativeObject nativeObject) {
        return requested.hasSameId(nativeObject.type()) ? Optional.of(nativeObject) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler, Class<?>... interfaces) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), interfaces, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        return null;
    }
}
