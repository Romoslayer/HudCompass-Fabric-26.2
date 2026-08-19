package dev.gigaherz.hudcompass.waypoints;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** One added/updated waypoint from the waypoint editor GUI, paired with the world it belongs to. */
public record PointAddRemoveEntry(Identifier worldKey, PointInfo<?> point)
{
    public static void write(RegistryFriendlyByteBuf buffer, PointAddRemoveEntry entry)
    {
        buffer.writeIdentifier(entry.worldKey);
        PointInfo.DISPATCH_STREAM_CODEC.encode(buffer, entry.point);
    }

    public static PointAddRemoveEntry read(RegistryFriendlyByteBuf buffer)
    {
        Identifier worldKey = buffer.readIdentifier();
        PointInfo<?> point = PointInfo.DISPATCH_STREAM_CODEC.decode(buffer);
        return new PointAddRemoveEntry(worldKey, point);
    }
}
