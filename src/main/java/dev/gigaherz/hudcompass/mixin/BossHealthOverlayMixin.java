package dev.gigaherz.hudcompass.mixin;

import dev.gigaherz.hudcompass.client.HudOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the vanilla boss bar down 28px while the compass is visible, so the two don't overlap --
 * the same effect as upstream's NeoForge {@code RenderGuiLayerEvent.Pre}/{@code Post} hook on the
 * boss-overlay layer, and the 26.x build's {@code HudElementRegistry.replaceElement} wrapper.
 * <p>
 * 1.21.1's Fabric API has neither of those: no HUD layer registry at all (only
 * {@code HudRenderCallback}), and vanilla draws the boss bar from an anonymous
 * {@code LayeredDraw} lambda inside {@code Gui}, which isn't a stable mixin target. So this wraps
 * {@link BossHealthOverlay#render} itself -- that HUD layer is its only caller. The flag makes sure
 * the pop at RETURN always matches a push at HEAD, even if {@link HudOverlay#canRender()} would
 * answer differently by then.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin
{
    @Unique
    private boolean hudcompass$pushedDown;

    @Inject(method = "render", at = @At("HEAD"))
    private void hudcompass$pushDown(GuiGraphics graphics, CallbackInfo ci)
    {
        hudcompass$pushedDown = HudOverlay.INSTANCE.canRender();
        if (hudcompass$pushedDown)
        {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 28, 0);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void hudcompass$restore(GuiGraphics graphics, CallbackInfo ci)
    {
        if (hudcompass$pushedDown)
        {
            graphics.pose().popPose();
            hudcompass$pushedDown = false;
        }
    }
}
