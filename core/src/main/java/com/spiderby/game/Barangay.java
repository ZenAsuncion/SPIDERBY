package com.spiderby.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spiderby.game.camera.CameraController;
import com.spiderby.game.entities.Player;
import com.spiderby.game.world.BarangayMap;
import com.spiderby.game.world.CollisionManager;

/** Main game screen for Spiderby. */
public class Barangay extends ApplicationAdapter {

    private SpriteBatch batch;
    private BarangayMap barangayMap;
    private CollisionManager collisionManager;
    private Player player;
    private CameraController cameraController;

    @Override
    public void create() {
        batch = new SpriteBatch();

        // Load the TMX background + the transparent foreground PNG.
        barangayMap = new BarangayMap();

        // IMPORTANT: CollisionManager needs the actual TiledMap.
        collisionManager = new CollisionManager(barangayMap.getTiledMap());

        // Constructor order is:
        // x, y, width, height, worldWidth, worldHeight, collisionManager
        player = new Player(
                300f,
                300f,
                48f,
                64f,
                barangayMap.getWidth(),
                barangayMap.getHeight(),
                collisionManager
        );

        cameraController = new CameraController(
                barangayMap.getWidth(),
                barangayMap.getHeight()
        );
        cameraController.update(player);
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 20f);

        player.update(delta);
        cameraController.update(player);

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 1) Draw the original TMX/base map.
        barangayMap.renderBackground(cameraController.getCamera());

        // 2) Draw player, then the transparent foreground LAST.
        // Any non-transparent tree/roof pixels therefore cover the player.
        batch.setProjectionMatrix(cameraController.getCamera().combined);
        batch.begin();
        player.render(batch);
        barangayMap.renderForeground(batch);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        cameraController.resize(width, height);
        cameraController.update(player);
    }

    @Override
    public void dispose() {
        if (player != null) player.dispose();
        if (barangayMap != null) barangayMap.dispose();
        if (batch != null) batch.dispose();
    }
}
