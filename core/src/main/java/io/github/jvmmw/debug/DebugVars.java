package io.github.jvmmw.debug;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Viewer knobs you can flip while testing. Add a public static here, then
 * read it from the code that needs it. Override without a rebuild:
 * jvmmw.debug.NAME in gitignored local.properties, -Djvmmw.debug.NAME, or
 * env JVMMW_DEBUG_NAME (dots to underscores).
 *
 * Dump / F4 list these. Vanilla wander is speed 1, turn 900, radius 0 and
 * node radius 0 (ESM AI_W), frequency 1.
 */
public final class DebugVars {
    /**
     * Wander move speed. 1 is the walk clip's own stride (feet match the
     * ground). 3 is a fast walk, not a 3× moonwalk.
     */
    public static final float wanderSpeed = f("wanderSpeed", 3f);

    /**
     * Creature wander move speed. Same units as wanderSpeed. NPCs keep
     * wanderSpeed.
     */
    public static final float creaWanderSpeed = f("creaWanderSpeed", 9f);

    /**
     * Pivot degrees per second. OpenMW is 900. Does not follow wanderSpeed.
     */
    public static final float wanderTurn = f("wanderTurn", 270f);

    /** TES-unit cap from spawn for random (no-grid) dests. 0 uses ESM AI_W. */
    public static final float wanderRadius = f("wanderRadius", 900f);

    /** TES-unit hop cap from where they stand now, for pathgrid dests. 0 uses ESM AI_W. */
    public static final float nodeWanderRadius = f("nodeWanderRadius", 900f);

    /** How often they pick a new point. 1 is a 2–5 s pause; 2 is twice as often. */
    public static final float wanderFrequency = f("wanderFrequency", 5f);

    private static Properties localProps;

    private DebugVars() {
    }

    public static void appendDump(StringBuilder sb) {
        sb.append("debug.wanderSpeed=").append(wanderSpeed)
            .append(" creaWanderSpeed=").append(creaWanderSpeed)
            .append(" wanderTurn=").append(wanderTurn)
            .append(" wanderRadius=").append(wanderRadius)
            .append(" nodeWanderRadius=").append(nodeWanderRadius)
            .append(" wanderFrequency=").append(wanderFrequency)
            .append('\n');
    }

    public static String hudLine() {
        return String.format(Locale.US, "debug spd=%.2g crea=%.2g turn=%.2g r=%.0f node=%.0f freq=%.2g",
            wanderSpeed, creaWanderSpeed, wanderTurn, wanderRadius, nodeWanderRadius, wanderFrequency);
    }

    private static float f(String name, float fallback) {
        String raw = value(name);
        if (raw == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String value(String name) {
        String key = "jvmmw.debug." + name;
        String env = System.getenv("JVMMW_DEBUG_" + name.replace('.', '_').toUpperCase(Locale.ROOT));
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        Properties p = localProperties();
        if (p != null) {
            String v = p.getProperty(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static Properties localProperties() {
        if (localProps != null) {
            return localProps;
        }
        Path local = Path.of("local.properties");
        if (!Files.isRegularFile(local)) {
            localProps = new Properties();
            return localProps;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(local)) {
            p.load(in);
        } catch (IOException e) {
            localProps = new Properties();
            return localProps;
        }
        localProps = p;
        return p;
    }
}
