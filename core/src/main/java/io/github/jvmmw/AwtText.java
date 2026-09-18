package io.github.jvmmw;

import com.badlogic.gdx.graphics.Pixmap;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Desktop AWT rasterizer so loader copy is sharp instead of a scaled bitmap font. */
final class AwtText {
    private AwtText() {
    }

    static Pixmap render(String text, int size) {
        String[] lines = text.split("\n", -1);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, size);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        pg.dispose();
        int lineH = fm.getHeight();
        int width = 1;
        for (String line : lines) {
            width = Math.max(width, fm.stringWidth(line));
        }
        int pad = Math.max(8, size / 4);
        int height = Math.max(1, lineH * lines.length);
        BufferedImage img = new BufferedImage(width + pad * 2, height + pad * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);
        int y = pad + fm.getAscent();
        for (String line : lines) {
            int x = pad + (width - fm.stringWidth(line)) / 2;
            g.drawString(line, x, y);
            y += lineH;
        }
        g.dispose();
        return toPixmap(img);
    }

    private static Pixmap toPixmap(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int a = (argb >>> 24) & 0xff;
            int r = (argb >>> 16) & 0xff;
            int g = (argb >>> 8) & 0xff;
            int b = argb & 0xff;
            pm.drawPixel(i % w, i / w, (r << 24) | (g << 16) | (b << 8) | a);
        }
        return pm;
    }
}
