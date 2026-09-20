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
 * Dump / F4 list these. Vanilla wander is speed 1, turn 900, radius 1, max 0 (no cap),
 * frequency 1.
 */
public final class DebugVars {
    /**
     * Wander move and walk-cycle speed. Vanilla is 80 TES units/s at 1.
     */
    public static final float wanderSpeed = f("wanderSpeed", 2f);

    /**
     * Pivot degrees per second. OpenMW is 900. Does not follow wanderSpeed.
     */
    public static final float wanderTurn = f("wanderTurn", 270f);

    /** Times the ESM AI_W radius. 1 is the full recorded distance. */
    public static final float wanderRadius = f("wanderRadius", 0.3f);

    /** Cap on that radius in TES units. 0 means no cap. */
    public static final float wanderRadiusMax = f("wanderRadiusMax", 256f);

    /** How often they pick a new point. 1 is a 2–5 s pause; 2 is twice as often. */
    public static final float wanderFrequency = f("wanderFrequency", 3f);

    private static Properties localProps;

    private DebugVars() {
    }

    public static void appendDump(StringBuilder sb) {
        sb.append("debug.wanderSpeed=").append(wanderSpeed)
            .append(" wanderTurn=").append(wanderTurn)
            .append(" wanderRadius=").append(wanderRadius)
            .append(" wanderRadiusMax=").append(wanderRadiusMax)
            .append(" wanderFrequency=").append(wanderFrequency)
            .append('\n');
    }

    public static String hudLine() {
        return String.format(Locale.US, "debug spd=%.2g turn=%.2g r=%.2g max=%.0f freq=%.2g",
            wanderSpeed, wanderTurn, wanderRadius, wanderRadiusMax, wanderFrequency);
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
