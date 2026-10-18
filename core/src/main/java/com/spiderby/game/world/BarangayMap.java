package com.spiderby.game.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class BarangayMap {

    private final TiledMap map;
    private final OrthogonalTiledMapRenderer renderer;

    private final float width;
    private final float height;

    public BarangayMap() {

        if (!Gdx.files.internal("map/barangay.tmx").exists()) {
            throw new GdxRuntimeException(
                    "Missing assets/map/barangay.tmx"
            );
        }

        TmxMapLoader.Parameters parameters =
                new TmxMapLoader.Parameters();

        parameters.textureMinFilter =
                Texture.TextureFilter.Nearest;

        parameters.textureMagFilter =
                Texture.TextureFilter.Nearest;

        parameters.flipY = true;
        parameters.convertObjectToTileSpace = false;

        map = new TmxMapLoader().load(
                "map/barangay.tmx",
                parameters
        );

        renderer =
                new OrthogonalTiledMapRenderer(
                        map,
                        1f
                );

        MapProperties properties =
                map.getProperties();

        width =
                properties.get("width", Integer.class)
                * properties.get("tilewidth", Integer.class);

        height =
                properties.get("height", Integer.class)
                * properties.get("tileheight", Integer.class);
    }

    public void render(OrthographicCamera camera) {

        renderer.setView(camera);
        renderer.render();
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

        renderer.dispose();
        map.dispose();
    }
}