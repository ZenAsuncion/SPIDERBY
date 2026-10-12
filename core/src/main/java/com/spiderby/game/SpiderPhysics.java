package com.spiderby.game;

import com.badlogic.gdx.math.Rectangle;

public class SpiderPhysics {
    private float x, y;
    private float velocityX, velocityY;
    
    // Environmental Anchors in LibGDX coordinates (0,0 is bottom-left)
    private final float stickTopY = 550f;
    private final float stickHeight = 3f;
    private final float stickBottomY = stickTopY - stickHeight;
    private final float minWebY = 150f;
    private final float clingOffset = 4f;
    
    private float posOnTop;
    private float posHanging;
    
    private boolean webDropLocked = false;
    private final float gravity = 0.4f;
    private final float springk = 0.15f;
    
    private double scale = 0.5;
    private float frameWidth, frameHeight;
    
    private float enemyX = 500f;
    private float enemyY;
    private float enemyWidth;
    private float enemyHeight;

    public SpiderPhysics(float startWidth, float startHeight) {
        this.frameWidth = startWidth;
        this.frameHeight = startHeight;
        
        float scaledWidth = frameWidth * (float) scale;
        float scaledHeight = frameHeight * (float) scale;
        
        this.enemyWidth = scaledWidth;
        this.enemyHeight = scaledHeight;
        
        posOnTop = stickTopY + clingOffset; 
        posHanging = stickBottomY - clingOffset; 
        
        this.x = 1200 / 2f - scaledWidth / 2f;
        this.y = posOnTop;
        this.enemyY = posOnTop;
    }

    public void update(float delta, boolean currentUp, boolean currentDown, boolean currentLeft, boolean currentRight, boolean previousUpPressed, boolean previousDownPressed, float speed) {
        if (y >= posOnTop - 1f) {
            if (currentLeft) x -= speed * (delta * 60f);
            if (currentRight) x += speed * (delta * 60f);
        }
        
        boolean atHanging = Math.abs(y - posHanging) < 1f;
        boolean atTop = Math.abs(y - posOnTop) < 1f;
        
        if (currentUp) {
            if (y < posHanging - 1f) {
                velocityY = speed * 2f;
            } else if (atHanging && !previousUpPressed) {
                velocityY = speed * 2f;
            } else if (y < posOnTop && y > posHanging + 1f) {
                velocityY = speed * 2f;
            }
        } else if (currentDown) {
            if (atTop && !previousDownPressed) {
                velocityY = -speed * 2f;
                webDropLocked = true;
            } else if (y < posOnTop - 1f && y > posHanging) {
                velocityY = -speed * 2f;
            } else if (y <= posHanging + 1f && !webDropLocked) {
                velocityY -= gravity;
            }
        } else {
            webDropLocked = false;
            if (y < posHanging) {
                velocityY *= 0.8f;
            } else {
                velocityY = 0;
            }
        }
        
        float previousY = y;
        y += velocityY;
        
        if (y >= posOnTop) { y = posOnTop; velocityY = 0; }
        if (previousY < posHanging && y >= posHanging) { y = posHanging; velocityY = 0; }
        if (previousY > posHanging && y <= posHanging && webDropLocked) { y = posHanging; velocityY = 0; }
        
        if (y < minWebY) {
            float displacement = minWebY - y;
            velocityY += displacement * springk;
        }
    }

    public Rectangle getPlayerBounds() {
        float scaledW = frameWidth * (float) scale;
        float scaledH = frameHeight * (float) scale;
        Rectangle r = new Rectangle(x, y, scaledW, scaledH);
        boolean isDropping = y < posHanging - 1f;
        if (isDropping) {
            float cx = r.x + r.width / 2f;
            float cy = r.y + r.height / 2f;
            return new Rectangle(cx - r.height / 2f, cy - r.width / 2f, r.height, r.width);
        }
        return r;
    }

    public Rectangle getEnemyBounds() {
        return new Rectangle(enemyX, enemyY, enemyWidth, enemyHeight);
    }

    public boolean checkCollision() {
        return getPlayerBounds().overlaps(getEnemyBounds());
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public float getStickTopY() { return stickTopY; }
    public float getStickBottomY() { return stickBottomY; }
    public float getPosHanging() { return posHanging; }
    public float getVelocityY() { return velocityY; }
}