package dev.gigaherz.hudcompass.icons;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridges this mod's sprite-name icon model onto 1.20.1's map decorations.
 * <p>
 * The other branches name every icon by sprite ({@link BasicIconData#spriteName}), because from
 * 1.20.5 vanilla stitches map decorations into a {@code map_decorations} atlas with one sprite per
 * decoration ({@code minecraft:red_x}, {@code minecraft:white_banner}, ...) -- and this mod's own
 * icons into it too. 1.20.1 predates that: decorations are a {@link MapDecoration.Type} enum,
 * drawn from cells of the single {@code textures/map/map_icons.png} sheet. Keeping the same sprite
 * names here means waypoint data (on disk and on the wire) has the same shape on every branch;
 * this table maps those names to and from the 1.20.1 enum, whose order matches the later sprite
 * list one-to-one.
 */
public final class MapDecorationSprites
{
    private MapDecorationSprites()
    {
    }

    private static final Map<MapDecoration.Type, ResourceLocation> SPRITES = new EnumMap<>(MapDecoration.Type.class);
    private static final Map<ResourceLocation, MapDecoration.Type> TYPES = new HashMap<>();

    static
    {
        register(MapDecoration.Type.PLAYER, "player");
        register(MapDecoration.Type.FRAME, "frame");
        register(MapDecoration.Type.RED_MARKER, "red_marker");
        register(MapDecoration.Type.BLUE_MARKER, "blue_marker");
        register(MapDecoration.Type.TARGET_X, "target_x");
        register(MapDecoration.Type.TARGET_POINT, "target_point");
        register(MapDecoration.Type.PLAYER_OFF_MAP, "player_off_map");
        register(MapDecoration.Type.PLAYER_OFF_LIMITS, "player_off_limits");
        register(MapDecoration.Type.MANSION, "woodland_mansion");
        register(MapDecoration.Type.MONUMENT, "ocean_monument");
        register(MapDecoration.Type.BANNER_WHITE, "white_banner");
        register(MapDecoration.Type.BANNER_ORANGE, "orange_banner");
        register(MapDecoration.Type.BANNER_MAGENTA, "magenta_banner");
        register(MapDecoration.Type.BANNER_LIGHT_BLUE, "light_blue_banner");
        register(MapDecoration.Type.BANNER_YELLOW, "yellow_banner");
        register(MapDecoration.Type.BANNER_LIME, "lime_banner");
        register(MapDecoration.Type.BANNER_PINK, "pink_banner");
        register(MapDecoration.Type.BANNER_GRAY, "gray_banner");
        register(MapDecoration.Type.BANNER_LIGHT_GRAY, "light_gray_banner");
        register(MapDecoration.Type.BANNER_CYAN, "cyan_banner");
        register(MapDecoration.Type.BANNER_PURPLE, "purple_banner");
        register(MapDecoration.Type.BANNER_BLUE, "blue_banner");
        register(MapDecoration.Type.BANNER_BROWN, "brown_banner");
        register(MapDecoration.Type.BANNER_GREEN, "green_banner");
        register(MapDecoration.Type.BANNER_RED, "red_banner");
        register(MapDecoration.Type.BANNER_BLACK, "black_banner");
        register(MapDecoration.Type.RED_X, "red_x");
    }

    private static void register(MapDecoration.Type type, String name)
    {
        ResourceLocation sprite = new ResourceLocation(name);
        SPRITES.put(type, sprite);
        TYPES.put(sprite, type);
    }

    /** The 1.20.5+-style sprite name for this decoration type. */
    public static ResourceLocation spriteFor(MapDecoration.Type type)
    {
        return SPRITES.get(type);
    }

    /** The vanilla decoration type a sprite name stands for, or null if it isn't one. */
    @Nullable
    public static MapDecoration.Type typeFor(ResourceLocation sprite)
    {
        return TYPES.get(sprite);
    }
}
