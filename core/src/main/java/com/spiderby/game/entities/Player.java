package com.spiderby.game.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.spiderby.game.graphics.PlayerAnimation;
import com.spiderby.game.graphics.PlayerAnimation.Direction;
import com.spiderby.game.world.CollisionManager;

/** The walking character; inherits GameObject. */
public class Player extends GameObject {
    private static final float SPEED = 120f;

    private final PlayerAnimation animation;
    private final CollisionManager collision;
    private final float worldWidth, worldHeight;

    private Direction direction = Direction.DOWN;
    private float animationTime = 0f;
    private boolean moving = false;

    public Player(float x, float y, float width, float height,
                  float worldWidth, float worldHeight, CollisionManager collision) {
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

        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) dx--;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx++;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) dy++;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) dy--;

        if (dx == 0f && dy == 0f) {
            moving = false;
            animationTime = 0f;
            return;
        }

        float magnitude = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= magnitude;
        dy /= magnitude;

        Direction nextDirection = direction;
        if (Math.abs(dx) > Math.abs(dy)) {
            nextDirection = dx < 0 ? Direction.LEFT : Direction.RIGHT;
        } else if (dy != 0f) {
            nextDirection = dy > 0 ? Direction.UP : Direction.DOWN;
        }
        if (nextDirection != direction) {
            direction = nextDirection;
            animationTime = 0f;
        }

        float previousX = x;
        float previousY = y;
        float nextX = MathUtils.clamp(x + dx * SPEED * delta,
                0f, Math.max(0f, worldWidth - width));
        float nextY = MathUtils.clamp(y + dy * SPEED * delta,
                0f, Math.max(0f, worldHeight - height));

        // Separate axes: naturally slides along a fence when walking diagonally.
        if (!collision.isColliding(nextX, y, width, height)) x = nextX;
        if (!collision.isColliding(x, nextY, width, height)) y = nextY;

        // A pressed key must NOT play walking frames when the player is stuck.
        moving = Math.abs(x - previousX) > 0.001f || Math.abs(y - previousY) > 0.001f;
        if (moving) animationTime += delta;
        else animationTime = 0f;
    }

    @Override
    public void render(SpriteBatch batch) {
        batch.draw(animation.getFrame(direction, animationTime, moving), x, y, width, height);
    }

    public void dispose() { animation.dispose(); }
}
