package dev.aller.screen.onboarding;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.font.SdfAtlas;

import java.util.Arrays;
import java.util.Random;

/**
 * The first thing a new player sees: half a minute in which the wordmark is put together out of
 * shards, shot as a 3D scene through a moving camera. A point of light in the dark, a flight down a
 * winding tunnel, out of it over a floor with monoliths going past towards a black orb, the camera
 * swinging round the orb while a disc of lit, tumbling shards gathers round it, and then it waits:
 * the player has to break the orb open by mashing keys. That sets off the collapse of the shards into
 * the letters, the hit when they lock, a held shot of the wordmark, and the settle into the header of
 * the steps that follow.
 *
 * <p>Everything is a function of one clock, so any moment can be drawn on its own. The clock stops
 * at {@link #GATE} until the orb is broken; what moves meanwhile runs on the time spent there.
 *
 * <p>It is drawn while the canvas is drained of colour: every colour here is a grey on purpose.
 */
public final class Intro {
    /** The moments the piece turns on, in seconds. */
    public static final float BURST = 3.0f, GRID = 8.5f, VORTEX = 13.5f, GATE = 19.0f, SNAP = 20.3f, SETTLE = 25.6f, LENGTH = 27.0f;
    private static final int LOGO = 680, FIELD = 150, ALL = LOGO + FIELD, MOTES = 90, RAYS = 44, CORONA = 36, CRACKS = 24, BOLTS = 10, BOLT_PARTS = 7;
    private static final float TAU = 6.2831855f;
    /** What one press adds to the pressure on the orb, and what leaks away each second. */
    private static final float STRIKE = 0.07f, LEAK = 0.1f;
    /** The beats of the build-up, each a pulse from the centre. */
    private static final float[] BEATS = {VORTEX, VORTEX + 1.5f, VORTEX + 2.5f, VORTEX + 3.5f, VORTEX + 4f, VORTEX + 4.5f, VORTEX + 4.75f, VORTEX + 5f, VORTEX + 5.25f};
    /** The opening's heartbeats. */
    private static final float[] HEART = {0.84f, 1.78f, 2.47f};

    // The world, in its own units: the tunnel, the floor, the orb, the disc round it.
    private static final float RING_GAP = 4, TUNNEL_R = 5, SIDES = 8, FLOOR_Y = -4, ORB_R = 4, ORB_Y = 6, ORB_DIST = 40, DISC = 26, NEAR = 0.4f;
    /** Where the tunnel comes out, and how far beyond that the orb stands. */
    private static final float EXIT = 14 * travelAt(GRID), ORB_Z = EXIT + 125;

    private record Cue(float at, String sound, float pitch, float volume) {}

    /** Sound effects on the moments; no music. */
    private static final Cue[] CUES = {
            new Cue(0.05f, "block.beacon.ambient", 0.5f, 0.7f),
            new Cue(HEART[0], "block.note_block.basedrum", 0.55f, 0.8f),
            new Cue(HEART[1], "block.note_block.basedrum", 0.62f, 0.9f),
            new Cue(HEART[2], "block.note_block.basedrum", 0.7f, 1f),
            new Cue(BURST - 0.05f, "block.beacon.activate", 0.7f, 0.8f),
            new Cue(BURST, "item.trident.riptide_3", 0.6f, 0.5f),
            new Cue(BURST, "entity.generic.explode", 0.6f, 0.3f),
            new Cue(BURST + 1.6f, "item.elytra.flying", 1.2f, 0.25f),
            new Cue(GRID - 0.1f, "item.trident.riptide_2", 0.8f, 0.5f),
            new Cue(GRID, "block.amethyst_cluster.break", 0.7f, 0.8f),
            new Cue(GRID + 0.3f, "block.beacon.ambient", 0.7f, 0.6f),
            new Cue(VORTEX - 1.2f, "block.conduit.activate", 0.6f, 0.7f),
            new Cue(BEATS[0], "block.respawn_anchor.charge", 0.6f, 0.6f),
            new Cue(BEATS[1], "block.respawn_anchor.charge", 0.76f, 0.63f),
            new Cue(BEATS[2], "block.respawn_anchor.charge", 0.92f, 0.66f),
            new Cue(BEATS[3], "block.respawn_anchor.charge", 1.08f, 0.7f),
            new Cue(BEATS[4], "block.respawn_anchor.charge", 1.24f, 0.73f),
            new Cue(BEATS[5], "block.respawn_anchor.charge", 1.4f, 0.76f),
            new Cue(BEATS[6], "block.respawn_anchor.charge", 1.56f, 0.8f),
            new Cue(BEATS[7], "block.respawn_anchor.charge", 1.72f, 0.83f),
            new Cue(BEATS[8], "block.respawn_anchor.charge", 1.88f, 0.86f),
            new Cue(GATE, "block.bell.use", 0.5f, 0.9f),
            new Cue(GATE, "block.note_block.basedrum", 0.5f, 1f),
            new Cue(GATE + 0.02f, "block.glass.break", 0.6f, 0.8f),
            new Cue(GATE + 0.02f, "item.trident.riptide_1", 0.5f, 0.7f),
            new Cue(GATE + 0.45f, "block.beacon.deactivate", 1.5f, 0.6f),
            new Cue(SNAP, "entity.generic.explode", 0.55f, 0.55f),
            new Cue(SNAP, "block.beacon.power_select", 0.8f, 0.9f),
            new Cue(SNAP, "block.amethyst_cluster.break", 0.6f, 0.9f),
            new Cue(SNAP, "entity.lightning_bolt.thunder", 1.2f, 0.25f),
            new Cue(SNAP + 0.42f, "entity.experience_orb.pickup", 0.7f, 0.6f),
            new Cue(SNAP + 0.8f, "block.amethyst_block.resonate", 1f, 0.8f),
            new Cue(SETTLE, "item.trident.riptide_1", 1.4f, 0.25f),
    };

    // Shards that end up as the letters: where each lands (em units from the wordmark's top centre),
    // and its place on the disc before that.
    private final float[] tx = new float[LOGO], ty = new float[LOGO];
    private final float[] angle = new float[LOGO], reach = new float[LOGO], spin = new float[LOGO], wait = new float[LOGO];
    private final float[] grain = new float[LOGO], turn = new float[LOGO], shade = new float[LOGO], fling = new float[LOGO];
    private final byte[] sides = new byte[LOGO];
    // Larger shards that never join the letters: the tunnel's, the outer ring, the debris.
    private final float[] fAngle = new float[FIELD], fReach = new float[FIELD], fDepth = new float[FIELD], fSize = new float[FIELD];
    private final float[] fTurn = new float[FIELD], fSpin = new float[FIELD], fShade = new float[FIELD], fFling = new float[FIELD];
    private final float[] fLift = new float[FIELD];
    private final byte[] fSides = new byte[FIELD];
    private final float[] mx = new float[MOTES], my = new float[MOTES], mz = new float[MOTES];
    private final float[] rayAngle = new float[RAYS], rayLag = new float[RAYS];
    private final float[] coronaAngle = new float[CORONA], coronaLen = new float[CORONA];
    private final float[] crackAngle = new float[CRACKS], crackBend = new float[CRACKS * 4];
    private final float[] boltAngle = new float[BOLTS], boltBend = new float[BOLTS * BOLT_PARTS];

    // Each shard this frame: where it is in the world, how it is turned, how far it is from the camera.
    private final float[] wx = new float[ALL], wy = new float[ALL], wz = new float[ALL], depth = new float[ALL];
    private final long[] order = new long[ALL];

    private float t;
    private int cue;
    /** At the gate: seconds spent there, how nearly the orb is broken, and whether it has been. */
    private float gateT, pressure;
    private boolean broke;
    private int strikes;
    /** 1 at a press and dying away; the turning the disc has done while the clock stood still. */
    private float jolt, whirl;
    /** The pointer, -1 to 1 each way from the middle, eased. */
    private float px, py;

    // The camera: where it is, its right, up and forward, its focal length and the middle of the screen.
    private float camX, camY, camZ, rX, rY, rZ, uX, uY, uZ, fX, fY, fZ, focal, scx, scy, roll;
    /** The last point projected: screen place, distance in front of the camera, pixels per world unit. */
    private float sx, sy, sz, ss;
    // The orb on screen this frame, and the disc's plane.
    private float ox, oy, oz, or;
    private float e1x, e1y, nx, ny;

