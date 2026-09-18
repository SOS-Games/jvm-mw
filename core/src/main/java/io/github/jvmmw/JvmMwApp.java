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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.render.ForwardRenderer;
import io.github.jvmmw.render.NifSceneBuilder;
import io.github.jvmmw.render.SceneNode;
import io.github.jvmmw.resource.TestData;

import java.nio.file.Files;

public final class JvmMwApp extends ApplicationAdapter {
    private static final String[] AUTO = {TestData.SHACK, TestData.TREE};

    private PerspectiveCamera camera;
    private SceneNode root;
    private NifSceneBuilder builder;
    private ForwardRenderer renderer;
    private Stage stage;
    private Skin skin;
    private Label status;
    private float yaw = 35f;
    private float pitch = 20f;
    private float distance = 180f;
    private final Vector3 lookAt = new Vector3();
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

    @Override
    public void create() {
        camera = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 1f;
        camera.far = 8000f;
        renderer = new ForwardRenderer();
        buildUi();
        loadMesh(AUTO[0], true);
        InputAdapter orbit = new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                scrollAccum += amountY;
                return true;
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, orbit));
    }

    private void loadMesh(String vfsPath, boolean keepAuto) {
        if (!keepAuto) {
            autoCycling = false;
        }
        lastError = "";
        dumpedFrame = false;
        framesOnMesh = 0;
        currentVfs = vfsPath;
        if (builder != null) {
            builder.dispose();
            builder = null;
        }
        root = null;
        try {
            var nifPath = TestData.ensureNif(vfsPath);
            byte[] bytes = Files.readAllBytes(nifPath);
            NifFile nif = NifFile.parse(bytes, nifPath.toString());
            builder = new NifSceneBuilder(nif, TestData.testdataRoot(), p ->
                Files.isRegularFile(TestData.testdataRoot().resolve(p.replace('/', java.io.File.separatorChar))));
            root = builder.build();
            frameCamera();
            Gdx.app.log("JVM-MW", nif.debugSummary());
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            Gdx.app.error("JVM-MW", "Load failed: " + vfsPath, e);
        }
    }

    private void frameCamera() {
        aabb.inf();
        if (root != null) {
            root.collectAabb(aabb);
        }
        if (aabb.isValid()) {
            aabb.getCenter(lookAt);
            float radius = aabb.getDimensions(new Vector3()).len() * 0.5f;
            distance = Math.max(40f, radius * 2.4f);
            camera.far = Math.max(8000f, distance * 20f);
        } else {
            lookAt.set(0, 0, 0);
            distance = 180f;
        }
    }

    private void buildUi() {
        BitmapFont font = new BitmapFont();
        Pixmap pm = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
        pm.setColor(0.15f, 0.16f, 0.2f, 0.92f);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        TextureRegionDrawable panel = new TextureRegionDrawable(tex);

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
        Window win = new Window("JVM-MW Phase 1", skin);
        win.defaults().pad(6);
        status = new Label("Loading…", skin);
        status.setWrap(true);
        win.add(status).width(400).colspan(3).row();
        win.add(new Label("Drag LMB orbit, scroll zoom.", skin)).width(400).colspan(3).row();
        win.add(meshButton("Chair", TestData.CHAIR));
        win.add(meshButton("Shack", TestData.SHACK));
        win.add(meshButton("Tree", TestData.TREE)).row();
        TextButton click = new TextButton("Click me", skin);
        click.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hudClicks++;
            }
        });
        win.add(click).padTop(8).colspan(3);
        win.pack();
        win.setPosition(12, Gdx.graphics.getHeight() - win.getHeight() - 12);
        stage.addActor(win);
    }

    private TextButton meshButton(String label, String vfs) {
        TextButton b = new TextButton(label, skin);
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                loadMesh(vfs, false);
            }
        });
        return b;
    }

    @Override
    public void render() {
        handleOrbit();
        camera.viewportWidth = Gdx.graphics.getWidth();
        camera.viewportHeight = Gdx.graphics.getHeight();
        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        float x = (float) (Math.cos(radPitch) * Math.sin(radYaw) * distance);
        float y = (float) (Math.sin(radPitch) * distance);
        float z = (float) (Math.cos(radPitch) * Math.cos(radYaw) * distance);
        camera.position.set(lookAt.x + x, lookAt.y + y, lookAt.z + z);
        camera.up.set(0, 1, 0);
        camera.lookAt(lookAt);
        camera.update();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        if (root != null) {
            renderer.render(camera, root);
        }
        lastGlError = Gdx.gl.glGetError();
        ForwardRenderer.resetForScene2d();
        if (status != null) {
            String err = lastError.isEmpty() ? "" : " err=" + lastError;
            String shortName = currentVfs.isEmpty() ? "?" : currentVfs.substring(currentVfs.lastIndexOf('/') + 1);
            status.setText(shortName + "  clicks=" + hudClicks + " glError=" + lastGlError + err);
        }
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
        framesOnMesh++;
        if (!dumpedFrame && framesOnMesh == 20) {
            dumpedFrame = true;
            dumpFrame();
            if (autoCycling) {
                autoIndex++;
                if (autoIndex < AUTO.length) {
                    loadMesh(AUTO[autoIndex], true);
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
        String path = "build/phase1-" + stem + ".png";
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, w, h);
        com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.local(path), pm);
        pm.dispose();
        Gdx.app.log("JVM-MW", "mesh=" + currentVfs + " glError=" + lastGlError + " meshes=" + countMeshes(root)
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

    private void handleOrbit() {
        var hit = stage.hit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY(), true);
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && hit == null) {
            yaw += Gdx.input.getDeltaX() * 0.4f;
            pitch = Math.max(-89f, Math.min(89f, pitch - Gdx.input.getDeltaY() * 0.4f));
        }
        distance = Math.max(20f, distance - scrollAccum * 12f);
        scrollAccum = 0f;
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (builder != null) {
            builder.dispose();
        }
        if (renderer != null) {
            renderer.dispose();
        }
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
    }
}
