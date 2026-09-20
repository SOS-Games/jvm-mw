package io.github.jvmmw.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.BufferUtils;

import io.github.jvmmw.debug.FrameProfiler;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Draws one frame of the cell. Morrowind files are Z-up; the cell root
 * rotates −90° so the GPU sees Y-up. Water at −1 and clip planes live in
 * that Y-up world.
 *
 * If the cell has water, each frame first draws a 512 map of what’s under
 * the surface, then a 512 map of what’s mirrored (no people). Then sky,
 * land, solid meshes, the water plane (sampling those two maps), then
 * leaves and glass. Interiors skip the water cameras and usually skip sky.
 *
 * Meshes off-camera, smaller than 2 pixels, or small and farther than 7168
 * are skipped. Sky and the water plane always draw. Never ModelBatch.
 */
public final class ForwardRenderer {
    /** Morrowind/OpenMW cap: eight dynamic lights per drawable. */
    public static final int MAX_LIGHTS = 8;
    /** OpenMW {@code [Camera] small feature culling pixel size} on the main view. */
    private static final float CAMERA_FEATURE_PIXELS = 2f;
    private static final float VIEW_DISTANCE_SQ
        = CellLighting.VIEW_DISTANCE * CellLighting.VIEW_DISTANCE;
    /** Longest AABB axis that still draws past 7168 (trees, shacks). */
    private static final float LANDMARK_EXTENT = 128f;
    private static final int GL_CLIP_DISTANCE0 = 0x3000;
    private static final int GL_TEXTURE_COMPARE_MODE = 0x884C;

