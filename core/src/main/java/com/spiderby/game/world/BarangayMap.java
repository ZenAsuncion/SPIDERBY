package com.spiderby.game.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.GdxRuntimeException;

/** Loads the flat barangay map and its transparent foreground overlay. */
public class BarangayMap {

    private static final String TMX_PATH = "map/barangay.tmx";
    private static final String BASE_MAP_PATH = "map/barangay_map.png";
    private static final String FOREGROUND_PATH = "map/barangay_foreground.png";

    private final TiledMap map;
    private final OrthogonalTiledMapRenderer renderer;
    private final Texture foregroundTexture;
    private final float width;
    private final float height;
    private final int backgroundLayerIndex;

    public BarangayMap() {
        requireFile(TMX_PATH);
        requireFile(BASE_MAP_PATH);
        requireFile(FOREGROUND_PATH);

        TmxMapLoader.Parameters settings = new TmxMapLoader.Parameters();
        settings.textureMinFilter = Texture.TextureFilter.Nearest;
        settings.textureMagFilter = Texture.TextureFilter.Nearest;

        // Tiled uses top-left object coordinates. LibGDX's loader converts them
        // to the same bottom-left world used by Player/CollisionManager.
        settings.flipY = true;
        settings.convertObjectToTileSpace = false;

        map = new TmxMapLoader().load(TMX_PATH, settings);
        renderer = new OrthogonalTiledMapRenderer(map, 1f);

        MapProperties properties = map.getProperties();
        Integer mapTilesWide = properties.get("width", Integer.class);
        Integer mapTilesHigh = properties.get("height", Integer.class);
        Integer tileWidth = properties.get("tilewidth", Integer.class);
        Integer tileHeight = properties.get("tileheight", Integer.class);

        if (mapTilesWide == null || mapTilesHigh == null
                || tileWidth == null || tileHeight == null) {
            throw new GdxRuntimeException("barangay.tmx is missing map size properties.");
        }

        width = mapTilesWide * tileWidth;
        height = mapTilesHigh * tileHeight;

        backgroundLayerIndex = findLayerIndex("Background");
        if (backgroundLayerIndex < 0) {
            throw new GdxRuntimeException("Tiled layer 'Background' is missing.");
        }

        foregroundTexture = new Texture(Gdx.files.internal(FOREGROUND_PATH));
        foregroundTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // The overlay must be pixel-for-pixel the same size as the source map.
        if (foregroundTexture.getWidth() != (int) width
                || foregroundTexture.getHeight() != (int) height) {
            throw new GdxRuntimeException(
                    "barangay_foreground.png must be " + (int) width + "x" + (int) height
                    + ", but it is " + foregroundTexture.getWidth() + "x"
                    + foregroundTexture.getHeight() + "."
            );
        }
    }

    private void requireFile(String path) {
        if (!Gdx.files.internal(path).exists()) {
            throw new GdxRuntimeException("Missing asset: assets/" + path);
        }
    }

    private int findLayerIndex(String name) {
        for (int i = 0; i < map.getLayers().getCount(); i++) {
            MapLayer layer = map.getLayers().get(i);
            if (name.equals(layer.getName())) return i;
        }
        return -1;
    }

    /** Draw ONLY the flat Background image layer from the TMX. */
    public void renderBackground(OrthographicCamera camera) {
        renderer.setView(camera);
        renderer.render(new int[] { backgroundLayerIndex });
    }

    /** Draw the transparent canopy/roof image above the player. */
    public void renderForeground(SpriteBatch batch) {
        batch.draw(foregroundTexture, 0f, 0f, width, height);
    }

    public TiledMap getTiledMap() {
        return map;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public void dispose() {
        foregroundTexture.dispose();
        renderer.dispose();
        map.dispose();
    }
}