    public Intro() {
        Random r = new Random(0xA11E5L);
        SdfAtlas atlas = Fonts.BOLD.atlas();
        String word = AllerClient.NAME;
        float[] pen = new float[word.length()];
        float total = 0, area = 0;
        for (int i = 0; i < word.length(); i++) {
            pen[i] = total;
            SdfAtlas.Glyph g = atlas.glyph(word.charAt(i));
            total += g.advance() - SdfAtlas.EM * TRACK;
            area += g.w() * g.h();
        }
        float dotX = total + SdfAtlas.EM * 0.19f, dotY = SdfAtlas.EM * 0.86f, dotR = SdfAtlas.EM * 0.095f;
        total += SdfAtlas.EM * 0.3f;
        for (int k = 0; k < LOGO; k++) {
            if (k < 14 || area <= 0) {
                // The full stop gets its own few.
                double a = r.nextDouble() * TAU, d = Math.sqrt(r.nextDouble()) * dotR;
                tx[k] = dotX + (float) (Math.cos(a) * d) - total / 2;
                ty[k] = dotY + (float) (Math.sin(a) * d);
            } else {
                // A letter chosen by the room it takes, then a point inside its outline.
                float pick = r.nextFloat() * area;
                int ch = 0;
                for (; ch < word.length() - 1; ch++) {
                    SdfAtlas.Glyph g = atlas.glyph(word.charAt(ch));
                    pick -= g.w() * g.h();
                    if (pick <= 0) break;
                }
                SdfAtlas.Glyph g = atlas.glyph(word.charAt(ch));
                int u = Math.round(g.u0() * SdfAtlas.SIZE), v = Math.round(g.v0() * SdfAtlas.SIZE);
                float gx = g.w() / 2, gy = g.h() / 2;
                for (int tries = 0; tries < 60; tries++) {
                    float ax = r.nextFloat() * g.w(), ay = r.nextFloat() * g.h();
                    int texel = atlas.pixels[Math.min(SdfAtlas.SIZE - 1, v + (int) ay) * SdfAtlas.SIZE + Math.min(SdfAtlas.SIZE - 1, u + (int) ax)];
                    if (texel >>> 24 > 150) {
                        gx = ax;
                        gy = ay;
                        break;
                    }
                }
                tx[k] = pen[ch] + g.xOff() + gx - total / 2;
                ty[k] = atlas.ascent + g.yOff() + gy;
            }
            angle[k] = r.nextFloat() * TAU;
            reach[k] = 0.3f + 0.7f * (float) Math.sqrt(r.nextFloat());
            spin[k] = (0.75f + 0.5f * r.nextFloat()) * (1.7f - reach[k]);
            wait[k] = r.nextFloat() * 0.32f;
            grain[k] = 0.8f + 1.5f * r.nextFloat() * r.nextFloat();
            turn[k] = (r.nextFloat() - 0.5f) * 7f;
            shade[k] = r.nextFloat();
            fling[k] = r.nextFloat() < 0.3f ? 70 + 330 * r.nextFloat() : 0;
            sides[k] = (byte) (r.nextFloat() < 0.5f ? 3 : r.nextFloat() < 0.6f ? 4 : 6);
        }
        for (int i = 0; i < FIELD; i++) {
            fAngle[i] = r.nextFloat() * TAU;
            fReach[i] = r.nextFloat();
            fDepth[i] = r.nextFloat();
            fSize[i] = 0.35f + 0.9f * r.nextFloat() * r.nextFloat();
            fTurn[i] = (r.nextFloat() - 0.5f) * 4f;
            fSpin[i] = -(0.35f + 0.5f * r.nextFloat());
            fShade[i] = r.nextFloat();
            fFling[i] = r.nextFloat();
            fLift[i] = r.nextFloat() * 2 - 1;
            float kind = r.nextFloat();
            fSides[i] = (byte) (kind < 0.45f ? 3 : kind < 0.75f ? 4 : 6);
        }
        for (int i = 0; i < MOTES; i++) {
            mx[i] = (r.nextFloat() - 0.5f) * 30;
            my[i] = (r.nextFloat() - 0.5f) * 18;
            mz[i] = 4 + r.nextFloat() * 70;
        }
        for (int i = 0; i < RAYS; i++) {
            rayAngle[i] = r.nextFloat() * TAU;
            rayLag[i] = r.nextFloat();
        }
        for (int i = 0; i < CORONA; i++) {
            coronaAngle[i] = (i + r.nextFloat() * 0.8f) / CORONA * TAU;
            coronaLen[i] = r.nextFloat() * r.nextFloat();
        }
        for (int i = 0; i < CRACKS; i++) crackAngle[i] = i * 2.399f + r.nextFloat() * 0.5f;
        for (int i = 0; i < crackBend.length; i++) crackBend[i] = r.nextFloat() - 0.5f;
        for (int i = 0; i < BOLTS; i++) boltAngle[i] = (i + r.nextFloat() * 0.7f) / BOLTS * TAU;
        for (int i = 0; i < boltBend.length; i++) boltBend[i] = r.nextFloat() * 2 - 1;
    }

    public float time() {
        return t;
    }

    public boolean done() {
        return t >= LENGTH;
    }

    /** Whether it is standing at the gate, waiting to be broken open. */
    public boolean waiting() {
        return t >= GATE && !broke;
    }

    /** Jumps to a moment without playing the sounds on the way there. Past the gate, the gate counts as broken. */
    public void seek(float seconds) {
        t = seconds;
        cue = 0;
        while (cue < CUES.length && CUES[cue].at <= t) cue++;
        broke = t > GATE;
        pressure = broke ? 1 : 0;
        strikes = broke ? 16 : 0;
        gateT = jolt = 0;
    }

    /** For a still of the gate part way through being broken. */
    public void press(float amount) {
        pressure = Math.clamp(amount, 0f, 0.99f);
        strikes = Math.round(pressure / STRIKE);
        gateT = 2;
    }

    /** One press from the player. It only counts at the gate. */
    public void strike() {
        if (!waiting() || gateT < 0.2f) return;
        pressure += STRIKE;
        strikes++;
        jolt = 1;
        Sounds.cue("block.note_block.basedrum", 0.5f + 0.4f * pressure, 0.9f);
        Sounds.cue("block.glass.break", 0.7f + 0.8f * pressure, 0.3f);
        Sounds.cue("block.amethyst_block.hit", 0.6f + 1.2f * pressure, 0.7f);
        if (strikes % 4 == 0) Sounds.cue("block.respawn_anchor.charge", 0.7f + 1.1f * pressure, 0.6f);
        if (pressure >= 1) {
            pressure = 1;
            broke = true;
        }
    }

    /**
     * Moves the clock on by a frame and plays whatever falls due.
     *
     * @param pointerX where the pointer is, -1 at the left edge to 1 at the right
     * @param pointerY and -1 at the top to 1 at the bottom
     */
    public void advance(float dt, float pointerX, float pointerY) {
        float ease = Math.min(1f, dt * 3);
        px += (Math.clamp(pointerX, -1f, 1f) - px) * ease;
        py += (Math.clamp(pointerY, -1f, 1f) - py) * ease;
        jolt *= (float) Math.exp(-dt * 9);
        if (waiting()) {
            t = GATE;
            gateT += dt;
            pressure = Math.max(0, pressure - LEAK * dt);
            whirl += dt * (0.4f + 0.9f * pressure);
        } else {
            t += dt;
            if (t > GATE && !broke) t = GATE;
        }
        while (cue < CUES.length && CUES[cue].at <= t) {
            Cue c = CUES[cue++];
            Sounds.cue(c.sound, c.pitch, c.volume);
        }
    }

    /** How strongly the backdrop behind the steps has arrived, 0 to 1 and a little over at the hit. */
    public float backdrop() {
        float u = t - SNAP;
        return u < 0 ? 0 : Math.min(1f, u / 1.4f) + 0.9f * (float) Math.exp(-u * 4);
    }

