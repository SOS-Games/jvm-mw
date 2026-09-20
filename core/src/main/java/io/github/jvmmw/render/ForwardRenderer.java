package io.github.jvmmw.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

public final class ForwardRenderer {
    public static final int MAX_LIGHTS = 8;
    private static final int GL_CLIP_DISTANCE0 = 0x3000;

    private final int program;
    private final int uMvp;
    private final int uModel;
    private final int uView;
    private final int uLightDir;
    private final int uAmbientLight;
    private final int uSunDiffuse;
    private final int uFogEnabled;
    private final int uFogStart;
    private final int uFogScale;
    private final int uFogColor;
    private final int uPointCount;
    private final int uPointPos;
    private final int uPointDiffuse;
    private final int uPointAtten;
    private final int uBase;
    private final int uDark;
    private final int uDetail;
    private final int uGlow;
    private final int uUseDark;
    private final int uUseDetail;
    private final int uUseGlow;
    private final int uAmbient;
    private final int uDiffuse;
    private final int uEmissive;
    private final int uMatAlpha;
    private final int uColorMode;
    private final int uAlphaTest;
    private final int uAlphaFunc;
    private final int uAlphaRef;
    private final int uBlendMap;
    private final int uUseBlendMap;
    private final int uUvScale;
    private final int uAdjustCoverage;
    private final int uCameraFar;
    private final int uDepthBias;
    private final int uClipPlane;
    private final int waterProgram;
    private final int uWaterMvp;
    private final int uWaterModel;
    private final int uWaterView;
    private final int uWaterNormal;
    private final int uWaterReflection;
    private final int uWaterTime;
    private final int uWaterCamTes;
    private final int uWaterSunDir;
    private final int uWaterSunDiffuse;
    private final int uWaterAmbient;
    private final int uWaterFar;
    private final int uWaterScreen;
    private final Matrix4 origCombined = new Matrix4();
    private final Matrix4 origView = new Matrix4();
    private final Matrix4 reflectMat = new Matrix4();
    private final int reflectFbo;
    private final int reflectColor;
    private final int reflectDepth;
    private final Texture waterNm;
    private float waterTime;
    private final FloatBuffer matBuf = BufferUtils.newFloatBuffer(16);
    private final FloatBuffer pointPosBuf = BufferUtils.newFloatBuffer(MAX_LIGHTS * 3);
    private final FloatBuffer pointDiffBuf = BufferUtils.newFloatBuffer(MAX_LIGHTS * 3);
    private final FloatBuffer pointAttenBuf = BufferUtils.newFloatBuffer(MAX_LIGHTS * 4);
    private final int[] pickIdx = new int[MAX_LIGHTS];
    private final float[] pickD2 = new float[MAX_LIGHTS];
    private final Vector3 meshCenter = new Vector3();
    private final Matrix4 mvp = new Matrix4();

