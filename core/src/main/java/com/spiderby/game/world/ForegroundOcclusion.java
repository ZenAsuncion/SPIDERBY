package com.spiderby.game.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * Draws the transparent foreground layer above the player.
 *
 * barangay_foreground.png contains:
 * - tree leaves
 * - tree canopies
 * - roof portions
 * - other objects that should cover the player
 *
 * The player can still walk underneath these areas because
 * collision is controlled separately by barangay.tmx.
 */
public class ForegroundOcclusion {

    private static final String FOREGROUND_PATH =
            "map/barangay_foreground.png";

    private Texture foregroundTexture;

    private final float mapWidth;
    private final float mapHeight;


    public ForegroundOcclusion(
            float mapWidth,
            float mapHeight) {

        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;


        // Make sure the foreground exists.
        if (!Gdx.files.internal(
                FOREGROUND_PATH).exists()) {

            throw new GdxRuntimeException(
                    "Missing foreground image:\n"
                    + "assets/"
                    + FOREGROUND_PATH
            );
        }


        // Load foreground.
        foregroundTexture =
                new Texture(
                        Gdx.files.internal(
                                FOREGROUND_PATH
                        )
                );


        foregroundTexture.setFilter(
                Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest
        );


        // Foreground must match the map size.
        if (foregroundTexture.getWidth()
                != (int) mapWidth
                ||
                foregroundTexture.getHeight()
                != (int) mapHeight) {

            int foregroundWidth =
                    foregroundTexture.getWidth();

            int foregroundHeight =
                    foregroundTexture.getHeight();

            foregroundTexture.dispose();
            foregroundTexture = null;

            throw new GdxRuntimeException(
                    "Foreground size does not match map size.\n\n"
                    + "Map size: "
                    + (int) mapWidth
                    + " x "
                    + (int) mapHeight
                    + "\n"
                    + "Foreground size: "
                    + foregroundWidth
                    + " x "
                    + foregroundHeight
            );
        }


        Gdx.app.log(
                "Foreground",
                "barangay_foreground.png loaded successfully."
        );
    }


    /**
     * Draw the foreground.
     *
     * Barangay.java calls this AFTER player.render(batch).
     *
     * Because of that, leaves and roofs will appear
     * over the player.
     */
    public void render(SpriteBatch batch) {

        if (foregroundTexture == null) {
            return;
        }

        batch.draw(
                foregroundTexture,
                0f,
                0f,
                mapWidth,
                mapHeight
        );
    }


    /**
     * Release texture when game closes.
     */
    public void dispose() {

        if (foregroundTexture != null) {

            foregroundTexture.dispose();

            foregroundTexture = null;
        }
    }
}