    /**
     * @param headCx   where the wordmark comes to rest once the piece is over: centre,
     * @param headTop  top
     * @param headSize and text size
     */
    public void draw(Canvas c, float w, float h, float headCx, float headTop, float headSize) {
        float cx = w / 2, cy = h / 2, unit = Math.min(w, h);
        float markSize = Math.clamp(Math.min(w * 0.088f, h * 0.17f), 22f, 68f);
        float markTop = cy - markSize * 0.66f;
        float live = t + gateT, u = t - SNAP;
        boolean before = u < 0;
        float held = t >= GATE && before ? pressure : 0;
        camera(cx, cy, unit, live, held);

        float bars = h * (0.115f + 0.03f * held) * (1 - smooth((u - 0.2f) / 0.9f)) * smooth(t / 0.6f);

        c.push();
        float shake = 2.6f * decay(t - BURST, 5) + 3f * decay(t - GRID, 5) + 9f * decay(u, 5.5f)
                + (before ? 1.2f * held + 4f * jolt + 1.6f * smooth((t - GATE) / (SNAP - GATE)) * (broke ? 1 : 0) : 0);
        for (float beat : BEATS) shake += 1.3f * decay(t - beat, 9);
        if (t > BURST && t < GRID) shake += 0.5f * smooth((t - BURST) / 4f);
        if (shake > 0.02f) c.translate((float) Math.sin(live * 97) * shake, (float) Math.cos(live * 83) * shake);
        if (before) c.scale(1 + 0.05f * held + 0.03f * jolt, cx, cy);

        if (t < BURST + 0.25f) spark(c, w);
        if (t >= BURST - 0.05f && t < GRID + 1.5f) tunnel(c, live);
        if (t >= GRID - 0.3f && u < 0.3f) floor(c, w, h, u);
        if (t >= BURST && t < VORTEX) dust(c);
        if (t >= VORTEX - 0.2f && before) orbits(c, live);

        // The wordmark: shards up to the hit, type from it, then the glide up to the header.
        float rise = Easing.IN_OUT_CUBIC.apply(Math.clamp((t - SETTLE) / (LENGTH - SETTLE), 0f, 1f));
        float size = markSize + (headSize - markSize) * rise;
        float mcx = cx + (headCx - cx) * rise, top = markTop + (headTop - markTop) * rise;
        boolean orb = t >= GRID - 0.2f && u < 0.15f;
        if (orb) orbAt(live, held);
        // Everything that is a shard, far to near, with the orb in its place among them.
        int n = place(live, unit, u, mcx, top, size);
        boolean orbDrawn = !orb;
        for (int k = 0; k < n; k++) {
            int i = (int) order[k];
            if (!orbDrawn && depth[i] < oz) {
                drawOrb(c, w, h, live, held);
                orbDrawn = true;
            }
            drawShard(c, i, live, u, mcx, top, size);
        }
        if (!orbDrawn) drawOrb(c, w, h, live, held);
        if (t >= GATE - 0.1f && u < 0.1f) rays(c, Math.max(w, h));
        if (waiting() || t >= GATE && t < GATE + 0.3f) prompt(c, cx, cy + unit * 0.3f, unit, live);
        if (u >= 0) {
            float my = markTop + markSize * 0.5f;
            beams(c, cx, my, unit, Math.max(w, h), u, 1 - rise);
            embers(c, w, h, u, live, 1 - rise);
            hit(c, cx, my, w, h, u);
            float punch = 1 + 0.17f * (float) (Math.exp(-u * 6) * Math.sin(u * 20));
            float push = 1 + 0.06f * Math.clamp(u / (SETTLE - SNAP), 0f, 1f) * (1 - rise);
            c.push();
            c.scale(punch * push, mcx, top + size * 0.5f);
            mark(c, mcx, top, size, Math.min(1f, u / 0.07f), Easing.OUT_BACK.apply(Math.clamp((u - 0.42f) / 0.35f, 0f, 1f)),
                    Colors.WHITE, u < 2.6f ? (u - 0.55f) * 16f : (u - 3.1f) * 16f);
            c.pop();
            tagline(c, cx, markTop + markSize * 1.42f, markSize, u);
        }
        c.pop();

        // A lens's darker corners, until the steps take over.
        float vig = 1 - smooth((u - (SETTLE - SNAP)) / 1.2f);
        if (vig > 0.01f) {
            int dark = Colors.withAlpha(Colors.BLACK, 0.55f * vig);
            c.gradientV(0, 0, w, h * 0.3f, 0, dark, 0);
            c.gradientV(0, h * 0.7f, w, h * 0.3f, 0, 0, dark);
            c.gradientH(0, 0, w * 0.22f, h, 0, dark, 0);
            c.gradientH(w * 0.78f, 0, w * 0.22f, h, 0, 0, dark);
        }
        float flash = 0.55f * decay(t - BURST, 6) + 0.6f * decay(t - GRID, 5) + 0.9f * decay(u, 6.5f) + (before ? 0.1f * jolt : 0);
        if (flash > 0.01f) c.rect(0, 0, w, h, 0, Colors.withAlpha(Colors.WHITE, Math.min(1f, flash)));
        if (bars > 0.3f) {
            c.rect(0, 0, w, bars, 0, Colors.BLACK);
            c.rect(0, h - bars, w, bars, 0, Colors.BLACK);
        }
        if (t < 0.5f) c.rect(0, 0, w, h, 0, Colors.withAlpha(Colors.BLACK, 1 - t / 0.5f));
    }

    // ---- the camera ----------------------------------------------------------------------------

    private static float travelAt(float time) {
        float g = Math.max(0, Math.min(time, VORTEX + 1.1f) - BURST), in = Math.min(g, GRID - BURST);
        return 0.5f * in + 0.11f * in * in + Math.max(0, g - in) * 1.2f;
    }

    /** How far the tunnel is pushed aside at a distance along it; it straightens out by the exit. */
    private static float pathX(float z) {
        return 6 * (float) Math.sin(z * 0.045f) * (1 - smooth((z - EXIT + 12) / 30));
    }

    private static float pathY(float z) {
        return 3.5f * (float) (Math.sin(z * 0.031f + 1) - Math.sin(1)) * (1 - smooth((z - EXIT + 12) / 30));
    }

    /**
     * Places the camera: drifting towards the point of light, flying the tunnel and the floor, then
     * swinging round the orb. The pointer moves it a little throughout.
     */
    private void camera(float cx, float cy, float unit, float live, float held) {
        scx = cx;
        scy = cy;
        float tunnel = smooth((t - BURST) / 4f) * (1 - smooth((t - GRID) / 1.5f));
        focal = unit * (0.95f - 0.25f * tunnel);
        float z = t < BURST ? -3 + 3 * smooth(t / BURST) : 14 * travelAt(t);
        float x, y, lx, ly, lz;
        if (t < BURST) {
            x = px * 0.8f;
            y = -py * 0.5f;
            lx = 0;
            ly = 0;
            lz = 60;
            roll = 0;
        } else {
            float grid = smooth((t - GRID + 0.5f) / 1.5f);
            x = pathX(z) + px * 1.2f;
            y = pathY(z) - py * 0.8f;
            lx = pathX(z + 18) + px * 0.5f;
            ly = pathY(z + 18) + grid * 1.2f;
            lz = z + 18;
            // Banking into the bends.
            roll = (0.18f * (float) Math.sin(t * 0.6f) - 1.6f * (pathX(z + 10) - pathX(z)) / 10) * (1 - grid) + 0.03f * (float) Math.sin(t * 0.4f) * grid;
        }
        // Round the orb.
        float b = smooth((t - (VORTEX - 1.4f)) / 2.2f);
        if (b > 0) {
            float u = Math.max(0, t - SNAP);
            float az = -0.35f + 0.05f * (live - VORTEX) + px * 0.4f, el = 0.32f - py * 0.18f;
            float d = ORB_DIST * (1 - 0.14f * held) * (1 - 0.06f * smooth(u / 5));
            float ax = d * (float) (Math.sin(az) * Math.cos(el)), ay = ORB_Y + d * (float) Math.sin(el), az2 = ORB_Z - d * (float) (Math.cos(az) * Math.cos(el));
            x += (ax - x) * b;
            y += (ay - y) * b;
            z += (az2 - z) * b;
            lx += (0 - lx) * b;
            ly += (ORB_Y - ly) * b;
            lz += (ORB_Z - lz) * b;
            roll *= 1 - b;
        }
        camX = x;
        camY = y;
        camZ = z;
        fX = lx - x;
        fY = ly - y;
        fZ = lz - z;
        float len = (float) Math.sqrt(fX * fX + fY * fY + fZ * fZ);
        fX /= len;
        fY /= len;
        fZ /= len;
        // Right from forward and the world's up, then the camera's up from forward and right.
        float ax = fZ, az = -fX;
        len = (float) Math.sqrt(ax * ax + az * az);
        ax /= len;
        az /= len;
        float bx = az * fY, by = ax * fZ - az * fX, bz = -ax * fY;
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        rX = ax * cr + bx * sr;
        rY = by * sr;
        rZ = az * cr + bz * sr;
        uX = bx * cr - ax * sr;
        uY = by * cr;
        uZ = bz * cr - az * sr;
        // The disc leans a little and rocks.
        float tau = 0.28f + 0.06f * (float) Math.sin(live * 0.5f);
        e1x = (float) Math.cos(tau);
        e1y = (float) Math.sin(tau);
        nx = e1y;
        ny = -e1x;
    }

    /** Projects a point of the world onto the screen; false if it is behind the camera. */
    private boolean project(float x, float y, float z) {
        float dx = x - camX, dy = y - camY, dz = z - camZ;
        sz = dx * fX + dy * fY + dz * fZ;
        if (sz < NEAR) return false;
        ss = focal / sz;
        sx = scx + (dx * rX + dy * rY + dz * rZ) * ss;
        sy = scy - (dx * uX + dy * uY + dz * uZ) * ss;
        return true;
    }

    /** A line between two points of the world, cut short where it passes behind the camera. */
    private void seg(Canvas c, float x0, float y0, float z0, float x1, float y1, float z1, float thickness, int color) {
        float d0 = (x0 - camX) * fX + (y0 - camY) * fY + (z0 - camZ) * fZ, d1 = (x1 - camX) * fX + (y1 - camY) * fY + (z1 - camZ) * fZ;
        if (d0 < NEAR && d1 < NEAR) return;
        if (d0 < NEAR || d1 < NEAR) {
            float k = (NEAR - d0) / (d1 - d0);
            if (d0 < NEAR) {
                x0 += (x1 - x0) * k;
                y0 += (y1 - y0) * k;
                z0 += (z1 - z0) * k;
            } else {
                x1 = x0 + (x1 - x0) * k;
                y1 = y0 + (y1 - y0) * k;
                z1 = z0 + (z1 - z0) * k;
            }
        }
        project(x0, y0, z0);
        float ax = sx, ay = sy;
        project(x1, y1, z1);
        c.line(ax, ay, sx, sy, thickness, color);
    }

