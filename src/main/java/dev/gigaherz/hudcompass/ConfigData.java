package dev.gigaherz.hudcompass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple Gson-backed config replacing upstream's NeoForge {@code ModConfigSpec}.
 * {@code config/hudcompass.json} can still be edited directly, and is the only option if neither
 * ModMenu nor Cloth Config is installed -- otherwise, see
 * {@code dev.gigaherz.hudcompass.integrations.modmenu} for the in-game config screen, a soft
 * dependency on both.
 */
public class ConfigData
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hudcompass.json");

    public enum DisplayWhen
    {
        NEVER,
        HOLDING_COMPASS,
        HAS_COMPASS,
        ALWAYS
    }

    public enum DisableLocatorBarWhen
    {
        NEVER,
        WHEN_VISIBLE,
        ALWAYS
    }

    public enum PlayerDisplay
    {
        NONE,
        TEAM,
        ALL
    }

    public static class Values
    {
        public boolean alwaysShowLabels = false;
        public boolean alwaysShowFocusedLabel = true;
        public boolean showAllLabelsOnSneak = true;
        public boolean animateLabels = true;
        public DisplayWhen displayWhen = DisplayWhen.HOLDING_COMPASS;
        public double waypointFadeDistance = 195.0;
        public double waypointViewDistance = 200.0;
        public boolean showLocatorBarWaypoints = true;
        public DisableLocatorBarWhen disableLocatorBarWhen = DisableLocatorBarWhen.WHEN_VISIBLE;

        public boolean enableVanillaMapIntegration = true;
        public boolean enableSpawnPointWaypoint = true;
        public boolean enableJourneymapIntegration = true;
        public PlayerDisplay playerDisplay = PlayerDisplay.TEAM;

        // Server-side: set on a hosted world/server to opt out of the multiplayer waypoint-sync
        // handshake (ServerHello/ClientHello/SyncWaypointData) entirely.
        public boolean disableServerHello = false;
    }

    private static Values values = new Values();

    // Baked config data, for performance -- mirrors upstream's static-field pattern.
    public static boolean alwaysShowLabels;
    public static boolean alwaysShowFocusedLabel;
    public static boolean showAllLabelsOnSneak;
    public static boolean animateLabels;
    public static DisplayWhen displayWhen;
    public static double waypointViewDistance;
    public static double waypointFadeDistance;
    public static boolean showLocatorBarWaypoints;
    public static DisableLocatorBarWhen disableLocatorBarWhen;
    public static boolean enableVanillaMapIntegration;
    public static boolean enableSpawnPointWaypoint;
    public static boolean enableJourneymapIntegration;
    public static PlayerDisplay playerDisplay;
    public static boolean disableServerHello;

    public static void load()
    {
        if (Files.exists(PATH))
        {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8))
            {
                Values loaded = GSON.fromJson(reader, Values.class);
                if (loaded != null)
                    values = loaded;
            }
            catch (IOException e)
            {
                LOGGER.error("Failed to load hudcompass.json, using defaults", e);
            }
        }
        refresh();
        save();
    }

    public static void save()
    {
        try
        {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8))
            {
                GSON.toJson(values, writer);
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Failed to save hudcompass.json", e);
        }
    }

    /**
     * The live, mutable backing values -- exposed for the ModMenu/Cloth Config integration
     * (a soft dependency, see {@code integrations.modmenu}) to bind its widgets directly against,
     * the same object {@link #load()}/{@link #save()} read and write.
     */
    public static Values getValues()
    {
        return values;
    }

    /**
     * Re-bakes the static fields from {@link #getValues()}. Public so the config screen can call
     * it after the player edits values in-place, before {@link #save()} -- mirrors what
     * {@link #load()} already does for itself.
     */
    public static void refresh()
    {
        alwaysShowLabels = values.alwaysShowLabels;
        alwaysShowFocusedLabel = values.alwaysShowFocusedLabel;
        showAllLabelsOnSneak = values.showAllLabelsOnSneak;
        animateLabels = values.animateLabels;
        displayWhen = values.displayWhen;
        waypointFadeDistance = values.waypointFadeDistance;
        waypointViewDistance = values.waypointViewDistance;
        showLocatorBarWaypoints = values.showLocatorBarWaypoints;
        disableLocatorBarWhen = values.disableLocatorBarWhen;
        enableVanillaMapIntegration = values.enableVanillaMapIntegration;
        enableSpawnPointWaypoint = values.enableSpawnPointWaypoint;
        enableJourneymapIntegration = values.enableJourneymapIntegration;
        playerDisplay = values.playerDisplay;
        disableServerHello = values.disableServerHello;
    }
}
