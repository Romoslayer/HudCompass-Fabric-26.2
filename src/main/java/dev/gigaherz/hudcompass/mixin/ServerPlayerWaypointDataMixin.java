package dev.gigaherz.hudcompass.mixin;

import dev.gigaherz.hudcompass.server.ServerWaypointSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persists each player's server-side waypoint store (see {@code ServerWaypointSync}) across
 * relogs and server restarts by piggybacking on {@code ServerPlayer}'s own save data. Fabric has
 * no equivalent to NeoForge's serializable {@code AttachmentType}, which upstream uses for this.
 * <p>
 * Deliberately mixed into the unconditional {@code mixins} list (not the {@code server}-only
 * one): a client process hosting a singleplayer world still runs a real {@code ServerPlayer}
 * internally, and Fabric's side-gated mixin lists are keyed off physical environment, not
 * whether the target class happens to be loaded -- a {@code server}-only mixin here would never
 * apply to that integrated-server player.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerWaypointDataMixin
{
    @Unique
    private static final String HUDCOMPASS_TAG = "hudcompass";

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void hudcompass$save(ValueOutput output, CallbackInfo ci)
    {
        ServerWaypointSync.get((ServerPlayer) (Object) this).serialize(output.child(HUDCOMPASS_TAG));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void hudcompass$load(ValueInput input, CallbackInfo ci)
    {
        input.child(HUDCOMPASS_TAG).ifPresent(child ->
                ServerWaypointSync.get((ServerPlayer) (Object) this).deserialize(child));
    }
}