    /** How much of something this far away shows through the haze. */
    private float fog(float distance) {
        return (float) Math.exp(-Math.max(0, distance - 6) / (t < VORTEX ? 70 : 130));
    }

    // ---- the scenery ---------------------------------------------------------------------------

    /** The opening: one point of light far ahead with a heartbeat, and dust hanging between it and the camera. */
    private void spark(Canvas c, float w) {
        float on = smooth(t / 0.9f) * (1 - smooth((t - BURST) / 0.2f));
        float pulse = 0.5f * decay(t - HEART[0], 5) + 0.7f * decay(t - HEART[1], 5) + 1f * decay(t - HEART[2], 5);
        for (int i = 0; i < MOTES; i++) {
            if (!project(mx[i], my[i], mz[i] - 1.5f * t)) continue;
            float twinkle = 0.5f + 0.5f * (float) Math.sin(t * 2.3f + i * 1.7f);
            float r = Math.max(0.4f, 0.05f * ss);
            c.circle(sx, sy, r, grey(0.9f, on * fog(sz) * (0.15f + 0.4f * twinkle) * Math.min(1f, 1.5f / r)));
        }
        if (!project(0, 0, 60)) return;
        float cx = sx, cy = sy;
        float r = 1.1f + 2.2f * pulse + 5 * smooth((t - 2.4f) / 0.5f);
        c.oval(cx, cy, 30 + 70 * pulse, 30 + 70 * pulse, 0.95f, grey(1f, 0.16f * on + 0.22f * pulse * on));
        c.oval(cx, cy, 8 + 10 * pulse, 8 + 10 * pulse, 0.8f, grey(1f, 0.5f * on));
        c.circle(cx, cy, r, grey(1f, on));
        for (float beat : HEART) {
            float p = (t - beat) / 0.9f;
            if (p > 0 && p < 1) c.ring(cx, cy, 4 + 90 * Easing.OUT_CUBIC.apply(p), 1.4f * (1 - p), grey(0.9f, 0.5f * (1 - p)));
        }
        float s = smooth((t - 1.7f) / 1.0f) * (1 - smooth((t - (BURST - 0.22f)) / 0.2f));
        if (s > 0.01f) {
            float half = w * 0.42f * s;
            c.gradientH(cx - half, cy - 0.5f, half, 1f, 0, grey(1f, 0), grey(1f, 0.85f * on));
            c.gradientH(cx, cy - 0.5f, half, 1f, 0, grey(1f, 0.85f * on), grey(1f, 0));
        }
    }

    /**
     * The tunnel: rings of eight panels winding away, shaded by which way each panel faces and by
     * the haze, with a pulse of light running down them on each beat, and the light of the way out
     * at the end.
     */
    private void tunnel(Canvas c, float live) {
        float in = smooth((t - BURST + 0.05f) / 0.3f);
        int first = (int) Math.floor(camZ / RING_GAP) + 1, count = 34;
        // The way out, behind everything.
        if (project(pathX(EXIT), pathY(EXIT), EXIT + 6)) {
            float near = Math.clamp(1 - (EXIT - camZ) / 120, 0f, 1f);
            float r = TUNNEL_R * ss;
            c.oval(sx, sy, r * 2.2f, r * 2.2f, 0.9f, grey(1f, (0.15f + 0.5f * near * near) * in));
            c.circle(sx, sy, r * 0.95f, grey(0.85f + 0.15f * near, (0.25f + 0.7f * near) * in));
        }
        float beat = frac((t - BURST) / 0.5f), wave = camZ + 110 * (1 - beat);
        int sides = (int) SIDES;
        float[][] ringX = new float[2][sides], ringY = new float[2][sides];
        float[] ringFog = new float[2], ringLit = new float[2];
        boolean[] ringOk = new boolean[2];
        for (int k = first + count; k >= first; k--) {
            float z = k * RING_GAP;
            int at = k & 1;
            boolean ok = z <= EXIT + 0.01f;
            float cxw = pathX(z), cyw = pathY(z), turn = k * 0.12f;
            for (int j = 0; j < sides && ok; j++) {
                float a = turn + j * TAU / sides;
                if (!project(cxw + (float) Math.cos(a) * TUNNEL_R, cyw + (float) Math.sin(a) * TUNNEL_R, z)) ok = false;
                ringX[at][j] = sx;
                ringY[at][j] = sy;
            }
            ringOk[at] = ok;
            if (!ok) continue;
            float d = z - camZ;
            ringFog[at] = fog(d) * in;
            ringLit[at] = 0.45f * (float) Math.exp(-Math.abs(z - wave) / 7) * (t < GRID ? 1 : 0);
            int other = at ^ 1;
            if (k < first + count && ringOk[other]) {
                for (int j = 0; j < sides; j++) {
                    int j2 = (j + 1) % sides;
                    float a = turn + (j + 0.5f) * TAU / sides;
                    float face = 0.06f + 0.13f * (0.5f + 0.5f * (float) Math.cos(a - 2.2f));
                    int near = grey(face + ringLit[at], ringFog[at]), far = grey(face + ringLit[other], ringFog[other]);
                    c.quad4(ringX[at][j], ringY[at][j], ringX[other][j], ringY[other][j], ringX[other][j2], ringY[other][j2], ringX[at][j2], ringY[at][j2],
                            near, far, far, near);
                }
            }
            float thick = Math.clamp(ss * 0.08f, 0.5f, 2.6f);
            for (int j = 0; j < sides; j++) {
                int j2 = (j + 1) % sides;
                c.line(ringX[at][j], ringY[at][j], ringX[at][j2], ringY[at][j2], thick, grey(1f, ringFog[at] * (0.4f + ringLit[at])));
                if (k < first + count && ringOk[other] && j % 2 == 0)
                    c.line(ringX[at][j], ringY[at][j], ringX[other][j], ringY[other][j], thick * 0.5f, grey(1f, ringFog[at] * 0.18f));
            }
        }
    }

    /** Streaks of dust rushing past in the tunnel and over the floor: what makes the speed. */
    private void dust(Canvas c) {
        float on = smooth((t - BURST) / 0.4f) * (1 - smooth((t - VORTEX + 1.5f) / 1f));
        if (on < 0.01f) return;
        float speed = 14 * (t < GRID ? 0.5f + 0.22f * (t - BURST) : 1.2f), grid = smooth((t - GRID) / 1f);
        for (int i = 0; i < MOTES; i++) {
            float z = camZ + 2 + frac(mz[i] / 74 - camZ / 90) * 90;
            float a = rayAngle[i % RAYS] + i;
            float tunnelX = pathX(z) + (float) Math.cos(a) * (1.2f + 3.4f * fReach[i % FIELD]), tunnelY = pathY(z) + (float) Math.sin(a) * (1.2f + 3.4f * fReach[i % FIELD]);
            float x = tunnelX + (mx[i] * 1.6f - tunnelX) * grid, y = tunnelY + (FLOOR_Y + 1 + (my[i] + 9) * 0.7f - tunnelY) * grid;
            float len = speed * 0.035f;
            seg(c, x, y, z, x, y, z + len, Math.clamp(0.04f * focal / Math.max(1f, z - camZ), 0.4f, 1.4f), grey(1f, 0.45f * on * fog(z - camZ)));
        }
    }

