package com.spiderby.game.camera;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.spiderby.game.entities.Player;

/** Follows the player without showing empty space outside the map. */
public class CameraController {

    private static final float VIEW_HEIGHT = 480f;

    private final OrthographicCamera camera;
    private final float worldWidth;
    private final float worldHeight;

    public CameraController(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 640f, VIEW_HEIGHT);
    }

    public void update(Player player) {
        float cameraX = player.getX() + player.getWidth() / 2f;
        float cameraY = player.getY() + player.getHeight() / 2f;

        float halfWidth = camera.viewportWidth / 2f;
        float halfHeight = camera.viewportHeight / 2f;

        cameraX = worldWidth > camera.viewportWidth
                ? MathUtils.clamp(cameraX, halfWidth, worldWidth - halfWidth)
                : worldWidth / 2f;

        cameraY = worldHeight > camera.viewportHeight
                ? MathUtils.clamp(cameraY, halfHeight, worldHeight - halfHeight)
                : worldHeight / 2f;

        camera.position.set(cameraX, cameraY, 0f);
        camera.update();
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        camera.viewportHeight = VIEW_HEIGHT;
        camera.viewportWidth = VIEW_HEIGHT * width / (float) height;
        camera.update();
    }

    public OrthographicCamera getCamera() {
        return camera;
    }
}
