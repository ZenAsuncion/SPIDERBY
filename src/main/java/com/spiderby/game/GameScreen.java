package com.spiderby.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class GameScreen extends ScreenAdapter {
    private static final String SHEET = "spiderv3.png";
    private static final int COLS = 4, ROWS = 3, FRAME_COUNT = 10; // grid layout, frames read row by row
    private static final int IDLE_FRAME = 0;       // pose shown while standing still
    private static final boolean FACES_RIGHT = true; // the head points right in the sheet
    private static final boolean SHOW_HITBOXES = true;
    private static final int ALPHA_MIN = 20;       // pixels at or below this alpha count as empty

    // attack sheet (put it in assets/ with this name, transparent background)
    private static final String ATK_SHEET = "spiderv3_basic_attack.png";
    private static final int ATK_COLS = 4, ATK_ROWS = 4, ATK_FRAME_COUNT = 16;
    // pure idle poses: they define the body size/position so attack frames line up with the walk sprite
    private static final int[] ATK_BASE_FRAMES = {0, 1, 2, 3, 4, 12, 13, 14, 15};
    // which sheet frames play during each attack phase (spread evenly over the phase's ticks)
    private static final int[] ATK_WINDUP_FRAMES = {5, 6, 7, 8};   // legs rear back
    private static final int[] ATK_ACTIVE_FRAMES = {9, 10};        // fangs + legs thrust
    private static final int[] ATK_RECOVERY_FRAMES = {11, 12};     // pull back to rest

    private static final float STEP = 1f / 60f;
    private static final float STRIDE = 90f;       // world px travelled per full walk cycle (raise if feet slide)
    private static final float MOVE_EPS = 6f;      // px/s under which the spider counts as standing still

    private final SPIDERBY game;
    private OrthographicCamera cam;
    private FitViewport vp;
    private SpriteBatch batch;
    private ShapeRenderer sr;
    private BitmapFont font;
    private Texture tex;
    private SpiderPhysics ph;

    private TextureRegion[] fr; // every frame has the same size, so the body never jitters

    private Texture atkTex;
    private TextureRegion[] atkFr;       // attack frames, all sharing one (larger) crop box
    private float atkW, atkH;            // on-screen size of an attack frame
    private float atkOx, atkOy;          // on-screen offset of the attack crop's centre from the body centre (y-down)

    private final SpiderPhysics.Pose pose = new SpiderPhysics.Pose();
    private final Vector2 tEnd = new Vector2();
    private SpiderPhysics.Mode lastMode; // null until the first frame, so the first frame counts no travel
    private float acc, phase, lastCx, lastCy, enPhase, lastEx;
    private boolean attackQueued;        // latched press, consumed by the first physics tick that sees it

    public GameScreen(SPIDERBY game) { this.game = game; }

    /** Purpose: Create camera, renderers, sprite frames and physics.
     *  Pre-condition: SHEET exists in assets/
     *  Post-condition: all fields are initialized */
    @Override
    public void show() {
        cam = new OrthographicCamera();
        vp = new FitViewport(SpiderPhysics.W, SpiderPhysics.H, cam);
        batch = new SpriteBatch();
        sr = new ShapeRenderer();
        font = new BitmapFont();
        font.setColor(Color.BLACK);

        tex = new Texture(Gdx.files.internal(SHEET), true);
        // soft-edged art scaled down: mipmaps keep it smooth. For hard pixel art use Nearest, Nearest.
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);

        float[] box = buildFrames();
        ph = new SpiderPhysics(box[0], box[1]);
        buildAttackFrames();
        lastEx = ph.enemyCx(1f);
    }

    /** Purpose: Cut the sheet's grid into frames that all share one tight crop box.
     *  The crop is the union of every frame's visible pixels, so no leg is cut off and,
     *  because the body sits at the same spot in each cell, it stays still while the legs move.
     *  Returns: {crop width, crop height} in source pixels
     *  Post-condition: fr holds FRAME_COUNT regions of identical size */
    private float[] buildFrames() {
        Pixmap pm = new Pixmap(Gdx.files.internal(SHEET));
        int cw = pm.getWidth() / COLS, ch = pm.getHeight() / ROWS; // one grid cell
        int x0 = cw, y0 = ch, x1 = -1, y1 = -1;
        for (int i = 0; i < FRAME_COUNT; i++) {
            int ox = (i % COLS) * cw, oy = (i / COLS) * ch;
            for (int y = 0; y < ch; y++)
                for (int x = 0; x < cw; x++)
                    if ((pm.getPixel(ox + x, oy + y) & 0xFF) > ALPHA_MIN) {
                        if (x < x0) x0 = x;
                        if (x > x1) x1 = x;
                        if (y < y0) y0 = y;
                        if (y > y1) y1 = y;
                    }
        }
        pm.dispose();
        if (x1 < 0) { x0 = 0; y0 = 0; x1 = cw - 1; y1 = ch - 1; } // blank sheet: keep whole cells

        int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
        fr = new TextureRegion[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++)
            fr[i] = new TextureRegion(tex, (i % COLS) * cw + x0, (i / COLS) * ch + y0, bw, bh);
        return new float[]{bw, bh};
    }

    /** Purpose: Load the attack sheet and cut it with its own crop box, aligned to the walk sprite.
     *  Two boxes are measured on the sheet: the idle poses (the body, same size as the walk sprite)
     *  and the union of all frames (includes the extended legs). The scale that fits the idle box to
     *  the physics size is applied to the bigger box, and its centre offset is remembered, so the
     *  body stays put when the sprite swaps from walking to attacking.
     *  Pre-condition: ATK_SHEET exists in assets/ and ph is built
     *  Post-condition: atkFr, atkW, atkH, atkOx, atkOy are set */
    private void buildAttackFrames() {
        atkTex = new Texture(Gdx.files.internal(ATK_SHEET), true);
        atkTex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);

        Pixmap pm = new Pixmap(Gdx.files.internal(ATK_SHEET));
        int cw = pm.getWidth() / ATK_COLS, ch = pm.getHeight() / ATK_ROWS;
        int[] all = new int[ATK_FRAME_COUNT];
        for (int i = 0; i < all.length; i++) all[i] = i;
        int[] base = visibleBox(pm, cw, ch, ATK_BASE_FRAMES);   // {x0, y0, x1, y1}
        int[] full = visibleBox(pm, cw, ch, all);
        pm.dispose();

        int baseW = base[2] - base[0] + 1, baseH = base[3] - base[1] + 1;
        int fullW = full[2] - full[0] + 1, fullH = full[3] - full[1] + 1;

        float kx = ph.getDw() / baseW, ky = ph.getDh() / baseH;  // source px -> screen px
        atkW = fullW * kx;
        atkH = fullH * ky;
        atkOx = ((full[0] + fullW / 2f) - (base[0] + baseW / 2f)) * kx;
        atkOy = ((full[1] + fullH / 2f) - (base[1] + baseH / 2f)) * ky;

        atkFr = new TextureRegion[ATK_FRAME_COUNT];
        for (int i = 0; i < ATK_FRAME_COUNT; i++)
            atkFr[i] = new TextureRegion(atkTex, (i % ATK_COLS) * cw + full[0], (i / ATK_COLS) * ch + full[1], fullW, fullH);
    }

    /** Returns: {x0, y0, x1, y1} of the visible pixels (inside a cell) unioned over the given frames. */
    private int[] visibleBox(Pixmap pm, int cw, int ch, int[] frames) {
        int x0 = cw, y0 = ch, x1 = -1, y1 = -1;
        for (int f : frames) {
            int ox = (f % ATK_COLS) * cw, oy = (f / ATK_COLS) * ch;
            for (int y = 0; y < ch; y++)
                for (int x = 0; x < cw; x++)
                    if ((pm.getPixel(ox + x, oy + y) & 0xFF) > ALPHA_MIN) {
                        if (x < x0) x0 = x;
                        if (x > x1) x1 = x;
                        if (y < y0) y0 = y;
                        if (y > y1) y1 = y;
                    }
        }
        if (x1 < 0) return new int[]{0, 0, cw - 1, ch - 1};      // blank: keep the whole cell
        return new int[]{x0, y0, x1, y1};
    }

    /** Returns: the attack-sheet frame for the current phase, or -1 when not attacking (use the walk frames). */
    private int attackFrameIndex() {
        float p = ph.getAtkProgress();
        switch (ph.getAtkPhase()) {
            case WINDUP:   return pick(ATK_WINDUP_FRAMES, p);
            case ACTIVE:   return pick(ATK_ACTIVE_FRAMES, p);
            case RECOVERY: return pick(ATK_RECOVERY_FRAMES, p);
            default:       return -1;
        }
    }

    /** Returns: the frame in the list that matches progress p (0..1). */
    private static int pick(int[] frames, float p) {
        return frames[Math.min((int) (p * frames.length), frames.length - 1)];
    }

    /** Purpose: Read input, run fixed-step physics, animate, draw.
     *  Parameters: dt - seconds since the last frame */
    @Override
    public void render(float dt) {
        boolean l = Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT);
        boolean r = Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT);
        boolean u = Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP);
        boolean d = Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN);

        // Latch the press: several (or zero) physics steps can run per frame, so a plain
        // isKeyJustPressed inside the loop could be missed or seen twice.
        attackQueued |= Gdx.input.isKeyJustPressed(Keys.SPACE) || Gdx.input.isKeyJustPressed(Keys.J);

        acc += Math.min(dt, 0.25f);
        while (acc >= STEP) {
            ph.step(u, d, l, r, attackQueued);
            attackQueued = false;                // consumed by the first step
            acc -= STEP;
        }
        float a = acc / STEP;
        ph.interpolate(a, pose);

        // the enemy walks with the same distance-driven cycle as the player
        float ex = ph.enemyCx(a);
        float enTravel = Math.abs(ex - lastEx);
        lastEx = ex;
        boolean enMoving = dt > 0f && enTravel / dt > MOVE_EPS;
        if (enMoving) enPhase = (enPhase + enTravel / STRIDE * FRAME_COUNT) % FRAME_COUNT;
        int enIdx = enMoving ? Math.min((int) enPhase, FRAME_COUNT - 1) : IDLE_FRAME;

        // The leg cycle advances with the distance actually travelled (sideways on the stick,
        // upwards on the silk), so the feet match the ground speed instead of skating.
        SpiderPhysics.Mode mode = ph.getMode();
        float travel = 0f;
        if (mode == lastMode)
            travel = mode == SpiderPhysics.Mode.DANGLE ? Math.max(0f, lastCy - pose.cy)
                                                       : Math.abs(pose.cx - lastCx);
        lastMode = mode;
        lastCx = pose.cx;
        lastCy = pose.cy;
        // the lunge shifts the pose sideways, so hold the legs still during a swing
        boolean attacking = ph.getAtkPhase() != SpiderPhysics.AtkPhase.READY;
        boolean moving = dt > 0f && travel / dt > MOVE_EPS && !attacking;
        if (moving) phase = (phase + travel / STRIDE * FRAME_COUNT) % FRAME_COUNT;
        int idx = moving ? Math.min((int) phase, FRAME_COUNT - 1) : IDLE_FRAME;
        int atkIdx = attackFrameIndex();   // -1 unless a swing is in progress

        Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        vp.apply();
        batch.setProjectionMatrix(cam.combined);
        sr.setProjectionMatrix(cam.combined);

        float H = SpiderPhysics.H;
        boolean stickOver = pose.cy > ph.stickTopY + ph.stickH / 2f; // legs grip from beneath

        // thread (and stick when the spider is on top)
        sr.begin(ShapeType.Filled);
        if (!stickOver) drawStick(H);
        if (mode == SpiderPhysics.Mode.DANGLE) {
            ph.threadEnd(pose, tEnd);
            sr.setColor(0.6f, 0.6f, 0.6f, 1f);
            sr.rectLine(ph.getX(), H - ph.stickBotY, tEnd.x, H - tEnd.y, 1.2f);
        }
        sr.end();

        batch.begin();
        // enemy hangs upside-down under the stick (vertical scale -1); flashes red while stunned
        if (!ph.isEnemyDead()) {
            boolean flash = ph.getEnemyHurtTicks() > 0 && (ph.getEnemyHurtTicks() / 3) % 2 == 0;
            if (flash) batch.setColor(1f, 0.25f, 0.25f, 1f);
            drawFrame(enIdx, ex, ph.getEnemyCy(), 0f, (ph.isEnemyFacingRight() == FACES_RIGHT) ? 1f : -1f, -1f);
            batch.setColor(Color.WHITE);
        }
        float sx = (ph.isFacingRight() == FACES_RIGHT) ? 1f : -1f;
        if (atkIdx >= 0)
            drawRegion(atkFr[atkIdx], atkW, atkH, atkOx, atkOy, pose.cx, pose.cy, pose.rot, sx, pose.vs);
        else
            drawFrame(idx, pose.cx, pose.cy, pose.rot, sx, pose.vs);
        font.draw(batch, ph.checkCollision() ? "Collision!" : "No Collision", 20, H - 20);
        font.draw(batch, "Mode: " + mode, 20, H - 40);
        font.draw(batch, "Attack: " + ph.getAtkPhase(), 20, H - 60);
        font.draw(batch, ph.isEnemyDead() ? "Enemy: DEAD (respawning)"
                : "Enemy HP: " + ph.getEnemyHp() + "/" + ph.getEnemyMaxHp(), 20, H - 80);
        batch.end();

        if (stickOver) { sr.begin(ShapeType.Filled); drawStick(H); sr.end(); }

        if (SHOW_HITBOXES) {
            sr.begin(ShapeType.Line);
            sr.setColor(ph.checkCollision() ? Color.RED : Color.GREEN);
            box(ph.boundsOf(pose));
            if (!ph.isEnemyDead()) {
                sr.setColor(Color.BLACK);
                box(ph.enemyBoundsAt(a));
            }
            Rectangle atk = ph.getAttackBox();          // only non-null during Active
            if (atk != null) {
                sr.setColor(Color.ORANGE);
                box(atk);
            }
            sr.end();
        }
    }

    /** Purpose: Draw one frame centred on (cx, cy), stretched to the physics size so hitbox and art match.
     *  Parameters: i - frame, cx/cy - centre (y-down), rotCW - clockwise degrees, sx/sy - scale (negative = mirror) */
    private void drawFrame(int i, float cx, float cy, float rotCW, float sx, float sy) {
        drawRegion(fr[i], ph.getDw(), ph.getDh(), 0f, 0f, cx, cy, rotCW, sx, sy);
    }

    /** Purpose: Draw a region whose centre sits (ox, oy) away from the body centre, pivoting about the body centre.
     *  Rotation and mirroring therefore behave exactly like the walk sprite, even for a wider attack frame.
     *  Parameters: w/h - on-screen size; ox/oy - offset of the region's centre from the body centre (y-down);
     *  cx/cy - body centre (y-down); rotCW - clockwise degrees; sx/sy - scale (negative = mirror) */
    private void drawRegion(TextureRegion r, float w, float h, float ox, float oy,
                            float cx, float cy, float rotCW, float sx, float sy) {
        float wy = SpiderPhysics.H - cy;                 // body centre on the y-up screen
        float x = cx + ox - w / 2f, y = wy - oy - h / 2f; // region's bottom-left before scale/rotation
        batch.draw(r, x, y, cx - x, wy - y, w, h, sx, sy, -rotCW);
    }

    private void drawStick(float H) {
        sr.setColor(139 / 255f, 69 / 255f, 19 / 255f, 1);
        sr.rect(ph.stickL, H - ph.stickBotY, ph.stickR - ph.stickL, ph.stickH);
    }

    /** Purpose: Draw a y-down rectangle on the y-up screen.
     *  Parameters: b - rectangle in physics (y-down) space */
    private void box(Rectangle b) {
        sr.rect(b.x, SpiderPhysics.H - b.y - b.height, b.width, b.height);
    }

    @Override
    public void resize(int w, int h) { vp.update(w, h, true); }

    @Override
    public void dispose() {
        batch.dispose();
        sr.dispose();
        font.dispose();
        tex.dispose();
        atkTex.dispose();
    }
}