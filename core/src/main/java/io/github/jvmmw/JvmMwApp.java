package io.github.jvmmw;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmReader;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.render.CellLighting;
import io.github.jvmmw.render.CellSceneBuilder;
import io.github.jvmmw.render.ForwardRenderer;
import io.github.jvmmw.render.NifSceneBuilder;
import io.github.jvmmw.render.SceneNode;
import io.github.jvmmw.resource.TestData;

import java.nio.file.Files;

public final class JvmMwApp extends ApplicationAdapter {
    private static final String CELL_PREFIX = "cell:";
    private static final String[] AUTO = {TestData.CHAIR, CELL_PREFIX + TestData.CENSUS_CELL};

    private static final float START_YAW = 215f;
    private static final float START_PITCH = -20f;

    private PerspectiveCamera camera;
    private SceneNode root;
    private NifSceneBuilder builder;
    private CellSceneBuilder cellBuilder;
    private EsmFile.LoadedCell loadedCell;
    private ForwardRenderer renderer;
    private Stage stage;
    private Skin skin;
    private Label status;
    private Table loader;
    private Image loaderImage;
    private Texture loaderTexture;
    private String loaderCaption = "Loading…";
    private float yaw = START_YAW;
    private float pitch = START_PITCH;
    private float moveScale = 180f;
    private final Vector3 eye = new Vector3();
    private final Vector3 lookDir = new Vector3();
    private final Vector2 tmpScreen = new Vector2();
    private final BoundingBox aabb = new BoundingBox();
    private String lastError = "";
    private int lastGlError;
    private int hudClicks;
    private int framesOnMesh;
    private boolean dumpedFrame;
    private boolean autoCycling = true;
    private int autoIndex;
    private String currentVfs = "";
    private float scrollAccum;
    private String pendingKey;
    private boolean pendingKeepAuto;
    private int pendingVisibleFrames;
    private boolean cellStepping;
    private boolean windowFocused;

