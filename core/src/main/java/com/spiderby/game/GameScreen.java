package com.spiderby.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

public class GameScreen implements Screen {
    private final SPIDERBY game;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    
    private Texture spriteSheet;
    private Animation<TextureRegion> idleAnim, walkAnim, freezeAnim;
    private float stateTime = 0f;

    private SpiderPhysics physics;
    
    private final int TOTAL_FRAMES = 16;
    private final int IDLE_FRAME_COUNT = 4;
    private final int FREEZE_FRAME_INDEX = 4;
    
    private boolean up, down, left, right;
    private boolean previousUpPressed = false;
    private boolean previousDownPressed = false;
    private boolean facingLeft = false;
    private final float speed = 3.5f;
    
    private long leftReleaseTime = 0;
    private long rightReleaseTime = 0;
    private final int DEBOUNCE_MS = 30;
    private final boolean SHOW_HITBOXES = true;

    public GameScreen(SPIDERBY game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();

        Pixmap rawPixmap = new Pixmap(Gdx.files.internal("spiderv1.png"));
        Pixmap cleanPixmap = new Pixmap(rawPixmap.getWidth(), rawPixmap.getHeight(), Pixmap.Format.RGBA8888);
        
        // Disable blending so drawPixel overwrites pixels with 0 alpha
        cleanPixmap.setBlending(Pixmap.Blending.None);
        cleanPixmap.drawPixmap(rawPixmap, 0, 0);
        
        Color tempColor = new Color();
        for (int x = 0; x < cleanPixmap.getWidth(); x++) {
            for (int y = 0; y < cleanPixmap.getHeight(); y++) {
                int colorInt = cleanPixmap.getPixel(x, y);
                Color.rgba8888ToColor(tempColor, colorInt);
                
                float r = tempColor.r;
                float g = tempColor.g;
                float b = tempColor.b;
                
                if (g > 0.58f && r < 0.47f && b < 0.47f && g > r + 0.23f && g > b + 0.23f) {
                    cleanPixmap.setColor(0, 0, 0, 0);
                    cleanPixmap.drawPixel(x, y);
                }
            }
        }
        spriteSheet = new Texture(cleanPixmap);
        rawPixmap.dispose();
        cleanPixmap.dispose();

        int frameWidth = spriteSheet.getWidth() / TOTAL_FRAMES;
        int frameHeight = spriteSheet.getHeight();
        
        TextureRegion[][] tmp = TextureRegion.split(spriteSheet, frameWidth, frameHeight);
        TextureRegion[] frames = new TextureRegion[TOTAL_FRAMES];
        for (int i = 0; i < TOTAL_FRAMES; i++) {
            frames[i] = tmp[0][i];
        }

        TextureRegion[] idleFrames = new TextureRegion[IDLE_FRAME_COUNT];
        System.arraycopy(frames, 0, idleFrames, 0, IDLE_FRAME_COUNT);
        idleAnim = new Animation<>(0.2f, idleFrames);
        idleAnim.setPlayMode(Animation.PlayMode.LOOP);

        int walkCount = TOTAL_FRAMES - IDLE_FRAME_COUNT;
        TextureRegion[] walkFrames = new TextureRegion[walkCount];
        System.arraycopy(frames, IDLE_FRAME_COUNT, walkFrames, 0, walkCount);
        walkAnim = new Animation<>(0.06f, walkFrames);
        walkAnim.setPlayMode(Animation.PlayMode.LOOP);

        TextureRegion[] freezeFrames = { frames[FREEZE_FRAME_INDEX] };
        freezeAnim = new Animation<>(1.0f, freezeFrames);

        physics = new SpiderPhysics(frameWidth, frameHeight);
    }

    @Override
    public void render(float delta) {
        stateTime += delta;

        up = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        down = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            left = true; facingLeft = true; leftReleaseTime = System.currentTimeMillis();
        } else {
            left = false;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            right = true; facingLeft = false; rightReleaseTime = System.currentTimeMillis();
        } else {
            right = false;
        }