    /** World-mesh program: land, statics, NPCs. */
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
    /** Harbor shader: waves plus the two 512 maps. */
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
    private final int uWaterSunVis;
    private final int uWaterAmbient;
    private final int uWaterFar;
    private final int uWaterScreen;
    private final int uWaterRefraction;
    private final int uWaterRefractionDepth;
    private final int uWaterFogEnabled;
    private final int uWaterFogStart;
    private final int uWaterFogScale;
    private final int uWaterFogColor;
    /** Atmosphere, clouds, sun disc, stars. Follows the camera, never the cell origin. */
    private final int skyProgram;
    private final int uSkyMvp;
    private final int uSkyModel;
    private final int uSkyView;
    private final int uSkyFar;
    private final int uSkyEmission;
    private final int uSkyPass;
    private final int uSkyDiffuse;
    private final int uSkyOpacity;
    private final int uSkyFog;
    private final int uSkyScroll;
    private SkyAtmosphere sky;
    private SkySun sun;
    private SkyClouds clouds;
    private SkyStars stars;
    /** Clear-day hour: sky color, sun position, land lighting. HUD slider drives this. */
    public final ClearCycle cycle = new ClearCycle();
    public FrameProfiler profiler;
    private final Matrix4 skyView = new Matrix4();
    private final Matrix4 skyCombined = new Matrix4();
    private final Matrix4 origCombined = new Matrix4();
    private final Matrix4 origView = new Matrix4();
    /** Flip Y through the water: y' = 2h − y. Multiplied onto view; the camera itself does not move. */
    private final Matrix4 reflectMat = new Matrix4();
    private final Matrix4 frustumInv = new Matrix4();
    private final BoundingBox cullBox = new BoundingBox();
    private final Vector3 aabbMin = new Vector3();
    private final Vector3 aabbMax = new Vector3();
    /** True while filling a water map so tiny-mesh cull uses 20 px on 512, not 2 px on the window. */
    private boolean waterRtt;
    private final int reflectFbo;
    private final int reflectColor;
    private final int reflectDepth;
    private final int refractFbo;
    private final int refractColor;
    private final int refractDepth;
    private final Texture waterNm;
    private float waterTime;
    private final float[] uwFog = new float[3];
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
        uWaterSunVis = Gdx.gl.glGetUniformLocation(waterProgram, "u_sunVis");
        uWaterAmbient = Gdx.gl.glGetUniformLocation(waterProgram, "u_ambientLight");
        uWaterFar = Gdx.gl.glGetUniformLocation(waterProgram, "u_cameraFar");
        uWaterScreen = Gdx.gl.glGetUniformLocation(waterProgram, "u_screenRes");
        uWaterRefraction = Gdx.gl.glGetUniformLocation(waterProgram, "u_refractionMap");
        uWaterRefractionDepth = Gdx.gl.glGetUniformLocation(waterProgram, "u_refractionDepthMap");
        uWaterFogEnabled = Gdx.gl.glGetUniformLocation(waterProgram, "u_fogEnabled");
        uWaterFogStart = Gdx.gl.glGetUniformLocation(waterProgram, "u_fogStart");
        uWaterFogScale = Gdx.gl.glGetUniformLocation(waterProgram, "u_fogScale");
        uWaterFogColor = Gdx.gl.glGetUniformLocation(waterProgram, "u_fogColor");
        Pixmap nmPix = new Pixmap(Gdx.files.internal("textures/omw/water_nm.png"));
        waterNm = new Texture(nmPix);
        nmPix.dispose();
        waterNm.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        waterNm.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        // Two 512 off-screen cameras. Refraction stores depth as a texture so the
        // water shader can measure how deep the seafloor is under each pixel.
        reflectColor = allocColor(WaterMesh.RTT_SIZE);
        reflectDepth = allocDepth(WaterMesh.RTT_SIZE);
        reflectFbo = allocFbo(reflectColor, reflectDepth, false);
        refractColor = allocColor(WaterMesh.RTT_SIZE);
        refractDepth = allocDepthTex(WaterMesh.RTT_SIZE);
        refractFbo = allocFbo(refractColor, refractDepth, true);
        // Column-major: scale Y by −1, translate Y by 2 * waterHeight.
        reflectMat.idt();
        reflectMat.val[5] = -1f;
        reflectMat.val[13] = 2f * WaterMesh.HEIGHT;
        skyProgram = compile(SKY_VERT, SKY_FRAG);
        uSkyMvp = Gdx.gl.glGetUniformLocation(skyProgram, "u_mvp");
        uSkyModel = Gdx.gl.glGetUniformLocation(skyProgram, "u_model");
        uSkyView = Gdx.gl.glGetUniformLocation(skyProgram, "u_view");
        uSkyFar = Gdx.gl.glGetUniformLocation(skyProgram, "u_cameraFar");
        uSkyEmission = Gdx.gl.glGetUniformLocation(skyProgram, "u_emission");
        uSkyPass = Gdx.gl.glGetUniformLocation(skyProgram, "u_pass");
        uSkyDiffuse = Gdx.gl.glGetUniformLocation(skyProgram, "u_diffuse");
        uSkyOpacity = Gdx.gl.glGetUniformLocation(skyProgram, "u_opacity");
        uSkyFog = Gdx.gl.glGetUniformLocation(skyProgram, "u_fogColor");
        uSkyScroll = Gdx.gl.glGetUniformLocation(skyProgram, "u_scroll");
        try {
            sky = new SkyAtmosphere();
            sky.load();
        } catch (Exception e) {
            Gdx.app.error("ForwardRenderer", "sky atmosphere", e);
            if (sky != null) {
                sky.dispose();
                sky = null;
            }
        }
        try {
            clouds = new SkyClouds();
            clouds.load();
        } catch (Exception e) {
            Gdx.app.error("ForwardRenderer", "sky clouds", e);
            if (clouds != null) {
                clouds.dispose();
                clouds = null;
            }
        }
        try {
            sun = new SkySun();
            sun.load();
        } catch (Exception e) {
            Gdx.app.error("ForwardRenderer", "sky sun", e);
            if (sun != null) {
                sun.dispose();
                sun = null;
            }
        }
        try {
            stars = new SkyStars();
            stars.load();
        } catch (Exception e) {
            Gdx.app.error("ForwardRenderer", "sky stars", e);
            if (stars != null) {
                stars.dispose();
                stars = null;
            }
        }
    }

    public void render(PerspectiveCamera cam, SceneNode root) {
        render(cam, root, null);
    }

    /** One frame: optional water maps, then sky / land / opaque / water / alpha. */
    public void render(PerspectiveCamera cam, SceneNode root, CellLighting lighting) {
        waterTime += Gdx.graphics.getDeltaTime();
        cycle.evaluate();
        if (lighting != null && lighting.exterior) {
            cycle.applyLighting(lighting);
        }
        if (sun != null) {
            sun.placeTes(cycle.sunPosTes[0], cycle.sunPosTes[1], cycle.sunPosTes[2]);
        }
        if (clouds != null) {
            clouds.setFromHour(cycle.hour);
        }
        boolean water = hasWater(root);
        // GL Y is up; water plane sits at y = −1.
        boolean underwater = water && cam.position.y < WaterMesh.HEIGHT;
        if (water) {
            waterRtt = true;
            if (profiler != null) {
                profiler.setRtt(true);
                profiler.begin(FrameProfiler.RTT_REFRACT);
            }
            renderRefraction(cam, root, lighting);
            if (profiler != null) {
                profiler.end(FrameProfiler.RTT_REFRACT);
                profiler.begin(FrameProfiler.RTT_REFLECT);
            }
            renderReflection(cam, root, lighting);
            if (profiler != null) {
                profiler.end(FrameProfiler.RTT_REFLECT);
                profiler.setRtt(false);
            }
            waterRtt = false;
        }
        // Main view. Clip plane off; water cameras used it to hide the other side of the surface.
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glDisable(GL_CLIP_DISTANCE0);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        if (water || (lighting != null && lighting.exterior)) {
            if (profiler != null) {
                profiler.begin(FrameProfiler.SKY);
            }
            drawSky(cam, false);
            if (profiler != null) {
                profiler.end(FrameProfiler.SKY);
            }
        }
        Gdx.gl.glUseProgram(program);
        upload(uView, cam.view);
        Gdx.gl.glUniform4f(uClipPlane, 0f, 0f, 0f, 1f);
        bindLighting(lighting, false, underwater);
        Gdx.gl.glUniform1i(uBase, 0);
        Gdx.gl.glUniform1i(uDark, 1);
        Gdx.gl.glUniform1i(uDetail, 2);
        Gdx.gl.glUniform1i(uGlow, 3);
        Gdx.gl.glUniform1i(uBlendMap, 4);
        Gdx.gl.glUniform1f(uCameraFar, cam.far);
        // Pass 1 = land layers (polygon offset so they lose to statics on the same plane).
        if (profiler != null) {
            profiler.begin(FrameProfiler.TERRAIN);
        }
        drawNode(cam, root, 1, lighting, false);
        if (profiler != null) {
            profiler.end(FrameProfiler.TERRAIN);
            profiler.begin(FrameProfiler.OPAQUE);
        }
        drawNode(cam, root, 0, lighting, false);
        if (profiler != null) {
            profiler.end(FrameProfiler.OPAQUE);
        }
        // Water after opaque so the depth buffer already holds docks and the seafloor.
        if (water) {
            if (profiler != null) {
                profiler.begin(FrameProfiler.WATER);
            }
            drawWater(cam, root, lighting, underwater);
            if (profiler != null) {
                profiler.end(FrameProfiler.WATER);
            }
        }
        Gdx.gl.glUseProgram(program);
        upload(uView, cam.view);
        Gdx.gl.glUniform4f(uClipPlane, 0f, 0f, 0f, 1f);
        bindLighting(lighting, false, underwater);
        // Pass 2 last: alpha blend (leaves, glass). Depth is already filled.
        if (profiler != null) {
            profiler.begin(FrameProfiler.ALPHA);
        }
        drawNode(cam, root, 2, lighting, false);
        if (profiler != null) {
            profiler.end(FrameProfiler.ALPHA);
        }
        Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
        Gdx.gl.glUseProgram(0);
        Gdx.gl30.glBindVertexArray(0);
    }

    /** Sun + ambient on the world program. Point lights are bound per mesh. */
    private void bindLighting(CellLighting lighting, boolean fogOff, boolean underwater) {
        if (lighting == null) {
            Gdx.gl.glUniform3f(uLightDir, 0.35f, 0.8f, 0.45f);
            Gdx.gl.glUniform3f(uAmbientLight, 0.35f, 0.35f, 0.35f);
            Gdx.gl.glUniform3f(uSunDiffuse, 1f, 1f, 1f);
            Gdx.gl.glUniform1i(uPointCount, 0);
        } else {
            Gdx.gl.glUniform3f(uLightDir, lighting.sunDir[0], lighting.sunDir[1], lighting.sunDir[2]);
            Gdx.gl.glUniform3f(uAmbientLight, lighting.ambient[0], lighting.ambient[1], lighting.ambient[2]);
            Gdx.gl.glUniform3f(uSunDiffuse, lighting.sunDiffuse[0], lighting.sunDiffuse[1], lighting.sunDiffuse[2]);
        }
        bindFog(uFogEnabled, uFogStart, uFogScale, uFogColor, lighting, fogOff, underwater);
    }

    /**
     * Fog on interiors (and underwater). Exterior Clear weather often has density 0,
     * so {@code fogEnabled} stays false even in Town.
     */
    private void bindFog(int enabledLoc, int startLoc, int scaleLoc, int colorLoc, CellLighting lighting,
        boolean fogOff, boolean underwater) {
        if (fogOff) {
            Gdx.gl.glUniform1i(enabledLoc, 0);
            return;
        }
        if (underwater) {
            if (lighting != null) {
                lighting.underwaterFogColor(uwFog);
            } else {
                uwFog[0] = CellLighting.UNDERWATER_COLOR[0] * CellLighting.UNDERWATER_WEIGHT
                    + 0.08f * (1f - CellLighting.UNDERWATER_WEIGHT);
                uwFog[1] = CellLighting.UNDERWATER_COLOR[1] * CellLighting.UNDERWATER_WEIGHT
                    + 0.09f * (1f - CellLighting.UNDERWATER_WEIGHT);
                uwFog[2] = CellLighting.UNDERWATER_COLOR[2] * CellLighting.UNDERWATER_WEIGHT
                    + 0.12f * (1f - CellLighting.UNDERWATER_WEIGHT);
            }
            Gdx.gl.glUniform1i(enabledLoc, 1);
            Gdx.gl.glUniform1f(startLoc, CellLighting.underwaterFogStart());
            Gdx.gl.glUniform1f(scaleLoc, CellLighting.underwaterFogScale());
            Gdx.gl.glUniform3f(colorLoc, uwFog[0], uwFog[1], uwFog[2]);
            return;
        }
        if (lighting == null || !lighting.fogEnabled) {
            Gdx.gl.glUniform1i(enabledLoc, 0);
            return;
        }
        Gdx.gl.glUniform1i(enabledLoc, 1);
        Gdx.gl.glUniform1f(startLoc, lighting.fogStart);
        Gdx.gl.glUniform1f(scaleLoc, lighting.fogScale);
        Gdx.gl.glUniform3f(colorLoc, lighting.fogColor[0], lighting.fogColor[1], lighting.fogColor[2]);
    }

    /**
     * Draw the world as seen from under the water, looking up. Vertices are multiplied
     * by {@link #reflectMat} so docks appear inverted. Clip discards anything below
     * the plane (the seafloor would otherwise show in the mirror). People are skipped
     * (OpenMW reflection detail 2).
     */
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
        origCombined.set(cam.combined);
        origView.set(cam.view);
        cam.combined.mul(reflectMat);
        cam.view.mul(reflectMat);
        // Keep the player frustum. Rebuilding it from the reflected combined
        // inverts the clip planes, so every mesh fails the cull and only sky remains.
        Gdx.gl.glDisable(GL_CLIP_DISTANCE0);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        drawSky(cam, true);
        Gdx.gl.glEnable(GL_CLIP_DISTANCE0);
        Gdx.gl.glUseProgram(program);
        upload(uView, cam.view);
        // Keep fragments with world.y >= water height (the side that should appear in the mirror).
        Gdx.gl.glUniform4f(uClipPlane, 0f, 1f, 0f, -WaterMesh.HEIGHT);
        bindLighting(lighting, false, false);
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
        syncFrustum(cam);
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        Gdx.gl.glDisable(GL_CLIP_DISTANCE0);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
    }

    /**
     * Draw what is under the water from the player camera. Clip keeps geometry below
     * the plane. Fog is off so the seafloor stays readable; the water shader tints it.
     * Actors still draw here (you can see someone swimming).
     */
    private void renderRefraction(PerspectiveCamera cam, SceneNode root, CellLighting lighting) {
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, refractFbo);
        Gdx.gl.glViewport(0, 0, WaterMesh.RTT_SIZE, WaterMesh.RTT_SIZE);
        Gdx.gl.glClearColor(0.090195f, 0.115685f, 0.12745f, 1f);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL_CLIP_DISTANCE0);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glUseProgram(program);
        upload(uView, cam.view);
        // Keep fragments with world.y <= water height (the seafloor).
        Gdx.gl.glUniform4f(uClipPlane, 0f, -1f, 0f, WaterMesh.HEIGHT);
        bindLighting(lighting, true, false);
        Gdx.gl.glUniform1i(uBase, 0);
        Gdx.gl.glUniform1i(uDark, 1);
        Gdx.gl.glUniform1i(uDetail, 2);
        Gdx.gl.glUniform1i(uGlow, 3);
        Gdx.gl.glUniform1i(uBlendMap, 4);
        Gdx.gl.glUniform1f(uCameraFar, cam.far);
        drawNode(cam, root, 1, lighting, false);
        drawNode(cam, root, 0, lighting, false);
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        Gdx.gl.glDisable(GL_CLIP_DISTANCE0);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
    }

    /**
     * One tree walk for one bucket: 1 = land, 0 = solid objects, 2 = alpha blend.
     * Sky and water meshes are skipped; they have their own draws.
     */
    private void drawNode(PerspectiveCamera cam, SceneNode node, int pass, CellLighting lighting, boolean reflection) {
        if (reflection && node.actor) {
            // OpenMW reflection detail 2: sky + land + statics, not NPCs or creatures.
            return;
        }
        if (node.debugDraw && (!PathgridDebug.visible || reflection || waterRtt)) {
            return;
        }
        if (!node.skipMeshes) {
            for (MeshInstance inst : node.meshes) {
                MeshGpu mesh = inst.mesh;
                if (mesh.waterShader || mesh.skyShader) {
                    continue;
                }
                int meshPass = mesh.terrainPass ? 1 : mesh.alphaBlend ? 2 : 0;
                if (meshPass != pass) {
                    continue;
                }
                if (frustumCulled(cam, node, mesh, reflection)) {
                    if (profiler != null) {
                        profiler.addCulled();
                    }
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
                    // Mirror flips winding; without this, back-faces become front.
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
                    // Land: first layer SRC_ALPHA,ZERO then later SRC_ALPHA,ONE. Leaves: typical alpha.
                    Gdx.gl.glDepthMask(mesh.terrainPass ? mesh.depthWrite : mesh.noSorter && mesh.depthWrite);
                } else {
                    Gdx.gl.glDisable(GL20.GL_BLEND);
                    Gdx.gl.glDepthMask(mesh.depthWrite);
                }
                if (mesh.terrainPass) {
                    // Land layers share one heightfield. Offset so later blend layers do not z-fight.
                    Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL);
                    Gdx.gl.glPolygonOffset(1f, 1f);
                } else {
                    Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);
                }
                Gdx.gl30.glBindVertexArray(mesh.vao);
                Gdx.gl.glDrawElements(mesh.primitive, mesh.indexCount, GL20.GL_UNSIGNED_SHORT, 0);
                if (profiler != null) {
                    profiler.addDraw(pass, mesh.indexCount);
                }
            }
        }
        for (SceneNode child : node.children) {
            drawNode(cam, child, pass, lighting, reflection);
        }
    }

    private void syncFrustum(PerspectiveCamera cam) {
        frustumInv.set(cam.combined).inv();
        cam.frustum.update(frustumInv);
    }

    /**
     * True means skip this draw. Invalid AABB always draws. Order: tiny-on-screen,
     * then far clutter, then frustum (with the box mirrored for the reflection pass).
     */
    private boolean frustumCulled(PerspectiveCamera cam, SceneNode node, MeshGpu mesh, boolean reflection) {
        if (mesh.localMin[0] > mesh.localMax[0]) {
            return false;
        }
        cullBox.inf();
        mesh.expandWorldAabb(node.world, cullBox);
        boolean tiny;
        if (waterRtt) {
            tiny = !mesh.terrainPass && featureCulled(cam, WaterMesh.RTT_SIZE, WaterMesh.RTT_FEATURE_PIXELS);
        } else {
            tiny = featureCulled(cam, Gdx.graphics.getHeight(), CAMERA_FEATURE_PIXELS);
        }
        boolean tooFar = !mesh.terrainPass && !landmarkMesh() && beyondViewDistance(cam);
        if (reflection) {
            reflectCullBoxOverWater();
        }
        if (!cam.frustum.boundsInFrustum(cullBox)) {
            return true;
        }
        return tiny || tooFar;
    }

    /** Mirror the world AABB over the water plane so it can be tested against the player frustum. */
    private void reflectCullBoxOverWater() {
        aabbMin.set(cullBox.min);
        aabbMax.set(cullBox.max);
        float minY = 2f * WaterMesh.HEIGHT - aabbMax.y;
        float maxY = 2f * WaterMesh.HEIGHT - aabbMin.y;
        aabbMin.y = minY;
        aabbMax.y = maxY;
        cullBox.set(aabbMin, aabbMax);
    }

    /**
     * How many pixels the mesh's bounding sphere covers. OpenMW uses viewport height.
     * Water maps pass 512 and 20 px; the main view passes window height and 2 px.
     */
    private boolean featureCulled(PerspectiveCamera cam, float viewportPx, float pixelSize) {
        if (viewportPx < 1f) {
            return false;
        }
        cullBox.getCenter(meshCenter);
        float dx = cullBox.max.x - cullBox.min.x;
        float dy = cullBox.max.y - cullBox.min.y;
        float dz = cullBox.max.z - cullBox.min.z;
        float radius = 0.5f * (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        meshCenter.mul(cam.view);
        float dist = Math.abs(meshCenter.z);
        if (dist <= radius) {
            return false;
        }
        float pixels = radius * viewportPx
            / (dist * 2f * (float) Math.tan(Math.toRadians(cam.fieldOfView) * 0.5f));
        return pixels < pixelSize;
    }

    /** Trees/shacks: longest world-axis ≥ 128, so distant hills are not empty. */
    private boolean landmarkMesh() {
        float dx = cullBox.max.x - cullBox.min.x;
        float dy = cullBox.max.y - cullBox.min.y;
        float dz = cullBox.max.z - cullBox.min.z;
        return Math.max(dx, Math.max(dy, dz)) >= LANDMARK_EXTENT;
    }

    /** Small clutter past OpenMW viewing distance. Landmarks still draw. */
    private boolean beyondViewDistance(PerspectiveCamera cam) {
        float d2 = 0f;
        if (cam.position.x < cullBox.min.x) {
            float d = cullBox.min.x - cam.position.x;
            d2 += d * d;
        } else if (cam.position.x > cullBox.max.x) {
            float d = cam.position.x - cullBox.max.x;
            d2 += d * d;
        }
        if (cam.position.y < cullBox.min.y) {
            float d = cullBox.min.y - cam.position.y;
            d2 += d * d;
        } else if (cam.position.y > cullBox.max.y) {
            float d = cam.position.y - cullBox.max.y;
            d2 += d * d;
        }
        if (cam.position.z < cullBox.min.z) {
            float d = cullBox.min.z - cam.position.z;
            d2 += d * d;
        } else if (cam.position.z > cullBox.max.z) {
            float d = cam.position.z - cullBox.max.z;
            d2 += d * d;
        }
        return d2 > VIEW_DISTANCE_SQ;
    }

    /**
     * Draw the water plane using the two maps filled earlier. Screen UVs sample those
     * maps; the normal map wobbles the UVs so the reflection looks wavy.
     */
    private void drawWater(PerspectiveCamera cam, SceneNode root, CellLighting lighting, boolean underwater) {
        Gdx.gl.glUseProgram(waterProgram);
        upload(uWaterView, cam.view);
        Gdx.gl.glUniform1i(uWaterNormal, 0);
        Gdx.gl.glUniform1i(uWaterReflection, 1);
        Gdx.gl.glUniform1i(uWaterRefraction, 2);
        Gdx.gl.glUniform1i(uWaterRefractionDepth, 3);
        Gdx.gl.glUniform1f(uWaterTime, waterTime);
        // Water shader is TES3 Z-up: GL (x,y,z) → TES (x, −z, y).
        Gdx.gl.glUniform3f(uWaterCamTes, cam.position.x, -cam.position.z, cam.position.y);
        if (lighting == null) {
            Gdx.gl.glUniform3f(uWaterSunDir, 0.35f, -0.45f, 0.8f);
            Gdx.gl.glUniform3f(uWaterSunDiffuse, 1f, 1f, 1f);
            Gdx.gl.glUniform1f(uWaterSunVis, 1f);
            Gdx.gl.glUniform3f(uWaterAmbient, 0.35f, 0.35f, 0.35f);
        } else {
            Gdx.gl.glUniform3f(uWaterSunDir, lighting.sunDir[0], -lighting.sunDir[2], lighting.sunDir[1]);
            Gdx.gl.glUniform3f(uWaterSunDiffuse, lighting.sunDiffuse[0], lighting.sunDiffuse[1], lighting.sunDiffuse[2]);
            Gdx.gl.glUniform1f(uWaterSunVis, lighting.exterior ? cycle.sunVis : 1f);
            Gdx.gl.glUniform3f(uWaterAmbient, lighting.ambient[0], lighting.ambient[1], lighting.ambient[2]);
        }
        Gdx.gl.glUniform1f(uWaterFar, cam.far);
        Gdx.gl.glUniform2f(uWaterScreen, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        bindFog(uWaterFogEnabled, uWaterFogStart, uWaterFogScale, uWaterFogColor, lighting, false, underwater);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, waterNm.getTextureObjectHandle());
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_REPEAT);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_REPEAT);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, reflectColor);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE2);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, refractColor);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, refractDepth);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL20.GL_NONE);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
        drawWaterNode(cam, root);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
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
                if (profiler != null) {
                    profiler.addDraw(-1, mesh.indexCount);
                }
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

    /**
     * Sky NIFs sit at the origin. Zeroing the view translation makes them follow the
     * camera so they never look like a painted lid over Seyda Neen. Depth writes off
     * so world meshes always win. Sun is skipped in the reflection map.
     */
    private void drawSky(PerspectiveCamera cam, boolean reflection) {
        boolean haveSky = sky != null && sky.root != null;
        boolean haveClouds = clouds != null && clouds.root != null;
        boolean haveSun = !reflection && sun != null && sun.root != null && cycle.sunAlpha > 0.01f;
        boolean haveStars = stars != null && stars.root != null && cycle.night && cycle.nightFade > 0.01f;
        if (!haveSky && !haveClouds && !haveSun && !haveStars) {
            return;
        }
        skyView.set(cam.view);
        skyView.val[12] = 0f;
        skyView.val[13] = 0f;
        skyView.val[14] = 0f;
        skyCombined.set(cam.projection).mul(skyView);
        Gdx.gl.glUseProgram(skyProgram);
        upload(uSkyView, skyView);
        Gdx.gl.glUniform1f(uSkyFar, cam.far);
        Gdx.gl.glUniform1i(uSkyDiffuse, 0);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        if (haveSky) {
            Gdx.gl.glUniform1i(uSkyPass, 0);
            Gdx.gl.glUniform3f(uSkyEmission, cycle.sky[0], cycle.sky[1], cycle.sky[2]);
            drawSkyNode(sky.root, reflection);
        }
        if (haveStars) {
            Gdx.gl.glUniform1i(uSkyPass, SkyStars.PASS);
            Gdx.gl.glUniform1f(uSkyOpacity, cycle.nightFade);
            drawSkyNode(stars.root, reflection);
        }
        if (haveSun) {
            Gdx.gl.glUniform1i(uSkyPass, SkySun.PASS);
            Gdx.gl.glUniform1f(uSkyOpacity, cycle.sunAlpha);
            drawSkyNode(sun.root, reflection);
        }
        if (haveClouds) {
            Gdx.gl.glUniform1i(uSkyPass, SkyClouds.PASS);
            Gdx.gl.glUniform3f(uSkyEmission, cycle.cloudEmission[0], cycle.cloudEmission[1], cycle.cloudEmission[2]);
            Gdx.gl.glUniform3f(uSkyFog, cycle.fog[0], cycle.fog[1], cycle.fog[2]);
            Gdx.gl.glUniform1f(uSkyOpacity, 1f);
            Gdx.gl.glUniform1f(uSkyScroll, clouds.timer);
            drawSkyNode(clouds.root, reflection);
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
    }

    private void drawSkyNode(SceneNode node, boolean reflection) {
        if (!node.skipMeshes) {
            for (MeshInstance inst : node.meshes) {
                MeshGpu mesh = inst.mesh;
                if (!mesh.skyShader) {
                    continue;
                }
                if (mesh.skyPass == SkyClouds.PASS || mesh.skyPass == SkySun.PASS
                    || mesh.skyPass == SkyStars.PASS) {
                    bindUnit(GL20.GL_TEXTURE0, mesh.baseTex, mesh.baseWrapS, mesh.baseWrapT);
                }
                mvp.set(skyCombined).mul(node.world);
                upload(uSkyMvp, mvp);
                upload(uSkyModel, node.world);
                boolean cw = inst.frontClockwise;
                if (reflection) {
                    cw = !cw;
                }
                Gdx.gl.glFrontFace(cw ? GL20.GL_CW : GL20.GL_CCW);
                Gdx.gl30.glBindVertexArray(mesh.vao);
                Gdx.gl.glDrawElements(GL20.GL_TRIANGLES, mesh.indexCount, GL20.GL_UNSIGNED_SHORT, 0);
            }
        }
        for (SceneNode child : node.children) {
            drawSkyNode(child, reflection);
        }
    }

    /** Eight nearest cell lights to this mesh. Morrowind does the same per-object cap. */
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
        if (sky != null) {
            sky.dispose();
            sky = null;
        }
        if (sun != null) {
            sun.dispose();
            sun = null;
        }
        if (stars != null) {
            stars.dispose();
            stars = null;
        }
        if (clouds != null) {
            clouds.dispose();
            clouds = null;
        }
        Gdx.gl.glDeleteProgram(program);
        Gdx.gl.glDeleteProgram(waterProgram);
        Gdx.gl.glDeleteProgram(skyProgram);
        if (waterNm != null) {
            waterNm.dispose();
        }
        Gdx.gl.glDeleteTexture(reflectColor);
        Gdx.gl.glDeleteTexture(refractColor);
        Gdx.gl.glDeleteTexture(refractDepth);
        IntBuffer ids = BufferUtils.newIntBuffer(1);
        ids.put(0, reflectDepth);
        Gdx.gl30.glDeleteRenderbuffers(1, ids);
        ids.put(0, reflectFbo);
        Gdx.gl30.glDeleteFramebuffers(1, ids);
        ids.put(0, refractFbo);
        Gdx.gl30.glDeleteFramebuffers(1, ids);
        // Interned DDS / static NIF templates live until the renderer dies.
        GpuCache.dispose();
    }

    /** Clear GL state so Scene2D HUD can draw after the 3D pass. */
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

    /** Color target for a water camera. */
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

    /** Depth renderbuffer for the reflection map (not sampled later). */
    private static int allocDepth(int size) {
        IntBuffer ids = BufferUtils.newIntBuffer(1);
        Gdx.gl30.glGenRenderbuffers(1, ids);
        int rb = ids.get(0);
        Gdx.gl30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rb);
        Gdx.gl30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, size, size);
        Gdx.gl30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        return rb;
    }

    /** Depth texture for refraction so the water shader can read seafloor distance. */
    private static int allocDepthTex(int size) {
        int tex = Gdx.gl.glGenTexture();
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, tex);
        Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT24, size, size, 0, GL20.GL_DEPTH_COMPONENT,
            GL20.GL_UNSIGNED_INT, null);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL20.GL_NONE);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int allocFbo(int color, int depth, boolean depthIsTexture) {
        IntBuffer ids = BufferUtils.newIntBuffer(1);
        Gdx.gl30.glGenFramebuffers(1, ids);
        int fbo = ids.get(0);
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        Gdx.gl30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, color, 0);
        if (depthIsTexture) {
            Gdx.gl30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL20.GL_TEXTURE_2D, depth, 0);
        } else {
            Gdx.gl30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER,
                depth);
        }
        int status = Gdx.gl30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("water RTT FBO: " + status);
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
        // World-mesh vertex. Clip plane is in world space (water cameras only).
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
            // Zero clip plane (main view) always passes. Water cameras pass ax+by+cz+d.
            gl_ClipDistance[0] = length(u_clipPlane.xyz) < 1e-6 ? 1.0 : dot(vec4(world.xyz, 1.0), u_clipPlane);
        }
        """;

    private static final String FRAG = """
        #version 330
        // Land, statics, actors. NiTexture slots: base, dark, detail, glow.
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
            // 17 VTEX samples across the cell. Half-texel so linear mix sits on square edges.
            if (u_useBlendMap != 0) {
                vec2 buv = v_uv * (16.0 / 17.0) + vec2(0.5 / 17.0);
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
            // Log depth: better precision at Morrowind distances than a linear 1/z buffer.
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
            // OpenMW water.frag is TES3 Z-up. GL Y-up world → (x, −z, y).
            v_tesPos = vec3(world.x, -world.z, world.y);
            v_viewZ = (u_view * world).z;
            gl_Position = u_mvp * vec4(a_pos, 1.0);
        }
        """;

    private static final String WATER_FRAG = """
        #version 330
        // OpenMW compatibility/water.frag (refraction on). Mix seafloor vs mirror by fresnel.
        in vec3 v_tesPos;
        in float v_viewZ;
        uniform sampler2D u_normalMap;
        uniform sampler2D u_reflectionMap;
        uniform sampler2D u_refractionMap;
        uniform sampler2D u_refractionDepthMap;
        uniform float u_time;
        uniform vec3 u_cameraTes;
        uniform vec3 u_sunDirTes;
        uniform vec3 u_sunDiffuse;
        uniform float u_sunVis;
        uniform vec3 u_ambientLight;
        uniform float u_cameraFar;
        uniform vec2 u_screenRes;
        uniform int u_fogEnabled;
        uniform float u_fogStart;
        uniform float u_fogScale;
        uniform vec3 u_fogColor;
        out vec4 frag;

        const vec2 BIG_WAVES = vec2(0.1, 0.1);
        const vec2 MID_WAVES = vec2(0.1, 0.1);
        const vec2 SMALL_WAVES = vec2(0.1, 0.1);
        const float WAVE_CHOPPYNESS = 0.05;
        const float WAVE_SCALE = 75.0;
        const float BUMP = 0.5;
        const float REFL_BUMP = 0.10;
        const float REFR_BUMP = 0.07;
        const float SPEC_HARDNESS = 256.0;
        const float SPEC_BUMPINESS = 5.0;
        const float SPEC_BRIGHTNESS = 1.5;
        const float SUN_SPEC_FADING_THRESHOLD = 0.15;
        const vec2 WIND_DIR = vec2(0.5, -0.8);
        const float WIND_SPEED = 0.2;
        const vec3 WATER_COLOR = vec3(0.090195, 0.115685, 0.12745);
        const float VISIBILITY = 2500.0;
        const float VISIBILITY_DEPTH = VISIBILITY * 1.5;
        const float DEPTH_FADE = 0.15;
        const float BUMP_SUPPRESS_DEPTH = 300.0;
        const float REFR_FOG_DISTORT_DISTANCE = 3000.0;

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

        float logDepthToView(float d) {
            return exp2(clamp(d, 0.0, 1.0) * log2(max(u_cameraFar, 1.0) + 1.0)) - 1.0;
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
            // Looking down → fresnel ~0 (see the bottom). Grazing → ~1 (see the sky/docks).
            float fresnel = clamp(fresnel_dielectric(viewDir, normal, ior), 0.0, 1.0);
            vec2 reflOffset = normal.xy * REFL_BUMP;
            vec2 refrOffset = normal.xy * REFR_BUMP;
            float surfaceDepth = abs(v_viewZ);
            float depthSample = logDepthToView(texture(u_refractionDepthMap, screenCoords).x);
            float realWaterDepth = depthSample - surfaceDepth;
            float depthSampleDistorted = logDepthToView(texture(u_refractionDepthMap, screenCoords - refrOffset).x);
            float waterDepthDistorted = max(depthSampleDistorted - surfaceDepth, 0.0);
            refrOffset *= clamp(realWaterDepth / BUMP_SUPPRESS_DEPTH, 0.0, 1.0);
            // Reflection bump stays on; only refraction is calmed in shallow water (shores).
            vec3 reflection = texture(u_reflectionMap, screenCoords + reflOffset).rgb;
            vec3 waterColor = WATER_COLOR * sunFade;
            const float SPEC_MAGIC = 1.55;
            vec3 specNormal = normalize(vec3(normal.x * SPEC_BUMPINESS, normal.y * SPEC_BUMPINESS, normal.z));
            vec3 viewReflectDir = reflect(viewDir, specNormal);
            float phongTerm = max(dot(viewReflectDir, sunWorldDir), 0.0);
            float specular = pow(atan(phongTerm * SPEC_MAGIC), SPEC_HARDNESS) * SPEC_BRIGHTNESS;
            specular = clamp(specular, 0.0, 1.0) * min(1.0, u_sunVis / SUN_SPEC_FADING_THRESHOLD);
            if (cameraPos.z > 0.0 && realWaterDepth <= VISIBILITY_DEPTH && waterDepthDistorted > VISIBILITY_DEPTH) {
                refrOffset = vec2(0.0);
            }
            depthSampleDistorted = logDepthToView(texture(u_refractionDepthMap, screenCoords - refrOffset).x);
            waterDepthDistorted = max(depthSampleDistorted - surfaceDepth, 0.0);
            waterDepthDistorted = mix(waterDepthDistorted, realWaterDepth, min(surfaceDepth / REFR_FOG_DISTORT_DISTANCE, 1.0));
            vec3 refraction = texture(u_refractionMap, screenCoords - refrOffset).rgb;
            if (cameraPos.z < 0.0) {
                refraction = clamp(refraction * 1.5, 0.0, 1.0);
            } else {
                float depthCorrection = sqrt(1.0 + 4.0 * DEPTH_FADE * DEPTH_FADE);
                float factor = DEPTH_FADE * DEPTH_FADE / (-0.5 * depthCorrection + 0.5 - waterDepthDistorted / VISIBILITY)
                    + 0.5 * depthCorrection + 0.5;
                refraction = mix(refraction, waterColor, clamp(factor, 0.0, 1.0));
            }
            vec3 rgb = mix(refraction, reflection, fresnel);
            rgb += specular * u_sunDiffuse;
            if (u_fogEnabled != 0) {
                float fogValue = clamp((abs(v_viewZ) - u_fogStart) * u_fogScale, 0.0, 1.0);
                rgb = mix(rgb, u_fogColor, fogValue);
            }
            frag = vec4(rgb, 1.0);
            gl_FragDepth = clamp(
                log2(max(1e-6, 1.0 + abs(v_viewZ))) / log2(max(u_cameraFar, 1.0) + 1.0),
                0.0, 1.0);
        }
        """;

    private static final String SKY_VERT = """
        #version 330
        layout(location = 0) in vec3 a_pos;
        layout(location = 2) in vec2 a_uv;
        layout(location = 3) in vec4 a_color;
        uniform mat4 u_mvp;
        uniform mat4 u_model;
        uniform mat4 u_view;
        uniform int u_pass;
        uniform float u_scroll;
        out float v_viewZ;
        out float v_alpha;
        out vec2 v_uv;
        void main() {
            v_alpha = a_color.a;
            v_uv = a_uv;
            if (u_pass == 2) {
                // Clouds: OpenMW scrolls V so they drift.
                v_uv.y += u_scroll;
            }
            vec4 world = u_model * vec4(a_pos, 1.0);
            v_viewZ = (u_view * world).z;
            gl_Position = u_mvp * vec4(a_pos, 1.0);
        }
        """;

    private static final String SKY_FRAG = """
        #version 330
        in float v_viewZ;
        in float v_alpha;
        in vec2 v_uv;
        uniform vec3 u_emission;
        uniform vec3 u_fogColor;
        uniform float u_cameraFar;
        uniform float u_opacity;
        uniform int u_pass;
        uniform sampler2D u_diffuse;
        out vec4 frag;
        void main() {
            vec4 color;
            // 0 atmosphere, 1 stars, 2 clouds, 4 sun disc.
            if (u_pass == 0) {
                color = vec4(u_emission, v_alpha);
            } else if (u_pass == 1) {
                color = texture(u_diffuse, v_uv);
                color.a *= v_alpha * u_opacity;
            } else if (u_pass == 4) {
                color = texture(u_diffuse, v_uv);
                color.a *= u_opacity;
            } else {
                color = texture(u_diffuse, v_uv);
                color.a *= v_alpha * u_opacity;
                color.xyz = clamp(color.xyz * u_emission, 0.0, 1.0);
                color = mix(vec4(u_fogColor, color.a), color, v_alpha);
            }
            frag = color;
            gl_FragDepth = clamp(
                log2(max(1e-6, 1.0 + abs(v_viewZ))) / log2(max(u_cameraFar, 1.0) + 1.0),
                0.0, 1.0);
        }
        """;
}