    @Override
    public void create() {
        camera = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 1f;
        camera.far = 8000f;
        renderer = new ForwardRenderer();
        buildUi();
        requestLoad(AUTO[0], true);
        InputAdapter look = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    if (isLoading()) {
                        Gdx.input.setCursorCatched(false);
                    } else {
                        Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                scrollAccum += amountY * (shift ? 10f : 1f);
                return true;
            }
        };
        InputAdapter lockOnClick = new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (isLoading() || Gdx.input.isCursorCatched() || button != Input.Buttons.LEFT) {
                    return false;
                }
                Vector2 at = stage.screenToStageCoordinates(tmpScreen.set(screenX, screenY));
                if (stage.hit(at.x, at.y, true) != null) {
                    return false;
                }
                Gdx.input.setCursorCatched(true);
                return true;
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(look, lockOnClick, stage));
        Gdx.input.setCursorCatched(false);
    }

    public void setWindowFocused(boolean focused) {
        windowFocused = focused;
        if (!focused && Gdx.input != null) {
            Gdx.input.setCursorCatched(false);
        }
    }

    private boolean isCellKey(String key) {
        return key != null && key.startsWith(CELL_PREFIX);
    }

    private boolean isLoading() {
        return cellStepping || isCellKey(pendingKey);
    }

    private void requestLoad(String key, boolean keepAuto) {
        pendingKey = key;
        pendingKeepAuto = keepAuto;
        pendingVisibleFrames = 0;
        cellStepping = false;
        lastError = "";
        currentVfs = key.startsWith(CELL_PREFIX) ? key.substring(CELL_PREFIX.length()) : key;
        if (!keepAuto) {
            autoCycling = false;
        }
    }

    private void pumpLoad() {
        if (cellStepping && cellBuilder != null) {
            try {
                if (cellBuilder.step(12_000_000L)) {
                    root = cellBuilder.end();
                    frameCamera();
                    Gdx.app.log("JVM-MW", cellBuilder.log.toString());
                    cellStepping = false;
                }
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                Gdx.app.error("JVM-MW", "Cell failed", e);
                cellStepping = false;
            }
            return;
        }
        if (pendingKey == null) {
            return;
        }
        if (isCellKey(pendingKey) && pendingVisibleFrames < 2) {
            pendingVisibleFrames++;
            return;
        }
        String key = pendingKey;
        boolean keep = pendingKeepAuto;
        pendingKey = null;
        load(key, keep);
    }

    private void updateLoader() {
        if (loader == null) {
            return;
        }
        boolean show = isLoading();
        loader.setVisible(show);
        if (!show) {
            return;
        }
        String name = currentVfs.isEmpty() ? "…" : currentVfs.substring(currentVfs.lastIndexOf('/') + 1);
        int tick = (int) (System.currentTimeMillis() / 400 % 4);
        String dots = ".".repeat(tick);
        if (cellStepping && cellBuilder != null) {
            setLoaderCaption("Loading " + name + dots + "\n" + cellBuilder.refIndex() + " / "
                + cellBuilder.refCount());
        } else {
            setLoaderCaption("Loading " + name + dots);
        }
    }

    private void setLoaderCaption(String text) {
        if (text.equals(loaderCaption) && loaderTexture != null) {
            return;
        }
        loaderCaption = text;
        if (loaderTexture != null) {
            loaderTexture.dispose();
        }
        Pixmap pm = AwtText.render(text, 42);
        loaderTexture = new Texture(pm);
        pm.dispose();
        loaderImage.setDrawable(new TextureRegionDrawable(loaderTexture));
        loaderImage.pack();
    }

    private void load(String key, boolean keepAuto) {
        if (key.startsWith(CELL_PREFIX)) {
            loadCell(key.substring(CELL_PREFIX.length()), keepAuto);
        } else {
            loadMesh(key, keepAuto);
        }
    }

    private void disposeScene() {
        if (builder != null) {
            builder.dispose();
            builder = null;
        }
        if (cellBuilder != null) {
            cellBuilder.dispose();
            cellBuilder = null;
        }
        root = null;
    }

    private void loadMesh(String vfsPath, boolean keepAuto) {
        if (!keepAuto) {
            autoCycling = false;
        }
        lastError = "";
        dumpedFrame = false;
        framesOnMesh = 0;
        currentVfs = vfsPath;
        disposeScene();
        try {
            var nifPath = TestData.ensureNif(vfsPath);
            byte[] bytes = Files.readAllBytes(nifPath);
            NifFile nif = NifFile.parse(bytes, nifPath.toString());
            builder = new NifSceneBuilder(nif, TestData.testdataRoot(), p ->
                Files.isRegularFile(TestData.testdataRoot().resolve(p.replace('/', java.io.File.separatorChar))));
            root = builder.build();
            frameCamera();
            if (nif.records.size() < 80) {
                Gdx.app.log("JVM-MW", nif.debugSummary());
            } else {
                Gdx.app.log("JVM-MW", "records=" + nif.records.size() + " roots=" + nif.roots.size());
            }
            Gdx.app.log("JVM-MW", "flatten\n" + builder.flattenLog);
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            Gdx.app.error("JVM-MW", "Load failed: " + vfsPath, e);
        }
    }

    private void loadCell(String wanted, boolean keepAuto) {
        if (!keepAuto) {
            autoCycling = false;
        }
        lastError = "";
        dumpedFrame = false;
        framesOnMesh = 0;
        currentVfs = wanted;
        disposeScene();
        try {
            if (loadedCell == null || !wanted.equalsIgnoreCase(loadedCell.name)) {
                Gdx.app.log("JVM-MW", "Parsing " + TestData.esmPath());
                loadedCell = EsmFile.loadInterior(EsmReader.open(TestData.esmPath()),
                    TestData.CENSUS_CELL, TestData.PRISON_SHIP);
            }
            cellBuilder = new CellSceneBuilder();
            cellBuilder.begin(loadedCell);
            currentVfs = loadedCell.name;
            cellStepping = true;
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            Gdx.app.error("JVM-MW", "Cell failed: " + wanted, e);
            cellStepping = false;
        }
    }

    private void frameCamera() {
        aabb.inf();
        if (root != null) {
            root.collectAabb(aabb);
        }
        if (aabb.isValid()) {
            aabb.getCenter(eye);
            float radius = aabb.getDimensions(new Vector3()).len() * 0.5f;
            moveScale = Math.max(40f, radius * 2.4f);
            camera.far = Math.max(8000f, moveScale * 20f);
        } else {
            eye.setZero();
            moveScale = 180f;
        }
        yaw = START_YAW;
        pitch = START_PITCH;
        updateLookDir();
        eye.mulAdd(lookDir, -moveScale);
    }

    private void buildUi() {
        BitmapFont font = new BitmapFont();
        Pixmap pm = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
        pm.setColor(0.15f, 0.16f, 0.2f, 0.92f);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        TextureRegionDrawable panel = new TextureRegionDrawable(tex);

        Pixmap dimPm = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
        dimPm.setColor(0.05f, 0.06f, 0.08f, 0.78f);
        dimPm.fill();
        Texture dimTex = new Texture(dimPm);
        dimPm.dispose();
        TextureRegionDrawable dim = new TextureRegionDrawable(dimTex);

        skin = new Skin();
        skin.add("default", font);
        LabelStyle ls = new LabelStyle(font, Color.WHITE);
        skin.add("default", ls);
        TextButtonStyle tbs = new TextButtonStyle();
        tbs.font = font;
        tbs.fontColor = Color.WHITE;
        tbs.up = panel.tint(new Color(0.25f, 0.4f, 0.55f, 1f));
        tbs.down = panel.tint(new Color(0.15f, 0.25f, 0.35f, 1f));
        skin.add("default", tbs);
        WindowStyle ws = new WindowStyle(font, Color.WHITE, panel);
        skin.add("default", ws);

        stage = new Stage(new ScreenViewport());
        Window win = new Window("JVM-MW Phase 5", skin);
        win.defaults().pad(6);
        status = new Label("Loading…", skin);
        status.setWrap(true);
        win.add(status).width(420).colspan(3).row();
        win.add(new Label("WASD walk, mouse look (click lock, Esc unlock), Space/Ctrl up-down, scroll dolly.", skin))
            .width(420).colspan(3).row();
        win.add(meshButton("Chair", TestData.CHAIR));
        win.add(meshButton("Shack", TestData.SHACK));
        win.add(meshButton("Tree", TestData.TREE)).row();
        win.add(meshButton("Glass", TestData.GLASS_DAGGER));
        win.add(meshButton("Banner", TestData.BANNER));
        win.add(meshButton("Dwrv", TestData.DWRV)).row();
        win.add(meshButton("Cell", CELL_PREFIX + TestData.CENSUS_CELL));
        TextButton click = new TextButton("Click me", skin);
        click.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hudClicks++;
            }
        });
        win.add(click).padTop(8).colspan(2).row();
        win.pack();
        win.setPosition(12, Gdx.graphics.getHeight() - win.getHeight() - 12);
        stage.addActor(win);

        loaderImage = new Image();
        loader = new Table();
        loader.setFillParent(true);
        loader.setTouchable(Touchable.enabled);
        loader.setBackground(dim);
        loader.add(loaderImage);
        loader.setVisible(false);
        stage.addActor(loader);
    }

    private TextButton meshButton(String label, String vfs) {
        TextButton b = new TextButton(label, skin);
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                requestLoad(vfs, false);
            }
        });
        return b;
    }

    @Override
    public void render() {
        pumpLoad();
        handleCamera();
        camera.viewportWidth = Gdx.graphics.getWidth();
        camera.viewportHeight = Gdx.graphics.getHeight();
        camera.position.set(eye);
        camera.direction.set(lookDir);
        camera.up.set(0, 1, 0);
        camera.update();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        if (root != null) {
            CellLighting mood = (cellBuilder != null && !isLoading()) ? cellBuilder.lighting : null;
            renderer.render(camera, root, mood);
        }
        lastGlError = Gdx.gl.glGetError();
        ForwardRenderer.resetForScene2d();
        updateLoader();
        if (status != null) {
            String err = lastError.isEmpty() ? "" : " err=" + lastError;
            String shortName = currentVfs.isEmpty() ? "?" : currentVfs.substring(currentVfs.lastIndexOf('/') + 1);
            String extra = "";
            if (cellBuilder != null) {
                extra = " placed=" + cellBuilder.placed + " lights=" + cellBuilder.lighting.lights.size()
                    + " skip=" + cellBuilder.skippedUnknown
                    + "+" + cellBuilder.skippedEmpty + "+" + cellBuilder.skippedActor
                    + "+" + cellBuilder.skippedNif;
            }
                status.setText(isLoading() ? loaderCaption.replace('\n', ' ')
                    : shortName + "  clicks=" + hudClicks + " glError=" + lastGlError + extra + err);
        }
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
        if (isLoading()) {
            return;
        }
        framesOnMesh++;
        if (!dumpedFrame && framesOnMesh == 20) {
            dumpedFrame = true;
            dumpFrame();
            if (autoCycling) {
                autoIndex++;
                if (autoIndex < AUTO.length) {
                    requestLoad(AUTO[autoIndex], true);
                } else {
                    autoCycling = false;
                }
            }
        }
    }

    private void dumpFrame() {
        String stem = currentVfs.isEmpty() ? "mesh" : currentVfs.substring(currentVfs.lastIndexOf('/') + 1);
        if (stem.endsWith(".nif")) {
            stem = stem.substring(0, stem.length() - 4);
        }
        stem = stem.replace(',', ' ').replace("  ", " ").trim();
        String path = (cellBuilder != null ? "build/phase5-" : "build/phase2-") + stem + ".png";
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, w, h);
        com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.local(path), pm);
        pm.dispose();
        Gdx.app.log("JVM-MW", "mesh=" + currentVfs + " glError=" + lastGlError + " meshes=" + countMeshes(root)
            + (cellBuilder != null ? " lights=" + cellBuilder.lighting.lights.size() : "")
            + " screenshot=" + path
            + (lastError.isEmpty() ? "" : " err=" + lastError));
    }

    private static int countMeshes(SceneNode node) {
        if (node == null) {
            return 0;
        }
        int n = node.skipMeshes ? 0 : node.meshes.size();
        for (SceneNode child : node.children) {
            n += countMeshes(child);
        }
        return n;
    }

    private void updateLookDir() {
        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        float cp = (float) Math.cos(radPitch);
        lookDir.set(cp * (float) Math.sin(radYaw), (float) Math.sin(radPitch), cp * (float) Math.cos(radYaw));
    }

    private void handleCamera() {
        stage.setKeyboardFocus(null);
        if (Gdx.input.isCursorCatched() && windowFocused && !isLoading()) {
            yaw -= Gdx.input.getDeltaX() * 0.4f / 3f;
            pitch = Math.max(-89f, Math.min(89f, pitch - Gdx.input.getDeltaY() * 0.4f));
        }
        updateLookDir();
        eye.mulAdd(lookDir, -scrollAccum * 12f);
        scrollAccum = 0f;
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        float speed = Math.max(60f, moveScale) * 0.4f * Gdx.graphics.getDeltaTime() * (shift ? 10f : 1f);
        float radYaw = (float) Math.toRadians(yaw);
        float sin = (float) Math.sin(radYaw);
        float cos = (float) Math.cos(radYaw);
        boolean forward = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean back = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        boolean left = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        if (forward) {
            eye.x += sin * speed;
            eye.z += cos * speed;
        }
        if (back) {
            eye.x -= sin * speed;
            eye.z -= cos * speed;
        }
        if (right) {
            eye.x -= cos * speed;
            eye.z += sin * speed;
        }
        if (left) {
            eye.x += cos * speed;
            eye.z -= sin * speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            eye.y += speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
            eye.y -= speed;
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
        if (Gdx.input != null) {
            Gdx.input.setCursorCatched(false);
        }
    }

    @Override
    public void dispose() {
        if (builder != null) {
            builder.dispose();
        }
        if (cellBuilder != null) {
            cellBuilder.dispose();
        }
        if (renderer != null) {
            renderer.dispose();
        }
        if (loaderTexture != null) {
            loaderTexture.dispose();
            loaderTexture = null;
        }
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
    }
}
