package dev.gigaherz.hudcompass.client;

import dev.gigaherz.hudcompass.network.ClientHello;
import dev.gigaherz.hudcompass.network.ServerHello;
import dev.gigaherz.hudcompass.network.SyncWaypointData;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Client-side half of multiplayer waypoint sharing: negotiates with {@code ServerWaypointSync}
 * (ServerHello -> ClientHello handshake) and applies the server's authoritative sync packets
 * into {@link PointsOfInterest#INSTANCE}. When the connected server never sends {@code
 * ServerHello} (no mod, or an old/incompatible version), {@code otherSideHasMod} simply stays
 * false and {@code ClientWaypointDatabase}'s local-disk fallback keeps handling persistence.
 */
public class ClientWaypointSync
{
    public static void init()
    {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                PointsOfInterest.INSTANCE.otherSideHasMod = false);

        ClientPlayNetworking.registerGlobalReceiver(ServerHello.TYPE, (payload, context) -> {
            PointsOfInterest.INSTANCE.otherSideHasMod = true;
            ClientPlayNetworking.send(ClientHello.INSTANCE);
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncWaypointData.TYPE, (payload, context) -> {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payload.bytes()), context.player().registryAccess());
            PointsOfInterest.INSTANCE.read(buffer);
        });
    }
}