    /** The floor the flight comes out over: a grid to the horizon, a glow along it, monoliths going past on both sides. */
    private void floor(Canvas c, float w, float h, float u) {
        float in = smooth((t - GRID + 0.3f) / 0.6f) * (1 - smooth(u / 0.25f));
        if (in < 0.01f) return;
        // The horizon, from two points very far off, so it leans with the camera.
        project(camX - 4000, FLOOR_Y, camZ + 4000);
        float hx0 = sx, hy0 = sy;
        project(camX + 4000, FLOOR_Y, camZ + 4000);
        float lean = (float) Math.atan2(sy - hy0, sx - hx0), hy = (hy0 + sy) / 2, hx = (hx0 + sx) / 2;
        c.push();
        c.rotate(lean, hx, hy);
        float span = w * 2;
        c.gradientV(hx - span, hy - h * 0.12f, span * 2, h * 0.12f, 0, grey(1f, 0), grey(1f, 0.2f * in));
        c.gradientV(hx - span, hy, span * 2, h * 0.2f, 0, grey(1f, 0.14f * in), grey(1f, 0));
        c.rect(hx - span, hy - 0.5f, span * 2, 1, 0, grey(1f, 0.5f * in));
        c.pop();
        // The orb's light lying along the floor towards the camera.
        if (project(0, FLOOR_Y, ORB_Z)) {
            float r = ORB_R * ss * 1.6f;
            c.gradientV(sx - r / 2, sy, r, h, 0, grey(1f, 0.16f * in), grey(1f, 0));
        }
        float far = 300, step = 5;
        float z0 = (float) Math.ceil((camZ + 1) / step) * step;
        for (float z = z0; z < camZ + far; z += step) {
            float a = in * fog(z - camZ) * 0.55f;
            if (a > 0.01f) seg(c, camX - 90, FLOOR_Y, z, camX + 90, FLOOR_Y, z, Math.clamp(0.06f * focal / (z - camZ), 0.4f, 1.6f), grey(0.9f, a));
        }
        float x0 = (float) Math.floor((camX - 90) / step) * step;
        for (float x = x0; x <= camX + 90; x += step) {
            for (int part = 0; part < 4; part++) {
                float za = camZ + 1 + far * part / 4f, zb = camZ + 1 + far * (part + 1) / 4f;
                seg(c, x, FLOOR_Y, za, x, FLOOR_Y, zb, 0.7f, grey(0.9f, in * 0.35f * fog(za - camZ)));
            }
        }
        // Monoliths, far to near; the side facing the middle is lit a little more than the front.
        int m0 = (int) Math.ceil(Math.max(camZ + 2, EXIT + 10) / 16), m1 = (int) ((camZ + 260) / 16);
        for (int m = m1; m >= m0; m--) {
            for (int side = -1; side <= 1; side += 2) {
                int hash = (m * 7 + (side > 0 ? 3 : 0)) % 5;
                float bx = side * (11 + 1.2f * hash), bz = m * 16f, tall = 6 + 2.2f * ((m * 3 + side + 9) % 5), half = 1.2f;
                float a = in * fog(bz - camZ);
                if (a < 0.02f) continue;
                float inner = bx - side * half;
                if (corners(inner, bz - half, inner, bz + half, tall)) {
                    c.quad4(qx[0], qy[0], qx[1], qy[1], qx[2], qy[2], qx[3], qy[3], grey(0.26f, a), grey(0.26f, a), grey(0.05f, a), grey(0.05f, a));
                    c.line(qx[0], qy[0], qx[3], qy[3], 0.8f, grey(1f, 0.3f * a));
                }
                if (corners(bx - half, bz - half, bx + half, bz - half, tall)) {
                    c.quad4(qx[0], qy[0], qx[1], qy[1], qx[2], qy[2], qx[3], qy[3], grey(0.15f, a), grey(0.15f, a), grey(0.03f, a), grey(0.03f, a));
                    c.line(qx[0], qy[0], qx[1], qy[1], 1f, grey(1f, 0.55f * a));
                    c.line(qx[0], qy[0], qx[3], qy[3], 0.7f, grey(1f, 0.25f * a));
                    c.line(qx[1], qy[1], qx[2], qy[2], 0.7f, grey(1f, 0.25f * a));
                }
            }
        }
    }

    private final float[] qx = new float[4], qy = new float[4];

    /** The four corners of an upright face standing on the floor, top two first; false if any is behind the camera. */
    private boolean corners(float x0, float z0, float x1, float z1, float tall) {
        float[][] p = {{x0, FLOOR_Y + tall, z0}, {x1, FLOOR_Y + tall, z1}, {x1, FLOOR_Y, z1}, {x0, FLOOR_Y, z0}};
        for (int i = 0; i < 4; i++) {
            if (!project(p[i][0], p[i][1], p[i][2])) return false;
            qx[i] = sx;
            qy[i] = sy;
        }
        return true;
    }

    /** A point of the disc round the orb: at an angle round it, a distance out, and a height off its plane. */
    private void disc(float a, float r, float lift) {
        float c = (float) Math.cos(a) * r, s = (float) Math.sin(a) * r;
        project(c * e1x + lift * nx, ORB_Y + c * e1y + lift * ny, ORB_Z + s);
    }

    /** How far the disc has drawn in: with time, and with the pressure put on the orb. */
    private float tighten() {
        return 1 - 0.3f * smooth((t - VORTEX) / (GATE - VORTEX)) - (t >= GATE ? 0.2f * pressure : 0) + 0.05f * jolt;
    }

    private float fall() {
        return broke ? Easing.IN_CUBIC.apply(Math.clamp((t - GATE) / (SNAP - GATE - 0.15f), 0f, 1f)) : 0;
    }

    /** Orbit lines in the disc's plane, with a bead running each; each beat throws a ring out across it. */
    private void orbits(Canvas c, float live) {
        float in = smooth((t - VORTEX + 0.2f) / 0.8f), fall = fall();
        for (int k = 0; k < 5; k++) {
            float r = (ORB_R * 1.9f + 6.5f * k) * tighten() * (1 - fall);
            if (r < 0.5f) continue;
            circle3(c, r, 0.8f, grey(0.85f, 0.22f * in * (1 - fall * 0.5f)));
            float a = (k % 2 == 0 ? 1 : -1) * (t - VORTEX + whirl * 3) * (1.4f - 0.2f * k) + k * 1.3f;
            disc(a, r, 0);
            if (sz > NEAR) {
                c.oval(sx, sy, 5, 5, 0.9f, grey(1f, 0.4f * in));
                c.circle(sx, sy, Math.max(1f, 0.12f * ss), grey(1f, 0.9f * in));
            }
        }
        for (float beat : BEATS) {
            float p = (live - beat) / 0.8f;
            if (p > 0 && p < 1) circle3(c, ORB_R + 60 * Easing.OUT_CUBIC.apply(p), 2.6f * (1 - p) + 0.4f, grey(1f, 0.55f * (1 - p)));
        }
    }

    private void circle3(Canvas c, float r, float thickness, int color) {
        int parts = 56;
        float tilt = r;
        for (int i = 0; i < parts; i++) {
            float a0 = i * TAU / parts, a1 = (i + 1) * TAU / parts;
            float c0 = (float) Math.cos(a0) * tilt, s0 = (float) Math.sin(a0) * tilt, c1 = (float) Math.cos(a1) * tilt, s1 = (float) Math.sin(a1) * tilt;
            seg(c, c0 * e1x, ORB_Y + c0 * e1y, ORB_Z + s0, c1 * e1x, ORB_Y + c1 * e1y, ORB_Z + s1, thickness, color);
        }
    }

    // ---- the orb -------------------------------------------------------------------------------

    /** Where the orb is on screen and how big, this frame. */
    private void orbAt(float live, float held) {
        float beat = 0;
        for (float b : BEATS) beat += decay(live - b, 7);
        float crush = Easing.IN_CUBIC.apply(Math.clamp((t - GATE) / (SNAP - GATE), 0f, 1f)) * (broke ? 1 : 0);
        float r = ORB_R * (1 + 0.06f * beat + (t >= GATE ? 0.18f * pressure + 0.06f * jolt : 0)) * (1 - crush);
        if (!project(0, ORB_Y, ORB_Z)) {
            oz = -1;
            or = 0;
            return;
        }
        ox = sx;
        oy = sy;
        oz = sz;
        or = r * ss;
    }

