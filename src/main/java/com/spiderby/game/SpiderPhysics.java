package com.spiderby.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/** Physics for the spider. All coordinates are y-down (same as Lab 5). */
public class SpiderPhysics {
    public static final float W = 800f, H = 600f;
    public static final float SCALE = 0.5f;

    /** TOP = standing on the stick, UNDER = upside-down beneath it, DANGLE = on a silk dragline. */
    public enum Mode { TOP, UNDER, DANGLE }

    /** Basic attack cycle. READY = free to attack. */
    public enum AtkPhase { READY, WINDUP, ACTIVE, RECOVERY }

    /** Drawable pose: centre, clockwise rotation (deg) and vertical scale (-1 = upside-down). */
    public static class Pose {
        public float cx, cy, rot, vs;
        void set(Pose o) { cx = o.cx; cy = o.cy; rot = o.rot; vs = o.vs; }
        void lerp(Pose a, Pose b, float t) {
            cx = a.cx + (b.cx - a.cx) * t;
            cy = a.cy + (b.cy - a.cy) * t;
            rot = a.rot + (b.rot - a.rot) * t;
            vs = a.vs + (b.vs - a.vs) * t;
        }
    }

    public final float stickL = 100f, stickR = 700f, stickTopY = 300f, stickH = 3f;
    public final float stickBotY = stickTopY + stickH;
    private final float cling = stickH / 2f; // legs grip the stick's surface, no overlap with the other side

    private static final float WALK_TOP = 3.2f, WALK_UNDER = 2.4f, ACCEL = 0.4f, FRICTION = 0.7f;
    private static final float GRAVITY = 0.4f, FALL_MAX = 6f, CLIMB = 2.4f; // spiders climb slower than they drop
    private static final float DECAY = 0.8f; // how fast pose transitions settle

    private static final float EN_PATROL = 1.4f, EN_CHASE = 2.0f, EN_ACCEL = 0.15f; // enemy speeds, px per tick
    private static final float EN_SIGHT = 0f;  // > 0: the enemy hunts a player on top of the stick within this many px
    private static final int EN_PAUSE = 45;    // ticks the enemy rests at each end of the stick

    // ---- basic attack tuning (all times in ticks at 60 Hz) ----
    private static final int ATK_WINDUP = 6, ATK_ACTIVE = 5, ATK_RECOVERY = 12;
    private static final float ATK_REACH = 20f;      // px the hitbox extends past the front of the body
    private static final float ATK_LUNGE = 10f;      // forward px jump at the start of Active
    private static final float ATK_PULLBACK = 4f;    // backward px during Windup (telegraph)
    private static final float ATK_TILT = 8f;        // degrees of body tilt during the strike
    private static final float MOVE_WINDUP = 0.3f, MOVE_RECOVERY = 0.6f; // walk-speed multipliers

    // ---- enemy health / reactions ----
    private static final int EN_MAX_HP = 3, ATK_DAMAGE = 1;
    private static final int EN_STUN = 30;           // ticks the enemy is stunned after a hit
    private static final float EN_KNOCKBACK = 3f;    // px per tick, decays with EN_KNOCK_FRICTION
    private static final float EN_KNOCK_FRICTION = 0.85f;
    private static final int EN_RESPAWN = 120;       // ticks until a dead enemy returns (for testing)
    /** Which side of the stick the enemy lives on. Attacks only connect from the same side. */
    private static final Mode ENEMY_SIDE = Mode.UNDER;

    private final float dw, dh, maxLen;
    private float enX, prevEnX, enVx;   // enemy left edge, left edge one tick ago, velocity
    private boolean enRight;            // enemy heading / facing
    private int enWait;                 // ticks left in an end-of-stick rest
    private int enHp = EN_MAX_HP, enHurt, enRespawn;
    private boolean enDead;

    private AtkPhase atkPhase = AtkPhase.READY;
    private int atkTimer;
    private boolean atkRight, hitThisSwing;   // swing direction is locked at the start of the swing
    private float atkOffX, atkOffRot;         // lunge offset, kept separate from `off` so isSettled() is unaffected

    private Mode mode = Mode.UNDER;     // both spiders start hanging beneath the stick
    private float x, vx, len, vy;       // x = anchor point on the stick, len = silk paid out
    private boolean facingRight = true, headUp;

    // cur/prev for render interpolation; off = decaying offset that smooths mode changes
    private final Pose cur = new Pose(), prev = new Pose(), off = new Pose(), ta = new Pose(), tb = new Pose();