    public ForwardRenderer() {
        program = compile(VERT, FRAG);
        uMvp = Gdx.gl.glGetUniformLocation(program, "u_mvp");
        uModel = Gdx.gl.glGetUniformLocation(program, "u_model");
        uView = Gdx.gl.glGetUniformLocation(program, "u_view");
        uLightDir = Gdx.gl.glGetUniformLocation(program, "u_lightDir");
        uAmbientLight = Gdx.gl.glGetUniformLocation(program, "u_ambientLight");
        uSunDiffuse = Gdx.gl.glGetUniformLocation(program, "u_sunDiffuse");
        uFogEnabled = Gdx.gl.glGetUniformLocation(program, "u_fogEnabled");
        uFogStart = Gdx.gl.glGetUniformLocation(program, "u_fogStart");
        uFogScale = Gdx.gl.glGetUniformLocation(program, "u_fogScale");
        uFogColor = Gdx.gl.glGetUniformLocation(program, "u_fogColor");
        uPointCount = Gdx.gl.glGetUniformLocation(program, "u_pointCount");
        uPointPos = Gdx.gl.glGetUniformLocation(program, "u_pointPos");
        uPointDiffuse = Gdx.gl.glGetUniformLocation(program, "u_pointDiffuse");
        uPointAtten = Gdx.gl.glGetUniformLocation(program, "u_pointAtten");
        uBase = Gdx.gl.glGetUniformLocation(program, "u_base");
        uDark = Gdx.gl.glGetUniformLocation(program, "u_dark");
        uDetail = Gdx.gl.glGetUniformLocation(program, "u_detail");
        uGlow = Gdx.gl.glGetUniformLocation(program, "u_glow");
        uUseDark = Gdx.gl.glGetUniformLocation(program, "u_useDark");
        uUseDetail = Gdx.gl.glGetUniformLocation(program, "u_useDetail");
        uUseGlow = Gdx.gl.glGetUniformLocation(program, "u_useGlow");
        uAmbient = Gdx.gl.glGetUniformLocation(program, "u_ambient");
        uDiffuse = Gdx.gl.glGetUniformLocation(program, "u_diffuse");
        uEmissive = Gdx.gl.glGetUniformLocation(program, "u_emissive");
        uMatAlpha = Gdx.gl.glGetUniformLocation(program, "u_matAlpha");
        uColorMode = Gdx.gl.glGetUniformLocation(program, "u_colorMode");
        uAlphaTest = Gdx.gl.glGetUniformLocation(program, "u_alphaTest");
        uAlphaFunc = Gdx.gl.glGetUniformLocation(program, "u_alphaFunc");
        uAlphaRef = Gdx.gl.glGetUniformLocation(program, "u_alphaRef");
        uBlendMap = Gdx.gl.glGetUniformLocation(program, "u_blendMap");
        uUseBlendMap = Gdx.gl.glGetUniformLocation(program, "u_useBlendMap");
        uUvScale = Gdx.gl.glGetUniformLocation(program, "u_uvScale");
        uAdjustCoverage = Gdx.gl.glGetUniformLocation(program, "u_adjustCoverage");
        uCameraFar = Gdx.gl.glGetUniformLocation(program, "u_cameraFar");
        uDepthBias = Gdx.gl.glGetUniformLocation(program, "u_depthBias");
        uClipPlane = Gdx.gl.glGetUniformLocation(program, "u_clipPlane");
        waterProgram = compile(WATER_VERT, WATER_FRAG);
        uWaterMvp = Gdx.gl.glGetUniformLocation(waterProgram, "u_mvp");
        uWaterModel = Gdx.gl.glGetUniformLocation(waterProgram, "u_model");
        uWaterView = Gdx.gl.glGetUniformLocation(waterProgram, "u_view");
        uWaterNormal = Gdx.gl.glGetUniformLocation(waterProgram, "u_normalMap");
        uWaterReflection = Gdx.gl.glGetUniformLocation(waterProgram, "u_reflectionMap");
        uWaterTime = Gdx.gl.glGetUniformLocation(waterProgram, "u_time");
        uWaterCamTes = Gdx.gl.glGetUniformLocation(waterProgram, "u_cameraTes");
        uWaterSunDir = Gdx.gl.glGetUniformLocation(waterProgram, "u_sunDirTes");
        uWaterSunDiffuse = Gdx.gl.glGetUniformLocation(waterProgram, "u_sunDiffuse");
        uWaterAmbient = Gdx.gl.glGetUniformLocation(waterProgram, "u_ambientLight");
        uWaterFar = Gdx.gl.glGetUniformLocation(waterProgram, "u_cameraFar");
        uWaterScreen = Gdx.gl.glGetUniformLocation(waterProgram, "u_screenRes");
        Pixmap nmPix = new Pixmap(Gdx.files.internal("textures/omw/water_nm.png"));
        waterNm = new Texture(nmPix);
        nmPix.dispose();
        waterNm.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        waterNm.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        reflectColor = allocColor(WaterMesh.RTT_SIZE);
        reflectDepth = allocDepth(WaterMesh.RTT_SIZE);
        reflectFbo = allocFbo(reflectColor, reflectDepth);
        reflectMat.idt();
        reflectMat.val[5] = -1f;
        reflectMat.val[13] = 2f * WaterMesh.HEIGHT;
    }

    public void render(PerspectiveCamera cam, SceneNode root) {
        render(cam, root, null);
    }

    public void render(PerspectiveCamera cam, SceneNode root, CellLighting lighting) {
        waterTime += Gdx.graphics.getDeltaTime();
        boolean water = hasWater(root);
        if (water) {
            renderReflection(cam, root, lighting);
        }
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glDisable(GL_CLIP_DISTANCE0);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glUseProgram(program);
        upload(uView, cam.view);
        Gdx.gl.glUniform4f(uClipPlane, 0f, 0f, 0f, 1f);
        bindLighting(lighting);
        Gdx.gl.glUniform1i(uBase, 0);
        Gdx.gl.glUniform1i(uDark, 1);
        Gdx.gl.glUniform1i(uDetail, 2);
        Gdx.gl.glUniform1i(uGlow, 3);
        Gdx.gl.glUniform1i(uBlendMap, 4);
        Gdx.gl.glUniform1f(uCameraFar, cam.far);
        drawNode(cam, root, 1, lighting, false);
        drawNode(cam, root, 0, lighting, false);
        drawNode(cam, root, 2, lighting, false);
        if (water) {
            drawWater(cam, root, lighting);
        }
        Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
        Gdx.gl.glUseProgram(0);
        Gdx.gl30.glBindVertexArray(0);
    }

