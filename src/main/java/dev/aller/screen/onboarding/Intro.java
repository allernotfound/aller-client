package dev.aller.screen.onboarding;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.font.SdfAtlas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The first thing a new player sees: half a minute in which the wordmark is put together out of
 * shards. A point of light, a flight down a tunnel, out of it over a racing floor towards an eclipse
 * on the horizon, the disc of shards that gathers round the eclipse, and then it waits: the player
 * has to break the eclipse open by mashing keys. That sets off the collapse into the letters, the
 * hit when they lock, a held shot of the wordmark, and the settle into the header of the steps that
 * follow. A score played on note blocks runs under all of it.
 *
 * <p>Everything is a function of one clock, so any moment can be drawn on its own. The clock stops
 * at {@link #GATE} until the eclipse is broken; what moves meanwhile runs on the time spent there.
 *
 * <p>It is drawn while the canvas is drained of colour: every colour here is a grey on purpose.
 */
public final class Intro {
    /** The moments the piece turns on, in seconds. They sit on the score's half-second beat up to the gate. */
    public static final float BURST = 3.0f, GRID = 8.5f, VORTEX = 13.5f, GATE = 19.0f, SNAP = 20.3f, SETTLE = 25.6f, LENGTH = 27.0f;
    private static final int LOGO = 680, FIELD = 150, RINGS = 15, RAYS = 44, CORONA = 36, CRACKS = 24, BOLTS = 10, BOLT_PARTS = 7;
    private static final float TAU = 6.2831855f;
    /** What one press adds to the pressure on the eclipse, and what leaks away each second. */
    private static final float STRIKE = 0.07f, LEAK = 0.1f;
    /** The beats of the build-up, each a pulse from the centre. */
    private static final float[] BEATS = {VORTEX, VORTEX + 1.5f, VORTEX + 2.5f, VORTEX + 3.5f, VORTEX + 4f, VORTEX + 4.5f, VORTEX + 4.75f, VORTEX + 5f, VORTEX + 5.25f};
    /** The opening's heartbeats. */
    private static final float[] HEART = {0.84f, 1.78f, 2.47f};

    private record Cue(float at, String sound, float pitch, float volume) {}

    private static final Cue[] CUES = score();

    /** A note block note, {@code semitone} above the F sharp at the bottom of its two octaves. */
    private static void note(List<Cue> out, float at, String instrument, int semitone, float volume) {
        out.add(new Cue(at, "block.note_block." + instrument, (float) Math.pow(2, (Math.clamp(semitone, 0, 24) - 12) / 12.0), volume));
    }

    /** The whole soundtrack: effects on the moments, and a piece in F sharp minor on a half-second beat. */
    private static Cue[] score() {
        List<Cue> s = new ArrayList<>();
        // Chords by root and third: F sharp minor, D, A, E.
        int[] root = {12, 8, 3, 10}, third = {3, 4, 4, 4};

        // The dark: a drone, three heartbeats, each with a low bell a step higher.
        s.add(new Cue(0.05f, "block.beacon.ambient", 0.5f, 0.7f));
        for (int i = 0; i < HEART.length; i++) {
            s.add(new Cue(HEART[i], "block.note_block.basedrum", 0.55f + 0.07f * i, 0.8f + 0.1f * i));
            note(s, HEART[i], "bell", new int[] {0, 3, 7}[i], 0.3f);
        }
        s.add(new Cue(BURST - 0.05f, "block.beacon.activate", 0.7f, 0.8f));
        s.add(new Cue(BURST, "item.trident.riptide_3", 0.6f, 0.5f));
        s.add(new Cue(BURST, "entity.generic.explode", 0.6f, 0.3f));

        // The tunnel: four on the floor, and an arpeggio that climbs through the chords.
        for (int beat = 0; beat < 11; beat++) {
            float at = BURST + beat * 0.5f, grow = beat / 10f;
            int ch = beat / 2 % 4;
            s.add(new Cue(at, "block.note_block.basedrum", 0.6f, 0.8f));
            s.add(new Cue(at + 0.25f, "block.note_block.hat", 1.2f, 0.3f));
            if (beat % 2 == 1) s.add(new Cue(at, "block.note_block.snare", 1f, 0.4f));
            note(s, at, "bass", root[ch], 0.7f);
            int[] tones = {root[ch], root[ch] + third[ch], root[ch] + 7, root[ch] + 12};
            for (int n = 0; n < 4; n++) note(s, at + n * 0.125f, "harp", tones[beat % 2 == 0 ? n : 3 - n], 0.3f + 0.25f * grow);
        }

        // Out over the floor: half time, a bell tune over held chords.
        s.add(new Cue(GRID, "item.trident.riptide_2", 0.8f, 0.5f));
        s.add(new Cue(GRID, "block.amethyst_cluster.break", 0.7f, 0.8f));
        int[] tune = {19, 17, 15, 20, 19, 15, 14, 17, 19, 24};
        for (int beat = 0; beat < 10; beat++) {
            float at = GRID + beat * 0.5f;
            int ch = beat / 2 % 4;
            s.add(new Cue(at, beat % 2 == 0 ? "block.note_block.basedrum" : "block.note_block.snare", beat % 2 == 0 ? 0.55f : 0.9f, beat % 2 == 0 ? 0.9f : 0.35f));
            note(s, at, "bass", root[ch], 0.7f);
            note(s, at, "bell", tune[beat], 0.55f);
            note(s, at + 0.25f, "harp", root[ch] + (beat % 2 == 0 ? third[ch] : 7), 0.22f);
            if (beat % 2 == 0) for (int n : new int[] {root[ch], root[ch] + third[ch], root[ch] + 7}) note(s, at, "chime", n, 0.3f);
        }

        // The disc: a scale climbing over a pedal note, the charges on the beats, and a roll into the gate.
        int[] scale = {0, 2, 3, 5, 7, 8, 10, 12, 14, 15, 17, 19, 20, 22, 24};
        for (int half = 0; half < 22; half++) {
            float at = VORTEX + half * 0.25f;
            note(s, at, "harp", scale[half * 14 / 21], 0.3f + 0.3f * half / 21f);
            if (half % 2 == 0) {
                s.add(new Cue(at, "block.note_block.basedrum", 0.6f, 0.8f));
                note(s, at, "bass", 0, 0.7f);
            }
        }
        for (int i = 0; i < BEATS.length; i++) s.add(new Cue(BEATS[i], "block.respawn_anchor.charge", 0.6f + 0.16f * i, 0.6f + 0.035f * i));
        for (int i = 0; i < 20; i++) s.add(new Cue(GATE - 2.5f + i * 0.125f, "block.note_block.snare", 0.8f + 0.03f * i, 0.1f + 0.022f * i));
        // Arriving at the gate: one deep bell, and then only what the player's own hits make.
        s.add(new Cue(GATE, "block.bell.use", 0.5f, 0.9f));
        s.add(new Cue(GATE, "block.note_block.basedrum", 0.5f, 1f));

        // Broken open: everything falls in.
        s.add(new Cue(GATE + 0.02f, "block.glass.break", 0.6f, 0.8f));
        s.add(new Cue(GATE + 0.02f, "item.trident.riptide_1", 0.5f, 0.7f));
        s.add(new Cue(GATE + 0.45f, "block.beacon.deactivate", 1.5f, 0.6f));

        // The hit, on a chord of A, and a slow figure falling away from it.
        s.add(new Cue(SNAP, "entity.generic.explode", 0.55f, 0.55f));
        s.add(new Cue(SNAP, "block.beacon.power_select", 0.8f, 0.9f));
        s.add(new Cue(SNAP, "block.amethyst_cluster.break", 0.6f, 0.9f));
        s.add(new Cue(SNAP, "entity.lightning_bolt.thunder", 1.2f, 0.25f));
        for (int n : new int[] {3, 7, 10, 15, 19}) {
            note(s, SNAP, "chime", n, 0.5f);
            note(s, SNAP, "bell", n, 0.4f);
        }
        note(s, SNAP, "bass", 3, 0.9f);
        s.add(new Cue(SNAP + 0.42f, "entity.experience_orb.pickup", 0.7f, 0.6f));
        s.add(new Cue(SNAP + 0.8f, "block.amethyst_block.resonate", 1f, 0.8f));
        int[] after = {3, 10, 15, 19, 22, 24, 22, 19, 15, 19, 24};
        for (int i = 0; i < after.length; i++) note(s, SNAP + 0.75f + i * 0.375f, "harp", after[i], 0.42f - 0.025f * i);
        s.add(new Cue(SETTLE, "item.trident.riptide_1", 1.4f, 0.25f));
        note(s, SETTLE + 0.9f, "chime", 15, 0.3f);
        note(s, SETTLE + 0.9f, "chime", 3, 0.3f);

        s.sort(Comparator.comparingDouble(Cue::at));
        return s.toArray(new Cue[0]);
    }

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
    private final float[] coronaAngle = new float[CORONA], coronaLen = new float[CORONA];
    private final float[] crackAngle = new float[CRACKS], crackBend = new float[CRACKS * 4];
    private final float[] boltAngle = new float[BOLTS], boltBend = new float[BOLTS * BOLT_PARTS];

    private float t;
    private int cue;
    /** At the gate: seconds spent there, how nearly the eclipse is broken, and whether it has been. */
    private float gateT, pressure;
    private boolean broke;
    private int strikes;
    /** 1 at a press and dying away; the turning the disc has done while the clock stood still. */
    private float jolt, whirl;
    /** The pointer, -1 to 1 each way from the middle, eased. */
    private float px, py;
    // Where things stand this frame: the vanishing point, the horizon, the eclipse.
    private float vx, hy, drop, ex, ey, er;

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
            reach[k] = 0.22f + 0.78f * (float) Math.sqrt(r.nextFloat());
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
        Sounds.cue("block.note_block.pling", (float) Math.pow(2, (Math.min(24, Math.round(pressure * 24)) - 12) / 12.0), 0.5f);
        Sounds.cue("block.glass.break", 0.7f + 0.8f * pressure, 0.22f);
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
        float ease = Math.min(1f, dt * 4);
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
        place(cx, cy, h, unit);

        // Letterbox bars for the build-up, closing a little under pressure, drawn back after the hit.
        float bars = h * (0.115f + 0.03f * held) * (1 - smooth((u - 0.2f) / 0.9f)) * smooth(t / 0.6f);

        c.push();
        float shake = 2.6f * decay(t - BURST, 5) + 3f * decay(t - GRID, 5) + 9f * decay(u, 5.5f)
                + (before ? 1.2f * held + 4f * jolt + 1.6f * smooth((t - GATE) / (SNAP - GATE)) * (broke ? 1 : 0) : 0);
        for (float beat : BEATS) shake += 1.3f * decay(t - beat, 9);
        if (shake > 0.02f) c.translate((float) Math.sin(live * 97) * shake, (float) Math.cos(live * 83) * shake);
        c.translate(-px * 5, -py * 3);
        if (before) c.scale(1 + 0.015f * pump() + 0.05f * held + 0.03f * jolt, cx, cy);

        if (t < BURST + 0.25f) spark(c, cx, cy, w, unit);
        if (t >= BURST && t < GRID + 0.4f) tunnel(c, cx, cy, unit);
        if (t >= BURST && t < VORTEX) streaks(c, cx, cy, Math.max(w, h));
        if (t >= GRID && drop < 0.99f) floor(c, w, h, unit);
        if (t >= VORTEX - 0.1f && before) orbits(c, unit);
        if (t >= BURST) field(c, cx, cy, w, h, unit);
        if (t >= GATE - 0.1f && u < 0.1f) rays(c, Math.max(w, h));

        // The wordmark: shards up to the hit, type from it, then the glide up to the header.
        float rise = Easing.IN_OUT_CUBIC.apply(Math.clamp((t - SETTLE) / (LENGTH - SETTLE), 0f, 1f));
        float size = markSize + (headSize - markSize) * rise;
        float mx = cx + (headCx - cx) * rise, top = markTop + (headTop - markTop) * rise;
        // The far half of the disc goes behind the eclipse, the near half in front of it.
        if (t >= VORTEX - 0.6f && before) shards(c, unit, mx, top, size, false);
        if (t >= GRID && u < 0.15f) eclipse(c, live);
        if (t >= VORTEX - 0.6f) shards(c, unit, mx, top, size, true);
        if (waiting() || t >= GATE && t < GATE + 0.3f) prompt(c, cx, cy + unit * 0.27f, unit, live);
        if (u >= 0) {
            float my = markTop + markSize * 0.5f;
            beams(c, cx, my, unit, Math.max(w, h), u, 1 - rise);
            embers(c, w, h, u, live, 1 - rise);
            hit(c, cx, my, w, h, u);
            float punch = 1 + 0.17f * (float) (Math.exp(-u * 6) * Math.sin(u * 20));
            // A slow push in while the shot is held.
            float push = 1 + 0.06f * Math.clamp(u / (SETTLE - SNAP), 0f, 1f) * (1 - rise);
            c.push();
            c.scale(punch * push, mx, top + size * 0.5f);
            mark(c, mx, top, size, Math.min(1f, u / 0.07f), Easing.OUT_BACK.apply(Math.clamp((u - 0.42f) / 0.35f, 0f, 1f)),
                    Colors.WHITE, u < 2.6f ? (u - 0.55f) * 16f : (u - 3.1f) * 16f);
            c.pop();
            tagline(c, cx, markTop + markSize * 1.42f, markSize, u);
        }
        c.pop();

        // The whiteouts.
        float flash = 0.55f * decay(t - BURST, 6) + 0.45f * decay(t - GRID, 6) + 0.9f * decay(u, 6.5f) + (before ? 0.1f * jolt : 0);
        if (flash > 0.01f) c.rect(0, 0, w, h, 0, Colors.withAlpha(Colors.WHITE, Math.min(1f, flash)));
        if (bars > 0.3f) {
            c.rect(0, 0, w, bars, 0, Colors.BLACK);
            c.rect(0, h - bars, w, bars, 0, Colors.BLACK);
        }
        // And out of black at the very start.
        if (t < 0.5f) c.rect(0, 0, w, h, 0, Colors.withAlpha(Colors.BLACK, 1 - t / 0.5f));
    }

    /** Works out where the horizon and the eclipse are at this moment. */
    private void place(float cx, float cy, float h, float unit) {
        float near = Math.clamp((t - GRID) / (VORTEX - GRID), 0f, 1f);
        // The floor falls away and the eclipse comes to the middle, where the disc forms round it.
        drop = smooth((t - (VORTEX - 0.9f)) / 1.6f);
        vx = cx + px * unit * 0.12f;
        float horizon = cy + unit * 0.10f;
        hy = horizon + drop * h;
        ex = vx + (cx - vx) * drop;
        float sky = horizon - unit * (0.05f + 0.11f * near);
        ey = sky + (cy - sky) * drop;
        float beat = 0;
        for (float b : BEATS) beat += decay(t - b, 7);
        float crush = Easing.IN_CUBIC.apply(Math.clamp((t - GATE) / (SNAP - GATE), 0f, 1f)) * (broke ? 1 : 0);
        er = unit * (0.035f + 0.05f * Easing.OUT_CUBIC.apply(near)) * (1 + 0.07f * beat + (t >= GATE ? 0.22f * pressure + 0.08f * jolt : 0)) * (1 - crush);
    }

    /** The kick of the score, 1 on each beat of the flight and dying away before the next. */
    private float pump() {
        return t >= BURST && t < VORTEX ? decay(frac((t - BURST) / 0.5f) * 0.5f, 9) : 0;
    }

    // ---- the pieces ----------------------------------------------------------------------------

    /** The opening: one point of light with a heartbeat, dust drawn towards it, and the streak it throws just before it bursts. */
    private void spark(Canvas c, float cx, float cy, float w, float unit) {
        float on = smooth(t / 0.9f) * (1 - smooth((t - BURST) / 0.2f));
        float pulse = 0.5f * decay(t - HEART[0], 5) + 0.7f * decay(t - HEART[1], 5) + 1f * decay(t - HEART[2], 5);
        for (int i = 0; i < 70; i++) {
            float pull = 1 - 0.45f * Easing.IN_CUBIC.apply(t / BURST) - 0.04f * pulse;
            float d = unit * (0.08f + 0.75f * fReach[i]) * pull, a = fAngle[i] + 0.12f * t * (1.4f - fReach[i]);
            float twinkle = 0.5f + 0.5f * (float) Math.sin(t * 2.3f + i * 1.7f);
            c.circle(cx + (float) Math.cos(a) * d - px * 14 * fShade[i], cy + (float) Math.sin(a) * d - py * 9 * fShade[i],
                    0.5f + 0.9f * fShade[i], grey(0.9f, on * (0.12f + 0.3f * twinkle * fShade[i])));
        }
        float r = 1.1f + 2.2f * pulse + 5 * smooth((t - 2.4f) / 0.5f);
        c.oval(cx, cy, 26 + 60 * pulse, 26 + 60 * pulse, 0.95f, grey(1f, 0.16f * on + 0.22f * pulse * on));
        c.circle(cx, cy, r, grey(1f, on));
        for (float beat : HEART) {
            float p = (t - beat) / 0.9f;
            if (p > 0 && p < 1) c.ring(cx, cy, 4 + 90 * Easing.OUT_CUBIC.apply(p), 1.4f * (1 - p), grey(0.9f, 0.5f * (1 - p)));
        }
        // An anamorphic streak that widens and snaps shut.
        float s = smooth((t - 1.7f) / 1.0f) * (1 - smooth((t - (BURST - 0.22f)) / 0.2f));
        if (s > 0.01f) {
            float half = w * 0.42f * s;
            c.gradientH(cx - half, cy - 0.5f, half, 1f, 0, grey(1f, 0), grey(1f, 0.85f * on));
            c.gradientH(cx, cy - 0.5f, half, 1f, 0, grey(1f, 0.85f * on), grey(1f, 0));
        }
    }

    /** How far the flight has gone, in tunnel lengths: gathering speed down the tunnel, steady over the floor. */
    private float travel() {
        float g = Math.min(t, VORTEX + 1.1f) - BURST, in = Math.min(g, GRID - BURST);
        return 0.5f * in + 0.11f * in * in + Math.max(0, g - in) * 1.2f;
    }

    private float speed() {
        return t < GRID ? 0.5f + 0.22f * (t - BURST) : 1.2f;
    }

    /** How far the far end of the tunnel is pushed aside: it bends towards the pointer and sways a little by itself. */
    private float bendX(float unit) {
        return unit * (0.22f * px + 0.07f * (float) Math.sin(t * 0.9f));
    }

    private float bendY(float unit) {
        return unit * (0.22f * py + 0.05f * (float) Math.cos(t * 0.7f));
    }

    /** The walls of the tunnel: polygon outlines coming out of the distance, turning as they pass. */
    private void tunnel(Canvas c, float cx, float cy, float unit) {
        float in = smooth((t - BURST) / 0.5f), out = 1 - smooth((t - GRID + 0.3f) / 0.6f);
        float roll = 0.45f * (t - BURST), bx = bendX(unit), by = bendY(unit), pump = pump();
        for (int k = 0; k < RINGS; k++) {
            float z = 1 - frac(k / (float) RINGS + travel());
            float near = 1 / (z * 5f + 0.12f);
            float r = unit * 0.2f * near;
            if (r > unit * 1.6f) continue;
            float a = in * out * smooth(z * 4) * smooth((1 - z) * 2.5f) * (0.5f + 0.35f * pump);
            if (a < 0.01f) continue;
            float x = cx + bx * z * z, y = cy + by * z * z;
            c.push();
            c.rotate(roll + k * 0.35f + z * 1.4f, x, y);
            c.polygonStroke(x, y, r, k % 3 == 0 ? 8 : 6, r * 0.06f, Math.clamp(0.5f + 0.5f * near, 0.6f, 3.2f), grey(0.75f + 0.25f * (1 - z), a));
            c.pop();
        }
    }

    /** Speed lines thrown outwards from the vanishing point, more of them the faster it goes. */
    private void streaks(Canvas c, float cx, float cy, float far) {
        float on = smooth((t - BURST - 1f) / 2.5f) * (1 - smooth((t - VORTEX + 1f) / 0.8f));
        if (on < 0.01f) return;
        float grid = smooth((t - GRID) / 0.8f);
        float ox = cx + (ex - cx) * grid, oy = cy + (ey - cy) * grid;
        for (int i = 0; i < RAYS; i++) {
            float q = frac(travel() * 1.7f + rayLag[i]);
            float head = far * (0.08f + 0.7f * q * q), len = far * 0.14f * q;
            float cos = (float) Math.cos(rayAngle[i]), sin = (float) Math.sin(rayAngle[i]);
            // Over the floor only the sky has them.
            if (grid > 0.5f && sin > 0.05f) continue;
            c.line(ox + cos * head, oy + sin * head, ox + cos * (head + len), oy + sin * (head + len), 0.7f, grey(0.9f, 0.3f * on * q * (1 - q) * 4));
        }
    }

    /** The floor the flight comes out over: a grid running to the horizon, and monoliths going past on both sides. */
    private void floor(Canvas c, float w, float h, float unit) {
        float in = smooth((t - GRID) / 0.5f) * (1 - drop);
        if (in < 0.01f) return;
        float deep = Math.max(20, h - hy) * 0.35f, g = t - GRID;
        c.gradientV(0, hy - unit * 0.07f, w, unit * 0.07f, 0, grey(1f, 0), grey(1f, 0.16f * in));
        c.gradientV(0, hy, w, unit * 0.12f, 0, grey(1f, 0.12f * in), grey(1f, 0));
        // The eclipse's light lying on the floor.
        c.gradientV(ex - er * 0.7f, hy, er * 1.4f, unit * 0.3f, 0, grey(1f, 0.2f * in), grey(1f, 0));
        for (int m = -11; m <= 11; m++) {
            float side = m * unit * 0.16f;
            c.line(vx + side / 4.1f, hy + deep / 4.1f, vx + side / 0.3f, hy + deep / 0.3f, 0.8f, grey(0.9f, 0.2f * in));
        }
        int lines = 14;
        float step = frac(g * 0.9f * lines);
        for (int j = lines - 1; j >= 0; j--) {
            float d = (j + 1 - step) / lines, z = 0.12f + d * 4;
            float y = hy + deep / z;
            if (y > h + 4) continue;
            c.rect(0, y, w, Math.min(1.8f, 0.5f + 0.25f / z), 0, grey(0.9f, 0.5f * in * (1 - d) * (float) Math.sqrt(1 - d)));
        }
        c.rect(0, hy - 0.5f, w, 1, 0, grey(1f, 0.55f * in));
        int pillars = 9;
        step = frac(g * 0.5f * pillars);
        for (int j = pillars - 1; j >= 0; j--) {
            float d = (j + 1 - step) / pillars, z = 0.14f + d * 4;
            float a = in * smooth((1 - d) * 3);
            if (a < 0.01f) continue;
            float wide = unit * 0.05f / z, tall = unit * (0.3f + 0.2f * ((j + (int) (g * 0.5f * pillars)) * 7 % 3)) / z, base = hy + deep / z;
            for (int side = -1; side <= 1; side += 2) {
                float x = vx + side * unit * 0.34f / z - wide / 2;
                if (x > w || x + wide < 0) continue;
                c.gradientV(x, base - tall, wide, tall, 0, grey(0.7f, a * 0.95f), grey(0.08f, a));
                c.stroke(x, base - tall, wide, tall, 0, Math.min(1.5f, 0.5f + 0.2f / z), grey(1f, a * 0.5f));
            }
        }
    }

    /** The disc's tilt: 1 seen flat on, less as it leans back. The pointer tips it. */
    private float tilt() {
        return Math.clamp(1 - 0.6f * smooth((t - VORTEX + 0.4f) / 1.6f) - 0.14f * py, 0.22f, 1f);
    }

    /** The slow swing of the whole disc. */
    private float lean() {
        return 0.3f * (float) Math.sin((t + gateT - VORTEX) * 0.55f) - 0.2f * smooth((t - VORTEX) / 2f) + 0.3f * px;
    }

    /** How far the disc has drawn in: with time, and with the pressure put on the eclipse. */
    private float tighten() {
        return 1 - 0.3f * smooth((t - VORTEX) / (GATE - VORTEX)) - (t >= GATE ? 0.2f * pressure : 0) + 0.05f * jolt;
    }

    /** Orbit lines the disc turns on; each beat sends another outwards, and the collapse takes them all. */
    private void orbits(Canvas c, float unit) {
        float in = smooth((t - VORTEX + 0.1f) / 0.8f);
        float fall = broke ? Easing.IN_CUBIC.apply(Math.clamp((t - GATE) / (SNAP - GATE - 0.15f), 0f, 1f)) : 0;
        c.push();
        c.rotate(lean(), ex, ey);
        c.stretch(1f, tilt(), ex, ey);
        for (int k = 0; k < 5; k++) {
            float r = unit * (0.15f + 0.105f * k) * tighten() * (1 - fall);
            if (r < 1) continue;
            c.ring(ex, ey, r, 0.7f, grey(0.8f, 0.2f * in * (1 - fall * 0.5f)));
            // A bead running each line, alternate ways round.
            float a = (k % 2 == 0 ? 1 : -1) * (t - VORTEX + whirl * 3) * (2.2f - 0.3f * k) + k * 1.3f;
            c.circle(ex + (float) Math.cos(a) * r, ey + (float) Math.sin(a) * r, 1.6f, grey(1f, 0.8f * in));
        }
        for (float beat : BEATS) {
            float p = (t + gateT - beat) / 0.7f;
            if (p > 0 && p < 1) c.ring(ex, ey, unit * 0.75f * Easing.OUT_CUBIC.apply(p), 2.4f * (1 - p), grey(1f, 0.55f * (1 - p)));
        }
        // Every press throws one too.
        if (jolt > 0.02f) c.ring(ex, ey, er + unit * 0.5f * (1 - jolt), 3f * jolt, grey(1f, 0.6f * jolt));
        c.pop();
    }

    /**
     * The eclipse: a black disc with the light behind it showing round the rim. Each press cracks it,
     * and the light leaks out along the crack; the collapse crushes it to nothing.
     */
    private void eclipse(Canvas c, float live) {
        float in = smooth((t - GRID) / 0.5f) * (1 - smooth((t - SNAP) / 0.12f));
        if (in < 0.01f || er < 0.4f) return;
        float heat = t >= GATE ? pressure : 0, r = er;
        c.oval(ex, ey, r * (3.2f + 1.5f * heat), r * (3.2f + 1.5f * heat), 0.97f, grey(1f, (0.16f + 0.2f * heat) * in));
        c.oval(ex, ey, r * 1.9f, r * 1.9f, 0.75f, grey(1f, (0.32f + 0.3f * heat) * in));
        for (int i = 0; i < CORONA; i++) {
            float a = coronaAngle[i] + live * 0.07f;
            float len = r * (0.22f + 1.1f * coronaLen[i] * (0.65f + 0.35f * (float) Math.sin(live * 1.7f + i * 2.4f)) + 0.8f * heat);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.line(ex + cos * r * 1.02f, ey + sin * r * 1.02f, ex + cos * (r + len), ey + sin * (r + len), Math.max(0.6f, r * 0.035f), grey(1f, 0.3f * in));
        }
        c.circle(ex, ey, r, grey(0.012f, in));
        c.ring(ex, ey, r + 0.6f, 1.3f, grey(1f, 0.95f * in));
        // The bead of light at the edge.
        float ba = -0.9f + live * 0.25f, bx = ex + (float) Math.cos(ba) * r, by = ey + (float) Math.sin(ba) * r;
        c.oval(bx, by, r * 0.5f, r * 0.5f, 0.9f, grey(1f, 0.6f * in));
        c.circle(bx, by, Math.max(1f, r * 0.07f), grey(1f, in));

        int n = Math.min(strikes, CRACKS);
        for (int i = 0; i < n; i++) {
            float fresh = i == n - 1 ? jolt : 0, x0 = ex, y0 = ey, a = crackAngle[i];
            for (int s = 1; s <= 4; s++) {
                a = crackAngle[i] + crackBend[i * 4 + s - 1] * (s == 4 ? 0.2f : 0.7f);
                float x1 = ex + (float) Math.cos(a) * r * s / 4, y1 = ey + (float) Math.sin(a) * r * s / 4;
                c.line(x0, y0, x1, y1, 0.9f + 1.4f * fresh, grey(1f, (0.75f + 0.25f * fresh) * in));
                x0 = x1;
                y0 = y1;
            }
            float leak = r * (0.4f + 1.7f * pressure) * (0.6f + 0.8f * Math.abs(crackBend[i * 4])) * (1 + 0.5f * fresh);
            c.line(x0, y0, x0 + (float) Math.cos(a) * leak, y0 + (float) Math.sin(a) * leak, 1.3f + fresh, grey(1f, (0.4f + 0.4f * fresh) * in));
        }
    }

    /** What to do at the gate, and how far along it is. */
    private void prompt(Canvas c, float cx, float y, float unit, float live) {
        float in = smooth((gateT - 0.3f) / 0.4f) * (broke ? 1 - smooth((t - GATE) / 0.25f) : 1);
        if (in < 0.01f) return;
        // Nobody has pressed anything for a while: ask louder.
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
    }

    /** Lines drawn in from the edges: faintly while the eclipse is under pressure, hard as everything falls to the centre. */
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
            c.line(ex + cos * head, ey + sin * head, ex + cos * tail, ey + sin * tail, 0.8f, grey(0.9f, a));
        }
    }

    /**
     * The large shards. They come down the tunnel and out across the sky, are caught into a ring
     * turning the other way round the disc, fall in with it, and are thrown back out by the hit.
     */
    private void field(Canvas c, float cx, float cy, float w, float h, float unit) {
        float caught = smooth((t - VORTEX) / 1.0f);
        float fall = broke ? Easing.IN_CUBIC.apply(Math.clamp((t - GATE) / (SNAP - GATE - 0.1f), 0f, 1f)) : 0;
        float in = smooth((t - BURST) / 0.4f), grid = smooth((t - GRID) / 0.8f);
        float u = t - SNAP;
        float roll = 0.45f * (t - BURST), squash = tilt(), swing = lean(), bx = bendX(unit), by = bendY(unit);
        float cs = (float) Math.cos(swing), sn = (float) Math.sin(swing);
        float v = Math.max(0, t - VORTEX), draw = tighten();
        for (int i = 0; i < FIELD; i++) {
            float x, y, size, a, rot = fTurn[i] * (t + gateT) + i;
            if (u >= 0) {
                // Thrown out: fast at first, then drifting.
                float d = 24 + fFling[i] * (1 - (float) Math.exp(-u * 2.6f)) + 14 * u;
                x = cx + (float) Math.cos(fAngle[i]) * d - px * 16 * fShade[i];
                y = cy + (float) Math.sin(fAngle[i]) * d - py * 10 * fShade[i];
                size = fSize[i] * (1.5f - 0.5f * Math.min(1f, u));
                a = (0.9f - 0.5f * fShade[i]) * Math.max(0f, 1 - u / 3.4f);
            } else {
                float z = 1 - frac(fDepth[i] + travel() * 1.4f);
                float near = 1 / (z * 5f + 0.12f);
                float ta = fAngle[i] + roll;
                float tr = unit * 0.2f * fReach[i] * near;
                float ox = cx + bx * z * z, oy = cy + by * z * z;
                ox += (ex - ox) * grid;
                oy += (ey - oy) * grid;
                float tunnelX = ox + (float) Math.cos(ta) * tr, tunnelY = oy + (float) Math.sin(ta) * tr;
                float tunnelA = smooth(z * 5) * smooth((1 - z) * 3);

                float oa = fAngle[i] + fSpin[i] * (v * 0.9f + 0.35f * v * v + whirl * 4.7f);
                float or = unit * (0.5f + 0.34f * fReach[i]) * draw * (1 - fall);
                float dx = (float) Math.cos(oa) * or, dy = (float) Math.sin(oa) * or * squash;
                float depth = 1 + 0.3f * (float) Math.sin(oa);
                float orbitX = ex + dx * cs - dy * sn, orbitY = ey + dx * sn + dy * cs;

                x = tunnelX + (orbitX - tunnelX) * caught;
                y = tunnelY + (orbitY - tunnelY) * caught;
                size = fSize[i] * (Math.min(near, 4f) * 0.55f * (1 - caught) + depth * caught) * (1 - 0.8f * fall);
                a = in * (0.35f + 0.5f * fShade[i]) * (tunnelA * (1 - caught) + caught) * (1 - fall * 0.6f);
                // Streaks along the line of flight while it is at speed.
                float fast = (1 - caught) * Math.min(near, 5f) * speed();
                if (fast > 1.2f && tunnelA > 0.05f) {
                    float len = fast * 3.2f;
                    c.line(x, y, x - (float) Math.cos(ta) * len, y - (float) Math.sin(ta) * len, Math.min(1.4f, size * 0.25f), grey(0.85f, a * 0.5f));
                }
            }
            if (a < 0.01f || x < -40 || y < -40 || x > w + 40 || y > h + 40) continue;
            shard(c, x, y, size, fSides[i], rot, grey(0.6f + 0.4f * fShade[i], a), i % 4 == 0);
        }
    }

    /**
     * The small shards: a disc of them that pours out of the eclipse and spins up round it, then every
     * one goes to its place in a letter. Drawn in two goes, the far half of the disc and the near half.
     */
    private void shards(Canvas c, float unit, float markCx, float markTop, float markSize, boolean front) {
        float g = t - (VORTEX - 0.6f), v = Math.max(0, t - VORTEX);
        float squash = tilt(), swing = lean(), draw = tighten();
        float cs = (float) Math.cos(swing), sn = (float) Math.sin(swing);
        float em = markSize / SdfAtlas.EM;
        float u = t - SNAP, live = t + gateT;
        float mcx = markCx, mcy = markTop + markSize * 0.5f;
        for (int i = 0; i < LOGO; i++) {
            float homeX = markCx + tx[i] * em, homeY = markTop + ty[i] * em;
            float x, y, size, a, rot = turn[i] * live + i;
            if (u >= 0) {
                if (fling[i] <= 0) {
                    // Most are simply replaced by the type.
                    a = 1 - u / 0.16f;
                    x = homeX;
                    y = homeY;
                    size = grain[i] * markSize * 0.028f;
                } else {
                    // The rest are knocked loose.
                    float dx = homeX - mcx + (shade[i] - 0.5f) * 30, dy = homeY - mcy + (grain[i] - 1.5f) * 40;
                    float len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
                    float d = fling[i] * (1 - (float) Math.exp(-u * 3f)) / 1.6f;
                    x = homeX + dx / len * d;
                    y = homeY + dy / len * d + 26 * u * u;
                    a = 1 - u / 1.7f;
                    size = grain[i] * (1.2f - 0.4f * Math.min(1f, u));
                }
            } else {
                float out = Easing.OUT_EXPO.apply(Math.clamp((g - wait[i] * 0.5f) / 1.5f, 0f, 1f));
                float oa = angle[i] + spin[i] * (0.45f * g + 0.3f * v * v + whirl * 3.75f);
                float sin = (float) Math.sin(oa);
                float p = broke ? Math.clamp((t - GATE - wait[i]) / (SNAP - GATE - 0.34f), 0f, 1f) : 0;
                // Once it is on its way to a letter it is in front of everything.
                if ((sin >= 0 || p > 0) != front) continue;
                float or = unit * 0.5f * reach[i] * out * draw * (1 + 0.05f * (float) Math.sin(live * 2.6f + i));
                float ox = (float) Math.cos(oa) * or, oy = sin * or * squash;
                float depth = 1 + 0.38f * sin * (1 - squash) / 0.6f;
                float orbitX = ex + ox * cs - oy * sn, orbitY = ey + ox * sn + oy * cs;

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

    /** The moment the letters lock: lightning out from the word, rings thrown outwards and a streak of light through it. */
    private void hit(Canvas c, float cx, float cy, float w, float h, float u) {
        float far = (float) Math.sqrt(w * w + h * h) * 0.62f;
        float strike = decay(u, 6) * Math.min(1f, u / 0.05f);
        if (strike > 0.02f) {
            for (int i = 0; i < BOLTS; i++) {
                float cos = (float) Math.cos(boltAngle[i]), sin = (float) Math.sin(boltAngle[i]);
                float len = far * (0.5f + 0.5f * Math.abs(boltBend[i * BOLT_PARTS])) * Math.min(1f, u / 0.07f);
                // It flickers: each bolt is dark for part of the time.
                float a = strike * (Math.sin(u * 70 + i * 2.1f) > -0.3f ? 1 : 0.25f);
                float x0 = cx, y0 = cy;
                for (int s = 1; s <= BOLT_PARTS; s++) {
                    float along = len * s / BOLT_PARTS, off = boltBend[i * BOLT_PARTS + s - 1] * len * 0.05f;
                    float x1 = cx + cos * along - sin * off, y1 = cy + sin * along + cos * off;
                    c.line(x0, y0, x1, y1, 2.2f * (1 - (s - 1f) / BOLT_PARTS) + 0.5f, grey(1f, a));
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
        // Letter-spaced, and drawing together as it arrives.
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
