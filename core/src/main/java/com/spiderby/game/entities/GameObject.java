package com.spiderby.game.entities;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Shared parent for game entities (abstraction and inheritance). */
public abstract class GameObject {
    protected float x, y, width, height;

    protected GameObject(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void update(float delta);
    public abstract void render(SpriteBatch batch);

    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
}
