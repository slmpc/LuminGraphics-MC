package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlAdoptedResource;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlBufferAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageViewAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlShaderAdoption;
import com.github.slmpc.prismrhi.context.PRhiContextIdentity;
import com.github.slmpc.prismrhi.context.PRhiInvalidationToken;
import com.github.slmpc.prismrhi.format.PRhiExtent3D;
import com.github.slmpc.prismrhi.format.PRhiFormat;
import com.github.slmpc.prismrhi.pipeline.PRhiGraphicsPipeline;
import com.github.slmpc.prismrhi.resource.PRhiBuffer;
import com.github.slmpc.prismrhi.resource.PRhiImage;
import com.github.slmpc.prismrhi.resource.PRhiImageAspect;
import com.github.slmpc.prismrhi.resource.PRhiImageView;
import com.github.slmpc.prismrhi.resource.PRhiNativeObject;
import com.github.slmpc.prismrhi.resource.PRhiNativeObjects;
import com.github.slmpc.prismrhi.resource.PRhiNativeObjectType;
import com.github.slmpc.prismrhi.resource.PRhiOwnership;
import com.github.slmpc.prismrhi.resource.PRhiSampler;
import com.github.slmpc.prismrhi.resource.PRhiSamplerCreateInfo;
import com.github.slmpc.prismrhi.shader.PRhiShader;
import com.github.slmpc.prismrhi.shader.PRhiShaderDesc;
import com.github.slmpc.prismrhi.shader.PRhiShaderHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class BridgeTestFixtures2612 {
    private BridgeTestFixtures2612() { }

    static final class Context implements BridgeContext2612 {
        final PRhiContextIdentity identity = new PRhiContextIdentity(2612, "test-2612");
        final PRhiInvalidationToken token = new PRhiInvalidationToken();
        boolean current = true;

        @Override public void requireCurrent() {
            token.requireValid();
            if (!current) throw new IllegalStateException("test context is not current");
        }
        @Override public PRhiContextIdentity identity() { return identity; }
        @Override public PRhiInvalidationToken invalidation() { return token; }
    }

    static final class Device implements InvocationHandler {
        final Context context;
        OpenGlImageAdoption imageAdoption;
        OpenGlImageViewAdoption viewAdoption;
        OpenGlBufferAdoption bufferAdoption;
        OpenGlShaderAdoption shaderAdoption;
        PRhiSamplerCreateInfo samplerInfo;

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
                case "createSampler" -> createSampler((PRhiSamplerCreateInfo) args[0]);
                case "api" -> BackendApi.OPENGL_41;
                case "close" -> null;
                case "toString" -> "OpenGlExternalDevice[test]";
                default -> defaultValue(method.getReturnType());
            };
        }

        private PRhiImage adoptImage(OpenGlImageAdoption adoption) {
            imageAdoption = adoption;
            return image(adoption.nativeObject().value(), adoption.createInfo().extent(),
                    adoption.createInfo().format(), adoption.contextIdentity(), adoption.invalidationToken());
        }

        private PRhiImageView adoptView(OpenGlImageViewAdoption adoption) {
            viewAdoption = adoption;
            return imageView(adoption.createInfo().image(), context.identity, context.token);
        }

        private PRhiBuffer adoptBuffer(OpenGlBufferAdoption adoption) {
            bufferAdoption = adoption;
            return buffer(adoption.nativeObject().value(), adoption.createInfo().size(),
                    adoption.contextIdentity(), adoption.invalidationToken());
        }

        private PRhiShader adoptShader(OpenGlShaderAdoption adoption) {
            shaderAdoption = adoption;
            return shader(adoption.nativeObject().value(), adoption.desc(),
                    adoption.contextIdentity(), adoption.invalidationToken());
        }

        private PRhiSampler createSampler(PRhiSamplerCreateInfo info) {
            samplerInfo = info;
            return sampler(104, context.identity, context.token);
        }
    }

    static PRhiImage image(long handle, PRhiExtent3D extent, PRhiFormat format,
                          PRhiContextIdentity identity, PRhiInvalidationToken token) {
        return resource(PRhiImage.class, OpenGlNativeObjectTypes.TEXTURE, handle, identity, token,
                Map.of("extent", extent, "format", format));
    }

    static PRhiImageView imageView(PRhiImage image, PRhiContextIdentity identity, PRhiInvalidationToken token) {
        return resource(PRhiImageView.class, OpenGlNativeObjectTypes.TEXTURE,
                PRhiNativeObjects.requireValue(image, OpenGlNativeObjectTypes.TEXTURE), identity, token,
                Map.of("image", image, "format", image.format(), "aspects", Set.of(PRhiImageAspect.COLOR)));
    }

    static PRhiBuffer buffer(long handle, long size, PRhiContextIdentity identity, PRhiInvalidationToken token) {
        return resource(PRhiBuffer.class, OpenGlNativeObjectTypes.BUFFER, handle, identity, token,
                Map.of("size", size));
    }

    static PRhiSampler sampler(long handle, PRhiContextIdentity identity, PRhiInvalidationToken token) {
        return resource(PRhiSampler.class, OpenGlNativeObjectTypes.SAMPLER, handle, identity, token, Map.of());
    }

    static PRhiGraphicsPipeline pipeline(long handle, PRhiContextIdentity identity, PRhiInvalidationToken token) {
        return resource(PRhiGraphicsPipeline.class, OpenGlNativeObjectTypes.PROGRAM, handle, identity, token, Map.of());
    }

    static PRhiShader shader(long handle, PRhiShaderDesc desc,
                            PRhiContextIdentity identity, PRhiInvalidationToken token) {
        return PRhiShaderHandle.adopted(BackendApi.OPENGL_41, desc,
                new PRhiNativeObject(OpenGlNativeObjectTypes.SHADER, handle), OpenGlNativeObjectTypes.SHADER,
                PRhiOwnership.BORROWED, identity, identity, token, ignored -> { });
    }

    private static <T> T resource(Class<T> type, PRhiNativeObjectType nativeType, long handle,
                                  PRhiContextIdentity identity, PRhiInvalidationToken token,
                                  Map<String, Object> values) {
        PRhiNativeObject nativeObject = new PRhiNativeObject(nativeType, handle);
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "api" -> BackendApi.OPENGL_41;
            case "ownership" -> PRhiOwnership.BORROWED;
            case "contextIdentity" -> identity;
            case "invalidationToken" -> token;
            case "getNativeObject" -> nativeObject((PRhiNativeObjectType) args[0], nativeObject);
            case "close" -> null;
            case "toString" -> type.getSimpleName() + "[test]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> values.getOrDefault(method.getName(), defaultValue(method.getReturnType()));
        };
        return proxy(type, handler, type, OpenGlAdoptedResource.class);
    }

    private static Optional<PRhiNativeObject> nativeObject(PRhiNativeObjectType requested,
                                                           PRhiNativeObject nativeObject) {
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
