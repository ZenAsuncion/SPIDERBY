package com.spiderby.game.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.spiderby.game.graphics.PlayerAnimation;
import com.spiderby.game.graphics.PlayerAnimation.Direction;
import com.spiderby.game.world.CollisionManager;

/** Main controllable barangay character. */
public class Player extends GameObject {

    private static final float SPEED = 120f;

    private final PlayerAnimation animation;
    private final CollisionManager collision;
    private final float worldWidth;
    private final float worldHeight;

    private Direction direction = Direction.DOWN;
    private float animationTime = 0f;
    private boolean moving = false;

    public Player(
            float x,
            float y,
            float width,
            float height,
            float worldWidth,
            float worldHeight,
            CollisionManager collision) {

        super(x, y, width, height);
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.collision = collision;
        animation = new PlayerAnimation();
    }

    @Override
    public void update(float delta) {
        float dx = 0f;
        float dy = 0f;

        if (Gdx.input.isKeyPressed(Input.Keys.A)
                || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            dx -= 1f;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.D)
                || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            dx += 1f;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.W)
                || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            dy += 1f;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.S)
                || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            dy -= 1f;
        }

        if (dx == 0f && dy == 0f) {
            moving = false;
            animationTime = 0f;
            return;
        }

        float length = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= length;
        dy /= length;

        Direction nextDirection = direction;

        if (Math.abs(dx) > Math.abs(dy)) {
            nextDirection = dx < 0f ? Direction.LEFT : Direction.RIGHT;
        } else if (dy != 0f) {
            nextDirection = dy > 0f ? Direction.UP : Direction.DOWN;
        }

        if (nextDirection != direction) {
            direction = nextDirection;
            animationTime = 0f;
        }

        float oldX = x;
        float oldY = y;

        float nextX = MathUtils.clamp(
                x + dx * SPEED * delta,
                0f,
                Math.max(0f, worldWidth - width)
        );

        float nextY = MathUtils.clamp(
                y + dy * SPEED * delta,
                0f,
                Math.max(0f, worldHeight - height)
        );

        // Separate-axis collision gives smoother sliding beside fences/walls.
        if (!collision.isColliding(nextX, y, width, height)) {
            x = nextX;
        }

        if (!collision.isColliding(x, nextY, width, height)) {
            y = nextY;
        }

        moving = Math.abs(x - oldX) > 0.001f
                || Math.abs(y - oldY) > 0.001f;

        if (moving) {
            animationTime += delta;
        } else {
            animationTime = 0f;
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        batch.draw(
                animation.getFrame(direction, animationTime, moving),
                x,
                y,
                width,
                height
        );
    }

    /** Center of the same feet zone used by CollisionManager. */
    public float getDepthY() {
        return y + height * 0.09f;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isMoving() {
        return moving;
    }

    public void dispose() {
        animation.dispose();
    }
}
