package com.spiderby.game.camera;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.spiderby.game.entities.Player;

/** Centers on the player unless that would show empty space outside the map. */
public class CameraController {
    private static final float VIEW_HEIGHT = 480f;
    private final OrthographicCamera camera;
    private final float worldWidth, worldHeight;

    public CameraController(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 640f, VIEW_HEIGHT);
    }

    public void update(Player player) {
        float x = player.getX() + player.getWidth() / 2f;
        float y = player.getY() + player.getHeight() / 2f;
        float hw = camera.viewportWidth / 2f;
        float hh = camera.viewportHeight / 2f;

        x = worldWidth > camera.viewportWidth
                ? MathUtils.clamp(x, hw, worldWidth - hw)
                : worldWidth / 2f;
        y = worldHeight > camera.viewportHeight
                ? MathUtils.clamp(y, hh, worldHeight - hh)
                : worldHeight / 2f;

        camera.position.set(x, y, 0f);
        camera.update();
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.viewportHeight = VIEW_HEIGHT;
        camera.viewportWidth = VIEW_HEIGHT * width / (float) height;
        camera.update();
    }

    public OrthographicCamera getCamera() { return camera; }
}
