package io.github.jvmmw.resource;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Minimal DDS (DXT1/3/5 and uncompressed) upload for Morrowind textures. */
public final class DdsTexture {
    public final int textureId;
    public final int width;
    public final int height;

    private DdsTexture(int textureId, int width, int height) {
        this.textureId = textureId;
        this.width = width;
        this.height = height;
    }

    public static DdsTexture load(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.getInt() != 0x20534444) {
            throw new IllegalArgumentException("Not a DDS file: " + path);
        }
        int headerSize = buf.getInt();
        if (headerSize != 124) {
            throw new IllegalArgumentException("Bad DDS header size in " + path);
        }
        buf.getInt(); // flags
        int height = buf.getInt();
        int width = buf.getInt();
        buf.getInt(); // pitch
        int depth = buf.getInt();
        int mipMapCount = Math.max(1, buf.getInt());
        buf.position(buf.position() + 44); // reserved
        int pfSize = buf.getInt();
        int pfFlags = buf.getInt();
        int fourCC = buf.getInt();
        int rgbBits = buf.getInt();
        int rMask = buf.getInt();
        int gMask = buf.getInt();
        int bMask = buf.getInt();
        int aMask = buf.getInt();
        buf.position(4 + headerSize); // start of data after magic+header

        int id = Gdx.gl.glGenTexture();
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, id);
        int minFilter = mipMapCount > 1 ? GL20.GL_LINEAR_MIPMAP_LINEAR : GL20.GL_LINEAR;
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, minFilter);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_REPEAT);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_REPEAT);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, Math.max(0, mipMapCount - 1));

        boolean compressed = (pfFlags & 0x4) != 0;
        if (compressed) {
            int format = switch (fourCC) {
                case 0x31545844 -> 0x83F0; // DXT1
                case 0x33545844 -> 0x83F2; // DXT3
                case 0x35545844 -> 0x83F3; // DXT5
                default -> throw new IllegalArgumentException("Unsupported DDS FourCC in " + path);
            };
            int w = width;
            int h = height;
            int blockSize = (format == 0x83F0) ? 8 : 16;
            for (int level = 0; level < mipMapCount; level++) {
                int size = Math.max(1, (w + 3) / 4) * Math.max(1, (h + 3) / 4) * blockSize;
                byte[] chunk = new byte[size];
                buf.get(chunk);
                ByteBuffer pixels = ByteBuffer.allocateDirect(size);
                pixels.put(chunk).flip();
                Gdx.gl.glCompressedTexImage2D(GL20.GL_TEXTURE_2D, level, format, w, h, 0, size, pixels);
                w = Math.max(1, w / 2);
                h = Math.max(1, h / 2);
            }
        } else {
            int bpp = rgbBits / 8;
            int w = width;
            int h = height;
            for (int level = 0; level < mipMapCount; level++) {
                int size = w * h * bpp;
                byte[] chunk = new byte[size];
                buf.get(chunk);
                ByteBuffer pixels = ByteBuffer.allocateDirect(size);
                pixels.put(chunk).flip();
                int glFmt = bpp == 4 ? GL20.GL_RGBA : GL20.GL_RGB;
                Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, level, glFmt, w, h, 0, glFmt, GL20.GL_UNSIGNED_BYTE, pixels);
                w = Math.max(1, w / 2);
                h = Math.max(1, h / 2);
            }
            if (mipMapCount == 1) {
                Gdx.gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
            }
        }
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        return new DdsTexture(id, width, height);
    }

    public void dispose() {
        Gdx.gl.glDeleteTexture(textureId);
    }
}
