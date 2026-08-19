package dev.gigaherz.hudcompass.integrations.modmenu;

import dev.gigaherz.hudcompass.ConfigData;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth Config screen ModMenu opens for this mod -- see {@link HudCompassModMenuPlugin}
 * for why this is kept as a separate class (isolates the Cloth Config soft dependency so it's
 * only classloaded once presence has already been confirmed).
 * <p>
 * Field groupings and tooltip wording are carried over from upstream's {@code ModConfigSpec}
 * comments ({@code ClientConfig}'s "display" group and {@code CommonConfig}'s "general" group),
 * just re-homed into two Cloth Config categories -- this port already flattened the
 * client/common/server config split into one {@link ConfigData.Values}, so there's no structural
 * reason left to keep them apart here.
 * <p>
 * Binds directly against {@link ConfigData#getValues()}'s live fields rather than staging a
 * separate copy -- Cloth Config already snapshots each entry's original value for its own
 * cancel/confirm-discard flow, so a second copy here would be redundant. On save,
 * {@link ConfigData#refresh()} re-bakes the static fields the rest of the mod actually reads,
 * then {@link ConfigData#save()} writes {@code config/hudcompass.json}.
 */
public class HudCompassConfigScreen
{
    public static Screen create(Screen parent)
    {
        ConfigData.Values values = ConfigData.getValues();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.hudcompass.title"))
                .setSavingRunnable(() -> {
                    ConfigData.refresh();
                    ConfigData.save();
                });

        ConfigEntryBuilder entry = builder.entryBuilder();

        ConfigCategory display = builder.getOrCreateCategory(Component.translatable("config.hudcompass.category.display"));
        display.addEntry(entry.startBooleanToggle(Component.translatable("config.hudcompass.alwaysShowLabels"), values.alwaysShowLabels)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.hudcompass.alwaysShowLabels.tooltip"))
                .setSaveConsumer(v -> values.alwaysShowLabels = v)
                .build());
        display.addEntry(entry.startBooleanToggle(Component.translatable("config.hudcompass.alwaysShowFocusedLabel"), values.alwaysShowFocusedLabel)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.hudcompass.alwaysShowFocusedLabel.tooltip"))
                .setSaveConsumer(v -> values.alwaysShowFocusedLabel = v)
                .build());
        display.addEntry(entry.startBooleanToggle(Component.translatable("config.hudcompass.showAllLabelsOnSneak"), values.showAllLabelsOnSneak)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.hudcompass.showAllLabelsOnSneak.tooltip"))
                .setSaveConsumer(v -> values.showAllLabelsOnSneak = v)
                .build());
        display.addEntry(entry.startBooleanToggle(Component.translatable("config.hudcompass.animateLabels"), values.animateLabels)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.hudcompass.animateLabels.tooltip"))
                .setSaveConsumer(v -> values.animateLabels = v)
                .build());
        display.addEntry(entry.startEnumSelector(Component.translatable("config.hudcompass.displayWhen"), ConfigData.DisplayWhen.class, values.displayWhen)
                .setDefaultValue(ConfigData.DisplayWhen.HOLDING_COMPASS)
                .setEnumNameProvider(v -> Component.translatable("config.hudcompass.displayWhen.value." + v.name()))
                .setTooltip(Component.translatable("config.hudcompass.displayWhen.tooltip"))
                .setSaveConsumer(v -> values.displayWhen = v)
                .build());
        display.addEntry(entry.startDoubleField(Component.translatable("config.hudcompass.waypointFadeDistance"), values.waypointFadeDistance)
                .setDefaultValue(195.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.hudcompass.waypointFadeDistance.tooltip"))
                .setSaveConsumer(v -> values.waypointFadeDistance = v)
                .build());
        display.addEntry(entry.startDoubleField(Component.translatable("config.hudcompass.waypointViewDistance"), values.waypointViewDistance)
                .setDefaultValue(200.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.hudcompass.waypointViewDistance.tooltip"))
                .setSaveConsumer(v -> values.waypointViewDistance = v)
                .build());
        display.addEntry(entry.startBooleanToggle(Component.translatable("config.hudcompass.showLocatorBarWaypoints"), values.showLocatorBarWaypoints)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.hudcompass.showLocatorBarWaypoints.tooltip"))
                .setSaveConsumer(v -> values.showLocatorBarWaypoints = v)
                .build());
        display.addEntry(entry.startEnumSelector(Component.translatable("config.hudcompass.disableLocatorBarWhen"), ConfigData.DisableLocatorBarWhen.class, values.disableLocatorBarWhen)
                .setDefaultValue(ConfigData.DisableLocatorBarWhen.WHEN_VISIBLE)
                .setEnumNameProvider(v -> Component.translatable("config.hudcompass.disableLocatorBarWhen.value." + v.name()))
                .setTooltip(Component.translatable("config.hudcompass.disableLocatorBarWhen.tooltip"))
                .setSaveConsumer(v -> values.disableLocatorBarWhen = v)
                .build());

        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("config.hudcompass.category.general"));
        general.addEntry(entry.startBooleanToggle(Component.translatable("config.hudcompass.enableVanillaMapIntegration"), values.enableVanillaMapIntegration)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.hudcompass.enableVanillaMapIntegration.tooltip"))
                .setSaveConsumer(v -> values.enableVanillaMapIntegration = v)
                .build());
        general.addEntry(entry.startBooleanToggle(Component.translatable("config.hudcompass.enableSpawnPointWaypoint"), values.enableSpawnPointWaypoint)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.hudcompass.enableSpawnPointWaypoint.tooltip"))
                .setSaveConsumer(v -> values.enableSpawnPointWaypoint = v)
                .build());
        general.addEntry(entry.startBooleanToggle(Component.translatable("config.hudcompass.enableJourneymapIntegration"), values.enableJourneymapIntegration)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.hudcompass.enableJourneymapIntegration.tooltip"))
                .setSaveConsumer(v -> values.enableJourneymapIntegration = v)
                .build());
        general.addEntry(entry.startEnumSelector(Component.translatable("config.hudcompass.playerDisplay"), ConfigData.PlayerDisplay.class, values.playerDisplay)
                .setDefaultValue(ConfigData.PlayerDisplay.TEAM)
                .setEnumNameProvider(v -> Component.translatable("config.hudcompass.playerDisplay.value." + v.name()))
                .setTooltip(Component.translatable("config.hudcompass.playerDisplay.tooltip"))
                .setSaveConsumer(v -> values.playerDisplay = v)
                .build());
        general.addEntry(entry.startBooleanToggle(Component.translatable("config.hudcompass.disableServerHello"), values.disableServerHello)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.hudcompass.disableServerHello.tooltip"))
                .setSaveConsumer(v -> values.disableServerHello = v)
                .build());

        return builder.build();
    }
}
