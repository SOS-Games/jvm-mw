package io.github.jvmmw.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import io.github.jvmmw.JvmMwApp;

/**
 * Desktop entry: open a GL 3.3 window, then the viewer.
 * gradlew.bat lwjgl3:run
 */
public final class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return;
        }
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("JVM-MW");
        config.useVsync(true);
        config.setWindowedMode(1280, 720);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 0, 0);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        JvmMwApp app = new JvmMwApp();
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
                app.setWindowFocused(false);
            }

            @Override
            public void focusGained() {
                app.setWindowFocused(true);
            }
        });
        new Lwjgl3Application(app, config);
    }
}