        boolean currentLeft = left || (System.currentTimeMillis() - leftReleaseTime < DEBOUNCE_MS);
        boolean currentRight = right || (System.currentTimeMillis() - rightReleaseTime < DEBOUNCE_MS);

        physics.update(delta, up, down, currentLeft, currentRight, previousUpPressed, previousDownPressed, speed);
        
        previousUpPressed = up;
        previousDownPressed = down;

        Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Draw Stick
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.54f, 0.27f, 0.07f, 1);
        shapeRenderer.rect(100, physics.getStickTopY(), 600, 3);
        shapeRenderer.end();

        // Draw Web line if dropping
        boolean isDropping = physics.getY() < physics.getPosHanging() - 1f;
        if (isDropping) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.LIGHT_GRAY);
            float drawWidth = (spriteSheet.getWidth() / TOTAL_FRAMES) * 0.5f;
            float drawHeight = spriteSheet.getHeight() * 0.5f;
            float lineX = physics.getX() + drawWidth / 2f;
            boolean isGoingUp = physics.getVelocityY() > 0.1f;
            float webAttachY = isGoingUp ? physics.getY() + drawHeight * 0.3f : physics.getY() + drawHeight * 0.7f;
            shapeRenderer.line(lineX, physics.getStickBottomY(), lineX, webAttachY);
            shapeRenderer.end();
        }

        batch.begin();
        
        boolean onWeb = physics.getY() < physics.getPosHanging() - 1f;
        Animation<TextureRegion> activeAnim = idleAnim;
        if (onWeb) {
            activeAnim = freezeAnim;
        } else if (currentLeft || currentRight) {
            activeAnim = walkAnim;
        }
        
        TextureRegion currentFrame = activeAnim.getKeyFrame(stateTime, true);
        
        float rawW = currentFrame.getRegionWidth();
        float rawH = currentFrame.getRegionHeight();
        
        float originX = rawW / 2f;
        float originY = rawH / 2f;
        
        float scaleX = facingLeft ? -0.5f : 0.5f;
        float scaleY = 0.5f;
        float rotation = 0f;
        
        if (isDropping) {
            boolean isGoingUp = physics.getVelocityY() > 0.1f;
            if (facingLeft) {
                rotation = isGoingUp ? -90f : 90f;
            } else {
                rotation = isGoingUp ? 90f : -90f;
            }
        } else if (Math.abs(physics.getY() - physics.getPosHanging()) < 1f) {
            rotation = 180f;
        }

        // Pass raw unscaled dimensions to batch.draw so scaleX and scaleY apply ONCE
        batch.draw(currentFrame, physics.getX(), physics.getY(), originX, originY, rawW, rawH, scaleX, scaleY, rotation);

        // Draw Enemy
        TextureRegion enemyFrame = idleAnim.getKeyFrame(0);
        batch.draw(enemyFrame, physics.getEnemyBounds().x, physics.getEnemyBounds().y, originX, originY, rawW, rawH, 0.5f, 0.5f, 0f);

        batch.end();

        // Hitboxes debug view
        if (SHOW_HITBOXES) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.RED);
            Rectangle pBounds = physics.getPlayerBounds();
            shapeRenderer.rect(pBounds.x, pBounds.y, pBounds.width, pBounds.height);
            
            shapeRenderer.setColor(Color.BLUE);
            Rectangle eBounds = physics.getEnemyBounds();
            shapeRenderer.rect(eBounds.x, eBounds.y, eBounds.width, eBounds.height);
            shapeRenderer.end();
        }

        batch.begin();
        if (physics.checkCollision()) {
            font.setColor(Color.RED);
            font.draw(batch, "COLLISION!", 20, 650);
        } else {
            font.setColor(Color.BLACK);
            font.draw(batch, "No Collision", 20, 650);
        }
        batch.end();
    }

    @Override public void resize(int width, int height) {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { dispose(); }
    @Override public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
        if (spriteSheet != null) spriteSheet.dispose();
    }
}