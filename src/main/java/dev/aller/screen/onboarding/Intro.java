package dev.aller.screen.onboarding;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.font.SdfAtlas;

import java.util.Random;

/**
 * The first thing a new player sees: ten seconds in which the wordmark is put together out of
 * shards. A point of light, a flight down a tunnel of them, a spinning disc that tightens, the
 * collapse into the letters, the hit when they lock, and the settle into the header of the steps
 * that follow. Everything is a function of one clock, so any moment can be drawn on its own.
 *
 * <p>It is drawn while the canvas is drained of colour: every colour here is a grey on purpose.
 */
public final class Intro {
    /** The moments the piece turns on, in seconds. */
    public static final float BURST = 1.6f, VORTEX = 4.2f, IMPLODE = 6.6f, SNAP = 7.7f, SETTLE = 9.5f, LENGTH = 10.4f;
    private static final int LOGO = 680, FIELD = 150, RINGS = 11, RAYS = 44;
    private static final float TAU = 6.2831855f;
    /** The beats of the build-up, each a pulse from the centre. */
    private static final float[] BEATS = {VORTEX, VORTEX + 0.75f, VORTEX + 1.35f, VORTEX + 1.8f, VORTEX + 2.12f, VORTEX + 2.34f};

    private record Cue(float at, String sound, float pitch, float volume) {}