    /** Purpose: Build the physics state from the real (tight) sprite size.
     *  Parameters: fw, fh - widest visible frame width and visible height in source pixels
     *  Pre-condition: fw > 0 and fh > 0
     *  Post-condition: player hangs under the left end of the stick, enemy under the right end */
    public SpiderPhysics(float fw, float fh) {
        dw = fw * SCALE;
        dh = fh * SCALE;
        maxLen = Math.max(20f, H - 20f - stickBotY - dw);
        x = stickL + dw / 2f;           // player: under the left end
        enX = prevEnX = stickR - dw;    // enemy: under the right end
        enRight = false;                // ...heading left
        target(tb);
        cur.set(tb);
        prev.set(tb);
    }

    /** Purpose: Advance one fixed tick.
     *  Parameters: up, down, left, right - current key states; attack - an attack press was latched
     *  Pre-condition: called at a fixed 60 Hz
     *  Post-condition: mode, position, pose, attack phase and enemy state are updated */
    public void step(boolean up, boolean down, boolean left, boolean right, boolean attack) {
        prev.set(cur);
        boolean busy = atkPhase != AtkPhase.READY;   // no rolling or silk release mid-swing
        if (mode == Mode.DANGLE) dangle(up, down);
        else crawl(up && !busy, down && !busy, left, right, isSettled());

        attackStep(attack);

        off.cx *= DECAY; off.cy *= DECAY; off.rot *= DECAY; off.vs *= DECAY;
        target(ta);
        cur.cx = ta.cx + off.cx + atkOffX;
        cur.cy = ta.cy + off.cy;
        cur.rot = ta.rot + off.rot + atkOffRot;
        cur.vs = ta.vs + off.vs;

        resolveAttackHit();
        enemyStep();
    }

    /** Purpose: Run the attack cycle: start a swing, then Windup -> Active -> Recovery -> Ready.
     *  Post-condition: atkPhase/atkTimer advance; atkOffX/atkOffRot hold the lunge pose */
    private void attackStep(boolean attack) {
        if (atkPhase == AtkPhase.READY && attack && canAttack()) {
            atkPhase = AtkPhase.WINDUP;
            atkTimer = ATK_WINDUP;
            atkRight = facingRight;     // lock the direction for the whole swing
            hitThisSwing = false;
        }

        float dir = atkRight ? 1f : -1f;
        float tilt = dir * ATK_TILT * (mode == Mode.TOP ? 1f : -1f);
        switch (atkPhase) {
            case WINDUP:                // rear back (telegraph); no damage yet
                atkOffX += (-dir * ATK_PULLBACK - atkOffX) * 0.4f;
                atkOffRot += (-tilt * 0.5f - atkOffRot) * 0.4f;
                if (--atkTimer <= 0) {
                    atkPhase = AtkPhase.ACTIVE;
                    atkTimer = ATK_ACTIVE;
                    atkOffX = dir * ATK_LUNGE;      // lunge: snap forward, then decay
                    atkOffRot = tilt;
                }
                break;
            case ACTIVE:                // hitbox is live
                atkOffX *= DECAY; atkOffRot *= DECAY;
                if (--atkTimer <= 0) { atkPhase = AtkPhase.RECOVERY; atkTimer = ATK_RECOVERY; }
                break;
            case RECOVERY:              // can't attack again, slowed
                atkOffX *= DECAY; atkOffRot *= DECAY;
                if (--atkTimer <= 0) { atkPhase = AtkPhase.READY; atkOffX = 0f; atkOffRot = 0f; }
                break;
            default:
                atkOffX = 0f; atkOffRot = 0f;
        }
    }

    /** A swing may start on the stick (TOP or UNDER) and only when not mid-roll. */
    private boolean canAttack() { return mode != Mode.DANGLE && isSettled(); }

    /** Purpose: Damage the enemy once per swing if the live attack box touches it.
     *  Post-condition: enemy loses HP, is stunned and knocked back; dies at 0 HP */
    private void resolveAttackHit() {
        if (atkPhase != AtkPhase.ACTIVE || hitThisSwing || enDead) return;
        if (mode != ENEMY_SIDE) return;                       // same-side rule
        if (!getAttackBox().overlaps(getEnemyBounds())) return;

        hitThisSwing = true;
        enHp -= ATK_DAMAGE;
        enHurt = EN_STUN;
        enWait = 0;
        enVx = (atkRight ? 1f : -1f) * EN_KNOCKBACK;
        if (enHp <= 0) { enHp = 0; enDead = true; enRespawn = EN_RESPAWN; }
    }

