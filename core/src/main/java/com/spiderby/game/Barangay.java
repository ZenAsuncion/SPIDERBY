package com.spiderby.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Polyline;
import com.badlogic.gdx.math.Rectangle;

import com.spiderby.game.camera.CameraController;
import com.spiderby.game.entities.Player;
import com.spiderby.game.world.BarangayMap;
import com.spiderby.game.world.CollisionManager;
import com.spiderby.game.world.ForegroundOcclusion;

/**
 * Main Spiderby barangay game screen.
 *
 * Render order:
 *
 * 1. Barangay map
 * 2. Player
 * 3. Foreground leaves / roofs
 *
 * Collision is loaded from barangay.tmx.
 */
public class Barangay extends ApplicationAdapter {

    // ---------------------------------------------------------
    // RENDERING
    // ---------------------------------------------------------

    private SpriteBatch batch;
    private ShapeRenderer shapes;


    // ---------------------------------------------------------
    // WORLD
    // ---------------------------------------------------------

    private BarangayMap barangayMap;

    private CollisionManager collisionManager;

    private ForegroundOcclusion foreground;


    // ---------------------------------------------------------
    // PLAYER
    // ---------------------------------------------------------

    private Player player;


    // ---------------------------------------------------------
    // CAMERA
    // ---------------------------------------------------------

    private CameraController cameraController;


    // ---------------------------------------------------------
    // DEBUG
    // ---------------------------------------------------------

    private boolean debugCollision = false;


    // ---------------------------------------------------------
    // PLAYER SETTINGS
    // ---------------------------------------------------------

    private static final float PLAYER_WIDTH = 48f;
    private static final float PLAYER_HEIGHT = 64f;

    /*
     * Starting position.
     *
     * You can change these later if you want a
     * specific PlayerSpawn object in Tiled.
     */
    private static final float PLAYER_START_X = 300f;
    private static final float PLAYER_START_Y = 300f;


    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public void create() {

        // -----------------------------------------------------
        // Sprite renderer
        // -----------------------------------------------------

        batch = new SpriteBatch();


        // -----------------------------------------------------
        // Debug renderer
        // -----------------------------------------------------

        shapes = new ShapeRenderer();


        // -----------------------------------------------------
        // Load barangay TMX
        // -----------------------------------------------------

        barangayMap = new BarangayMap();


        // -----------------------------------------------------
        // Load collision from TMX
        // -----------------------------------------------------

        collisionManager =
                new CollisionManager(
                        barangayMap.getTiledMap()
                );


        // -----------------------------------------------------
        // Load transparent foreground
        //
        // map/barangay_foreground.png
        // -----------------------------------------------------

        foreground =
                new ForegroundOcclusion(
                        barangayMap.getWidth(),
                        barangayMap.getHeight()
                );


        // -----------------------------------------------------
        // Create player
        // -----------------------------------------------------

        player =
                new Player(
                        PLAYER_START_X,
                        PLAYER_START_Y,

                        PLAYER_WIDTH,
                        PLAYER_HEIGHT,

                        barangayMap.getWidth(),
                        barangayMap.getHeight(),

                        collisionManager
                );


        // -----------------------------------------------------
        // Camera
        // -----------------------------------------------------

        cameraController =
                new CameraController(
                        barangayMap.getWidth(),
                        barangayMap.getHeight()
                );


        cameraController.update(player);
    }


    // =========================================================
    // GAME LOOP
    // =========================================================

    @Override
    public void render() {

        /*
         * Prevent a very large delta if the window freezes,
         * moves, or loses focus.
         *
         * This prevents the player from jumping through
         * collision objects.
         */
        float delta =
                Math.min(
                        Gdx.graphics.getDeltaTime(),
                        1f / 20f
                );


        // -----------------------------------------------------
        // F3 = toggle collision debugging
        // -----------------------------------------------------

        if (Gdx.input.isKeyJustPressed(
                Input.Keys.F3)) {

            debugCollision =
                    !debugCollision;
        }


        // -----------------------------------------------------
        // Update player
        // -----------------------------------------------------

        player.update(delta);


        // -----------------------------------------------------
        // Follow player
        // -----------------------------------------------------

        cameraController.update(player);


        // -----------------------------------------------------
        // Clear screen
        // -----------------------------------------------------

        Gdx.gl.glClearColor(
                0f,
                0f,
                0f,
                1f
        );

        Gdx.gl.glClear(
                GL20.GL_COLOR_BUFFER_BIT
        );


        // =====================================================
        // 1. DRAW BASE MAP
        // =====================================================

        barangayMap.render(
                cameraController.getCamera()
        );


        // =====================================================
        // SPRITE BATCH
        // =====================================================

        batch.setProjectionMatrix(
                cameraController
                        .getCamera()
                        .combined
        );


        batch.begin();


        // =====================================================
        // 2. DRAW PLAYER
        // =====================================================

        player.render(batch);


        // =====================================================
        // 3. DRAW TREE LEAVES / ROOFS
        //
        // Foreground is intentionally rendered AFTER
        // the player.
        //
        // This makes the character appear underneath
        // the tree leaves instead of floating on top.
        // =====================================================

        foreground.render(batch);


        batch.end();


        // =====================================================
        // DEBUG COLLISION
        // =====================================================

        if (debugCollision) {

            renderCollisionDebug();
        }
    }


