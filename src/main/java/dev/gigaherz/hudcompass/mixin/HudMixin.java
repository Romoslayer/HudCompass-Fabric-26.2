package dev.gigaherz.hudcompass.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.client.HudOverlay;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Upstream (targeting MC 26.1.2) mixed into {@code Gui#nextContextualInfoState}. In real MC
 * 26.2, that method (and its {@code ContextualInfo} enum) moved from {@code Gui} to a new
 * {@code Hud} class as part of the same Gui/Hud split documented elsewhere in this port --
 * verified via javap/decompile against the actual 26.2 jar, not assumed. The
 * {@code ClientWaypointManager#hasWaypoints()} call site inside it is still the sole call to
 * that method in the target method body, so the implicit ordinal=0 match is still safe.
 * <p>
 * Lives in the {@code client}-only mixin list (not the unconditional one): {@code Hud} itself is
 * a client-only vanilla class, so on a dedicated server -- now that the mod's environment is "*"
 * for multiplayer waypoint sync -- this target wouldn't even exist to resolve.
 */
@Mixin(Hud.class)
public class HudMixin
{
    @ModifyExpressionValue(method = "nextContextualInfoState()Lnet/minecraft/client/gui/Hud$ContextualInfo;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/waypoints/ClientWaypointManager;hasWaypoints()Z"))
    private static boolean hudcompass$suppressLocatorBar(boolean canDisplayLocatorBar)
    {
        return canDisplayLocatorBar && shouldShowLocatorBar();
    }

    private static boolean shouldShowLocatorBar()
    {
        return switch (ConfigData.disableLocatorBarWhen)
        {
            case NEVER -> true;
            case WHEN_VISIBLE -> !HudOverlay.INSTANCE.canRender();
            case ALWAYS -> false;
        };
    }
}
