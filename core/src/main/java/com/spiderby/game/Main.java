package com.spiderby.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;

    private Texture spriteSheet;
    private TextureRegion[] frames;
    private Animation<TextureRegion> idleAnim;
    private Animation<TextureRegion> walkAnim;
    private Animation<TextureRegion> freezeAnim;
    private float stateTime = 0f;

    // Game dimensions
    private final int scWidth = 800;
    private final int scHeight = 600;

    // Player and Enemy state
    private float playerX, playerY;
    private float enemyX = 500f, enemyY;
    private double scale = 0.5;
    private int frameWidth, frameHeight;

    // Environmental Anchors ("walis tingting" stick dimensions)
    private final float stickTopY = 300f;
    private final float stickHeight = 3f;
    private final float stickBottomY = stickTopY + stickHeight;
    private final float maxWebY = 500f;
    private final float clingOffset = 4f;
    private float posOnTop;
    private float posHanging;

    // Physics & Movement
    private float velocityY = 0f;
    private final float speed = 5.0f;
    private final float gravity = 0.4f;
    private final float springk = 0.15f;
    private boolean facingLeft = false;

    // State machine flags & debounce tracking
    private boolean previousUpPressed = false;
    private boolean previousDownPressed = false;
    private boolean webDropLocked = false;
    private boolean prevLeftPressed = false;
    private boolean prevRightPressed = false;
    private long leftReleaseTime = 0;
    private long rightReleaseTime = 0;
    private final long DEBOUNCE_MS = 30;

    private boolean showHitboxes = true;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();

        // Load your 6-frame sprite sheet from the assets folder
        spriteSheet = new Texture("spiderv1.png");
        int totalFrames = 6;
        
        frameWidth = spriteSheet.getWidth() / totalFrames;
        frameHeight = spriteSheet.getHeight();

        TextureRegion[][] tmp = TextureRegion.split(spriteSheet, frameWidth, frameHeight);
        frames = new TextureRegion[totalFrames];
        for (int i = 0; i < totalFrames; i++) {
            frames[i] = tmp[0][i];
        }

        // Build Animations using the 6 frames
        idleAnim = new Animation<>(0.2f, frames[0]);
        walkAnim = new Animation<>(0.08f, frames);
        freezeAnim = new Animation<>(1.0f, frames[0]);

        float scaledHeight = (float) (frameHeight * scale);
        posOnTop = stickTopY - scaledHeight + clingOffset;
        posHanging = stickBottomY - clingOffset;

        // Spawn player and enemy
        playerX = scWidth / 2f - (frameWidth * (float) scale) / 2f;
        playerY = posOnTop;
        enemyY = posOnTop;
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        stateTime += dt;

        // --- Input Handling ---
        boolean up = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean down = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        
        if (Gdx.input.isKeyJustPressed(Input.Keys.A) || Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            facingLeft = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.D) || Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            facingLeft = false;
        }

        boolean leftPressed = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean rightPressed = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);

        // Track key releases for debounce grace period
        if (prevLeftPressed && !leftPressed) {
            leftReleaseTime = System.currentTimeMillis();
        }
        if (prevRightPressed && !rightPressed) {
            rightReleaseTime = System.currentTimeMillis();
        }
        prevLeftPressed = leftPressed;
        prevRightPressed = rightPressed;

        boolean currentLeft = leftPressed || (System.currentTimeMillis() - leftReleaseTime < DEBOUNCE_MS);
        boolean currentRight = rightPressed || (System.currentTimeMillis() - rightReleaseTime < DEBOUNCE_MS);

        float startX = playerX;
        float startY = playerY;

        // Horizontal movement on stick
        if (playerY <= posHanging + 1f) {
            if (currentLeft) playerX -= speed * (dt * 60f);
            if (currentRight) playerX += speed * (dt * 60f);
        }

        boolean atHanging = Math.abs(playerY - posHanging) < 1f;
        boolean atTop = Math.abs(playerY - posOnTop) < 1f;

        // Vertical physics & web climbing
        if (up) {
            if (playerY > posHanging + 1f) {
                velocityY = -speed;
            } else if (atHanging && !previousUpPressed) {
                velocityY = -speed;
            } else if (playerY > posOnTop && playerY < posHanging - 1f) {
                velocityY = -speed;
            }
        } else if (down) {
            if (atTop && !previousDownPressed) {
                velocityY = speed;
                webDropLocked = true;
            } else if (playerY > posOnTop + 1f && playerY < posHanging) {
                velocityY = speed;
            } else if (playerY >= posHanging - 1f && !webDropLocked) {
                velocityY += gravity;
            }
        } else {
            webDropLocked = false;
            if (playerY > posHanging) {
                velocityY *= 0.8f; // Web friction
            } else {
                velocityY = 0;
            }
        }

        float previousY = playerY;
        playerY += velocityY * (dt * 60f);

        // Precise boundaries
        if (playerY <= posOnTop) {
            playerY = posOnTop;
            velocityY = 0;
        }
        if (previousY > posHanging && playerY <= posHanging) {
            playerY = posHanging;
            velocityY = 0;
        }
        if (previousY < posHanging && playerY >= posHanging && webDropLocked) {
            playerY = posHanging;
            velocityY = 0;
        }

        // Web elasticity spring physics
        if (playerY > maxWebY) {
            float displacementY = playerY - maxWebY;
            velocityY -= displacementY * springk;
        }

        previousUpPressed = up;
        previousDownPressed = down;

        // --- Rendering ---
        ScreenUtils.clear(1, 1, 1, 1);

        // Draw stick
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.55f, 0.27f, 0.07f, 1f); // Brown color
        shapeRenderer.rect(100, stickTopY, 600, stickHeight);
        shapeRenderer.end();

        batch.begin();

        boolean isDropping = playerY > posHanging + 1f;
        boolean isGoingUp = velocityY < -0.1f;

        // Draw web line if dropping
        if (isDropping) {
            batch.end();
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(0.8f, 0.8f, 0.8f, 1f);
            float drawW = frameWidth * (float)scale;
            float drawH = frameHeight * (float)scale;
            int webAttachY = isGoingUp ? (int)(playerY - drawH * 0.7) : (int)(playerY + drawH * 0.3);
            shapeRenderer.line(playerX + drawW / 2, stickBottomY, playerX + drawW / 2, webAttachY);
            shapeRenderer.end();
            batch.begin();
        }

        // Select current animation frame
        boolean moved = Math.abs(playerX - startX) > 0.01f || Math.abs(playerY - startY) > 0.01f;
        TextureRegion currentFrame;
        if (isDropping) {
            currentFrame = freezeAnim.getKeyFrame(stateTime);
        } else if (moved) {
            currentFrame = walkAnim.getKeyFrame(stateTime, true);
        } else {
            currentFrame = idleAnim.getKeyFrame(stateTime, true);
        }

        float drawWidth = frameWidth * (float) scale;
        float drawHeight = frameHeight * (float) scale;

        // Draw Player with rotation and scaling
        float rotation = 0f;
        if (isDropping) {
            rotation = facingLeft ? 90f : -90f;
        } else if (Math.abs(playerY - posHanging) < 1f) {
            currentFrame.flip(false, true);
        }

        batch.draw(
            currentFrame,
            playerX, playerY,
            drawWidth / 2f, drawHeight / 2f,
            drawWidth, drawHeight,
            facingLeft ? -1f : 1f, 1f,
            rotation
        );

        // Draw Enemy
        batch.draw(frames[0], enemyX, enemyY, drawWidth, drawHeight);

        // Collision Check & Hitboxes
        Rectangle playerBounds = getPlayerBounds();
        Rectangle enemyBounds = new Rectangle(enemyX, enemyY, drawWidth, drawHeight);
        boolean colliding = playerBounds.overlaps(enemyBounds);

        if (colliding) {
            font.setColor(Color.RED);
            font.draw(batch, "COLLISION!", 20, 50);
        } else {
            font.setColor(Color.BLACK);
            font.draw(batch, "No Collision", 20, 50);
        }

        batch.end();

        // Render Hitboxes for Debugging
        if (showHitboxes) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.RED);
            shapeRenderer.rect(playerBounds.x, playerBounds.y, playerBounds.width, playerBounds.height);
            shapeRenderer.setColor(Color.BLUE);
            shapeRenderer.rect(enemyBounds.x, enemyBounds.y, enemyBounds.width, enemyBounds.height);
            shapeRenderer.end();
        }
    }

    private Rectangle getPlayerBounds() {
        float drawWidth = frameWidth * (float) scale;
        float drawHeight = frameHeight * (float) scale;
        boolean isDropping = playerY > posHanging + 1f;
        
        if (isDropping) {
            float cx = playerX + drawWidth / 2f;
            float cy = playerY + drawHeight / 2f;
            return new Rectangle(cx - drawHeight / 2f, cy - drawWidth / 2f, drawHeight, drawWidth);
        }
        return new Rectangle(playerX, playerY, drawWidth, drawHeight);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
        spriteSheet.dispose();
    }
}