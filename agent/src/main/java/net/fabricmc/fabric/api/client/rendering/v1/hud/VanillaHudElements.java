package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;

/** Éléments HUD vanilla (surface Irium — valeurs réelles lues du client 26.2). */
public final class VanillaHudElements {

    public static final Identifier ALL;
    public static final Identifier LAYER_NAME;
    public static final Identifier EXPERIENCE_BAR;
    public static final Identifier HOTBAR;
    public static final Identifier JUMP_METER;
    public static final Identifier CROSSHAIR;
    public static final Identifier ITEM_NAME;
    public static final Identifier SLEEP_OVERLAY;
    public static final Identifier EFFECTS;
    public static final Identifier DEBUG;
    public static final Identifier SCOREBOARD;
    public static final Identifier TEXT_CHAT;
    public static final Identifier PLAYER_LIST;
    public static final Identifier SUBTITLES;
    public static final Identifier TITLE;
    public static final Identifier MISSED;

    static {
        ALL = Identifier.fromNamespaceAndPath("vanilla", "all");
        LAYER_NAME = Identifier.fromNamespaceAndPath("vanilla", "layer_name");
        EXPERIENCE_BAR = Identifier.fromNamespaceAndPath("vanilla", "experience_bar");
        HOTBAR = Identifier.fromNamespaceAndPath("vanilla", "hotbar");
        JUMP_METER = Identifier.fromNamespaceAndPath("vanilla", "jump_meter");
        CROSSHAIR = Identifier.fromNamespaceAndPath("vanilla", "crosshair");
        ITEM_NAME = Identifier.fromNamespaceAndPath("vanilla", "item_name");
        SLEEP_OVERLAY = Identifier.fromNamespaceAndPath("vanilla", "sleep_overlay");
        EFFECTS = Identifier.fromNamespaceAndPath("vanilla", "effects");
        DEBUG = Identifier.fromNamespaceAndPath("vanilla", "debug");
        SCOREBOARD = Identifier.fromNamespaceAndPath("vanilla", "scoreboard");
        TEXT_CHAT = Identifier.fromNamespaceAndPath("vanilla", "text_chat");
        PLAYER_LIST = Identifier.fromNamespaceAndPath("vanilla", "player_list");
        SUBTITLES = Identifier.fromNamespaceAndPath("vanilla", "subtitles");
        TITLE = Identifier.fromNamespaceAndPath("vanilla", "title");
        MISSED = Identifier.fromNamespaceAndPath("vanilla", "missed");
    }

    private VanillaHudElements() {}
}
