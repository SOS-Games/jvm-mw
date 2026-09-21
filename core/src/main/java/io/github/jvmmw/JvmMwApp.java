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
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import io.github.jvmmw.debug.DebugVars;
import io.github.jvmmw.debug.FrameProfiler;
import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmObject;
import io.github.jvmmw.esm.EsmReader;
import io.github.jvmmw.esm.LandRecord;
import io.github.jvmmw.esm.LevelledCreatures;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.render.BulletWorld;
import io.github.jvmmw.render.CellLighting;
import io.github.jvmmw.render.CellSceneBuilder;
import io.github.jvmmw.render.CollisionWorld;
import io.github.jvmmw.render.DoorSwing;
import io.github.jvmmw.render.ForwardRenderer;
import io.github.jvmmw.render.GpuCache;
import io.github.jvmmw.render.NifSceneBuilder;
import io.github.jvmmw.render.PathgridDebug;
import io.github.jvmmw.render.NavmeshDb;
import io.github.jvmmw.render.NavmeshDebug;
import io.github.jvmmw.render.SceneNode;
import io.github.jvmmw.render.WaterMesh;
import io.github.jvmmw.resource.TestData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The walk-in viewer window: load a cell, fly around, HUD buttons, E to
 * activate. Scene2D is only the overlay — the 3D pass is ForwardRenderer.
 *
 * Town is Seyda Neen. Walking recenters nearby cells in the background.
 * F3 dumps a snapshot; F4 toggles the fps overlay; F5 toggles pathgrid;
 * F6 toggles the Recast carpet.
 */
public final class JvmMwApp extends ApplicationAdapter {
    private static final String CELL_PREFIX = "cell:";
    private static final String EXT_PREFIX = "ext:";
    private static final String[] AUTO = {TestData.CHAIR, CELL_PREFIX + TestData.ADDAMASARTUS};

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
    private Window hudWin;
    private ScrollPane hudScroll;
    private Label status;
    private Label hourLabel;
    private Slider hourSlider;
    private TextButton playClock;
    private TextButton dumpButton;
    private TextButton copyPosButton;
    private float dumpCopiedLeft;
    private float posCopiedLeft;
    private boolean settingHour;
    private Table loader;
    private Image loaderImage;
    private Texture loaderTexture;
    private String loaderCaption = "Loading…";
    private Table walkTiles;
    private Label walkTileCaption;
    private ProgressBar[][] walkBars;
    private float walkHudHold;
    private Label perfLabel;
    private Table perfHud;
    private Label posLabel;
    private final FrameProfiler profiler = new FrameProfiler();
    private boolean perfHudOn = true;
    private float yaw = START_YAW;
    private float pitch = START_PITCH;
    private float moveScale = 180f;
    private final Vector3 eye = new Vector3();
    private final Vector3 lookDir = new Vector3();
    private final Vector2 tmpScreen = new Vector2();
    private final BoundingBox aabb = new BoundingBox();
    private String lastError = "";
    private int lastGlError;
    private int framesOnMesh;
    private boolean dumpedFrame;
    private boolean autoCycling;
    private int autoIndex;
    private String currentVfs = "";
    private float scrollAccum;
    private String pendingKey;
    private boolean pendingKeepAuto;
    private int pendingVisibleFrames;
    private boolean cellStepping;
    private boolean windowFocused;
    private boolean doorArrival;
    private boolean keepEye;
    private int lastWalkGx = Integer.MIN_VALUE;
    private int lastWalkGy;
    private int walkGx = Integer.MIN_VALUE;
    private int walkGy;
    private int walkPendingGx = Integer.MIN_VALUE;
    private int walkPendingGy;
    private final AtomicInteger walkGen = new AtomicInteger();
    private volatile EsmFile.LoadedCell walkReady;
    private volatile int walkReadyGen;
    private volatile String walkParseError;
    private volatile int walkErrorGen;
    private CellSceneBuilder walkBuilder;
    private EsmFile.LoadedCell walkIncoming;
    private boolean walkStepping;
    private final Set<String> takenKeys = new HashSet<>();
    private final float[] doorArrivalPos = new float[3];
    private float doorArrivalYaw;

