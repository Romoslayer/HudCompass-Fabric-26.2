package dev.gigaherz.hudcompass.integrations.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Provides the in-game config screen ModMenu shows for this mod's gear icon -- replaces the
 * MVP's "edit {@code config/hudcompass.json} by hand" note (see {@link dev.gigaherz.hudcompass.ConfigData}).
 * <p>
 * <b>Soft dependency, loaded lazily by design</b>, same pattern as
 * {@link dev.gigaherz.hudcompass.integrations.journeymap.HudCompassJourneymapPlugin}: registered
 * via the {@code "modmenu"} entrypoint in {@code fabric.mod.json}, which Fabric Loader only
 * resolves when ModMenu itself asks for entrypoints under that key -- so if ModMenu isn't
 * installed, this class is never loaded at all.
 * <p>
 * The actual screen is built by {@link HudCompassConfigScreen}, a separate class that references
 * Cloth Config types -- Cloth Config is a <i>second</i>, independent soft dependency (ModMenu
 * provides the "Configure" button, Cloth Config provides the widgets it opens). Deliberately kept
 * out of this class: this method only calls into {@link HudCompassConfigScreen} -- which triggers
 * classloading of Cloth Config's types -- once {@link FabricLoader#isModLoaded} has already
 * confirmed Cloth Config is present. If it isn't, a factory that returns {@code null} is returned
 * instead -- exactly what {@link ModMenuApi}'s own default implementation does in ModMenu 7.x,
 * and ModMenu treats a null screen as "nothing to configure" rather than opening a broken one.
 * (The 26.x build returns ModMenu's {@code NullScreenFactory} sentinel here; that type doesn't
 * exist yet in ModMenu 7.x, the version 1.20.1 uses.)
 */
public class HudCompassModMenuPlugin implements ModMenuApi
{
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory()
    {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config"))
            return parent -> null;

        return HudCompassConfigScreen::create;
    }
}