    /** Purpose: Move the enemy one tick: patrol the stick end to end, or hunt a player on top.
     *  Post-condition: enX stays on the stick; enRight points the way the enemy is going */
    private void enemyStep() {
        prevEnX = enX;
        if (enDead) {                                                   // gone until it respawns
            if (--enRespawn <= 0) respawnEnemy();
            return;
        }
        if (enHurt > 0) {                                               // stunned: slide with knockback
            enHurt--;
            enVx *= EN_KNOCK_FRICTION;
            enX = MathUtils.clamp(enX + enVx, stickL, stickR - dw);
            return;
        }

        float dx = x - (enX + dw / 2f);
        float target;
        if (mode == Mode.TOP && Math.abs(dx) < EN_SIGHT) {              // player in sight: chase
            enWait = 0;
            target = Math.abs(dx) < 4f ? 0f : Math.signum(dx) * EN_CHASE;
            if (target != 0f) enRight = target > 0f;
        } else if (enWait > 0) {                                        // resting at the end
            if (--enWait == 0) enRight = !enRight;                      // then turn around
            target = 0f;
        } else if ((enRight && enX >= stickR - dw) || (!enRight && enX <= stickL)) {
            enWait = EN_PAUSE;                                          // reached the end
            target = 0f;
        } else {
            target = enRight ? EN_PATROL : -EN_PATROL;
        }
        enVx += MathUtils.clamp(target - enVx, -EN_ACCEL, EN_ACCEL);    // ease in and out
        enX = MathUtils.clamp(enX + enVx, stickL, stickR - dw);
    }

    private void respawnEnemy() {
        enDead = false;
        enHp = EN_MAX_HP;
        enHurt = 0;
        enWait = 0;
        enVx = 0f;
        enRight = false;
        enX = prevEnX = stickR - dw;
    }

    /** Walking on the top or underside, and rolling around the stick. */
    private void crawl(boolean up, boolean down, boolean left, boolean right, boolean settled) {
        if (atkPhase == AtkPhase.READY) {               // facing is locked during a swing
            if (left && !right) facingRight = false;
            if (right && !left) facingRight = true;
        }

        float scale = atkPhase == AtkPhase.READY ? 1f
                    : atkPhase == AtkPhase.RECOVERY ? MOVE_RECOVERY : MOVE_WINDUP;
        float max = (mode == Mode.TOP ? WALK_TOP : WALK_UNDER) * scale;
        int dir = settled ? (right ? 1 : 0) - (left ? 1 : 0) : 0; // no walking mid-roll
        if (dir != 0) vx += dir * ACCEL;
        else { vx *= FRICTION; if (Math.abs(vx) < 0.05f) vx = 0f; }
        vx = MathUtils.clamp(vx, -max, max);
        x += vx;
        if (x < stickL || x > stickR) { x = MathUtils.clamp(x, stickL, stickR); vx = 0f; }

        if (!settled) return;
        if (mode == Mode.TOP && down && !up) {
            retarget(Mode.UNDER, false);                 // roll under the stick
        } else if (mode == Mode.UNDER && up && !down) {
            retarget(Mode.TOP, false);                   // roll back on top
        } else if (mode == Mode.UNDER && down && !up) {  // release a dragline
            len = 0f; vy = 0f; vx = 0f;
            retarget(Mode.DANGLE, false);
        }
    }

    /** Hanging on silk: pay out, climb, brake and bounce. The spider stays directly
     *  below its anchor point (x), so the thread is always perfectly vertical. */
    private void dangle(boolean up, boolean down) {
        if (up) {
            vy += (-CLIMB - vy) * 0.3f;
            if (!headUp) retarget(Mode.DANGLE, true);    // turn head-up to climb
        } else if (down) {
            vy = Math.min(vy + GRAVITY, FALL_MAX);
            if (headUp) retarget(Mode.DANGLE, false);    // head-down to descend
        } else {
            vy *= 0.85f;                                  // silk brake
            if (Math.abs(vy) < 0.02f) vy = 0f;
        }

        len += vy;
        if (len >= maxLen) {                              // end of the silk: small elastic bounce
            len = maxLen;
            if (vy > 0f) vy = down ? 0f : -vy * 0.25f;
        }

        if (len <= 0f) {                                  // fully reeled in
            len = 0f; vy = 0f;
            if (!down) retarget(Mode.UNDER, false);
        }
    }

    /** Switch mode/heading while keeping the drawn pose continuous (offset decays to 0). */
    private void retarget(Mode m, boolean hu) {
        target(ta);
        mode = m;
        headUp = hu;
        target(tb);
        off.cx += ta.cx - tb.cx;
        off.cy += ta.cy - tb.cy;
        off.rot += ta.rot - tb.rot;
        off.vs += ta.vs - tb.vs;
    }