    private void bindLighting(CellLighting lighting) {
        if (lighting == null) {
            Gdx.gl.glUniform3f(uLightDir, 0.35f, 0.8f, 0.45f);
            Gdx.gl.glUniform3f(uAmbientLight, 0.35f, 0.35f, 0.35f);
            Gdx.gl.glUniform3f(uSunDiffuse, 1f, 1f, 1f);
            Gdx.gl.glUniform1i(uPointCount, 0);
            Gdx.gl.glUniform1i(uFogEnabled, 0);
        } else {
            Gdx.gl.glUniform3f(uLightDir, lighting.sunDir[0], lighting.sunDir[1], lighting.sunDir[2]);
            Gdx.gl.glUniform3f(uAmbientLight, lighting.ambient[0], lighting.ambient[1], lighting.ambient[2]);
            Gdx.gl.glUniform3f(uSunDiffuse, lighting.sunDiffuse[0], lighting.sunDiffuse[1], lighting.sunDiffuse[2]);
            Gdx.gl.glUniform1i(uFogEnabled, lighting.fogEnabled ? 1 : 0);
            Gdx.gl.glUniform1f(uFogStart, lighting.fogStart);
            Gdx.gl.glUniform1f(uFogScale, lighting.fogScale);
            Gdx.gl.glUniform3f(uFogColor, lighting.fogColor[0], lighting.fogColor[1], lighting.fogColor[2]);
        }
    }

