package dev.gigaherz.hudcompass.network;

import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointAddRemoveEntry;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import dev.gigaherz.hudcompass.network.codec.ByteBufCodecs;
import dev.gigaherz.hudcompass.network.codec.StreamCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sent client -> server: a bulk save from the waypoint editor GUI (see
 * {@code dev.gigaherz.hudcompass.client.ClientWaypointManagerScreen}).
 * <p>
 * Carries {@code PointInfo}, which needs {@code PointInfo.DISPATCH_STREAM_CODEC} (a
 * {@code FriendlyByteBuf}-typed codec) to encode -- following {@code SyncWaypointData}'s
 * established workaround for the same problem (see the shared porting-notes memory on
 * {@code StreamCodec.map()}/{@code .cast()} wildcard-capture failures) rather than a
 * {@code StreamCodec.composite} built from dispatch codecs directly: the payload is hand-encoded
 * into a plain {@code byte[]} up front, and decoded back out on demand via {@link #decode}.
 */
public record UpdateWaypointsFromGui(byte[] bytes) implements FabricPacket
{
    public static final ResourceLocation ID = HudCompass.location("update_waypoints_from_gui");
    public static final PacketType<UpdateWaypointsFromGui> TYPE = PacketType.create(ID, buf -> UpdateWaypointsFromGui.STREAM_CODEC.decode(buf));

    public static final StreamCodec<FriendlyByteBuf, UpdateWaypointsFromGui> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(1 << 20), UpdateWaypointsFromGui::bytes,
            UpdateWaypointsFromGui::new
    );

    @Override
    public PacketType<UpdateWaypointsFromGui> getType()
    {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf)
    {
        STREAM_CODEC.encode(buf, this);
    }

    public static UpdateWaypointsFromGui of(
            List<PointAddRemoveEntry> pointsAdded,
            List<PointAddRemoveEntry> pointsUpdated,
            List<UUID> pointsRemoved,
            RegistryAccess registryAccess)
    {
        var temp = new FriendlyByteBuf(Unpooled.buffer());
        temp.writeVarInt(pointsAdded.size());
        for (var entry : pointsAdded)
            PointAddRemoveEntry.write(temp, entry);
        temp.writeVarInt(pointsUpdated.size());
        for (var entry : pointsUpdated)
            PointAddRemoveEntry.write(temp, entry);
        temp.writeVarInt(pointsRemoved.size());
        for (var id : pointsRemoved)
            temp.writeUUID(id);
        var bytes = new byte[temp.readableBytes()];
        temp.readBytes(bytes, 0, bytes.length);
        return new UpdateWaypointsFromGui(bytes);
    }

    public record Decoded(List<PointAddRemoveEntry> pointsAdded, List<PointAddRemoveEntry> pointsUpdated, List<UUID> pointsRemoved) {}

    public Decoded decode(RegistryAccess registryAccess)
    {
        var temp = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));

        int addedCount = temp.readVarInt();
        List<PointAddRemoveEntry> pointsAdded = new ArrayList<>(addedCount);
        for (int i = 0; i < addedCount; i++)
            pointsAdded.add(PointAddRemoveEntry.read(temp));

        int updatedCount = temp.readVarInt();
        List<PointAddRemoveEntry> pointsUpdated = new ArrayList<>(updatedCount);
        for (int i = 0; i < updatedCount; i++)
            pointsUpdated.add(PointAddRemoveEntry.read(temp));

        int removedCount = temp.readVarInt();
        List<UUID> pointsRemoved = new ArrayList<>(removedCount);
        for (int i = 0; i < removedCount; i++)
            pointsRemoved.add(temp.readUUID());

        return new Decoded(pointsAdded, pointsUpdated, pointsRemoved);
    }
}
