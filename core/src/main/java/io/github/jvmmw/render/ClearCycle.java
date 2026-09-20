package io.github.jvmmw.render;

/**
 * Clear-weather hour: sky, fog, sun color, sun in the sky, night flag.
 * No other weathers yet. The HUD slider, [ ], and Play scrub the hour
 * (stars after sunset).
 */
public final class ClearCycle {
    public static final float SUNRISE = 6f;
    public static final float SUNSET = 18f;
    public static final float SUNRISE_DUR = 2f;
    public static final float SUNSET_DUR = 2f;
    public static final float NIGHT_END = SUNRISE;
    public static final float DAY_START = SUNRISE + SUNRISE_DUR;
    public static final float DAY_END = SUNSET;
    public static final float NIGHT_START = SUNSET + SUNSET_DUR;
    /** Peak of the day orbit. */
    public static final float DEFAULT_HOUR = 13f;
    public static final float PLAY_HOURS_PER_SEC = 1f;
    public static final float STARS_POST_SUNSET = 1f;
    public static final float STARS_FADING = 2f;

    public float hour = DEFAULT_HOUR;
    public boolean playing;

    public final float[] sky = new float[3];
    public final float[] fog = new float[3];
    public final float[] ambient = new float[3];
    public final float[] sun = new float[3];
    public final float[] cloudEmission = new float[3];
    public final float[] sunPosTes = new float[3];
    public final float[] lightDirGl = new float[3];
    public float nightFade;
    public float sunAlpha;
    /** {@code Glare_View * glareFade}; water specular alpha. Clear glare is 1. */
    public float sunVis;
    public boolean night;

    private static final float[] SKY_RISE = rgb(117, 141, 164);
    private static final float[] SKY_DAY = rgb(95, 135, 203);
    private static final float[] SKY_SET = rgb(56, 89, 129);
    private static final float[] SKY_NIGHT = rgb(9, 10, 11);
    private static final float[] FOG_RISE = rgb(255, 189, 157);
    private static final float[] FOG_DAY = rgb(206, 227, 255);
    private static final float[] FOG_SET = rgb(255, 189, 157);
    private static final float[] FOG_NIGHT = rgb(9, 10, 11);
    private static final float[] AMB_RISE = rgb(47, 66, 96);
    private static final float[] AMB_DAY = rgb(137, 140, 160);
    private static final float[] AMB_SET = rgb(68, 75, 96);
    private static final float[] AMB_NIGHT = rgb(32, 35, 42);
    private static final float[] SUN_RISE = rgb(242, 159, 119);
    private static final float[] SUN_DAY = rgb(255, 252, 238);
    private static final float[] SUN_SET = rgb(255, 114, 79);
    private static final float[] SUN_NIGHT = rgb(59, 97, 176);

    private static final float[] SKY_T = {0.5f, 1f, 1.5f, 0.5f};
    private static final float[] FOG_T = {0.5f, 1f, 2f, 1f};
    private static final float[] AMB_T = {0.5f, 2f, 1f, 1.25f};
    private static final float[] SUN_T = {0f, 0f, 1f, 1.25f};
    private static final float[] STAR_T = {2f, 0f, 1f, 1f};

    public void wrapHour() {
        hour = hour % 24f;
        if (hour < 0f) {
            hour += 24f;
        }
    }

    public void tick(float dt) {
        if (!playing) {
            return;
        }
        hour += dt * PLAY_HOURS_PER_SEC;
        wrapHour();
    }

    public void evaluate() {
        wrapHour();
        colorAt(hour, SKY_T, SKY_RISE, SKY_DAY, SKY_SET, SKY_NIGHT, sky);
        colorAt(hour, FOG_T, FOG_RISE, FOG_DAY, FOG_SET, FOG_NIGHT, fog);
        colorAt(hour, AMB_T, AMB_RISE, AMB_DAY, AMB_SET, AMB_NIGHT, ambient);
        colorAt(hour, SUN_T, SUN_RISE, SUN_DAY, SUN_SET, SUN_NIGHT, sun);
        nightFade = floatAt(hour, STAR_T, 0f, 0f, 0f, 1f);
        night = hour < SUNRISE || hour > NIGHT_START + STARS_POST_SUNSET - STARS_FADING;
        sunAlpha = sunPercentage(hour);
        sunVis = glareFade(hour);
        cloudEmission[0] = fog[0] + 0.13f;
        cloudEmission[1] = fog[1] + 0.13f;
        cloudEmission[2] = fog[2] + 0.13f;
        orbit(hour, sunPosTes, lightDirGl);
    }

    public void applyLighting(CellLighting lighting) {
        lighting.ambient[0] = ambient[0];
        lighting.ambient[1] = ambient[1];
        lighting.ambient[2] = ambient[2];
        lighting.sunDiffuse[0] = sun[0];
        lighting.sunDiffuse[1] = sun[1];
        lighting.sunDiffuse[2] = sun[2];
        lighting.fogColor[0] = fog[0];
        lighting.fogColor[1] = fog[1];
        lighting.fogColor[2] = fog[2];
        lighting.sunDir[0] = lightDirGl[0];
        lighting.sunDir[1] = lightDirGl[1];
        lighting.sunDir[2] = lightDirGl[2];
    }