    @Override
    public void create() {
        BulletWorld.initNatives();
        camera = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 1f;
        camera.far = 8000f;
        renderer = new ForwardRenderer();
        renderer.profiler = profiler;
        buildUi();
        requestLoad(EXT_PREFIX + TestData.TOWN_GRID_X + "," + TestData.TOWN_GRID_Y, false);
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
                if (keycode == Input.Keys.F3) {
                    debugSnapshot();
                    return true;
                }
                if (keycode == Input.Keys.F4) {
                    perfHudOn = !perfHudOn;
                    if (perfHud != null) {
                        perfHud.setVisible(perfHudOn);
                    }
                    return true;
                }
                if (keycode == Input.Keys.F5) {
                    boolean on = PathgridDebug.toggleVisible();
                    Gdx.app.log("JVM-MW", on ? "pathgrid on" : "pathgrid off");
                    return true;
                }
                if (keycode == Input.Keys.F6) {
                    boolean on = NavmeshDebug.toggleVisible();
                    Gdx.app.log("JVM-MW", on ? "navmesh on" : "navmesh off");
                    return true;
                }
                if (keycode == Input.Keys.E) {
                    if (cellBuilder != null && !isLoading()) {
                        String msg = cellBuilder.activateLooking(eye, lookDir);
                        Gdx.app.log("JVM-MW", msg == null ? "activate none in range" : msg);
                        DoorSwing.InteriorTeleport dest = cellBuilder.doors.consumeInteriorTeleport();
                        if (dest != null) {
                            if (dest.destCell.isEmpty()) {
                                startExteriorTeleport(dest);
                            } else {
                                startInteriorTeleport(dest);
                            }
                        }
                    }
                    return true;
                }
                if (keycode == Input.Keys.LEFT_BRACKET) {
                    stepHour(-(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) ? 2f : 0.25f));
                    return true;
                }
                if (keycode == Input.Keys.RIGHT_BRACKET) {
                    stepHour(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) ? 2f : 0.25f);
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
        return key != null && (key.startsWith(CELL_PREFIX) || key.startsWith(EXT_PREFIX));
    }

    private boolean isLoading() {
        return cellStepping || isCellKey(pendingKey);
    }

    private void requestLoad(String key, boolean keepAuto) {
        cancelWalkLoad();
        doorArrival = false;
        keepEye = false;
        pendingKey = key;
        pendingKeepAuto = keepAuto;
        pendingVisibleFrames = 0;
        cellStepping = false;
        lastError = "";
        currentVfs = key.startsWith(CELL_PREFIX) ? key.substring(CELL_PREFIX.length())
            : key.startsWith(EXT_PREFIX) ? key.substring(EXT_PREFIX.length()) : key;
        if (!keepAuto) {
            autoCycling = false;
        }
    }

    private void pumpLoad() {
        if (cellStepping && cellBuilder != null) {
            try {
                if (cellBuilder.step(12_000_000L)) {
                    root = cellBuilder.end();
                    if (keepEye && loadedCell != null && !loadedCell.interior) {
                        keepWalkCamera();
                    } else {
                        frameCamera();
                    }
                    keepEye = false;
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
            setLoaderCaption("Loading " + name + dots + "\n" + cellBuilder.gpuPhase());
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
        } else if (key.startsWith(EXT_PREFIX)) {
            loadExterior(key.substring(EXT_PREFIX.length()), keepAuto);
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
        BulletWorld.disposeWorld();
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
            builder = new NifSceneBuilder(nif, TestData.testdataRoot(), TestData::vfsExists);
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
        LevelledCreatures.forget();
        disposeScene();
        try {
            if (loadedCell == null || !loadedCell.interior || !wanted.equalsIgnoreCase(loadedCell.name)) {
                Gdx.app.log("JVM-MW", "Parsing " + TestData.esmPath());
                String[] names = wanted.equalsIgnoreCase(TestData.CENSUS_CELL)
                    ? new String[] { TestData.CENSUS_CELL, TestData.PRISON_SHIP }
                    : new String[] { wanted };
                loadedCell = EsmFile.loadInterior(EsmReader.open(TestData.esmPath()), names);
            }
            cellBuilder = new CellSceneBuilder();
            cellBuilder.takenKeys = takenKeys;
            cellBuilder.begin(loadedCell);
            currentVfs = loadedCell.name;
            cellStepping = true;
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            Gdx.app.error("JVM-MW", "Cell failed: " + wanted, e);
            cellStepping = false;
            doorArrival = false;
        }
    }

    private void loadExterior(String grid, boolean keepAuto) {
        if (!keepAuto) {
            autoCycling = false;
        }
        lastError = "";
        dumpedFrame = false;
        framesOnMesh = 0;
        currentVfs = grid;
        LevelledCreatures.forget();
        try {
            int[] xy = parseGrid(grid);
            EsmFile.LoadedCell next = loadedCell;
            if (loadedCell == null || loadedCell.interior
                || loadedCell.gridX != xy[0] || loadedCell.gridY != xy[1]) {
                Gdx.app.log("JVM-MW", "Parsing exterior " + TestData.esmPath());
                next = EsmFile.loadExterior(EsmReader.open(TestData.esmPath()), xy[0], xy[1]);
            }
            disposeScene();
            loadedCell = next;
            lastWalkGx = loadedCell.gridX;
            lastWalkGy = loadedCell.gridY;
            cellBuilder = new CellSceneBuilder();
            cellBuilder.takenKeys = takenKeys;
            cellBuilder.begin(loadedCell);
            currentVfs = loadedCell.name + " (" + loadedCell.gridX + "," + loadedCell.gridY + ")";
            cellStepping = true;
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            Gdx.app.error("JVM-MW", "Exterior failed: " + grid, e);
            cellStepping = false;
            doorArrival = false;
            keepEye = false;
        }
    }

    private static int[] parseGrid(String grid) {
        String[] parts = grid.trim().split("[,\\s]+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("exterior grid needs x y, got " + grid);
        }
        return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
    }

    private void startInteriorTeleport(DoorSwing.InteriorTeleport dest) {
        if (loadedCell != null && dest.destCell.equalsIgnoreCase(loadedCell.name)) {
            placeEye(dest.destPos, dest.destRot[2]);
            updateLookDir();
            Gdx.app.log("JVM-MW", "teleport same cell tes=(" + dest.destPos[0] + ','
                + dest.destPos[1] + ',' + dest.destPos[2] + ')');
            return;
        }
        System.arraycopy(dest.destPos, 0, doorArrivalPos, 0, 3);
        doorArrivalYaw = dest.destRot[2];
        requestLoad(CELL_PREFIX + dest.destCell, false);
        doorArrival = true;
    }

    private void startExteriorTeleport(DoorSwing.InteriorTeleport dest) {
        int gx = LandRecord.cellGrid(dest.destPos[0]);
        int gy = LandRecord.cellGrid(dest.destPos[1]);
        if (loadedCell != null && !loadedCell.interior
            && loadedCell.gridX == gx && loadedCell.gridY == gy) {
            placeEye(dest.destPos, dest.destRot[2]);
            updateLookDir();
            Gdx.app.log("JVM-MW", "teleport same exterior tes=(" + dest.destPos[0] + ','
                + dest.destPos[1] + ',' + dest.destPos[2] + ')');
            return;
        }
        System.arraycopy(dest.destPos, 0, doorArrivalPos, 0, 3);
        doorArrivalYaw = dest.destRot[2];
        requestLoad(EXT_PREFIX + gx + "," + gy, false);
        doorArrival = true;
    }

    private void maybeRecenterGrid() {
        if (isLoading() || cellBuilder == null || loadedCell == null || loadedCell.interior) {
            return;
        }
        int[] next = LandRecord.newGridCenter(eye.x, -eye.z, loadedCell.gridX, loadedCell.gridY);
        if (next[0] == loadedCell.gridX && next[1] == loadedCell.gridY) {
            return;
        }
        if (walkBusy() && next[0] == walkGx && next[1] == walkGy) {
            return;
        }
        if (walkBusy()) {
            walkPendingGx = next[0];
            walkPendingGy = next[1];
            return;
        }
        if (next[0] == lastWalkGx && next[1] == lastWalkGy) {
            return;
        }
        startWalkLoad(next[0], next[1]);
    }

    private boolean walkBusy() {
        return walkGx != Integer.MIN_VALUE || walkStepping
            || (walkReady != null && walkReadyGen == walkGen.get());
    }

    private void startWalkLoad(int gx, int gy) {
        lastWalkGx = gx;
        lastWalkGy = gy;
        walkGx = gx;
        walkGy = gy;
        walkPendingGx = Integer.MIN_VALUE;
        int gen = walkGen.incrementAndGet();
        walkReady = null;
        walkParseError = null;
        profiler.resetWalk();
        Gdx.app.log("JVM-MW", "walk recenter (" + loadedCell.gridX + "," + loadedCell.gridY
            + ") -> (" + gx + "," + gy + ")");
        Thread worker = new Thread(() -> {
            try {
                long parseStart = System.nanoTime();
                EsmFile.LoadedCell cell = EsmFile.loadExterior(EsmReader.open(TestData.esmPath()), gx, gy);
                if (gen == walkGen.get()) {
                    profiler.setWalkParseNs(System.nanoTime() - parseStart);
                    walkReadyGen = gen;
                    walkReady = cell;
                }
            } catch (Exception e) {
                if (gen == walkGen.get()) {
                    walkErrorGen = gen;
                    walkParseError = e.getMessage() == null ? e.toString() : e.getMessage();
                    Gdx.app.error("JVM-MW", "walk parse " + gx + "," + gy, e);
                }
            }
        }, "walk-grid");
        worker.setDaemon(true);
        worker.start();
    }

    private void cancelWalkLoad() {
        walkGen.incrementAndGet();
        walkReady = null;
        walkParseError = null;
        walkIncoming = null;
        walkGx = Integer.MIN_VALUE;
        walkPendingGx = Integer.MIN_VALUE;
        walkStepping = false;
        if (walkBuilder != null) {
            walkBuilder.dispose();
            walkBuilder = null;
        }
    }

    private void pumpWalkLoad() {
        if (walkReady != null && walkReadyGen != walkGen.get()) {
            walkReady = null;
        }
        if (walkParseError != null && walkErrorGen != walkGen.get()) {
            walkParseError = null;
        }
        if (isLoading()) {
            return;
        }
        if (walkParseError != null && walkErrorGen == walkGen.get()) {
            lastError = walkParseError;
            walkParseError = null;
            walkGx = Integer.MIN_VALUE;
            startPendingWalk();
            return;
        }
        if (walkReady != null && walkReadyGen == walkGen.get() && walkBuilder == null) {
            EsmFile.LoadedCell cell = walkReady;
            walkReady = null;
            try {
                long gpuStart = System.nanoTime();
                walkBuilder = new CellSceneBuilder();
                walkBuilder.takenKeys = takenKeys;
                walkBuilder.begin(cell);
                profiler.addWalkGpuNs(System.nanoTime() - gpuStart);
                walkIncoming = cell;
                walkStepping = true;
                applyExteriorCycle(walkBuilder.lighting);
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                Gdx.app.error("JVM-MW", "walk begin", e);
                if (walkBuilder != null) {
                    walkBuilder.dispose();
                    walkBuilder = null;
                }
                walkGx = Integer.MIN_VALUE;
                startPendingWalk();
                return;
            }
        }
        if (!walkStepping || walkBuilder == null) {
            return;
        }
        try {
            long stepStart = System.nanoTime();
            boolean done = walkBuilder.step(12_000_000L);
            profiler.addWalkGpuNs(System.nanoTime() - stepStart);
            if (!done) {
                return;
            }
            long swapStart = System.nanoTime();
            SceneNode nextRoot = walkBuilder.end();
            walkBuilder.takeWanderFrom(cellBuilder);
            profiler.addWalkGpuNs(System.nanoTime() - swapStart);
            CellSceneBuilder old = cellBuilder;
            cellBuilder = walkBuilder;
            root = nextRoot;
            loadedCell = walkIncoming;
            walkBuilder = null;
            walkIncoming = null;
            walkStepping = false;
            walkGx = Integer.MIN_VALUE;
            lastWalkGx = loadedCell.gridX;
            lastWalkGy = loadedCell.gridY;
            currentVfs = loadedCell.name + " (" + loadedCell.gridX + "," + loadedCell.gridY + ")";
            keepWalkCamera();
            applyExteriorCycle(cellBuilder.lighting);
            Gdx.app.log("JVM-MW", cellBuilder.log.toString());
            if (old != null) {
                old.dispose();
            }
            profiler.setWalkSwapNs(System.nanoTime() - swapStart);
            walkHudHold = 0.45f;
            startPendingWalk();
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            Gdx.app.error("JVM-MW", "walk step", e);
            walkStepping = false;
            if (walkBuilder != null) {
                walkBuilder.dispose();
                walkBuilder = null;
            }
            walkIncoming = null;
            walkGx = Integer.MIN_VALUE;
            startPendingWalk();
        }
    }

    private void startPendingWalk() {
        if (walkPendingGx == Integer.MIN_VALUE || loadedCell == null || loadedCell.interior) {
            return;
        }
        int gx = walkPendingGx;
        int gy = walkPendingGy;
        walkPendingGx = Integer.MIN_VALUE;
        if (gx == loadedCell.gridX && gy == loadedCell.gridY) {
            return;
        }
        startWalkLoad(gx, gy);
    }

    private String walkStatus() {
        if (walkGx == Integer.MIN_VALUE && !walkStepping && walkReady == null) {
            return "";
        }
        int gx = walkGx != Integer.MIN_VALUE ? walkGx : lastWalkGx;
        int gy = walkGx != Integer.MIN_VALUE ? walkGy : lastWalkGy;
        String phase = walkBuilder != null ? " " + walkBuilder.gpuPhase()
            : walkReady != null ? " gpu" : " parse";
        return " loading grid=(" + gx + "," + gy + ")" + phase;
    }

    private void updateWalkTiles() {
        if (walkTiles == null) {
            return;
        }
        boolean live = walkBusy();
        if (!live && walkHudHold > 0f) {
            walkHudHold -= Gdx.graphics.getDeltaTime();
        }
        boolean show = live || walkHudHold > 0f;
        walkTiles.setVisible(show);
        if (!show) {
            return;
        }
        int gx = walkGx != Integer.MIN_VALUE ? walkGx : lastWalkGx;
        int gy = walkGx != Integer.MIN_VALUE ? walkGy : lastWalkGy;
        CellSceneBuilder gpu = walkBuilder;
        boolean parse = live && gpu == null && walkReady == null;
        String caption;
        if (!live && walkHudHold > 0f) {
            caption = "swap (" + lastWalkGx + "," + lastWalkGy + ")";
        } else if (parse) {
            caption = "parse (" + gx + "," + gy + ")";
        } else if (gpu != null) {
            caption = gpu.gpuPhase() + " (" + gx + "," + gy + ")";
        } else {
            caption = "gpu (" + gx + "," + gy + ")";
        }
        walkTileCaption.setText(caption);
        float pulse = 0.18f + 0.16f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 180.0));
        int radius = EsmFile.CELL_GRID_RADIUS;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                ProgressBar bar = walkBars[dx + radius][dy + radius];
                if (bar == null) {
                    continue;
                }
                float value;
                if (!live && walkHudHold > 0f) {
                    value = 1f;
                } else if (parse) {
                    value = pulse;
                } else if (gpu != null) {
                    value = gpu.tileProgress(gx + dx, gy + dy);
                } else {
                    value = pulse;
                }
                bar.setValue(value);
                if (value >= 1f) {
                    bar.setColor(0.45f, 0.95f, 0.55f, 1f);
                } else if (parse || gpu == null) {
                    bar.setColor(0.95f, 0.85f, 0.35f, 1f);
                } else if (gpu.landIndex() < gpu.landCount()) {
                    bar.setColor(0.95f, 0.7f, 0.35f, 1f);
                } else {
                    bar.setColor(0.55f, 0.85f, 1f, 1f);
                }
            }
        }
    }

    private void keepWalkCamera() {
        camera.near = 1f;
        camera.far = 40000f;
        moveScale = 220f;
    }

    private void applyExteriorCycle(CellLighting lighting) {
        if (lighting == null || !lighting.exterior || renderer == null) {
            return;
        }
        renderer.cycle.evaluate();
        renderer.cycle.applyLighting(lighting);
    }

    private void frameCamera() {
        aabb.inf();
        if (root != null) {
            root.collectAabb(aabb);
        }
        if (cellBuilder != null && loadedCell != null) {
            frameCellCamera();
            return;
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

    private static final float EYE_HEIGHT = CollisionWorld.EYE_HEIGHT;

    private void frameCellCamera() {
        camera.near = 1f;
        camera.far = loadedCell.interior
            ? Math.max(8000f, CellLighting.VIEW_DISTANCE + 256f)
            : 40000f;
        moveScale = 220f;
        if (doorArrival) {
            placeEye(doorArrivalPos, doorArrivalYaw);
            snapWalk();
            doorArrival = false;
            Gdx.app.log("JVM-MW", "spawn door DODT tes=(" + doorArrivalPos[0] + ','
                + doorArrivalPos[1] + ',' + doorArrivalPos[2] + ')');
            updateLookDir();
            return;
        }
        if (loadedCell.hasSpawn) {
            placeEye(loadedCell.spawnPos, loadedCell.spawnRot[2]);
            snapWalk();
            Gdx.app.log("JVM-MW", "spawn inbound tes=(" + loadedCell.spawnPos[0] + ','
                + loadedCell.spawnPos[1] + ',' + loadedCell.spawnPos[2] + ')');
        } else {
            CellRef door = findTeleportDoor(loadedCell);
            if (door != null) {
                float h = door.rot[2];
                float[] tes = {
                    door.pos[0] - (float) Math.sin(h) * 160f,
                    door.pos[1] - (float) Math.cos(h) * 160f,
                    door.pos[2]
                };
                placeEye(tes, h + (float) Math.PI);
                snapWalk();
                Gdx.app.log("JVM-MW", "spawn door=" + door.refId);
            } else if (aabb.isValid()) {
                aabb.getCenter(eye);
                eye.y = aabb.min.y + EYE_HEIGHT;
                snapWalk();
                yaw = START_YAW;
                pitch = START_PITCH;
            } else {
                eye.setZero();
                yaw = START_YAW;
                pitch = START_PITCH;
            }
        }
        updateLookDir();
    }

    private void placeEye(float[] tes, float heading) {
        eye.set(tes[0], tes[2] + EYE_HEIGHT, -tes[1]);
        yaw = (float) Math.toDegrees(Math.atan2(Math.sin(heading), -Math.cos(heading)));
        pitch = 0f;
        snapWalk();
    }

    private void snapWalk() {
        if (cellBuilder != null) {
            cellBuilder.collision.snapSpawn(eye, loadedCell != null && loadedCell.interior);
        }
    }

    private static CellRef findTeleportDoor(EsmFile.LoadedCell cell) {
        CellRef fallback = null;
        for (CellRef ref : cell.refs) {
            if (ref.deleted) {
                continue;
            }
            EsmObject obj = cell.objects.get(ref.refId.toLowerCase(java.util.Locale.ROOT));
            if (obj == null || !"DOOR".equals(obj.rec)) {
                continue;
            }
            if (ref.teleport) {
                return ref;
            }
            if (fallback == null) {
                fallback = ref;
            }
        }
        return fallback;
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
        Pixmap barPm = new Pixmap(8, 6, Pixmap.Format.RGBA8888);
        barPm.setColor(0.18f, 0.22f, 0.28f, 1f);
        barPm.fill();
        Texture barTex = new Texture(barPm);
        barPm.dispose();
        Pixmap knobPm = new Pixmap(10, 16, Pixmap.Format.RGBA8888);
        knobPm.setColor(0.75f, 0.82f, 0.9f, 1f);
        knobPm.fill();
        Texture knobTex = new Texture(knobPm);
        knobPm.dispose();
        SliderStyle sls = new SliderStyle();
        sls.background = new TextureRegionDrawable(barTex);
        sls.knob = new TextureRegionDrawable(knobTex);
        skin.add("default-horizontal", sls);
        Pixmap fillPm = new Pixmap(8, 6, Pixmap.Format.RGBA8888);
        fillPm.setColor(0.55f, 0.85f, 0.7f, 1f);
        fillPm.fill();
        Texture fillTex = new Texture(fillPm);
        fillPm.dispose();
        ProgressBarStyle pbs = new ProgressBarStyle();
        pbs.background = new TextureRegionDrawable(barTex);
        pbs.knobBefore = new TextureRegionDrawable(fillTex);
        skin.add("default-horizontal", pbs);
        ScrollPaneStyle sps = new ScrollPaneStyle();
        sps.vScroll = panel.tint(new Color(0.2f, 0.22f, 0.28f, 0.9f));
        sps.vScrollKnob = panel.tint(new Color(0.45f, 0.55f, 0.65f, 1f));
        skin.add("default", sps);

        stage = new Stage(new ScreenViewport());
        Window win = new Window("JVM-MW", skin);
        Table body = new Table();
        body.defaults().pad(6);
        status = new Label("Loading…", skin);
        status.setWrap(true);
        body.add(status).width(420).colspan(3).row();
        posLabel = new Label("gl=(0,0,0) tes=(0,0,0) grid=(0,0) recast=(0,0)", skin);
        posLabel.setWrap(true);
        body.add(posLabel).width(420).colspan(3).left().row();
        dumpButton = new TextButton("Dump", skin);
        dumpButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                debugSnapshot();
            }
        });
        copyPosButton = new TextButton("Copy pos", skin);
        copyPosButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                copyPosition();
            }
        });
        body.add(dumpButton);
        body.add(copyPosButton).row();
        body.add(new Label("WASD walk on land, mouse look (click lock, Esc unlock), Space/Ctrl up-down (ceilings stop you), E activate, scroll dolly, [ ] hour, Dump/F3 copy perf, F4 overlay, F5 pathgrid, F6 navmesh.", skin))
            .width(420).colspan(3).row();
        body.add(meshButton("Chair", TestData.CHAIR));
        body.add(meshButton("Shack", TestData.SHACK));
        body.add(meshButton("Tree", TestData.TREE)).row();
        body.add(meshButton("Glass", TestData.GLASS_DAGGER));
        body.add(meshButton("Banner", TestData.BANNER));
        body.add(meshButton("Dwrv", TestData.DWRV)).row();
        body.add(meshButton("Cell", CELL_PREFIX + TestData.CENSUS_CELL));
        body.add(meshButton("Cave", CELL_PREFIX + TestData.ADDAMASARTUS));
        body.add(meshButton("Nix", CELL_PREFIX + TestData.PUNSABANIT)).row();
        body.add(meshButton("Guild", CELL_PREFIX + TestData.WOLVERINE_GUILD));
        body.add(meshButton("Town", EXT_PREFIX + TestData.TOWN_GRID_X + "," + TestData.TOWN_GRID_Y));
        body.add(meshButton("Zain", CELL_PREFIX + TestData.ZAINSIPILU)).row();
        hourLabel = new Label(hourText(), skin);
        body.add(hourLabel).width(80);
        hourSlider = new Slider(0f, 24f, 0.05f, false, skin);
        hourSlider.setValue(renderer.cycle.hour);
        hourSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (settingHour) {
                    return;
                }
                renderer.cycle.hour = hourSlider.getValue();
                renderer.cycle.playing = false;
                if (playClock != null) {
                    playClock.setText("Play");
                }
                hourLabel.setText(hourText());
            }
        });
        body.add(hourSlider).width(250).padRight(6);
        playClock = new TextButton("Play", skin);
        playClock.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                renderer.cycle.playing = !renderer.cycle.playing;
                playClock.setText(renderer.cycle.playing ? "Pause" : "Play");
            }
        });
        body.add(playClock).row();
        hudScroll = new ScrollPane(body, skin);
        hudScroll.setFadeScrollBars(false);
        hudScroll.setScrollingDisabled(true, false);
        hudWin = win;
        win.add(hudScroll);
        layoutHud();
        stage.addActor(win);

        loaderImage = new Image();
        loader = new Table();
        loader.setFillParent(true);
        loader.setTouchable(Touchable.enabled);
        loader.setBackground(dim);
        loader.add(loaderImage);
        loader.setVisible(false);
        stage.addActor(loader);

        walkTileCaption = new Label("", skin);
        walkBars = new ProgressBar[2 * EsmFile.CELL_GRID_RADIUS + 1][2 * EsmFile.CELL_GRID_RADIUS + 1];
        Table grid = new Table();
        int radius = EsmFile.CELL_GRID_RADIUS;
        for (int dy = radius; dy >= -radius; dy--) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (!EsmFile.inCellGrid(dx, dy, 0, 0, radius)) {
                    grid.add().size(38, 10).pad(1);
                    continue;
                }
                ProgressBar bar = new ProgressBar(0f, 1f, 0.01f, false, skin);
                bar.setAnimateDuration(0f);
                walkBars[dx + radius][dy + radius] = bar;
                grid.add(bar).size(36, 10).pad(1);
            }
            grid.row();
        }
        walkTiles = new Table();
        walkTiles.setFillParent(true);
        walkTiles.bottom().right().pad(12);
        walkTiles.setTouchable(Touchable.disabled);
        walkTiles.add(walkTileCaption).right().padBottom(4).row();
        walkTiles.add(grid);
        walkTiles.setVisible(false);
        stage.addActor(walkTiles);

        perfLabel = new Label("", skin);
        perfHud = new Table();
        perfHud.setFillParent(true);
        perfHud.top().right().pad(12);
        perfHud.setTouchable(Touchable.disabled);
        perfHud.add(perfLabel).right();
        stage.addActor(perfHud);
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

    private void layoutHud() {
        if (hudWin == null || hudScroll == null) {
            return;
        }
        float maxH = Math.max(160f, Gdx.graphics.getHeight() - 24f);
        hudScroll.getColor().a = 1f;
        hudWin.pack();
        if (hudWin.getHeight() > maxH) {
            hudScroll.setSize(hudScroll.getPrefWidth(), maxH - 40f);
            hudWin.setSize(hudWin.getPrefWidth(), maxH);
            hudWin.validate();
        }
        hudWin.setPosition(12, Gdx.graphics.getHeight() - hudWin.getHeight() - 12);
    }

    private void stepHour(float delta) {
        renderer.cycle.playing = false;
        if (playClock != null) {
            playClock.setText("Play");
        }
        renderer.cycle.hour += delta;
        renderer.cycle.wrapHour();
        syncHourHud();
    }

    private String hourText() {
        int h = (int) renderer.cycle.hour;
        int m = (int) ((renderer.cycle.hour - h) * 60f);
        return String.format("hour %02d:%02d", h, m);
    }

    private void syncHourHud() {
        if (hourSlider == null || hourLabel == null) {
            return;
        }
        settingHour = true;
        hourSlider.setValue(renderer.cycle.hour);
        settingHour = false;
        hourLabel.setText(hourText());
        if (playClock != null) {
            playClock.setText(renderer.cycle.playing ? "Pause" : "Play");
        }
    }

    @Override
    public void render() {
        profiler.beginFrame();
        pumpLoad();
        profiler.begin(FrameProfiler.WALK_STEP);
        pumpWalkLoad();
        profiler.end(FrameProfiler.WALK_STEP);
        handleCamera();
        maybeRecenterGrid();
        camera.viewportWidth = Gdx.graphics.getWidth();
        camera.viewportHeight = Gdx.graphics.getHeight();
        camera.position.set(eye);
        camera.direction.set(lookDir);
        camera.up.set(0, 1, 0);
        camera.update();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        renderer.cycle.tick(Gdx.graphics.getDeltaTime());
        syncHourHud();
        CellLighting mood = (cellBuilder != null && !isLoading()) ? cellBuilder.lighting : null;
        if (mood != null) {
            applyExteriorCycle(mood);
            mood.updateFlicker(Gdx.graphics.getDeltaTime());
            profiler.begin(FrameProfiler.UPDATE);
            cellBuilder.update(Gdx.graphics.getDeltaTime());
            profiler.end(FrameProfiler.UPDATE);
            Gdx.gl.glClearColor(mood.fogColor[0], mood.fogColor[1], mood.fogColor[2], 1f);
        } else {
            Gdx.gl.glClearColor(0.08f, 0.09f, 0.12f, 1f);
        }
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        if (root != null) {
            renderer.render(camera, root, mood);
        }
        lastGlError = Gdx.gl.glGetError();
        ForwardRenderer.resetForScene2d();
        profiler.begin(FrameProfiler.HUD);
        updateLoader();
        updateWalkTiles();
        fillSceneCounts();
        if (dumpCopiedLeft > 0f) {
            dumpCopiedLeft -= Gdx.graphics.getDeltaTime();
            if (dumpCopiedLeft <= 0f && dumpButton != null) {
                dumpButton.setText("Dump");
            }
        }
        if (posCopiedLeft > 0f) {
            posCopiedLeft -= Gdx.graphics.getDeltaTime();
            if (posCopiedLeft <= 0f && copyPosButton != null) {
                copyPosButton.setText("Copy pos");
            }
        }
        if (perfLabel != null && perfHudOn) {
            String hud = profiler.hudText();
            String debug = DebugVars.hudLine();
            perfLabel.setText(debug.isEmpty() ? hud : hud + "\n" + debug);
        }
        if (posLabel != null) {
            posLabel.setText(posLine());
        }
        if (status != null) {
            String err = lastError.isEmpty() ? "" : " err=" + lastError;
            String shortName = currentVfs.isEmpty() ? "?" : currentVfs.substring(currentVfs.lastIndexOf('/') + 1);
            String extra = "";
            if (cellBuilder != null) {
                extra = " placed=" + cellBuilder.placed + " lights=" + cellBuilder.lighting.lights.size()
                    + " fog=" + cellBuilder.lighting.fogDensity
                    + " skip=" + cellBuilder.skippedUnknown
                    + "+" + cellBuilder.skippedEmpty + "+" + cellBuilder.skippedActor
                    + "+" + cellBuilder.skippedNif;
            }
            String fps = String.format(java.util.Locale.US, " fps=%.0f", profiler.fps);
            status.setText(isLoading() ? loaderCaption.replace('\n', ' ')
                : shortName + "  glError=" + lastGlError + extra + walkStatus() + fps + err);
        }
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
        profiler.end(FrameProfiler.HUD);
        profiler.endFrame();
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
        String path = (cellBuilder != null ? "build/phase6-" : "build/phase2-") + stem + ".png";
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, w, h);
        com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.local(path), pm);
        pm.dispose();
        Gdx.app.log("JVM-MW", "mesh=" + currentVfs + " glError=" + lastGlError + " meshes=" + countMeshes(root)
            + (cellBuilder != null ? " lights=" + cellBuilder.lighting.lights.size()
                + " fog=" + cellBuilder.lighting.fogDensity : "")
            + " screenshot=" + path
            + (lastError.isEmpty() ? "" : " err=" + lastError));
    }

    private final StringBuilder snapshotBuf = new StringBuilder(1024);

    private String snapshotText() {
        snapshotBuf.setLength(0);
        snapshotBuf.append("cell=").append(currentVfs).append('\n');
        snapshotBuf.append("gl=(").append(eye.x).append(',').append(eye.y).append(',').append(eye.z).append(") yaw=")
            .append(yaw).append(" pitch=").append(pitch).append('\n');
        if (cellBuilder != null) {
            float tesX = eye.x;
            float tesY = -eye.z;
            float tesZ = eye.y - EYE_HEIGHT;
            snapshotBuf.append(posLine()).append('\n');
            snapshotBuf.append("tes=(").append(tesX).append(',').append(tesY).append(',').append(tesZ)
                .append(") eyeHeight=").append(EYE_HEIGHT)
                .append(" recast=(").append(NavmeshDb.recastTile(tesX)).append(',')
                .append(NavmeshDb.recastTile(tesY)).append(")\n");
            CollisionWorld col = cellBuilder.collision;
            snapshotBuf.append("onGround=").append(col.onGround)
                .append(" floorY=").append(Float.isNaN(col.floorY) ? "none" : col.floorY)
                .append(" ceilY=").append(Float.isNaN(col.ceilY) ? "none" : col.ceilY).append('\n');
            snapshotBuf.append("bullet=").append(BulletWorld.alive() ? 1 : 0)
                .append(" bodies=").append(BulletWorld.bodyCount()).append('\n');
            if (loadedCell != null && !loadedCell.interior) {
                snapshotBuf.append("grid=(").append(loadedCell.gridX).append(',').append(loadedCell.gridY).append(")\n");
            }
            CellLighting fog = cellBuilder.lighting;
            snapshotBuf.append("fogDensity=").append(fog.fogDensity)
                .append(" fogStart=").append(fog.fogStart)
                .append(" fogEnd=").append(fog.fogEnd)
                .append(" fogEnabled=").append(fog.fogEnabled)
                .append(" underwater=").append(eye.y < WaterMesh.HEIGHT && loadedCell != null && !loadedCell.interior)
                .append(" lights=").append(fog.lights.size())
                .append(" hour=").append(renderer.cycle.hour).append('\n');
            if (loadedCell != null && loadedCell.hasSpawn) {
                snapshotBuf.append("spawn inbound tes=(").append(loadedCell.spawnPos[0]).append(',')
                    .append(loadedCell.spawnPos[1]).append(',').append(loadedCell.spawnPos[2]).append(")\n");
            }
            snapshotBuf.append("nav=").append(cellBuilder.navPolys)
                .append(" tiles=").append(cellBuilder.navTiles)
                .append(" src=").append(cellBuilder.navSource)
                .append(" navPath=").append(cellBuilder.navPath)
                .append(" patch=").append(cellBuilder.navPatch).append('\n');
        } else {
            snapshotBuf.append("bullet=0\n");
        }
        snapshotBuf.append("glError=").append(lastGlError).append('\n');
        profiler.appendDump(snapshotBuf);
        DebugVars.appendDump(snapshotBuf);
        return snapshotBuf.toString();
    }

    private String posLine() {
        float tesX = eye.x;
        float tesY = -eye.z;
        float tesZ = eye.y - EYE_HEIGHT;
        int gx = LandRecord.cellGrid(tesX);
        int gy = LandRecord.cellGrid(tesY);
        return String.format(java.util.Locale.US,
            "gl=(%.0f,%.0f,%.0f) tes=(%.0f,%.0f,%.0f) grid=(%d,%d) recast=(%d,%d)",
            eye.x, eye.y, eye.z, tesX, tesY, tesZ, gx, gy,
            NavmeshDb.recastTile(tesX), NavmeshDb.recastTile(tesY));
    }

    private void copyPosition() {
        String text = posLine();
        copySnapshot(text);
        if (copyPosButton != null) {
            copyPosButton.setText("Copied");
            posCopiedLeft = 1.5f;
        }
    }

    private void writeSnapshotFile(String text) {
        try {
            Path out = Path.of("build/debug-snapshot.txt");
            Files.createDirectories(out.getParent());
            Files.writeString(out, text);
        } catch (Exception e) {
            Gdx.app.error("JVM-MW", "snapshot write failed", e);
        }
    }

    private static void copySnapshot(String text) {
        try {
            Gdx.app.getClipboard().setContents(text);
        } catch (Exception e) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            } catch (Exception e2) {
                Gdx.app.error("JVM-MW", "snapshot clipboard failed", e2);
            }
        }
    }

    private void debugSnapshot() {
        String text = snapshotText();
        Gdx.app.log("JVM-MW", "F3\n" + text.trim());
        copySnapshot(text);
        writeSnapshotFile(text);
        if (dumpButton != null) {
            dumpButton.setText("Copied");
            dumpCopiedLeft = 1.5f;
        }
    }

    private void fillSceneCounts() {
        profiler.meshes = countMeshes(root);
        if (cellBuilder != null) {
            profiler.placed = cellBuilder.placed;
            profiler.npc = cellBuilder.placedNpc;
            profiler.crea = cellBuilder.placedCrea;
            profiler.lights = cellBuilder.lighting.lights.size();
        } else {
            profiler.placed = 0;
            profiler.npc = 0;
            profiler.crea = 0;
            profiler.lights = 0;
        }
        profiler.landTiles = loadedCell != null && !loadedCell.interior ? loadedCell.tiles.size() : 0;
        profiler.texGpu = GpuCache.texGpu();
        profiler.nifGpu = GpuCache.nifGpu();
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
        float dt = Gdx.graphics.getDeltaTime();
        float scroll = -scrollAccum * 12f;
        scrollAccum = 0f;
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        float speed = Math.max(60f, moveScale) * 0.4f * dt;
        if (loadedCell != null && !loadedCell.interior) {
            speed *= shift ? 30f : 9f;
        } else {
            speed *= shift ? 10f : 1f;
        }
        float radYaw = (float) Math.toRadians(yaw);
        float sin = (float) Math.sin(radYaw);
        float cos = (float) Math.cos(radYaw);
        float dx = lookDir.x * scroll;
        float dy = lookDir.y * scroll;
        float dz = lookDir.z * scroll;
        boolean forward = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean back = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        boolean left = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        if (forward) {
            dx += sin * speed;
            dz += cos * speed;
        }
        if (back) {
            dx -= sin * speed;
            dz -= cos * speed;
        }
        if (right) {
            dx -= cos * speed;
            dz += sin * speed;
        }
        if (left) {
            dx += cos * speed;
            dz -= sin * speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            dy += speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
            dy -= speed;
        }
        if (cellBuilder != null && !isLoading()) {
            cellBuilder.collision.move(eye, dx, dy, dz, dt);
        } else {
            eye.add(dx, dy, dz);
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        stage.getViewport().update(width, height, true);
        layoutHud();
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
        if (walkBuilder != null) {
            walkBuilder.dispose();
            walkBuilder = null;
        }
        BulletWorld.disposeWorld();
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