    /** Ideal pose for the current mode. */
    private void target(Pose t) {
        switch (mode) {
            case TOP:
                t.cx = x; t.cy = stickTopY + cling - dh / 2f; t.rot = 0f; t.vs = 1f;
                break;
            case UNDER:
                t.cx = x; t.cy = stickBotY - cling + dh / 2f; t.rot = 0f; t.vs = -1f;
                break;
            default: {
                float base = facingRight ? 90f : -90f;
                t.cx = x;                                 // always directly below the anchor
                t.cy = stickBotY + len + dw / 2f;
                t.rot = headUp ? -base : base;
                t.vs = 1f;
            }
        }
    }

    private boolean isSettled() {
        return Math.abs(off.cx) < 2f && Math.abs(off.cy) < 2f
            && Math.abs(off.rot) < 4f && Math.abs(off.vs) < 0.1f;
    }

    /** Purpose: Pose blended between the last two ticks for smooth rendering.
     *  Parameters: a - blend factor 0..1; out - receives the pose */
    public void interpolate(float a, Pose out) { out.lerp(prev, cur, MathUtils.clamp(a, 0f, 1f)); }

    /** Returns: tight AABB of a pose (handles rotation and squash while rolling). */
    public Rectangle boundsOf(Pose p) {
        float hw = dw / 2f, hh = dh / 2f * Math.abs(p.vs);
        float r = p.rot * MathUtils.degRad;
        float c = Math.abs(MathUtils.cos(r)), s = Math.abs(MathUtils.sin(r));
        float bw = 2f * (hw * c + hh * s), bh = 2f * (hw * s + hh * c);
        return new Rectangle(p.cx - bw / 2f, p.cy - bh / 2f, bw, bh);
    }

    /** Returns: where the silk meets the spider's body (for drawing the thread). */
    public void threadEnd(Pose p, Vector2 out) {
        float dx = p.cx - x, dy = p.cy - stickBotY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.001f) { out.set(x, stickBotY); return; }
        float k = Math.max(0f, d - (dw / 2f - 4f)) / d;
        out.set(x + dx * k, stickBotY + dy * k);
    }

    /** Returns: 0..1 leg-animation speed (ground speed, or climbing speed on silk). */
    public float gaitRate() {
        if (mode == Mode.DANGLE) return Math.max(0f, -vy) / CLIMB;
        return Math.abs(vx) / (mode == Mode.TOP ? WALK_TOP : WALK_UNDER);
    }

    public Rectangle getPlayerBounds() { return boundsOf(cur); }
    public Rectangle getEnemyBounds() { return enemyBoundsAt(1f); }
    public boolean checkCollision() { return !enDead && getPlayerBounds().overlaps(getEnemyBounds()); }

    /** Returns: the live attack hitbox (from the body centre to reach past the front), or null outside Active.
     *  Starting at the centre means an enemy overlapping the body is still hit. */
    public Rectangle getAttackBox() {
        if (atkPhase != AtkPhase.ACTIVE) return null;
        Rectangle b = boundsOf(cur);
        float cx = b.x + b.width / 2f;
        float w = b.width / 2f + ATK_REACH;
        return new Rectangle(atkRight ? cx : cx - w, b.y, w, b.height);
    }

    public Mode getMode() { return mode; }
    public AtkPhase getAtkPhase() { return atkPhase; }

    /** Returns: 0..1 progress through the current attack phase (0 when READY), for picking animation frames. */
    public float getAtkProgress() {
        int total;
        switch (atkPhase) {
            case WINDUP:   total = ATK_WINDUP;   break;
            case ACTIVE:   total = ATK_ACTIVE;   break;
            case RECOVERY: total = ATK_RECOVERY; break;
            default:       return 0f;
        }
        return MathUtils.clamp(1f - (float) atkTimer / total, 0f, 1f);
    }
    public int getEnemyHp() { return enHp; }
    public int getEnemyMaxHp() { return EN_MAX_HP; }
    public boolean isEnemyDead() { return enDead; }
    public int getEnemyHurtTicks() { return enHurt; }
    public boolean isFacingRight() { return facingRight; }
    public float getX() { return x; }
    public float getDw() { return dw; }
    public float getDh() { return dh; }
    public float getEnemyCx() { return enemyCx(1f); }

    /** Returns: enemy centre x blended between the last two ticks (a = 0..1), for smooth rendering. */
    public float enemyCx(float a) { return prevEnX + (enX - prevEnX) * MathUtils.clamp(a, 0f, 1f) + dw / 2f; }

    /** Returns: enemy bounds at the same blended position (hanging under the stick). */
    public Rectangle enemyBoundsAt(float a) {
        return new Rectangle(enemyCx(a) - dw / 2f, stickBotY - cling, dw, dh);
    }

    public boolean isEnemyFacingRight() { return enRight; }
    public float getEnemyCy() { return stickBotY - cling + dh / 2f; }
}