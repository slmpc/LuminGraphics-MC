package com.github.slmpc.lumingraphics.mc.v262.bridge;

import com.github.slmpc.prismrhi.backend.RhiGlStateBridge;
import com.mojang.blaze3d.opengl.GlStateManager;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFrontFace;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL20.glBlendEquationSeparate;
import static org.lwjgl.opengl.GL30.glBindBufferRange;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

public final class GlStateManagerBridge262 implements RhiGlStateBridge {
    public static final GlStateManagerBridge262 INSTANCE = new GlStateManagerBridge262();

    private GlStateManagerBridge262() {
    }

    @Override
    public void enable(int cap) {
        switch (cap) {
            case GL_BLEND -> GlStateManager._enableBlend(0);
            case GL_CULL_FACE -> GlStateManager._enableCull();
            case GL_DEPTH_TEST -> GlStateManager._enableDepthTest();
            case GL_SCISSOR_TEST -> GlStateManager._enableScissorTest();
            default -> glEnable(cap);
        }
    }

    @Override
    public void disable(int cap) {
        switch (cap) {
            case GL_BLEND -> GlStateManager._disableBlend(0);
            case GL_CULL_FACE -> GlStateManager._disableCull();
            case GL_DEPTH_TEST -> GlStateManager._disableDepthTest();
            case GL_SCISSOR_TEST -> GlStateManager._disableScissorTest();
            default -> glDisable(cap);
        }
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GlStateManager._viewport(x, y, width, height);
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        GlStateManager._scissorBox(x, y, width, height);
    }

    @Override
    public void polygonMode(int face, int mode) {
        GlStateManager._polygonMode(face, mode);
    }

    @Override
    public void frontFace(int mode) {
        glFrontFace(mode);
    }

    @Override
    public void lineWidth(float width) {
        glLineWidth(width);
    }

    @Override
    public void cullFace(int mode) {
        glCullFace(mode);
    }

    @Override
    public void depthFunc(int func) {
        GlStateManager._depthFunc(func);
    }

    @Override
    public void depthMask(boolean mask) {
        GlStateManager._depthMask(mask);
    }

    @Override
    public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquationSeparate(int modeRgb, int modeAlpha) {
        glBlendEquationSeparate(modeRgb, modeAlpha);
    }

    @Override
    public int genFramebuffer() {
        return GlStateManager.glGenFramebuffers();
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        GlStateManager._glBindFramebuffer(target, framebuffer);
    }

    @Override
    public void deleteFramebuffer(int framebuffer) {
        GlStateManager._glDeleteFramebuffers(framebuffer);
    }

    @Override
    public void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GlStateManager._glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @Override
    public void useProgram(int program) {
        GlStateManager._glUseProgram(program);
    }

    @Override
    public void activeTexture(int texture) {
        GlStateManager._activeTexture(texture);
    }

    @Override
    public void bindTexture(int target, int texture) {
        if (target == GL_TEXTURE_2D) {
            GlStateManager._bindTexture(texture);
        } else {
            org.lwjgl.opengl.GL11.glBindTexture(target, texture);
        }
    }

    @Override
    public void bindSampler(int unit, int sampler) {
        glBindSampler(unit, sampler);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        GlStateManager._glBindBuffer(target, buffer);
    }

    @Override
    public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        glBindBufferRange(target, index, buffer, offset, size);
    }

    @Override
    public int genVertexArray() {
        return GlStateManager._glGenVertexArrays();
    }

    @Override
    public void bindVertexArray(int array) {
        GlStateManager._glBindVertexArray(array);
    }

    @Override
    public void deleteVertexArray(int array) {
        glDeleteVertexArrays(array);
    }

    @Override
    public void enableVertexAttribArray(int index) {
        GlStateManager._enableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GlStateManager._vertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        glVertexAttribDivisor(index, divisor);
    }
}
