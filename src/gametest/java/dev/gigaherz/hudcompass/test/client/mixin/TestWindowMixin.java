package dev.gigaherz.hudcompass.test.client.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client test runs must not get in the way of whatever is being done on the machine meanwhile: the
 * window is created hidden and unfocused, moved to a secondary monitor when there is one, and only
 * then shown -- without taking focus. 26.2 still creates its window through GLFW (26.3 moved to
 * SDL; that branch has its own version of this mixin).
 */
@Mixin(Window.class)
public abstract class TestWindowMixin
{
    @Shadow
    @Final
    private long handle;

    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private static void hudcompassTest$hiddenAndUnfocused(CallbackInfoReturnable<Long> cir)
    {
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void hudcompassTest$showOnSecondaryMonitor(CallbackInfo ci)
    {
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
                GLFW.glfwSetWindowPos(handle, x[0] + 40, y[0] + 60);
                break;
            }
        }
        GLFW.glfwShowWindow(handle);
    }
}
