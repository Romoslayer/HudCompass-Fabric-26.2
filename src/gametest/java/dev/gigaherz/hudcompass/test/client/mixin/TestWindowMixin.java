package dev.gigaherz.hudcompass.test.client.mixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.MonitorManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.mojang.renderpearl.api.device.GpuBackend;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

/**
 * Client test runs must not get in the way of whatever is being done on the machine meanwhile: the
 * window is created hidden and can never take focus (so it can't take the keyboard or the mouse
 * either), is placed on a secondary display when there is one, and only then shown. 26.3 creates
 * its window through SDL (26.2 still uses GLFW; that branch has its own version of this mixin).
 * Same approach as the user's FSRDLSS test harness.
 */
@Mixin(Window.class)
public abstract class TestWindowMixin
{
    @Shadow
    @Final
    private long handle;

    @ModifyArg(method = "createWindow", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), index = 3)
    private long hudcompassTest$hiddenAndUnfocusable(long flags)
    {
        return flags | SDLVideo.SDL_WINDOW_HIDDEN | SDLVideo.SDL_WINDOW_NOT_FOCUSABLE;
    }

    @ModifyArg(method = "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;ZLjava/lang/String;Lcom/mojang/blaze3d/platform/MonitorManager;Lcom/mojang/renderpearl/api/device/GpuBackend;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/MonitorManager;getMonitor(I)Lcom/mojang/blaze3d/platform/Monitor;"))
    private int hudcompassTest$secondaryDisplay(int primary)
    {
        IntBuffer displays = SDLVideo.SDL_GetDisplays();
        if (displays == null)
            return primary;
        try
        {
            for (int i = 0; i < displays.remaining(); i++)
            {
                if (displays.get(i) != primary)
                    return displays.get(i);
            }
            return primary;
        }
        finally
        {
            SDLStdinc.SDL_free(displays);
        }
    }

    @Inject(method = "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;ZLjava/lang/String;Lcom/mojang/blaze3d/platform/MonitorManager;Lcom/mojang/renderpearl/api/device/GpuBackend;I)V",
            at = @At("RETURN"))
    private void hudcompassTest$show(WindowEventHandler eventHandler, DisplayData displayData, String fullscreenVideoMode,
                                     boolean exclusiveFullscreen, String title, MonitorManager monitorManager,
                                     GpuBackend backend, int maximumSize, CallbackInfo ci)
    {
        SDLVideo.SDL_ShowWindow(handle);
    }
}