    // =========================================================
    // COLLISION DEBUGGER
    // =========================================================

    private void renderCollisionDebug() {

        shapes.setProjectionMatrix(
                cameraController
                        .getCamera()
                        .combined
        );


        shapes.begin(
                ShapeRenderer.ShapeType.Line
        );


        // =====================================================
        // NORMAL COLLISION
        // RED
        // =====================================================

        shapes.setColor(Color.RED);


        // -----------------------------------------------------
        // Rectangle collisions
        // -----------------------------------------------------

        for (Rectangle rectangle
                : collisionManager
                .getCollisionRectangles()) {

            shapes.rect(
                    rectangle.x,
                    rectangle.y,
                    rectangle.width,
                    rectangle.height
            );
        }


        // -----------------------------------------------------
        // Polygon collisions
        // -----------------------------------------------------

        for (Polygon polygon
                : collisionManager
                .getCollisionPolygons()) {

            float[] vertices =
                    polygon.getTransformedVertices();

            if (vertices.length >= 6) {

                shapes.polygon(vertices);
            }
        }


        // -----------------------------------------------------
        // Polyline collisions
        // -----------------------------------------------------

        for (Polyline line
                : collisionManager
                .getCollisionLines()) {

            float[] vertices =
                    line.getTransformedVertices();

            if (vertices.length >= 4) {

                shapes.polyline(vertices);
            }
        }


        // =====================================================
        // WATER COLLISION
        // CYAN
        // =====================================================

        shapes.setColor(Color.CYAN);


        // -----------------------------------------------------
        // Water rectangles
        // -----------------------------------------------------

        for (Rectangle rectangle
                : collisionManager
                .getWaterRectangles()) {

            shapes.rect(
                    rectangle.x,
                    rectangle.y,
                    rectangle.width,
                    rectangle.height
            );
        }


        // -----------------------------------------------------
        // Water polygons
        // -----------------------------------------------------

        for (Polygon polygon
                : collisionManager
                .getWaterPolygons()) {

            float[] vertices =
                    polygon.getTransformedVertices();

            if (vertices.length >= 6) {

                shapes.polygon(vertices);
            }
        }


        // -----------------------------------------------------
        // Water polylines
        // -----------------------------------------------------

        for (Polyline line
                : collisionManager
                .getWaterLines()) {

            float[] vertices =
                    line.getTransformedVertices();

            if (vertices.length >= 4) {

                shapes.polyline(vertices);
            }
        }


        // =====================================================
        // PLAYER FEET HITBOX
        // YELLOW
        // =====================================================

        Rectangle feet =
                collisionManager
                .getPlayerHitbox(
                        player.getX(),
                        player.getY(),
                        player.getWidth(),
                        player.getHeight()
                );


        shapes.setColor(Color.YELLOW);


        shapes.rect(
                feet.x,
                feet.y,
                feet.width,
                feet.height
        );


        shapes.end();
    }


    // =========================================================
    // WINDOW RESIZE
    // =========================================================

    @Override
    public void resize(
            int width,
            int height) {

        if (width <= 0 || height <= 0) {
            return;
        }


        /*
         * Fixes the duplicated / stretched map strips
         * that can appear after resizing the game window.
         */
        Gdx.gl.glViewport(
                0,
                0,
                width,
                height
        );


        cameraController.resize(
                width,
                height
        );


        cameraController.update(player);
    }


    // =========================================================
    // DISPOSE
    // =========================================================

    @Override
    public void dispose() {

        // SpriteBatch
        if (batch != null) {

            batch.dispose();
            batch = null;
        }


        // Debug ShapeRenderer
        if (shapes != null) {

            shapes.dispose();
            shapes = null;
        }


        // Transparent foreground texture
        if (foreground != null) {

            foreground.dispose();
            foreground = null;
        }


        // Player animations / textures
        if (player != null) {

            player.dispose();
            player = null;
        }


        // TMX renderer and TiledMap
        if (barangayMap != null) {

            barangayMap.dispose();
            barangayMap = null;
        }
    }
}