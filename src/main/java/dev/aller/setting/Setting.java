package dev.aller.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A single user-editable value belonging to a module or HUD element. */
public abstract class Setting<T> {
    public final String id;
    public final String name;
    public String description = "";
    protected T value;
    protected final T defaultValue;
    private Consumer<T> onChange;
    private BooleanSupplier visible = () -> true;

    protected Setting(String id, String name, T defaultValue) {
        this.id = id;
        this.name = name;
        this.value = this.defaultValue = defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T v) {
        if (v == null || v.equals(value)) return;
        value = v;
        if (onChange != null) onChange.accept(v);
    }

    public void reset() {
        set(defaultValue);
    }

    public T defaultValue() {
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S describe(String text) {
        description = text;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Consumer<T> listener) {
        onChange = listener;
        return (S) this;
    }

    /** Hides this setting in the UI unless the condition holds (e.g. only when a parent toggle is on). */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        visible = condition;
        return (S) this;
    }

    public boolean visible() {
        return visible.getAsBoolean();
    }

    public abstract JsonElement save();

    /** Must tolerate malformed input: configs are hand-editable and survive across versions. */
    public abstract void load(JsonElement json);
}
