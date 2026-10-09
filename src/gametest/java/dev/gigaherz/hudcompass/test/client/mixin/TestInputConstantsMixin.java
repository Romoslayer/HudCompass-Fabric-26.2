package dev.gigaherz.hudcompass.test.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grabbing or releasing the mouse warps the real system cursor, even for a window without focus.
 * The client test drives the game itself, so the user's cursor is left alone.
 */
@Mixin(InputConstants.class)
public abstract class TestInputConstantsMixin
{
    @Inject(method = "grabOrReleaseMouse", at = @At("HEAD"), cancellable = true)
    private static void hudcompassTest$leaveCursorAlone(CallbackInfo ci)
    {
        ci.cancel();
    }
}