    private static final Cue[] CUES = {
            new Cue(0.05f, "block.beacon.ambient", 0.5f, 0.7f),
            new Cue(0.45f, "block.note_block.basedrum", 0.55f, 0.8f),
            new Cue(0.95f, "block.note_block.basedrum", 0.6f, 0.9f),
            new Cue(1.32f, "block.note_block.basedrum", 0.7f, 1f),
            new Cue(BURST - 0.05f, "block.beacon.activate", 0.7f, 0.8f),
            new Cue(BURST, "item.trident.riptide_3", 0.6f, 0.5f),
            new Cue(2.1f, "block.amethyst_block.chime", 0.7f, 0.7f),
            new Cue(2.5f, "block.amethyst_block.chime", 0.85f, 0.7f),
            new Cue(2.9f, "block.amethyst_block.chime", 1.0f, 0.7f),
            new Cue(3.25f, "block.amethyst_block.chime", 1.2f, 0.7f),
            new Cue(3.55f, "block.amethyst_block.chime", 1.4f, 0.7f),
            new Cue(3.8f, "block.amethyst_block.chime", 1.6f, 0.7f),
            new Cue(4.0f, "block.amethyst_block.chime", 1.85f, 0.7f),
            new Cue(BEATS[0], "block.respawn_anchor.charge", 0.6f, 0.7f),
            new Cue(BEATS[1], "block.respawn_anchor.charge", 0.8f, 0.7f),
            new Cue(BEATS[2], "block.respawn_anchor.charge", 1.0f, 0.75f),
            new Cue(BEATS[3], "block.respawn_anchor.charge", 1.25f, 0.8f),
            new Cue(BEATS[4], "block.respawn_anchor.charge", 1.5f, 0.85f),
            new Cue(BEATS[5], "block.respawn_anchor.charge", 1.8f, 0.9f),
            new Cue(IMPLODE, "item.trident.riptide_1", 0.5f, 0.7f),
            new Cue(IMPLODE + 0.45f, "block.beacon.deactivate", 1.5f, 0.6f),
            new Cue(SNAP, "entity.generic.explode", 0.55f, 0.55f),
            new Cue(SNAP, "block.beacon.power_select", 0.8f, 0.9f),
            new Cue(SNAP, "block.amethyst_cluster.break", 0.6f, 0.9f),
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
    // Larger shards that never join the letters: the tunnel, the outer ring, the debris.
    private final float[] fAngle = new float[FIELD], fReach = new float[FIELD], fDepth = new float[FIELD], fSize = new float[FIELD];
    private final float[] fTurn = new float[FIELD], fSpin = new float[FIELD], fShade = new float[FIELD], fFling = new float[FIELD];
    private final byte[] fSides = new byte[FIELD];
    private final float[] rayAngle = new float[RAYS], rayLag = new float[RAYS];

    private float t;
    private int cue;

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
                float px = g.w() / 2, py = g.h() / 2;
                for (int tries = 0; tries < 60; tries++) {
                    float ax = r.nextFloat() * g.w(), ay = r.nextFloat() * g.h();
                    int texel = atlas.pixels[Math.min(SdfAtlas.SIZE - 1, v + (int) ay) * SdfAtlas.SIZE + Math.min(SdfAtlas.SIZE - 1, u + (int) ax)];
                    if (texel >>> 24 > 150) {
                        px = ax;
                        py = ay;
                        break;
                    }
                }
                tx[k] = pen[ch] + g.xOff() + px - total / 2;
                ty[k] = atlas.ascent + g.yOff() + py;
            }
            angle[k] = r.nextFloat() * TAU;
            reach[k] = 0.16f + 0.84f * (float) Math.sqrt(r.nextFloat());
            spin[k] = (0.75f + 0.5f * r.nextFloat()) * (1.7f - reach[k]);
            wait[k] = r.nextFloat() * 0.32f;
            grain[k] = 0.8f + 1.5f * r.nextFloat() * r.nextFloat();
            turn[k] = (r.nextFloat() - 0.5f) * 9f;
            shade[k] = r.nextFloat();
            fling[k] = r.nextFloat() < 0.3f ? 70 + 330 * r.nextFloat() : 0;
            sides[k] = (byte) (r.nextFloat() < 0.5f ? 4 : r.nextFloat() < 0.6f ? 3 : 6);
        }
        for (int i = 0; i < FIELD; i++) {
            fAngle[i] = r.nextFloat() * TAU;
            fReach[i] = 0.3f + 0.9f * r.nextFloat();
            fDepth[i] = r.nextFloat();
            fSize[i] = 2.2f + 6f * r.nextFloat() * r.nextFloat();
            fTurn[i] = (r.nextFloat() - 0.5f) * 5f;
            fSpin[i] = -(0.35f + 0.5f * r.nextFloat());
            fShade[i] = r.nextFloat();
            fFling[i] = 180 + 520 * r.nextFloat();
            float kind = r.nextFloat();
            fSides[i] = (byte) (kind < 0.12f ? 1 : kind < 0.42f ? 3 : kind < 0.66f ? 4 : kind < 0.82f ? 5 : 6);
        }
        for (int i = 0; i < RAYS; i++) {
            rayAngle[i] = r.nextFloat() * TAU;
            rayLag[i] = r.nextFloat();
        }
    }

    public float time() {
        return t;
    }

    public boolean done() {
        return t >= LENGTH;
    }

    /** Jumps to a moment without playing the sounds on the way there. */
    public void seek(float seconds) {
        t = seconds;
        while (cue < CUES.length && CUES[cue].at <= t) cue++;
    }

    /** Moves the clock on by a frame and plays whatever falls due. */
    public void advance(float dt) {
        t += dt;
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

        // Letterbox bars for the build-up, drawn back after the hit.
        float bars = h * 0.115f * (1 - smooth((t - SNAP - 0.2f) / 0.9f)) * smooth(t / 0.6f);

        c.push();
        float shake = 2.6f * decay(t - BURST, 5) + 7.5f * decay(t - SNAP, 5.5f) + 1.6f * smooth((t - IMPLODE) / (SNAP - IMPLODE)) * (t < SNAP ? 1 : 0);
        for (float beat : BEATS) shake += 1.3f * decay(t - beat, 9);
        if (shake > 0.02f) c.translate((float) Math.sin(t * 97) * shake, (float) Math.cos(t * 83) * shake);

        if (t < BURST + 0.25f) spark(c, cx, cy, w);
        if (t >= BURST && t < VORTEX + 1.1f) tunnel(c, cx, cy, unit);
        if (t >= VORTEX - 0.1f && t < SNAP) orbits(c, cx, cy, unit);
        if (t >= BURST) field(c, cx, cy, w, h, unit);
        if (t >= IMPLODE - 0.1f && t < SNAP + 0.1f) rays(c, cx, cy, Math.max(w, h));
        if (t >= VORTEX - 0.3f && t < SNAP + 0.2f) core(c, cx, cy, unit);

        // The wordmark: shards up to the hit, type from it, then the glide up to the header.
        float rise = Easing.IN_OUT_CUBIC.apply(Math.clamp((t - SETTLE) / (LENGTH - SETTLE), 0f, 1f));
        float size = markSize + (headSize - markSize) * rise;
        float mx = cx + (headCx - cx) * rise, top = markTop + (headTop - markTop) * rise;
        if (t >= BURST) shards(c, cx, cy, unit, mx, top, size);
        float u = t - SNAP;
        if (u >= 0) {
            hit(c, cx, markTop + markSize * 0.5f, w, h, u);
            float punch = 1 + 0.17f * (float) (Math.exp(-u * 6) * Math.sin(u * 20));
            c.push();
            c.scale(punch, mx, top + size * 0.5f);
            mark(c, mx, top, size, Math.min(1f, u / 0.07f), Easing.OUT_BACK.apply(Math.clamp((u - 0.42f) / 0.35f, 0f, 1f)),
                    Colors.WHITE, (u - 0.55f) * 16f);
            c.pop();
            tagline(c, cx, markTop + markSize * 1.42f, markSize, u);
        }
        c.pop();

        // The two whiteouts.
        float flash = 0.55f * decay(t - BURST, 6) + 0.9f * decay(t - SNAP, 6.5f);
        if (flash > 0.01f) c.rect(0, 0, w, h, 0, Colors.withAlpha(Colors.WHITE, flash));
        if (bars > 0.3f) {
            c.rect(0, 0, w, bars, 0, Colors.BLACK);
            c.rect(0, h - bars, w, bars, 0, Colors.BLACK);
        }
        // And out of black at the very start.
        if (t < 0.5f) c.rect(0, 0, w, h, 0, Colors.withAlpha(Colors.BLACK, 1 - t / 0.5f));
    }

    // ---- the pieces ----------------------------------------------------------------------------

    /** The opening: one point of light with a heartbeat, and the streak it throws just before it bursts. */
    private void spark(Canvas c, float cx, float cy, float w) {
        float on = smooth(t / 0.5f) * (1 - smooth((t - BURST) / 0.2f));
        float pulse = 0.5f * decay(t - 0.45f, 5) + 0.7f * decay(t - 0.95f, 5) + 1f * decay(t - 1.32f, 5);
        float r = 1.1f + 2.2f * pulse + 5 * smooth((t - 1.3f) / 0.3f);
        c.oval(cx, cy, 26 + 60 * pulse, 26 + 60 * pulse, 0.95f, grey(1f, 0.16f * on + 0.22f * pulse * on));
        c.circle(cx, cy, r, grey(1f, on));
        for (float beat : new float[] {0.45f, 0.95f, 1.32f}) {
            float p = (t - beat) / 0.9f;
            if (p > 0 && p < 1) c.ring(cx, cy, 4 + 90 * Easing.OUT_CUBIC.apply(p), 1.4f * (1 - p), grey(0.9f, 0.5f * (1 - p)));
        }
        // An anamorphic streak that widens and snaps shut.
        float s = smooth((t - 0.9f) / 0.55f) * (1 - smooth((t - 1.48f) / 0.12f));
        if (s > 0.01f) {
            float half = w * 0.42f * s;
            c.gradientH(cx - half, cy - 0.5f, half, 1f, 0, grey(1f, 0), grey(1f, 0.85f * on));
            c.gradientH(cx, cy - 0.5f, half, 1f, 0, grey(1f, 0.85f * on), grey(1f, 0));
        }
    }

    /** How far the flight down the tunnel has gone, in tunnel lengths. */
    private float travel() {
        float g = Math.min(t, VORTEX + 1.1f) - BURST;
        return 0.5f * g + 0.13f * g * g;
    }

    /** The walls of the tunnel: polygon outlines coming out of the distance, turning as they pass. */
    private void tunnel(Canvas c, float cx, float cy, float unit) {
        float in = smooth((t - BURST) / 0.5f), out = 1 - smooth((t - VORTEX) / 1.0f);
        float roll = 0.45f * (t - BURST);
        for (int k = 0; k < RINGS; k++) {
            float z = 1 - frac(k / (float) RINGS + travel());
            float near = 1 / (z * 5f + 0.12f);
            float r = unit * 0.2f * near;
            if (r > unit * 1.6f) continue;
            float a = in * out * smooth(z * 4) * smooth((1 - z) * 2.5f) * 0.55f;
            if (a < 0.01f) continue;
            c.push();
            c.rotate(roll + k * 0.35f + z * 1.4f, cx, cy);
            c.polygonStroke(cx, cy, r, k % 3 == 0 ? 8 : 6, r * 0.06f, Math.clamp(0.5f + 0.5f * near, 0.6f, 3.2f), grey(0.75f + 0.25f * (1 - z), a));
            c.pop();
        }
    }

    /** The disc's tilt: 1 seen flat on, less as it leans back. */
    private float tilt() {
        return 1 - 0.6f * smooth((t - VORTEX + 0.4f) / 1.6f);
    }

    /** The slow swing of the whole disc. */
    private float lean() {
        return 0.3f * (float) Math.sin((t - BURST) * 0.55f) - 0.2f * smooth((t - VORTEX) / 2f);
    }

    /** Orbit lines the disc turns on; each beat sends another outwards, and the collapse takes them all. */
    private void orbits(Canvas c, float cx, float cy, float unit) {
        float in = smooth((t - VORTEX + 0.1f) / 0.8f);
        float fall = Easing.IN_CUBIC.apply(Math.clamp((t - IMPLODE) / (SNAP - IMPLODE - 0.15f), 0f, 1f));
        c.push();
        c.rotate(lean(), cx, cy);
        c.stretch(1f, tilt(), cx, cy);
        for (int k = 0; k < 5; k++) {
            float r = unit * (0.13f + 0.105f * k) * (1 - 0.3f * smooth((t - VORTEX) / (IMPLODE - VORTEX))) * (1 - fall);
            if (r < 1) continue;
            c.ring(cx, cy, r, 0.7f, grey(0.8f, 0.2f * in * (1 - fall * 0.5f)));
            // A bead running each line, alternate ways round.
            float a = (k % 2 == 0 ? 1 : -1) * (t - VORTEX) * (2.2f - 0.3f * k) + k * 1.3f;
            c.circle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 1.6f, grey(1f, 0.8f * in));
        }
        for (float beat : BEATS) {
            float p = (t - beat) / 0.7f;
            if (p > 0 && p < 1) c.ring(cx, cy, unit * 0.75f * Easing.OUT_CUBIC.apply(p), 2.4f * (1 - p), grey(1f, 0.55f * (1 - p)));
        }
        c.pop();
    }

    /** The light at the middle of the disc, growing with each beat and crushed to a point before the hit. */
    private void core(Canvas c, float cx, float cy, float unit) {
        float grow = smooth((t - VORTEX + 0.3f) / 2.2f);
        float crush = Easing.IN_CUBIC.apply(Math.clamp((t - IMPLODE) / (SNAP - IMPLODE), 0f, 1f));
        float beat = 0;
        for (float b : BEATS) beat += decay(t - b, 7);
        float gone = 1 - smooth((t - SNAP) / 0.15f);
        float glow = unit * (0.05f + 0.13f * grow + 0.05f * beat) * (1 - 0.7f * crush);
        c.oval(cx, cy, glow * 2.2f, glow * 2.2f, 0.96f, grey(1f, (0.14f + 0.12f * beat + 0.3f * crush) * grow * gone));
        c.oval(cx, cy, glow, glow, 0.8f, grey(1f, (0.3f + 0.25f * beat + 0.4f * crush) * grow * gone));
        c.circle(cx, cy, Math.max(1.2f, glow * 0.11f * (1 - crush)), grey(1f, grow * gone));
    }

    /** Lines drawn in from the edges as everything falls to the centre. */
    private void rays(Canvas c, float cx, float cy, float far) {
        float p = Math.clamp((t - IMPLODE) / (SNAP - IMPLODE), 0f, 1f);
        for (int i = 0; i < RAYS; i++) {
            float q = frac(p * 2.2f + rayLag[i]);
            float head = far * 0.8f * (1 - Easing.IN_CUBIC.apply(q)) + 18, tail = head + far * (0.05f + 0.16f * q);
            float a = smooth(p * 3) * (1 - smooth((t - SNAP) / 0.1f)) * (float) Math.sin(q * Math.PI) * 0.5f;
            if (a < 0.01f) continue;
            float cos = (float) Math.cos(rayAngle[i]), sin = (float) Math.sin(rayAngle[i]);
            c.line(cx + cos * head, cy + sin * head, cx + cos * tail, cy + sin * tail, 0.8f, grey(0.9f, a));
        }
    }

    /**
     * The large shards. They come down the tunnel, are caught into a ring turning the other way
     * round the disc, fall in with it, and are thrown back out by the hit.
     */
    private void field(Canvas c, float cx, float cy, float w, float h, float unit) {
        float caught = smooth((t - VORTEX) / 1.0f);
        float fall = Easing.IN_CUBIC.apply(Math.clamp((t - IMPLODE) / (SNAP - IMPLODE - 0.1f), 0f, 1f));
        float in = smooth((t - BURST) / 0.4f);
        float u = t - SNAP;
        float roll = 0.45f * (t - BURST), squash = tilt(), swing = lean();
        float cs = (float) Math.cos(swing), sn = (float) Math.sin(swing);
        for (int i = 0; i < FIELD; i++) {
            float x, y, size, a, rot = fTurn[i] * t + i;
            if (u >= 0) {
                // Thrown out: fast at first, then drifting.
                float d = 24 + fFling[i] * (1 - (float) Math.exp(-u * 2.6f)) + 14 * u;
                x = cx + (float) Math.cos(fAngle[i]) * d;
                y = cy + (float) Math.sin(fAngle[i]) * d;
                size = fSize[i] * (1.5f - 0.5f * Math.min(1f, u));
                a = (0.9f - 0.5f * fShade[i]) * Math.max(0f, 1 - u / 2.3f);
            } else {
                float z = 1 - frac(fDepth[i] + travel() * 1.4f);
                float near = 1 / (z * 5f + 0.12f);
                float ta = fAngle[i] + roll;
                float tr = unit * 0.2f * fReach[i] * near;
                float tunnelX = cx + (float) Math.cos(ta) * tr, tunnelY = cy + (float) Math.sin(ta) * tr;
                float tunnelA = smooth(z * 5) * smooth((1 - z) * 3);

                float oa = fAngle[i] + fSpin[i] * ((t - VORTEX) * 0.9f + 0.35f * sq(Math.max(0, t - VORTEX)));
                float or = unit * (0.5f + 0.34f * fReach[i]) * (1 - 0.25f * smooth((t - VORTEX) / 2.4f)) * (1 - fall);
                float ox = (float) Math.cos(oa) * or, oy = (float) Math.sin(oa) * or * squash;
                float depth = 1 + 0.3f * (float) Math.sin(oa);
                float orbitX = cx + ox * cs - oy * sn, orbitY = cy + ox * sn + oy * cs;

                x = tunnelX + (orbitX - tunnelX) * caught;
                y = tunnelY + (orbitY - tunnelY) * caught;
                size = fSize[i] * (Math.min(near, 4f) * 0.55f * (1 - caught) + depth * caught) * (1 - 0.8f * fall);
                a = in * (0.35f + 0.5f * fShade[i]) * (tunnelA * (1 - caught) + caught) * (1 - fall * 0.6f);
                // Streaks along the line of flight while the tunnel is at speed.
                float speed = (1 - caught) * Math.min(near, 5f) * (0.5f + 0.26f * (t - BURST));
                if (speed > 1.2f && tunnelA > 0.05f) {
                    float len = speed * 3.2f;
                    c.line(x, y, x - (float) Math.cos(ta) * len, y - (float) Math.sin(ta) * len, Math.min(1.4f, size * 0.25f), grey(0.85f, a * 0.5f));
                }
            }
            if (a < 0.01f || x < -40 || y < -40 || x > w + 40 || y > h + 40) continue;
            shard(c, x, y, size, fSides[i], rot, grey(0.6f + 0.4f * fShade[i], a), i % 4 == 0);
        }
    }

    /** The small shards: a disc of them that spins up, then every one goes to its place in a letter. */
    private void shards(Canvas c, float cx, float cy, float unit, float markCx, float markTop, float markSize) {
        float g = t - BURST, v = Math.max(0, t - VORTEX);
        float squash = tilt(), swing = lean();
        float cs = (float) Math.cos(swing), sn = (float) Math.sin(swing);
        float tighten = 1 - 0.34f * smooth(v / (IMPLODE - VORTEX));
        float em = markSize / SdfAtlas.EM;
        float u = t - SNAP;
        for (int i = 0; i < LOGO; i++) {
            float homeX = markCx + tx[i] * em, homeY = markTop + ty[i] * em;
            float x, y, size, a, rot = turn[i] * t + i;
            if (u >= 0) {
                if (fling[i] <= 0) {
                    // Most are simply replaced by the type.
                    a = 1 - u / 0.16f;
                    x = homeX;
                    y = homeY;
                    size = grain[i] * markSize * 0.028f;
                } else {
                    // The rest are knocked loose.
                    float dx = homeX - cx + (shade[i] - 0.5f) * 30, dy = homeY - cy + (grain[i] - 1.5f) * 40;
                    float len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
                    float d = fling[i] * (1 - (float) Math.exp(-u * 3f)) / 1.6f;
                    x = homeX + dx / len * d;
                    y = homeY + dy / len * d + 26 * u * u;
                    a = 1 - u / 1.7f;
                    size = grain[i] * (1.2f - 0.4f * Math.min(1f, u));
                }
            } else {
                float out = Easing.OUT_EXPO.apply(Math.clamp((g - wait[i] * 0.5f) / 1.5f, 0f, 1f));
                float oa = angle[i] + spin[i] * (0.45f * g + 0.3f * v * v);
                float or = unit * 0.5f * reach[i] * out * tighten * (1 + 0.05f * (float) Math.sin(t * 2.6f + i));
                float ox = (float) Math.cos(oa) * or, oy = (float) Math.sin(oa) * or * squash;
                float depth = 1 + 0.38f * (float) Math.sin(oa) * (1 - squash) / 0.6f;
                float orbitX = cx + ox * cs - oy * sn, orbitY = cy + ox * sn + oy * cs;

                float p = Math.clamp((t - IMPLODE - wait[i]) / (SNAP - IMPLODE - 0.34f), 0f, 1f);
                float e = p * p * p * (p * (6 * p - 15) + 10);
                // Spiral in rather than straight.
                float curl = (float) Math.sin(e * Math.PI) * 0.9f;
                float dx = orbitX - homeX, dy = orbitY - homeY;
                float cc = (float) Math.cos(curl), ss = (float) Math.sin(curl);
                x = homeX + (dx * cc - dy * ss) * (1 - e);
                y = homeY + (dx * ss + dy * cc) * (1 - e);
                size = grain[i] * (depth * 1.25f * (1 - e) + markSize * 0.028f * e);
                a = Math.min(1f, out * 3) * (0.45f + 0.55f * shade[i] + 0.4f * e) * Math.min(1f, 0.55f + 0.45f * depth);
                rot *= 1 - e;
            }
            if (a < 0.01f) continue;
            shard(c, x, y, size, sides[i], rot, grey(0.62f + 0.38f * shade[i], Math.min(1f, a)), false);
        }
    }

    private static void shard(Canvas c, float x, float y, float r, int sides, float rot, int color, boolean outline) {
        if (r < 0.3f) return;
        c.push();
        c.rotate(rot, x, y);
        if (sides == 1) c.star(x, y, r * 1.2f, color);
        else if (outline) c.polygonStroke(x, y, r, sides, r * 0.12f, Math.max(0.6f, r * 0.16f), color);
        else c.polygon(x, y, r, sides, r * 0.12f, color);
        c.pop();
    }

    /** The moment the letters lock: rings thrown outwards and a streak of light through the word. */
    private void hit(Canvas c, float cx, float cy, float w, float h, float u) {
        float far = (float) Math.sqrt(w * w + h * h) * 0.62f;
        for (int k = 0; k < 4; k++) {
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

    private static void tagline(Canvas c, float cx, float y, float markSize, float u) {
        float in = smooth((u - 0.75f) / 0.6f) * (1 - smooth((u - (SETTLE - SNAP) + 0.15f) / 0.3f));
        if (in < 0.01f) return;
        String line = "YOUR GAME, YOUR WAY";
        float size = Math.max(6.5f, markSize * 0.17f);
        // Letter-spaced, and drawing together as it arrives.
        float gap = size * (0.34f + 0.5f * (1 - Easing.OUT_CUBIC.apply(Math.clamp((u - 0.75f) / 1.2f, 0f, 1f))));
        float total = -gap;
        for (int i = 0; i < line.length(); i++) total += Fonts.MEDIUM.width(line.substring(i, i + 1), size) + gap;
        float pen = cx - total / 2;
        for (int i = 0; i < line.length(); i++) {
            String ch = line.substring(i, i + 1);
            c.text(Fonts.MEDIUM, ch, pen, y, size, grey(0.9f, in));
            pen += Fonts.MEDIUM.width(ch, size) + gap;
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

    private static float sq(float x) {
        return x * x;
    }
}