    /** OpenMW {@code WeatherManager::getSunPercentage}. */
    public static float sunPercentage(float hour) {
        if (hour <= NIGHT_END || hour >= NIGHT_START) {
            return 0f;
        }
        if (hour <= DAY_START) {
            return (hour - NIGHT_END) / SUNRISE_DUR;
        }
        if (hour > DAY_END) {
            return 1f - (hour - DAY_END) / SUNSET_DUR;
        }
        return 1f;
    }

    /**
     * OpenMW {@code WeatherManager::update} glareFade. Zero when the sun is
     * below the horizon so water specular does not keep the night orbit.
     */
    public static float glareFade(float hour) {
        if (hour < SUNRISE || hour > NIGHT_START) {
            return 0f;
        }
        float peak = SUNRISE + (NIGHT_START - SUNRISE) * 0.5f;
        if (hour < peak) {
            return 1f - (peak - hour) / (peak - SUNRISE);
        }
        return 1f - (hour - peak) / (NIGHT_START - peak);
    }

    /**
     * Weather orbit then {@code RenderingManager::setSunDirection}: disc TES3
     * position and GL light direction ({@code match sunlight to sun} false).
     */
    static void orbit(float hour, float[] sunPosTes, float[] lightDirGl) {
        float adjusted = hour < SUNRISE ? hour + 24f : hour;
        float nightStart = NIGHT_START;
        boolean isNight = adjusted >= nightStart;
        float dayDur = nightStart - SUNRISE;
        float nightDur = 24f - dayDur;
        float orbit;
        if (!isNight) {
            float t = (adjusted - SUNRISE) / dayDur;
            orbit = 1f - 2f * t;
        } else {
            float t = (adjusted - nightStart) / nightDur;
            orbit = 2f * t - 1f;
        }
        float wx = -400f * orbit;
        float wy = 75f;
        float wz = -100f;
        float px = -wx;
        float py = -wy;
        float pz = 400f - Math.abs(px);
        sunPosTes[0] = px;
        sunPosTes[1] = py;
        sunPosTes[2] = pz;
        float lx = -wx;
        float ly = -wz;
        float lz = wy;
        float len = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (len < 1e-6f) {
            lightDirGl[0] = 0f;
            lightDirGl[1] = 1f;
            lightDirGl[2] = 0f;
            return;
        }
        lightDirGl[0] = lx / len;
        lightDirGl[1] = ly / len;
        lightDirGl[2] = lz / len;
    }

    private static void colorAt(float hour, float[] t, float[] rise, float[] day, float[] set, float[] night,
        float[] out) {
        sample(hour, t, rise, day, set, night, out);
    }

    private static float floatAt(float hour, float[] t, float rise, float day, float set, float night) {
        float[] a = {rise};
        float[] b = {day};
        float[] c = {set};
        float[] d = {night};
        float[] o = new float[1];
        sample(hour, t, a, b, c, d, o);
        return o[0];
    }

    private static void sample(float h, float[] t, float[] rise, float[] day, float[] set, float[] night, float[] out) {
        float preRise = t[0];
        float postRise = t[1];
        float preSet = t[2];
        float postSet = t[3];
        if (h < NIGHT_END - preRise || h > NIGHT_START + postSet) {
            copy(night, out);
            return;
        }
        if (h >= NIGHT_END - preRise && h <= DAY_START + postRise) {
            float duration = DAY_START + postRise - NIGHT_END + preRise;
            float middle = NIGHT_END - preRise + duration / 2f;
            if (h <= middle) {
                float factor = duration > 0f ? (middle - h) / duration * 2f : 0f;
                mix(rise, night, factor, out);
            } else {
                float factor = duration > 0f ? (h - middle) / duration * 2f : 1f;
                mix(rise, day, factor, out);
            }
            return;
        }
        if (h > DAY_START + postRise && h < DAY_END - preSet) {
            copy(day, out);
            return;
        }
        if (h >= DAY_END - preSet && h <= NIGHT_START + postSet) {
            float duration = NIGHT_START + postSet - DAY_END + preSet;
            float middle = DAY_END - preSet + duration / 2f;
            if (h <= middle) {
                float factor = duration > 0f ? (middle - h) / duration * 2f : 0f;
                mix(set, day, factor, out);
            } else {
                float factor = duration > 0f ? (h - middle) / duration * 2f : 1f;
                mix(set, night, factor, out);
            }
            return;
        }
        copy(night, out);
    }

    private static void mix(float[] a, float[] b, float t, float[] out) {
        for (int i = 0; i < out.length; i++) {
            out[i] = a[i] + (b[i] - a[i]) * t;
        }
    }

    private static void copy(float[] src, float[] dst) {
        System.arraycopy(src, 0, dst, 0, dst.length);
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[] {r / 255f, g / 255f, b / 255f};
    }
}
