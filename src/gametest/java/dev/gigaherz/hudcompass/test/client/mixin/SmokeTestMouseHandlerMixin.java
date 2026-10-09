package dev.gigaherz.hudcompass.test.client.mixin;

import dev.gigaherz.hudcompass.test.client.HudCompassClientSmokeTest;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smoke-test runs only: never grab (or warp) the cursor. Vanilla already skips the grab while
 * the window is inactive, which it stays for the whole run; this is the backstop in case the
 * window does end up activated (e.g. the user clicks it).
 */
@Mixin(MouseHandler.class)
public abstract class SmokeTestMouseHandlerMixin
{
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void hudcompassTest$neverGrab(CallbackInfo ci)
    {
        if (HudCompassClientSmokeTest.ENABLED)
            ci.cancel();
    }
}