    /**
     * The black orb: lit from behind so only its rim burns, a gloss of the sky on its upper side,
     * a corona round it, a flare across the lens, and the cracks the player's hits make in it.
     */
    private void drawOrb(Canvas c, float w, float h, float live, float held) {
        float in = smooth((t - GRID + 0.2f) / 0.6f) * (1 - smooth((t - SNAP) / 0.12f));
        if (in < 0.01f || or < 0.4f || oz < NEAR) return;
        float heat = t >= GATE ? pressure : 0, r = or;
        c.oval(ox, oy, r * (3.4f + 1.5f * heat), r * (3.4f + 1.5f * heat), 0.97f, grey(1f, (0.14f + 0.2f * heat) * in));
        c.oval(ox, oy, r * 1.8f, r * 1.8f, 0.7f, grey(1f, (0.3f + 0.3f * heat) * in));
        for (int i = 0; i < CORONA; i++) {
            float a = coronaAngle[i] + live * 0.07f;
            float len = r * (0.2f + 1.1f * coronaLen[i] * (0.65f + 0.35f * (float) Math.sin(live * 1.7f + i * 2.4f)) + 0.8f * heat);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.line(ox + cos * r, oy + sin * r, ox + cos * (r + len), oy + sin * (r + len), Math.max(0.5f, r * 0.03f), grey(1f, 0.26f * in));
        }
        // The body: black, a faint sky on top, darker still away from the light.
        c.gradientV(ox - r, oy - r, r * 2, r * 2, r, grey(0.11f, in), grey(0.0f, in));
        c.oval(ox + r * 0.18f, oy + r * 0.26f, r * 0.85f, r * 0.85f, 0.7f, grey(0f, 0.7f * in));
        // Its rim: a hard line of light and a softer one inside it.
        c.ring(ox, oy, r - r * 0.07f, r * 0.14f, grey(1f, 0.1f * in));
        c.ring(ox, oy, r + 0.4f, Math.max(1.1f, r * 0.035f), grey(1f, 0.95f * in));
        // A gloss, high and to the left.
        c.push();
        c.rotate(-0.6f, ox - r * 0.38f, oy - r * 0.45f);
        c.oval(ox - r * 0.38f, oy - r * 0.45f, r * 0.34f, r * 0.15f, 0.9f, grey(1f, 0.2f * in));
        c.oval(ox - r * 0.38f, oy - r * 0.45f, r * 0.1f, r * 0.04f, 0.8f, grey(1f, 0.45f * in));
        c.pop();
        // The bead of light at the edge.
        float ba = -0.9f + live * 0.25f, bx = ox + (float) Math.cos(ba) * r, by = oy + (float) Math.sin(ba) * r;
        c.oval(bx, by, r * 0.5f + 4, r * 0.5f + 4, 0.9f, grey(1f, 0.6f * in));
        c.circle(bx, by, Math.max(1f, r * 0.06f), grey(1f, in));

        int n = Math.min(strikes, CRACKS);
        for (int i = 0; i < n; i++) {
            float fresh = i == n - 1 ? jolt : 0, x0 = ox, y0 = oy, a = crackAngle[i];
            for (int s = 1; s <= 4; s++) {
                a = crackAngle[i] + crackBend[i * 4 + s - 1] * (s == 4 ? 0.2f : 0.7f);
                float x1 = ox + (float) Math.cos(a) * r * s / 4, y1 = oy + (float) Math.sin(a) * r * s / 4;
                c.line(x0, y0, x1, y1, 0.9f + 1.4f * fresh, grey(1f, (0.75f + 0.25f * fresh) * in));
                x0 = x1;
                y0 = y1;
            }
            float leak = r * (0.4f + 1.7f * pressure) * (0.6f + 0.8f * Math.abs(crackBend[i * 4])) * (1 + 0.5f * fresh);
            c.line(x0, y0, x0 + (float) Math.cos(a) * leak, y0 + (float) Math.sin(a) * leak, 1.3f + fresh, grey(1f, (0.4f + 0.4f * fresh) * in));
        }

        // The flare: a streak through it, and ghosts strung out along the line through the middle of the frame.
        c.gradientH(ox - w * 0.45f, oy - 0.6f, w * 0.45f, 1.2f, 0, grey(1f, 0), grey(1f, 0.3f * in));
        c.gradientH(ox, oy - 0.6f, w * 0.45f, 1.2f, 0, grey(1f, 0.3f * in), grey(1f, 0));
        float dx = scx - ox, dy = scy - oy, off = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / 60);
        if (off > 0.05f) {
            float[] at = {0.6f, 1.15f, 1.5f, 2.1f}, size = {0.5f, 1.2f, 0.35f, 0.8f};
            for (int i = 0; i < at.length; i++) {
                float gx = ox + dx * at[i], gy = oy + dy * at[i], gr = Math.max(4, r * size[i]);
                c.polygon(gx, gy, gr, 6, gr * 0.2f, grey(1f, 0.04f * in * off));
                c.polygonStroke(gx, gy, gr, 6, gr * 0.2f, 0.7f, grey(1f, 0.07f * in * off));
            }
        }
    }

    // ---- the shards ----------------------------------------------------------------------------

    /**
     * Works out where every shard is in the world and sorts them far to near. The small ones pour
     * out of the orb into the disc and later go to the letters; the large ones hang in the tunnel and
     * over the floor, are caught into an outer ring, and are thrown out past the camera by the hit.
     *
     * @return how many are in {@link #order}
     */
    private int place(float live, float unit, float u, float markCx, float markTop, float markSize) {
        int n = 0;
        float v = Math.max(0, t - VORTEX), g = t - (VORTEX - 0.6f), draw = tighten(), fall = fall();
        if (t >= VORTEX - 0.6f && u < 0) {
            for (int i = 0; i < LOGO; i++) {
                float out = Easing.OUT_EXPO.apply(Math.clamp((g - wait[i] * 0.5f) / 1.5f, 0f, 1f));
                if (out <= 0) continue;
                float oa = angle[i] + spin[i] * (0.45f * g + 0.3f * v * v + whirl * 3.75f);
                float r = (ORB_R + DISC * reach[i]) * out * draw * (1 + 0.05f * (float) Math.sin(live * 2.6f + i));
                float lift = (shade[i] - 0.5f) * 2.4f * reach[i] + 0.3f * (float) Math.sin(live * 1.9f + i);
                float c = (float) Math.cos(oa) * r, s = (float) Math.sin(oa) * r;
                wx[i] = c * e1x + lift * nx;
                wy[i] = ORB_Y + c * e1y + lift * ny;
                wz[i] = ORB_Z + s;
                if (!project(wx[i], wy[i], wz[i])) continue;
                // On its way to a letter it is in front of everything.
                depth[i] = broke && t - GATE - wait[i] > 0 ? 0.001f : sz;
                order[n++] = (long) Float.floatToIntBits(depth[i]) << 32 | i;
            }
        }
        if (t >= BURST) {
            float caught = smooth((t - VORTEX + 0.6f) / 1.6f), grid = smooth((t - GRID) / 1f);
            for (int k = 0; k < FIELD; k++) {
                int i = LOGO + k;
                if (u >= 0) {
                    // Thrown out from the orb, most of them towards the camera and past it.
                    float ea = fAngle[k], el = fLift[k] * 0.9f;
                    float dx = (float) (Math.cos(ea) * Math.cos(el)), dy = (float) Math.sin(el), dz = (float) (Math.sin(ea) * Math.cos(el));
                    float towards = 0.4f + 1.2f * fShade[k];
                    dx -= fX * towards;
                    dy -= fY * towards;
                    dz -= fZ * towards;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz), d = 3 + (14 + 40 * fFling[k]) * (1 - (float) Math.exp(-u * 2.2f)) + 4 * u;
                    wx[i] = dx / len * d;
                    wy[i] = ORB_Y + dy / len * d;
                    wz[i] = ORB_Z + dz / len * d;
                } else {
                    float z = camZ + 3 + frac(fDepth[k] - camZ / 110) * 110;
                    if (caught > 0.999f) z = camZ + 60;
                    float ta = fAngle[k] + 0.3f * (t - BURST);
                    float tr = 1.5f + 3f * fReach[k];
                    float flyX = pathX(z) + (float) Math.cos(ta) * tr, flyY = pathY(z) + (float) Math.sin(ta) * tr;
                    flyX += ((fAngle[k] / TAU - 0.5f) * 60 - flyX) * grid;
                    flyY += (FLOOR_Y + 1.5f + 12 * fReach[k] - flyY) * grid;
                    float oa = fAngle[k] + fSpin[k] * (v * 0.9f + 0.35f * v * v + whirl * 4.7f);
                    float r = (DISC + 8 + 16 * fReach[k]) * draw * (1 - fall);
                    float lift = fLift[k] * 5 * (1 - fall);
                    float c = (float) Math.cos(oa) * r, s = (float) Math.sin(oa) * r;
                    wx[i] = flyX + (c * e1x + lift * nx - flyX) * caught;
                    wy[i] = flyY + (ORB_Y + c * e1y + lift * ny - flyY) * caught;
                    wz[i] = z + (ORB_Z + s - z) * caught;
                }
                if (!project(wx[i], wy[i], wz[i])) continue;
                depth[i] = sz;
                order[n++] = (long) Float.floatToIntBits(depth[i]) << 32 | i;
            }
        }
        if (u >= 0) {
            // After the hit the small shards are flat on the screen: the ones still about go in front.
            for (int i = 0; i < LOGO; i++) {
                depth[i] = 0.001f;
                order[n++] = (long) Float.floatToIntBits(0.001f) << 32 | i;
            }
        }
        Arrays.sort(order, 0, n);
        // Far first.
        for (int a = 0, b = n - 1; a < b; a++, b--) {
            long x = order[a];
            order[a] = order[b];
            order[b] = x;
        }
        for (int k = 0; k < n; k++) order[k] &= 0xFFFFFFFFL;
        return n;
    }

    // The light the shards are lit by: from high on the left, a little towards the camera.
    private static final float LX = -0.5f, LY = 0.72f, LZ = -0.48f;

    private final float[] vx = new float[6], vy = new float[6];

    private void drawShard(Canvas c, int i, float live, float u, float markCx, float markTop, float markSize) {
        float em = markSize / SdfAtlas.EM;
        if (i < LOGO) {
            float homeX = markCx + tx[i] * em, homeY = markTop + ty[i] * em, flat = grain[i] * markSize * 0.028f;
            if (u >= 0) {
                float x, y, size, a;
                if (fling[i] <= 0) {
                    a = 1 - u / 0.16f;
                    x = homeX;
                    y = homeY;
                    size = flat;
                } else {
                    float mcx = markCx, mcy = markTop + markSize * 0.5f;
                    float dx = homeX - mcx + (shade[i] - 0.5f) * 30, dy = homeY - mcy + (grain[i] - 1.5f) * 40;
                    float len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
                    float d = fling[i] * (1 - (float) Math.exp(-u * 3f)) / 1.6f;
                    x = homeX + dx / len * d;
                    y = homeY + dy / len * d + 26 * u * u;
                    a = 1 - u / 1.7f;
                    size = grain[i] * (1.2f - 0.4f * Math.min(1f, u));
                }
                if (a > 0.01f && size > 0.3f) {
                    c.push();
                    c.rotate(turn[i] * live * (u < 0.2f ? 0 : 1) + i, x, y);
                    c.polygon(x, y, size, sides[i], size * 0.12f, grey(0.62f + 0.38f * shade[i], Math.min(1f, a)));
                    c.pop();
                }
                return;
            }
            float p = broke ? Math.clamp((t - GATE - wait[i]) / (SNAP - GATE - 0.34f), 0f, 1f) : 0;
            float e = p * p * p * (p * (6 * p - 15) + 10);
            float out = Math.min(1f, Easing.OUT_EXPO.apply(Math.clamp((t - (VORTEX - 0.6f) - wait[i] * 0.5f) / 1.5f, 0f, 1f)) * 3);
            solid(c, wx[i], wy[i], wz[i], grain[i] * 0.28f, sides[i], turn[i] * live + i, turn[i] * 0.6f * live + i * 0.7f,
                    out * (0.7f + 0.3f * shade[i] + 0.3f * e), e, homeX, homeY, flat, i);
            return;
        }
        int k = i - LOGO;
        float a;
        if (u >= 0) {
            a = Math.max(0f, 1 - u / 3.4f);
        } else {
            a = smooth((t - BURST) / 0.4f) * (1 - fall() * 0.6f);
            // Streaks along the line of flight while it is at speed.
            float caught = smooth((t - VORTEX + 0.6f) / 1.6f);
            if (caught < 0.95f && t < VORTEX) {
                float speed = 14 * (t < GRID ? 0.5f + 0.22f * (t - BURST) : 1.2f);
                seg(c, wx[i], wy[i], wz[i], wx[i], wy[i], wz[i] + speed * 0.06f * (1 - caught), Math.clamp(fSize[k] * 0.15f * focal / Math.max(1f, depth[i]), 0.4f, 2f),
                        grey(0.9f, 0.35f * a * fog(depth[i])));
            }
        }
        solid(c, wx[i], wy[i], wz[i], fSize[k], fSides[k], fTurn[k] * live + k, fTurn[k] * 0.7f * live + k * 1.3f, a, 0, 0, 0, 0, i);
    }

    /**
     * One shard as a flat, lit polygon turned in space: its corners are projected one by one, so it
     * foreshortens as it tumbles. Small ones are drawn as plain polygons. Part way to the letters
     * ({@code e} above 0) its corners move on to a flat polygon at its place in the wordmark.
     */
    private void solid(Canvas c, float x, float y, float z, float size, int count, float a1, float a2, float alpha,
            float e, float homeX, float homeY, float homeSize, int seed) {
        if (alpha < 0.01f) return;
        float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
        // Two edges of its plane, and the way it faces.
        float ux = c1, uy = s1 * c2, uz = s1 * s2, wx = -s1, wy = c1 * c2, wz = c1 * s2;
        float nX = uy * wz - uz * wy, nY = uz * wx - ux * wz, nZ = ux * wy - uy * wx;
        float lambert = Math.abs(nX * LX + nY * LY + nZ * LZ);
        // A glint when it turns its face between the light and the camera.
        float hx = LX - fX, hy = LY - fY, hz = LZ - fZ, hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        float glint = (float) Math.pow(Math.abs(nX * hx + nY * hy + nZ * hz) / hl, 40);
        if (!project(x, y, z) && e <= 0) return;
        float dist = sz, haze = fog(dist);
        // Very near the camera it is out of focus: fainter.
        float focus = smooth((dist - 2.5f) / 6f);
        float bright = (0.12f + 0.72f * (float) Math.pow(lambert, 1.4f)) * (0.55f + 0.45f * haze) + 0.9f * glint;
        float a = alpha * Math.max(haze, e) * Math.max(focus, e);
        if (a < 0.01f) return;
        float pixels = size * ss;
        if (pixels < 2.4f && e < 0.5f) {
            float cx = sx + (homeX - sx) * e, cy = sy + (homeY - sy) * e;
            float r = Math.max(0.45f, pixels * (0.55f + 0.45f * (float) Math.sqrt(Math.abs(nX * fX + nY * fY + nZ * fZ)))) * (1 - e) + homeSize * e;
            c.push();
            c.rotate(a1, cx, cy);
            c.polygon(cx, cy, r, count, r * 0.1f, grey(Math.min(1f, bright + 0.15f), a));
            c.pop();
            return;
        }
        for (int k = 0; k < count; k++) {
            float ang = k * TAU / count - TAU / 4;
            float cs = (float) Math.cos(ang) * size, sn = (float) Math.sin(ang) * size;
            project(x + ux * cs + wx * sn, y + uy * cs + wy * sn, z + uz * cs + wz * sn);
            float flatX = homeX + (float) Math.cos(ang) * homeSize, flatY = homeY + (float) Math.sin(ang) * homeSize;
            vx[k] = sx + (flatX - sx) * e;
            vy[k] = sy + (flatY - sy) * e;
        }
        bright = bright * (1 - e) + (0.62f + 0.38f * shade[seed % LOGO]) * e;
        int lit = grey(Math.min(1f, bright * 1.12f), a), dim = grey(bright * 0.82f, a);
        if (count == 3) c.quad4(vx[0], vy[0], vx[1], vy[1], vx[2], vy[2], vx[2], vy[2], lit, dim, dim, dim);
        else if (count == 4) c.quad4(vx[0], vy[0], vx[1], vy[1], vx[2], vy[2], vx[3], vy[3], lit, dim, dim, lit);
        else {
            c.quad4(vx[0], vy[0], vx[1], vy[1], vx[2], vy[2], vx[3], vy[3], lit, lit, dim, dim);
            c.quad4(vx[3], vy[3], vx[4], vy[4], vx[5], vy[5], vx[0], vy[0], dim, dim, lit, lit);
        }
        // Its edges, catching the light: also what smooths them.
        int edge = grey(Math.min(1f, bright * 1.35f + 0.1f), a * 0.9f);
        float thick = Math.clamp(pixels * 0.06f, 0.5f, 1.2f);
        for (int k = 0; k < count; k++) {
            int k2 = (k + 1) % count;
            c.line(vx[k], vy[k], vx[k2], vy[k2], thick, edge);
        }
    }

    // ---- the gate and the hit ------------------------------------------------------------------

    /** What to do at the gate, and how far along it is. */
    private void prompt(Canvas c, float cx, float y, float unit, float live) {
        float in = smooth((gateT - 0.3f) / 0.4f) * (broke ? 1 - smooth((t - GATE) / 0.25f) : 1);
        if (in < 0.01f) return;
        float nag = strikes == 0 ? smooth((gateT - 4f) / 2f) : 0;
        float beat = 0.5f + 0.5f * (float) Math.sin(live * (3f + 3f * nag));
        float size = Math.max(8f, unit * 0.034f) * (1 + 0.08f * jolt + 0.1f * nag * beat);
        spaced(c, Fonts.BOLD, "MASH ANY KEY", cx, y, size, size * 0.4f, grey(1f, in * (0.7f + 0.3f * beat)));
        float bw = Math.max(90, unit * 0.34f), by = y + size * 2.1f, bh = 3 + 2 * jolt;
        c.rect(cx - bw / 2, by - bh / 2, bw, bh, bh / 2, grey(1f, 0.16f * in));
        if (pressure > 0.004f) {
            c.shadow(cx - bw / 2, by - bh / 2, bw * pressure, bh, bh / 2, 6 + 6 * jolt, grey(1f, 0.5f * in));
            c.rect(cx - bw / 2, by - bh / 2, bw * pressure, bh, bh / 2, grey(1f, in));
        }
        c.textCentered(Fonts.REGULAR, strikes == 0 ? "Any key, or click. Fast." : pressure > 0.6f ? "Nearly through" : "Faster: it heals",
                cx, by + 7, Math.max(6.5f, unit * 0.02f), grey(0.85f, 0.75f * in));
        if (jolt > 0.02f) c.ring(ox, oy, or + unit * 0.4f * (1 - jolt), 3f * jolt, grey(1f, 0.5f * jolt));
    }

    /** Lines drawn in from the edges: faintly while the orb is under pressure, hard as everything falls to the centre. */
    private void rays(Canvas c, float far) {
        float p = broke ? Math.clamp((t - GATE) / (SNAP - GATE), 0f, 1f) : 0;
        float on = Math.max(0.7f * pressure * smooth(gateT / 0.3f), smooth(p * 3)) * (1 - smooth((t - SNAP) / 0.1f));
        if (on < 0.01f) return;
        for (int i = 0; i < RAYS; i++) {
            float q = frac(whirl * 0.9f + p * 2.2f + rayLag[i]);
            float head = far * 0.8f * (1 - Easing.IN_CUBIC.apply(q)) + 18, tail = head + far * (0.05f + 0.16f * q);
            float a = on * (float) Math.sin(q * Math.PI) * 0.5f;
            if (a < 0.01f) continue;
            float cos = (float) Math.cos(rayAngle[i]), sin = (float) Math.sin(rayAngle[i]);
            c.line(ox + cos * head, oy + sin * head, ox + cos * tail, oy + sin * tail, 0.8f, grey(0.9f, a));
        }
    }

    /** The moment the letters lock: lightning out from the word, rings thrown outwards and a streak of light through it. */
    private void hit(Canvas c, float cx, float cy, float w, float h, float u) {
        float far = (float) Math.sqrt(w * w + h * h) * 0.62f;
        float strike = decay(u, 6) * Math.min(1f, u / 0.05f);
        if (strike > 0.02f) {
            for (int i = 0; i < BOLTS; i++) {
                float cos = (float) Math.cos(boltAngle[i]), sin = (float) Math.sin(boltAngle[i]);
                float len = far * (0.5f + 0.5f * Math.abs(boltBend[i * BOLT_PARTS])) * Math.min(1f, u / 0.07f);
                float a = strike * (Math.sin(u * 70 + i * 2.1f) > -0.3f ? 1 : 0.25f);
                float x0 = cx, y0 = cy;
                for (int s = 1; s <= BOLT_PARTS; s++) {
                    float along = len * s / BOLT_PARTS, off = boltBend[i * BOLT_PARTS + s - 1] * len * 0.05f;
                    float x1 = cx + cos * along - sin * off, y1 = cy + sin * along + cos * off;
                    float thick = 2.2f * (1 - (s - 1f) / BOLT_PARTS) + 0.5f;
                    c.line(x0, y0, x1, y1, thick * 3, grey(1f, a * 0.15f));
                    c.line(x0, y0, x1, y1, thick, grey(1f, a));
                    x0 = x1;
                    y0 = y1;
                }
            }
        }
        for (int k = 0; k < 5; k++) {
            float p = (u - k * 0.08f) / (0.85f + 0.2f * k);
            if (p <= 0 || p >= 1) continue;
            float e = Easing.OUT_CUBIC.apply(p);
            c.ring(cx, cy, 12 + far * e, (k == 0 ? 16 : 6) * (1 - e) + 0.6f, grey(1f, (k == 0 ? 0.75f : 0.4f) * (1 - p) * (1 - p)));
        }
        float glow = decay(u, 2.2f);
        c.oval(cx, cy, w * 0.42f, h * 0.2f, 0.95f, grey(1f, 0.2f * glow));
        float streak = Easing.OUT_EXPO.apply(Math.clamp(u / 0.5f, 0f, 1f)), fade = decay(u, 2.6f);
        float half = w * 0.6f * streak;
        c.gradientH(cx - half, cy - 0.6f, half, 1.2f, 0, grey(1f, 0), grey(1f, 0.9f * fade));
        c.gradientH(cx, cy - 0.6f, half, 1.2f, 0, grey(1f, 0.9f * fade), grey(1f, 0));
    }

    /** Shafts of light turning slowly behind the wordmark while the shot is held. */
    private void beams(Canvas c, float cx, float cy, float unit, float far, float u, float keep) {
        float on = smooth(u / 0.4f) * (1 - smooth((u - (SETTLE - SNAP) + 0.8f) / 0.8f)) * keep;
        if (on < 0.01f) return;
        for (int i = 0; i < 16; i++) {
            float wide = unit * (0.02f + 0.06f * rayLag[i]);
            float a = on * (0.035f + 0.07f * (0.5f + 0.5f * (float) Math.sin(u * 1.3f + i * 1.9f))) + 0.15f * decay(u, 3);
            c.push();
            c.rotate(i * TAU / 16 + u * 0.1f + rayLag[i + 16] * 0.3f, cx, cy);
            c.gradientH(cx + unit * 0.06f, cy - wide / 2, far, wide, 0, grey(1f, a), grey(1f, 0));
            c.pop();
        }
    }

    /** Specks rising through the held shot, nearer ones moving more with the pointer. */
    private void embers(Canvas c, float w, float h, float u, float live, float keep) {
        float on = smooth((u - 0.3f) / 1.2f) * keep;
        if (on < 0.01f) return;
        for (int i = 0; i < 80; i++) {
            float up = frac(fDepth[i] + u * (0.03f + 0.06f * fShade[i]));
            float x = w * frac(fAngle[i] / TAU + 0.03f * (float) Math.sin(live * 0.6f + i)) - px * 22 * fShade[i];
            float y = h * (1.05f - 1.1f * up) - py * 14 * fShade[i];
            float a = on * 0.5f * (float) Math.sin(up * Math.PI) * (0.4f + 0.6f * (float) Math.sin(live * 2.2f + i * 1.3f));
            if (a > 0.01f) c.circle(x, y, 0.5f + 1.1f * fShade[i], grey(1f, a));
        }
    }

    private static void tagline(Canvas c, float cx, float y, float markSize, float u) {
        float in = smooth((u - 0.75f) / 0.6f) * (1 - smooth((u - (SETTLE - SNAP) + 0.15f) / 0.3f));
        if (in < 0.01f) return;
        float size = Math.max(6.5f, markSize * 0.17f);
        float gap = size * (0.34f + 0.5f * (1 - Easing.OUT_CUBIC.apply(Math.clamp((u - 0.75f) / 1.2f, 0f, 1f))));
        spaced(c, Fonts.MEDIUM, "YOUR GAME, YOUR WAY", cx, y, size, gap, grey(0.9f, in));
    }

    /** A line set with a gap between its letters, centred. */
    private static void spaced(Canvas c, Fonts font, String line, float cx, float y, float size, float gap, int color) {
        float total = -gap;
        for (int i = 0; i < line.length(); i++) total += font.width(line.substring(i, i + 1), size) + gap;
        float pen = cx - total / 2;
        for (int i = 0; i < line.length(); i++) {
            String ch = line.substring(i, i + 1);
            c.text(font, ch, pen, y, size, color);
            pen += font.width(ch, size) + gap;
        }
    }

    // ---- the wordmark --------------------------------------------------------------------------

    /** How much each letter is drawn in over the last, as a share of the text size. */
    private static final float TRACK = 0.016f;

    public static float markWidth(float size) {
        String word = AllerClient.NAME;
        float total = size * 0.3f;
        for (int i = 0; i < word.length(); i++) total += Fonts.BOLD.width(word.substring(i, i + 1), size) - size * TRACK;
        return total;
    }

    /**
     * The wordmark, centred on {@code cx}.
     *
     * @param dot      how far the full stop has grown, 0 to 1 and a little over
     * @param dotColor its colour: the one thing here that may not be grey
     * @param sweep    a ripple running along the letters, by letter index; out of range for none
     */
    public static void mark(Canvas c, float cx, float top, float size, float alpha, float dot, int dotColor, float sweep) {
        String word = AllerClient.NAME;
        float pen = cx - markWidth(size) / 2;
        c.pushAlpha(alpha);
        for (int i = 0; i < word.length(); i++) {
            String ch = word.substring(i, i + 1);
            float d = sweep - i, lift = d > -1.5f && d < 1.5f ? (float) Math.cos(d / 1.5f * Math.PI / 2) : 0;
            c.text(Fonts.BOLD, ch, pen, top - lift * size * 0.07f, size, Theme.TEXT);
            pen += Fonts.BOLD.width(ch, size) - size * TRACK;
        }
        float r = size * 0.095f * dot, dx = pen + size * 0.19f, dy = top + size * 0.86f;
        if (r > 0.05f) {
            c.shadow(dx - r, dy - r, r * 2, r * 2, r, size * 0.4f, Colors.withAlpha(dotColor, 0.75f * Math.min(1f, dot)));
            c.circle(dx, dy, r, dotColor);
        }
        c.popAlpha();
    }

    /** Where the full stop of a wordmark drawn by {@link #mark} is. */
    public static float dotX(float cx, float size) {
        return cx + markWidth(size) / 2 - size * 0.11f;
    }

    public static float dotY(float top, float size) {
        return top + size * 0.86f;
    }

    // ---- small maths ---------------------------------------------------------------------------

    private static int grey(float value, float alpha) {
        int v = Math.clamp(Math.round(value * 245), 0, 255);
        return Colors.argb(Math.clamp(Math.round(alpha * 255), 0, 255), v, v, Math.min(255, v + 6));
    }

    private static float smooth(float x) {
        x = Math.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    /** 1 at the moment something happens, dying away after it; 0 before. */
    private static float decay(float since, float rate) {
        return since < 0 ? 0 : (float) Math.exp(-since * rate);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }
}