    private void renderReflection(PerspectiveCamera cam, SceneNode root, CellLighting lighting) {
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, reflectFbo);
        Gdx.gl.glViewport(0, 0, WaterMesh.RTT_SIZE, WaterMesh.RTT_SIZE);
        if (lighting != null) {
            Gdx.gl.glClearColor(lighting.fogColor[0], lighting.fogColor[1], lighting.fogColor[2], 1f);
        } else {
            Gdx.gl.glClearColor(0.08f, 0.09f, 0.12f, 1f);
        }
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL_CLIP_DISTANCE0);
        origCombined.set(cam.combined);
        origView.set(cam.view);
        cam.combined.mul(reflectMat);
        cam.view.mul(reflectMat);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glUseProgram(program);
        upload(uView, cam.view);
        Gdx.gl.glUniform4f(uClipPlane, 0f, 1f, 0f, -WaterMesh.HEIGHT);
        bindLighting(lighting);
        Gdx.gl.glUniform1i(uBase, 0);
        Gdx.gl.glUniform1i(uDark, 1);
        Gdx.gl.glUniform1i(uDetail, 2);
        Gdx.gl.glUniform1i(uGlow, 3);
        Gdx.gl.glUniform1i(uBlendMap, 4);
        Gdx.gl.glUniform1f(uCameraFar, cam.far);
        drawNode(cam, root, 1, lighting, true);
        drawNode(cam, root, 0, lighting, true);
        cam.combined.set(origCombined);
        cam.view.set(origView);
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        Gdx.gl.glDisable(GL_CLIP_DISTANCE0);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
    }

    private void drawNode(PerspectiveCamera cam, SceneNode node, int pass, CellLighting lighting, boolean reflection) {
        if (!node.skipMeshes) {
            for (MeshInstance inst : node.meshes) {
                MeshGpu mesh = inst.mesh;
                if (mesh.waterShader) {
                    continue;
                }
                int meshPass = mesh.terrainPass ? 1 : mesh.alphaBlend ? 2 : 0;
                if (meshPass != pass) {
                    continue;
                }
                if (lighting != null) {
                    bindClosestLights(node, mesh, lighting.lights);
                }
                mvp.set(cam.combined).mul(node.world);
                upload(uMvp, mvp);
                upload(uModel, node.world);
                bindUnit(GL20.GL_TEXTURE0, mesh.baseTex, mesh.baseWrapS, mesh.baseWrapT);
                bindUnit(GL20.GL_TEXTURE1, mesh.darkTex, mesh.darkWrapS, mesh.darkWrapT);
                bindUnit(GL20.GL_TEXTURE2, mesh.detailTex, mesh.detailWrapS, mesh.detailWrapT);
                bindUnit(GL20.GL_TEXTURE3, mesh.glowTex, mesh.glowWrapS, mesh.glowWrapT);
                if (mesh.useBlendMap) {
                    bindUnit(GL20.GL_TEXTURE4, mesh.blendMapTex, mesh.blendMapWrapS, mesh.blendMapWrapT);
                }
                Gdx.gl.glUniform1i(uUseDark, mesh.useDark ? 1 : 0);
                Gdx.gl.glUniform1i(uUseDetail, mesh.useDetail ? 1 : 0);
                Gdx.gl.glUniform1i(uUseGlow, mesh.useGlow ? 1 : 0);
                Gdx.gl.glUniform1i(uUseBlendMap, mesh.useBlendMap ? 1 : 0);
                Gdx.gl.glUniform1f(uUvScale, mesh.uvScale);
                Gdx.gl.glUniform3f(uAmbient, mesh.ambient[0], mesh.ambient[1], mesh.ambient[2]);
                Gdx.gl.glUniform3f(uDiffuse, mesh.diffuse[0], mesh.diffuse[1], mesh.diffuse[2]);
                Gdx.gl.glUniform3f(uEmissive, mesh.emissive[0], mesh.emissive[1], mesh.emissive[2]);
                Gdx.gl.glUniform1f(uMatAlpha, mesh.matAlpha);
                Gdx.gl.glUniform1i(uColorMode, mesh.colorMode);
                Gdx.gl.glUniform1i(uAlphaTest, mesh.alphaTest ? 1 : 0);
                Gdx.gl.glUniform1i(uAlphaFunc, mesh.alphaFunc);
                Gdx.gl.glUniform1f(uAlphaRef, mesh.alphaRef);
                Gdx.gl.glUniform1i(uAdjustCoverage, mesh.alphaTest && !mesh.alphaBlend ? 1 : 0);
                Gdx.gl.glUniform1f(uDepthBias, mesh.terrainPass ? 4e-5f : 0f);
                if (mesh.cull) {
                    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
                } else {
                    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
                }
                boolean cw = inst.frontClockwise;
                if (reflection) {
                    cw = !cw;
                }
                Gdx.gl.glFrontFace(cw ? GL20.GL_CW : GL20.GL_CCW);
                if (mesh.depthTest || mesh.depthWrite) {
                    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                    Gdx.gl.glDepthFunc(mesh.depthTest ? mesh.depthFunc : GL20.GL_ALWAYS);
                } else {
                    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
                }
                if (mesh.alphaBlend) {
                    Gdx.gl.glEnable(GL20.GL_BLEND);
                    Gdx.gl.glBlendFunc(mesh.blendSrc, mesh.blendDst);
                    Gdx.gl.glDepthMask(mesh.terrainPass ? mesh.depthWrite : mesh.noSorter && mesh.depthWrite);
                } else {
                    Gdx.gl.glDisable(GL20.GL_BLEND);
                    Gdx.gl.glDepthMask(mesh.depthWrite);
                }
                if (mesh.terrainPass) {
                    Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL);
                    Gdx.gl.glPolygonOffset(1f, 1f);
                } else {
                    Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);
                }
                Gdx.gl30.glBindVertexArray(mesh.vao);
                Gdx.gl.glDrawElements(GL20.GL_TRIANGLES, mesh.indexCount, GL20.GL_UNSIGNED_SHORT, 0);
            }
        }
        for (SceneNode child : node.children) {
            drawNode(cam, child, pass, lighting, reflection);
        }
    }

    private void drawWater(PerspectiveCamera cam, SceneNode root, CellLighting lighting) {
        Gdx.gl.glUseProgram(waterProgram);
        upload(uWaterView, cam.view);
        Gdx.gl.glUniform1i(uWaterNormal, 0);
        Gdx.gl.glUniform1i(uWaterReflection, 1);
        Gdx.gl.glUniform1f(uWaterTime, waterTime);
        Gdx.gl.glUniform3f(uWaterCamTes, cam.position.x, -cam.position.z, cam.position.y);
        if (lighting == null) {
            Gdx.gl.glUniform3f(uWaterSunDir, 0.35f, -0.45f, 0.8f);
            Gdx.gl.glUniform3f(uWaterSunDiffuse, 1f, 1f, 1f);
            Gdx.gl.glUniform3f(uWaterAmbient, 0.35f, 0.35f, 0.35f);
        } else {
            Gdx.gl.glUniform3f(uWaterSunDir, lighting.sunDir[0], -lighting.sunDir[2], lighting.sunDir[1]);
            Gdx.gl.glUniform3f(uWaterSunDiffuse, lighting.sunDiffuse[0], lighting.sunDiffuse[1], lighting.sunDiffuse[2]);
            Gdx.gl.glUniform3f(uWaterAmbient, lighting.ambient[0], lighting.ambient[1], lighting.ambient[2]);
        }
        Gdx.gl.glUniform1f(uWaterFar, cam.far);
        Gdx.gl.glUniform2f(uWaterScreen, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, waterNm.getTextureObjectHandle());
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_REPEAT);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_REPEAT);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, reflectColor);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
        drawWaterNode(cam, root);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glDepthMask(true);
    }

    private void drawWaterNode(PerspectiveCamera cam, SceneNode node) {
        if (!node.skipMeshes) {
            for (MeshInstance inst : node.meshes) {
                MeshGpu mesh = inst.mesh;
                if (!mesh.waterShader) {
                    continue;
                }
                mvp.set(cam.combined).mul(node.world);
                upload(uWaterMvp, mvp);
                upload(uWaterModel, node.world);
                Gdx.gl30.glBindVertexArray(mesh.vao);
                Gdx.gl.glDrawElements(GL20.GL_TRIANGLES, mesh.indexCount, GL20.GL_UNSIGNED_SHORT, 0);
            }
        }
        for (SceneNode child : node.children) {
            drawWaterNode(cam, child);
        }
    }

    private static boolean hasWater(SceneNode node) {
        if (!node.skipMeshes) {
            for (MeshInstance inst : node.meshes) {
                if (inst.mesh.waterShader) {
                    return true;
                }
            }
        }
        for (SceneNode child : node.children) {
            if (hasWater(child)) {
                return true;
            }
        }
        return false;
    }

    private void bindClosestLights(SceneNode node, MeshGpu mesh, List<CellLight> lights) {
        if (mesh.localMin[0] > mesh.localMax[0]) {
            node.world.getTranslation(meshCenter);
        } else {
            meshCenter.set(
                (mesh.localMin[0] + mesh.localMax[0]) * 0.5f,
                (mesh.localMin[1] + mesh.localMax[1]) * 0.5f,
                (mesh.localMin[2] + mesh.localMax[2]) * 0.5f);
            meshCenter.mul(node.world);
        }
        Arrays.fill(pickD2, Float.POSITIVE_INFINITY);
        Arrays.fill(pickIdx, -1);
        for (int i = 0; i < lights.size(); i++) {
            CellLight light = lights.get(i);
            float dx = light.pos[0] - meshCenter.x;
            float dy = light.pos[1] - meshCenter.y;
            float dz = light.pos[2] - meshCenter.z;
            float d2 = dx * dx + dy * dy + dz * dz;
            int worst = 0;
            for (int s = 1; s < MAX_LIGHTS; s++) {
                if (pickD2[s] > pickD2[worst]) {
                    worst = s;
                }
            }
            if (d2 < pickD2[worst]) {
                pickD2[worst] = d2;
                pickIdx[worst] = i;
            }
        }
        pointPosBuf.clear();
        pointDiffBuf.clear();
        pointAttenBuf.clear();
        int count = 0;
        for (int s = 0; s < MAX_LIGHTS; s++) {
            if (pickIdx[s] < 0) {
                continue;
            }
            CellLight light = lights.get(pickIdx[s]);
            pointPosBuf.put(light.pos);
            pointDiffBuf.put(light.diffuse);
            pointAttenBuf.put(light.constant).put(light.linear).put(light.quadratic).put(light.radius);
            count++;
        }
        Gdx.gl.glUniform1i(uPointCount, count);
        if (count > 0) {
            pointPosBuf.flip();
            pointDiffBuf.flip();
            pointAttenBuf.flip();
            Gdx.gl.glUniform3fv(uPointPos, count, pointPosBuf);
            Gdx.gl.glUniform3fv(uPointDiffuse, count, pointDiffBuf);
            Gdx.gl.glUniform4fv(uPointAtten, count, pointAttenBuf);
        }
    }

    private static void bindUnit(int unit, int tex, int wrapS, int wrapT) {
        Gdx.gl.glActiveTexture(unit);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, tex);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, wrapS);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, wrapT);
    }

    private void upload(int loc, Matrix4 m) {
        matBuf.clear();
        matBuf.put(m.val);
        matBuf.flip();
        Gdx.gl.glUniformMatrix4fv(loc, 1, false, matBuf);
    }

    public void dispose() {
        Gdx.gl.glDeleteProgram(program);
        Gdx.gl.glDeleteProgram(waterProgram);
        if (waterNm != null) {
            waterNm.dispose();
        }
        Gdx.gl.glDeleteTexture(reflectColor);
        IntBuffer ids = BufferUtils.newIntBuffer(1);
        ids.put(0, reflectDepth);
        Gdx.gl30.glDeleteRenderbuffers(1, ids);
        ids.put(0, reflectFbo);
        Gdx.gl30.glDeleteFramebuffers(1, ids);
    }

    public static void resetForScene2d() {
        Gdx.gl.glDisable(GL_CLIP_DISTANCE0);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
        Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (Gdx.gl30 != null) {
            Gdx.gl30.glBindVertexArray(0);
        }
    }

    private static int allocColor(int size) {
        int tex = Gdx.gl.glGenTexture();
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, tex);
        Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, size, size, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE,
            null);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int allocDepth(int size) {
        IntBuffer ids = BufferUtils.newIntBuffer(1);
        Gdx.gl30.glGenRenderbuffers(1, ids);
        int rb = ids.get(0);
        Gdx.gl30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rb);
        Gdx.gl30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, size, size);
        Gdx.gl30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        return rb;
    }

    private static int allocFbo(int color, int depth) {
        IntBuffer ids = BufferUtils.newIntBuffer(1);
        Gdx.gl30.glGenFramebuffers(1, ids);
        int fbo = ids.get(0);
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        Gdx.gl30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, color, 0);
        Gdx.gl30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depth);
        int status = Gdx.gl30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("water reflection FBO: " + status);
        }
        return fbo;
    }

    private static int compile(String vert, String frag) {
        int vs = Gdx.gl.glCreateShader(GL20.GL_VERTEX_SHADER);
        Gdx.gl.glShaderSource(vs, vert);
        Gdx.gl.glCompileShader(vs);
        checkShader(vs, "vertex");
        int fs = Gdx.gl.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        Gdx.gl.glShaderSource(fs, frag);
        Gdx.gl.glCompileShader(fs);
        checkShader(fs, "fragment");
        int p = Gdx.gl.glCreateProgram();
        Gdx.gl.glAttachShader(p, vs);
        Gdx.gl.glAttachShader(p, fs);
        Gdx.gl.glLinkProgram(p);
        checkProgram(p);
        Gdx.gl.glDeleteShader(vs);
        Gdx.gl.glDeleteShader(fs);
        return p;
    }

    private static void checkShader(int sh, String kind) {
        IntBuffer status = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetShaderiv(sh, GL20.GL_COMPILE_STATUS, status);
        if (status.get(0) == GL20.GL_FALSE) {
            throw new IllegalStateException(kind + " shader: " + Gdx.gl.glGetShaderInfoLog(sh));
        }
    }

    private static void checkProgram(int p) {
        IntBuffer status = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetProgramiv(p, GL20.GL_LINK_STATUS, status);
        if (status.get(0) == GL20.GL_FALSE) {
            throw new IllegalStateException("program: " + Gdx.gl.glGetProgramInfoLog(p));
        }
    }

    private static final String VERT = """
        #version 330
        layout(location = 0) in vec3 a_pos;
        layout(location = 1) in vec3 a_normal;
        layout(location = 2) in vec2 a_uv;
        layout(location = 3) in vec4 a_color;
        uniform mat4 u_mvp;
        uniform mat4 u_model;
        uniform mat4 u_view;
        uniform vec4 u_clipPlane;
        out vec3 v_normal;
        out vec3 v_worldPos;
        out float v_viewZ;
        out vec2 v_uv;
        out vec4 v_color;
        void main() {
            v_uv = a_uv;
            v_color = a_color;
            vec4 world = u_model * vec4(a_pos, 1.0);
            v_worldPos = world.xyz;
            v_viewZ = (u_view * world).z;
            v_normal = mat3(u_model) * a_normal;
            gl_Position = u_mvp * vec4(a_pos, 1.0);
            gl_ClipDistance[0] = length(u_clipPlane.xyz) < 1e-6 ? 1.0 : dot(vec4(world.xyz, 1.0), u_clipPlane);
        }
        """;

    private static final String FRAG = """
        #version 330
        in vec3 v_normal;
        in vec3 v_worldPos;
        in float v_viewZ;
        in vec2 v_uv;
        in vec4 v_color;
        uniform sampler2D u_base;
        uniform sampler2D u_dark;
        uniform sampler2D u_detail;
        uniform sampler2D u_glow;
        uniform int u_useDark;
        uniform int u_useDetail;
        uniform int u_useGlow;
        uniform vec3 u_lightDir;
        uniform vec3 u_ambientLight;
        uniform vec3 u_sunDiffuse;
        uniform int u_pointCount;
        uniform vec3 u_pointPos[8];
        uniform vec3 u_pointDiffuse[8];
        uniform vec4 u_pointAtten[8];
        uniform int u_fogEnabled;
        uniform float u_fogStart;
        uniform float u_fogScale;
        uniform vec3 u_fogColor;
        uniform vec3 u_ambient;
        uniform vec3 u_diffuse;
        uniform vec3 u_emissive;
        uniform float u_matAlpha;
        uniform int u_colorMode;
        uniform int u_alphaTest;
        uniform int u_alphaFunc;
        uniform float u_alphaRef;
        uniform sampler2D u_blendMap;
        uniform int u_useBlendMap;
        uniform float u_uvScale;
        uniform int u_adjustCoverage;
        uniform float u_cameraFar;
        uniform float u_depthBias;
        out vec4 frag;
        float quickstep(float x) {
            x = clamp(x, 0.0, 1.0);
            x = 1.0 - x * x;
            x = 1.0 - x * x;
            return x;
        }
        bool alphaPass(float a, float r, int f) {
            if (f == 0) return true;
            if (f == 1) return a < r;
            if (f == 2) return abs(a - r) < 1e-5;
            if (f == 3) return a <= r;
            if (f == 4) return a > r;
            if (f == 5) return abs(a - r) >= 1e-5;
            if (f == 6) return a >= r;
            if (f == 7) return false;
            return a <= r;
        }
        float mipmapLevel(vec2 scaleduv) {
            vec2 dUVdx = dFdx(scaleduv);
            vec2 dUVdy = dFdy(scaleduv);
            float maxDUVSquared = max(dot(dUVdx, dUVdx), dot(dUVdy, dUVdy));
            return max(0.0, 0.5 * log2(maxDUVSquared));
        }
        void main() {
            vec2 baseUV = v_uv * u_uvScale;
            vec4 tex = texture(u_base, baseUV);
            vec3 amb = u_ambient;
            vec3 diff = u_diffuse;
            vec3 emi = u_emissive;
            float matA = u_matAlpha;
            if (u_colorMode == 1) {
                emi = v_color.rgb;
            } else if (u_colorMode == 2) {
                amb = v_color.rgb;
                diff = v_color.rgb;
                matA = v_color.a;
            }
            tex.a *= matA;
            if (u_useBlendMap != 0) {
                vec2 buv = (v_uv - vec2(0.5)) * (16.0 / 17.0) + vec2(0.5);
                buv += vec2(1.0 / 16.0 / 4.0, -1.0 / 16.0 / 4.0);
                tex.a *= texture(u_blendMap, buv).r;
            }
            if (u_useDark != 0) {
                tex *= texture(u_dark, baseUV);
            }
            if (u_adjustCoverage != 0) {
                vec2 ts = vec2(textureSize(u_base, 0));
                tex.a *= 1.0 + mipmapLevel(baseUV * ts) * 0.25;
            }
            if (u_alphaTest != 0 && !alphaPass(tex.a, u_alphaRef, u_alphaFunc)) discard;
            gl_FragDepth = clamp(
                log2(max(1e-6, 1.0 + abs(v_viewZ))) / log2(max(u_cameraFar, 1.0) + 1.0) + u_depthBias,
                0.0, 1.0);
            if (u_useDetail != 0) {
                tex.rgb *= texture(u_detail, baseUV).rgb * 2.0;
            }
            vec3 n = normalize(v_normal);
            vec3 sunDir = normalize(u_lightDir);
            float ndl = max(dot(n, sunDir), 0.0);
            vec3 lighting = amb * u_ambientLight + diff * ndl * u_sunDiffuse + emi;
            for (int i = 0; i < u_pointCount; ++i) {
                vec3 lightPos = u_pointPos[i] - v_worldPos;
                float dist = length(lightPos);
                float radius = u_pointAtten[i].w;
                if (dist > radius * 2.0) {
                    continue;
                }
                vec3 lightDir = lightPos / max(dist, 1e-5);
                float illumination = 1.0 / (u_pointAtten[i].x + u_pointAtten[i].y * dist
                    + u_pointAtten[i].z * dist * dist);
                illumination *= 1.0 - quickstep(dist / radius - 1.0);
                lighting += diff * max(dot(n, lightDir), 0.0) * u_pointDiffuse[i] * illumination;
            }
            lighting = max(lighting, vec3(0.0));
            tex.rgb *= lighting;
            if (u_useGlow != 0) {
                tex.rgb += texture(u_glow, baseUV).rgb;
            }
            if (u_fogEnabled != 0) {
                float fogValue = clamp((abs(v_viewZ) - u_fogStart) * u_fogScale, 0.0, 1.0);
                tex.rgb = mix(tex.rgb, u_fogColor, fogValue);
            }
            frag = tex;
        }
        """;

    private static final String WATER_VERT = """
        #version 330
        layout(location = 0) in vec3 a_pos;
        layout(location = 1) in vec3 a_normal;
        layout(location = 2) in vec2 a_uv;
        layout(location = 3) in vec4 a_color;
        uniform mat4 u_mvp;
        uniform mat4 u_model;
        uniform mat4 u_view;
        out vec3 v_tesPos;
        out float v_viewZ;
        void main() {
            vec4 world = u_model * vec4(a_pos, 1.0);
            v_tesPos = vec3(world.x, -world.z, world.y);
            v_viewZ = (u_view * world).z;
            gl_Position = u_mvp * vec4(a_pos, 1.0);
        }
        """;

    private static final String WATER_FRAG = """
        #version 330
        in vec3 v_tesPos;
        in float v_viewZ;
        uniform sampler2D u_normalMap;
        uniform sampler2D u_reflectionMap;
        uniform float u_time;
        uniform vec3 u_cameraTes;
        uniform vec3 u_sunDirTes;
        uniform vec3 u_sunDiffuse;
        uniform vec3 u_ambientLight;
        uniform float u_cameraFar;
        uniform vec2 u_screenRes;
        out vec4 frag;

        const vec2 BIG_WAVES = vec2(0.1, 0.1);
        const vec2 MID_WAVES = vec2(0.1, 0.1);
        const vec2 SMALL_WAVES = vec2(0.1, 0.1);
        const float WAVE_CHOPPYNESS = 0.05;
        const float WAVE_SCALE = 75.0;
        const float BUMP = 0.5;
        const float REFL_BUMP = 0.10;
        const float SPEC_HARDNESS = 256.0;
        const float SPEC_BUMPINESS = 5.0;
        const float SPEC_BRIGHTNESS = 1.5;
        const vec2 WIND_DIR = vec2(0.5, -0.8);
        const float WIND_SPEED = 0.2;
        const vec3 WATER_COLOR = vec3(0.090195, 0.115685, 0.12745);

        vec2 normalCoords(vec2 uv, float scale, float speed, float time, float timer1, float timer2, vec3 previousNormal) {
            return uv * (WAVE_SCALE * scale) + WIND_DIR * time * (WIND_SPEED * speed)
                - (previousNormal.xy / previousNormal.zz) * WAVE_CHOPPYNESS + vec2(time * timer1, time * timer2);
        }

        float fresnel_dielectric(vec3 incoming, vec3 normal, float eta) {
            float c = abs(dot(incoming, normal));
            float g = eta * eta - 1.0 + c * c;
            if (g > 0.0) {
                g = sqrt(g);
                float A = (g - c) / (g + c);
                float B = (c * (g + c) - 1.0) / (c * (g - c) + 1.0);
                return 0.5 * A * A * (1.0 + B * B);
            }
            return 1.0;
        }

        void main() {
            vec2 UV = v_tesPos.xy / (8192.0 * 5.0) * 3.0;
            vec2 screenCoords = gl_FragCoord.xy / u_screenRes;
            float waterTimer = u_time;
            vec3 normal0 = 2.0 * texture(u_normalMap, normalCoords(UV, 0.05, 0.04, waterTimer, -0.015, -0.005, vec3(0.0, 0.0, 0.0))).rgb - 1.0;
            vec3 normal1 = 2.0 * texture(u_normalMap, normalCoords(UV, 0.1, 0.08, waterTimer, 0.02, 0.015, normal0)).rgb - 1.0;
            vec3 normal2 = 2.0 * texture(u_normalMap, normalCoords(UV, 0.25, 0.07, waterTimer, -0.04, -0.03, normal1)).rgb - 1.0;
            vec3 normal3 = 2.0 * texture(u_normalMap, normalCoords(UV, 0.5, 0.09, waterTimer, 0.03, 0.04, normal2)).rgb - 1.0;
            vec3 normal4 = 2.0 * texture(u_normalMap, normalCoords(UV, 1.0, 0.4, waterTimer, -0.02, 0.1, normal3)).rgb - 1.0;
            vec3 normal5 = 2.0 * texture(u_normalMap, normalCoords(UV, 2.0, 0.7, waterTimer, 0.1, -0.06, normal4)).rgb - 1.0;
            vec3 normal = (normal0 * BIG_WAVES.x + normal1 * BIG_WAVES.y + normal2 * MID_WAVES.x
                + normal3 * MID_WAVES.y + normal4 * SMALL_WAVES.x + normal5 * SMALL_WAVES.y);
            normal = normalize(vec3(-normal.x * BUMP, -normal.y * BUMP, normal.z));
            vec3 sunWorldDir = normalize(u_sunDirTes);
            vec3 cameraPos = u_cameraTes;
            vec3 viewDir = normalize(v_tesPos - cameraPos);
            float sunFade = length(u_ambientLight);
            float ior = (cameraPos.z > 0.0) ? (1.333 / 1.0) : (1.0 / 1.333);
            float fresnel = clamp(fresnel_dielectric(viewDir, normal, ior), 0.0, 1.0);
            vec2 screenCoordsOffset = normal.xy * REFL_BUMP;
            vec3 reflection = texture(u_reflectionMap, screenCoords + screenCoordsOffset).rgb;
            vec3 waterColor = WATER_COLOR * sunFade;
            const float SPEC_MAGIC = 1.55;
            vec3 specNormal = normalize(vec3(normal.x * SPEC_BUMPINESS, normal.y * SPEC_BUMPINESS, normal.z));
            vec3 viewReflectDir = reflect(viewDir, specNormal);
            float phongTerm = max(dot(viewReflectDir, sunWorldDir), 0.0);
            float specular = pow(atan(phongTerm * SPEC_MAGIC), SPEC_HARDNESS) * SPEC_BRIGHTNESS;
            specular = clamp(specular, 0.0, 1.0);
            float waterTransparency = clamp(fresnel * 6.0 + specular, 0.0, 1.0);
            vec3 rgb = mix(waterColor, reflection, (1.0 + fresnel) * 0.5);
            rgb += specular * u_sunDiffuse;
            frag = vec4(rgb, waterTransparency);
            gl_FragDepth = clamp(
                log2(max(1e-6, 1.0 + abs(v_viewZ))) / log2(max(u_cameraFar, 1.0) + 1.0),
                0.0, 1.0);
        }
        """;
}
