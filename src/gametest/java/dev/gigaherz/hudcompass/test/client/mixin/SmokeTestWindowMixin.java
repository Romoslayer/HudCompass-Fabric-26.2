package dev.gigaherz.hudcompass.test.client.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.gigaherz.hudcompass.test.client.HudCompassClientSmokeTest;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smoke-test runs only: the game window is created hidden and unfocused, moved to a secondary
 * monitor, then shown without taking focus -- so the run never steals the user's focus or appears
 * on the monitor they're working on. Vanilla 1.20.1 creates the window visible and focused on the
 * primary monitor.
 */
@Mixin(Window.class)
public abstract class SmokeTestWindowMixin
{
    @Shadow
    @Final
    private long window;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private void hudcompassTest$createHiddenAndUnfocused(CallbackInfo ci)
    {
        if (!HudCompassClientSmokeTest.ENABLED)
            return;
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void hudcompassTest$showOnSecondaryMonitor(CallbackInfo ci)
    {
        if (!HudCompassClientSmokeTest.ENABLED)
            return;

        long primary = GLFW.glfwGetPrimaryMonitor();
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors != null)
        {
            for (int i = 0; i < monitors.limit(); i++)
            {
                long monitor = monitors.get(i);
                if (monitor == primary)
                    continue;
                int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
                GLFW.glfwGetMonitorWorkarea(monitor, x, y, w, h);
                GLFW.glfwSetWindowPos(window, x[0] + 40, y[0] + 60);
                break;
            }
        }
        GLFW.glfwShowWindow(window);
    }
}
