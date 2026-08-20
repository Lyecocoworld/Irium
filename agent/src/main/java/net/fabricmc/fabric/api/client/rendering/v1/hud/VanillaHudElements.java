package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;

/** Éléments HUD vanilla (surface Irium — valeurs réelles fabric-api 26.2). */
public final class VanillaHudElements {

    private VanillaHudElements() {}

    public static final Identifier MISC_OVERLAYS;
    public static final Identifier CROSSHAIR;
    public static final Identifier SPECTATOR_MENU;
    public static final Identifier HOTBAR;
    public static final Identifier ARMOR_BAR;
    public static final Identifier HEALTH_BAR;
    public static final Identifier FOOD_BAR;
    public static final Identifier AIR_BAR;
    public static final Identifier MOUNT_HEALTH;
    public static final Identifier INFO_BAR;
    public static final Identifier EXPERIENCE_LEVEL;
    public static final Identifier HELD_ITEM_TOOLTIP;
    public static final Identifier SPECTATOR_TOOLTIP;
    public static final Identifier MOB_EFFECTS;
    public static final Identifier BOSS_BAR;
    public static final Identifier SLEEP;
    public static final Identifier DEMO_TIMER;
    public static final Identifier SCOREBOARD;
    public static final Identifier OVERLAY_MESSAGE;
    public static final Identifier TITLE_AND_SUBTITLE;
    public static final Identifier CHAT;
    public static final Identifier PLAYER_LIST;
    public static final Identifier SUBTITLES;

    static {
        MISC_OVERLAYS = Identifier.withDefaultNamespace("misc_overlays");
        CROSSHAIR = Identifier.withDefaultNamespace("crosshair");
        SPECTATOR_MENU = Identifier.withDefaultNamespace("spectator_menu");
        HOTBAR = Identifier.withDefaultNamespace("hotbar");
        ARMOR_BAR = Identifier.withDefaultNamespace("armor_bar");
        HEALTH_BAR = Identifier.withDefaultNamespace("health_bar");
        FOOD_BAR = Identifier.withDefaultNamespace("food_bar");
        AIR_BAR = Identifier.withDefaultNamespace("air_bar");
        MOUNT_HEALTH = Identifier.withDefaultNamespace("mount_health");
        INFO_BAR = Identifier.withDefaultNamespace("info_bar");
        EXPERIENCE_LEVEL = Identifier.withDefaultNamespace("experience_level");
        HELD_ITEM_TOOLTIP = Identifier.withDefaultNamespace("held_item_tooltip");
        SPECTATOR_TOOLTIP = Identifier.withDefaultNamespace("spectator_tooltip");
        MOB_EFFECTS = Identifier.withDefaultNamespace("mob_effects");
        BOSS_BAR = Identifier.withDefaultNamespace("boss_bar");
        SLEEP = Identifier.withDefaultNamespace("sleep");
        DEMO_TIMER = Identifier.withDefaultNamespace("demo_timer");
        SCOREBOARD = Identifier.withDefaultNamespace("scoreboard");
        OVERLAY_MESSAGE = Identifier.withDefaultNamespace("overlay_message");
        TITLE_AND_SUBTITLE = Identifier.withDefaultNamespace("title_and_subtitle");
        CHAT = Identifier.withDefaultNamespace("chat");
        PLAYER_LIST = Identifier.withDefaultNamespace("player_list");
        SUBTITLES = Identifier.withDefaultNamespace("subtitles");
    }
}
