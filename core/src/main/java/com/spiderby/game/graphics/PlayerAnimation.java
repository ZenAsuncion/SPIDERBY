package com.spiderby.game.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Handles the four directional walking sprite sheets. */
public class PlayerAnimation {

    public enum Direction { UP, DOWN, LEFT, RIGHT }

    private static final float FRAME_TIME = 0.15f;
    private static final int COLUMNS = 5;
    private static final int ROWS = 5;

    private final Texture upTexture;
    private final Texture downTexture;
    private final Texture leftTexture;
    private final Texture rightTexture;

    private final Animation<TextureRegion> up;
    private final Animation<TextureRegion> down;
    private final Animation<TextureRegion> left;
    private final Animation<TextureRegion> right;

    public PlayerAnimation() {
        upTexture = loadTexture("walk_up.png", false);
        downTexture = loadTexture("walk_down.png", true);
        leftTexture = loadTexture("walk_left.png", false);
        rightTexture = loadTexture("walk_right.png", false);

        up = makeAnimation(upTexture, 23);
        down = makeAnimation(downTexture, 25);
        left = makeAnimation(leftTexture, 25);
        right = makeAnimation(rightTexture, 25);
    }

    private Texture loadTexture(String filename, boolean removeGreen) {
        String path = "character1/" + filename;

        if (!Gdx.files.internal(path).exists()) {
            throw new IllegalStateException("Missing asset: assets/" + path);
        }

        if (!removeGreen) {
            Texture texture = new Texture(Gdx.files.internal(path));
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return texture;
        }

        // Keeps the existing green-background removal for walk_down.png.
        Pixmap source = new Pixmap(Gdx.files.internal(path));
        Pixmap result = new Pixmap(source.getWidth(), source.getHeight(), Pixmap.Format.RGBA8888);
        result.setBlending(Pixmap.Blending.None);

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getPixel(x, y);
                int r = (pixel >>> 24) & 255;
                int g = (pixel >>> 16) & 255;
                int b = (pixel >>> 8) & 255;
                int a = pixel & 255;

                boolean green = Math.abs(r - 70) < 12
                        && Math.abs(g - 112) < 12
                        && Math.abs(b - 77) < 12;

                result.drawPixel(x, y, green || a == 0 ? 0x00000000 : pixel);
            }
        }

        Texture texture = new Texture(result);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        source.dispose();
        result.dispose();
        return texture;
    }

    private Animation<TextureRegion> makeAnimation(Texture sheet, int count) {
        int frameWidth = sheet.getWidth() / COLUMNS;
        int frameHeight = sheet.getHeight() / ROWS;

        TextureRegion[] frames = new TextureRegion[count];

        for (int i = 0; i < count; i++) {
            frames[i] = new TextureRegion(
                    sheet,
                    (i % COLUMNS) * frameWidth,
                    (i / COLUMNS) * frameHeight,
                    frameWidth,
                    frameHeight
            );
        }

        return new Animation<>(FRAME_TIME, frames);
    }

    public TextureRegion getFrame(Direction direction, float time, boolean moving) {
        Animation<TextureRegion> selected;

        switch (direction) {
            case UP:
                selected = up;
                break;
            case LEFT:
                selected = left;
                break;
            case RIGHT:
                selected = right;
                break;
            case DOWN:
            default:
                selected = down;
                break;
        }

        if (moving) {
            return selected.getKeyFrame(time, true);
        }

        TextureRegion[] frames = selected.getKeyFrames();
        int idleIndex = direction == Direction.RIGHT ? 3 : 4;
        return frames[Math.min(idleIndex, frames.length - 1)];
    }

    public void dispose() {
        upTexture.dispose();
        downTexture.dispose();
        leftTexture.dispose();
        rightTexture.dispose();
    }
}
