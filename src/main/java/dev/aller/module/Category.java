package dev.aller.module;

public enum Category {
    HUD("HUD", "Information on screen"),
    VISUAL("Visual", "How the game looks"),
    UTILITY("Utility", "Quality of life"),
    COMBAT("PvP", "Fair-play combat aids"),
    WORLD("World", "Waypoints and world tools"),
    CHAT("Chat", "Chat and social"),
    COSMETIC("Cosmetic", "Style extras");

    public final String label;
    public final String blurb;

    Category(String label, String blurb) {
        this.label = label;
        this.blurb = blurb;
    }
}
