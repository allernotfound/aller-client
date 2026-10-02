package dev.aller.ui.widget;

import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Single-line text input with a click-to-place caret, word jumps and deletion, select-all and the clipboard. */
public class TextField extends Widget {
    public String text = "";
    public String placeholder = "";
    public int maxLength = 64;
    public float textSize = 9f;
    public boolean focused;
    /** When true the field draws no chrome, for embedding in a larger surface (the palette). */
    public boolean bare;
    public Consumer<String> onChange;
    private int caret;
    private boolean allSelected;
    private final Spring focus = Spring.snappy(0);
    private final Spring caretX = new Spring(0, 900f, 50f);
    private float blink;
    /** How far the text is shifted left so the caret stays in view when it is longer than the field. */
    private float scrollX;

    public TextField(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setText(String value) {
        text = value;
        caret = value.length();
        allSelected = false;
    }

    public boolean caretAtEnd() {
        return caret >= text.length();
    }

    @Override
    public void draw(Canvas c, float mx, float my) {
        float f = focus.target(focused ? 1 : 0).update();
        float pad = bare ? 0 : 8;
        if (!bare) {
            c.rect(x, y, w, h, Theme.R_MD, Colors.mix(0x12FFFFFF, 0x1CFFFFFF, f));
            c.stroke(x, y, w, h, Theme.R_MD, 1, Colors.mix(Theme.BORDER, Colors.withAlpha(Theme.accent(), 0.9f), f));
        }
        float inner = Math.max(1, w - pad * 2);
        if (focused) {
            float caretAt = Fonts.REGULAR.width(text.substring(0, Math.min(caret, text.length())), textSize);
            if (caretAt - scrollX > inner - 2) scrollX = caretAt - inner + 2;
            if (caretAt - scrollX < 0) scrollX = caretAt;
            scrollX = Math.clamp(scrollX, 0, Math.max(0, Fonts.REGULAR.width(text, textSize) - inner + 2));
        } else {
            scrollX = 0;
        }
        float tx = x + pad - scrollX;
        float th = Fonts.REGULAR.height(textSize);
        float ty = y + (h - th) / 2;
        c.clip(x + pad, y, inner + 1, h);
        if (text.isEmpty()) {
            c.text(Fonts.REGULAR, placeholder, tx, ty, textSize, Theme.TEXT_MUTED);
        } else {
            if (allSelected && focused) {
                float sw = Fonts.REGULAR.width(text, textSize);
                c.rect(tx - 1, ty - 1, sw + 2, th + 2, 2, Colors.withAlpha(Theme.accent(), 0.45f));
            }
            c.text(Fonts.REGULAR, text, tx, ty, textSize, Theme.TEXT);
        }
        if (focused) {
            blink += Motion.delta();
            float target = tx + Fonts.REGULAR.width(text.substring(0, caret), textSize);
            float cx = caretX.target(target).update();
            float a = 0.55f + 0.45f * (float) Math.cos(blink * 5.5f);
            c.rect(cx, ty, 1, th, 0.5f, Colors.withAlpha(Theme.accent(), a));
        } else {
            caretX.snap(tx + Fonts.REGULAR.width(text, textSize));
        }
        c.unclip();
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        focused = hit(mx, my);
        allSelected = false;
        if (focused) {
            // Put the caret at the gap between characters nearest the click.
            float local = mx - (x + (bare ? 0 : 8)) + scrollX;
            caret = text.length();
            for (int i = 0; i < text.length(); i++) {
                float mid = (Fonts.REGULAR.width(text.substring(0, i), textSize) + Fonts.REGULAR.width(text.substring(0, i + 1), textSize)) / 2;
                if (local < mid) {
                    caret = i;
                    break;
                }
            }
            blink = 0;
        }
        return focused;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (!focused) return false;
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0 || (mods & GLFW.GLFW_MOD_SUPER) != 0;
        blink = 0;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (allSelected) replaceAll("");
                else if (caret > 0) {
                    int from = ctrl ? wordStart(caret) : caret - 1;
                    text = text.substring(0, from) + text.substring(caret);
                    caret = from;
                    changed();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (allSelected) replaceAll("");
                else if (caret < text.length()) {
                    text = text.substring(0, caret) + text.substring(caret + 1);
                    changed();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                caret = allSelected ? 0 : Math.max(0, ctrl ? wordStart(caret) : caret - 1);
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                caret = allSelected ? text.length() : ctrl ? wordEnd(caret) : Math.min(text.length(), caret + 1);
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                caret = 0;
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                caret = text.length();
                allSelected = false;
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    allSelected = !text.isEmpty();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) {
                    if (allSelected) Mc.setClipboard(text);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl) {
                    if (allSelected) {
                        Mc.setClipboard(text);
                        replaceAll("");
                    }
                    return true;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    insert(Mc.clipboard().replaceAll("[\\r\\n]", " "));
                    return true;
                }
            }
            default -> {}
        }
        return false;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (!focused || codepoint < 32 || codepoint == 127) return false;
        insert(new String(Character.toChars(codepoint)));
        return true;
    }

    private void insert(String s) {
        if (allSelected) {
            text = "";
            caret = 0;
            allSelected = false;
        }
        int room = maxLength - text.length();
        if (room <= 0 || s.isEmpty()) return;
        if (s.length() > room) s = s.substring(0, room);
        text = text.substring(0, caret) + s + text.substring(caret);
        caret += s.length();
        changed();
    }

    private void replaceAll(String s) {
        text = s;
        caret = s.length();
        allSelected = false;
        changed();
    }

    private int wordStart(int from) {
        int i = from;
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && text.charAt(i - 1) != ' ') i--;
        return i;
    }

    private int wordEnd(int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') i++;
        while (i < text.length() && text.charAt(i) != ' ') i++;
        return i;
    }

    private void changed() {
        if (onChange != null) onChange.accept(text);
    }
}
