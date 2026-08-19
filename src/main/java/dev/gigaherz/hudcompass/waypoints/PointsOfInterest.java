package dev.gigaherz.hudcompass.waypoints;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Waypoint store: one client-side singleton ({@link #INSTANCE}) representing the local player's
 * view, plus one server-side instance per online player (see
 * {@code dev.gigaherz.hudcompass.server.ServerWaypointSync}), replacing upstream's NeoForge
 * {@code AttachmentType} attached to the player. Fabric has no attachment-API equivalent, so
 * server-side persistence instead piggybacks on {@code ServerPlayer}'s own save data via a mixin,
 * and instances are looked up by player UUID rather than held as attachment data.
 * <p>
 * When a connected server doesn't have the mod ({@link #otherSideHasMod} stays false), the client
 * singleton falls back to {@code ClientWaypointDatabase}'s local disk persistence instead.
 */
public class PointsOfInterest
{
    public static final PointsOfInterest INSTANCE = new PointsOfInterest();

    private PointInfo<?> targetted;

    public int changeNumber;
    public int savedNumber;

    /**
     * Separate from {@link #changeNumber}: that one only counts non-dynamic points (the ones
     * worth persisting), so a dynamic-only change (e.g. {@code PlayerTracker} adding/removing a
     * nearby-player waypoint) wouldn't otherwise trigger a resync. This counts every add/remove/
     * dirty mark unconditionally, since a connected client needs to learn about dynamic points
     * changing too, even though they're never saved.
     */
    public int syncNumber;
    public int syncedNumber;

    /**
     * Whether the other side of the connection (server, from the client's view; that connected
     * player's client, from the server's view) also has the mod and understands the sync packets.
     * Gates {@code addPointRequest}/{@code removePointRequest} routing and whether sync packets
     * get sent/expected at all.
     */
    public boolean otherSideHasMod = false;

    private final Map<Identifier, Object> addonData = Maps.newHashMap();

    @SuppressWarnings("unchecked")
    public <T> T getOrCreateAddonData(Identifier addonId, Supplier<T> factory)
    {
        return (T) addonData.computeIfAbsent(addonId, key -> factory.get());
    }

    public Collection<WorldPoints> getAllWorlds()
    {
        return Collections.unmodifiableCollection(perWorld.values());
    }

    private final Map<ResourceKey<Level>, WorldPoints> perWorld = Maps.newHashMap();

    public void serialize(ValueOutput valueOutput)
    {
        var list = valueOutput.childrenList("Worlds");
        for (Map.Entry<ResourceKey<Level>, WorldPoints> entry : perWorld.entrySet())
        {
            var element = list.addChild();
            element.putString("World", entry.getKey().identifier().toString());
            entry.getValue().write(element.childrenList("POIs"));
        }
    }

    public void deserialize(ValueInput valueInput)
    {
        perWorld.clear();
        for (var element : valueInput.childrenListOrEmpty("Worlds"))
        {
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(element.getString("World").orElseThrow()));
            WorldPoints p = get(key);
            p.read(element.childrenList("POIs").orElseThrow());
        }
        savedNumber = changeNumber = syncNumber = 0;
    }

    /** Full-state wire format for multiplayer sync -- see {@code SyncWaypointData}. */
    public void write(RegistryFriendlyByteBuf buffer)
    {
        buffer.writeVarInt(perWorld.size());
        for (Map.Entry<ResourceKey<Level>, WorldPoints> entry : perWorld.entrySet())
        {
            buffer.writeIdentifier(entry.getKey().identifier());
            entry.getValue().write(buffer);
        }
    }

    /**
     * Full-state wire format for multiplayer sync -- see {@code SyncWaypointData}. Clears
     * server-managed points from every already-known world first, not just the ones the incoming
     * payload happens to mention -- otherwise a sync telling the client "you have zero
     * server-side waypoints" (an empty payload) would leave stale client-local-disk-loaded
     * points sitting untouched in worlds the payload never bothered to list.
     */
    public void read(RegistryFriendlyByteBuf buffer)
    {
        perWorld.values().forEach(w -> w.points.values().removeIf(PointInfo::isServerManaged));

        int numWorlds = buffer.readVarInt();
        for (int i = 0; i < numWorlds; i++)
        {
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, buffer.readIdentifier());
            get(key).read(buffer);
        }
        savedNumber = changeNumber = syncNumber = syncedNumber = 0;
    }

    public void clear()
    {
        perWorld.values().forEach(WorldPoints::clear);
        perWorld.clear();
    }

    public void setTargetted(@Nullable PointInfo<?> targetted)
    {
        this.targetted = targetted;
    }

    public PointInfo<?> getTargetted()
    {
        return targetted;
    }

    public void tick(Player player)
    {
        WorldPoints points = get(player.level().dimension());
        points.tick(player);
    }

    public WorldPoints get(Level world)
    {
        return get(world.dimension());
    }

    public WorldPoints get(ResourceKey<Level> worldKey)
    {
        return perWorld.computeIfAbsent(Objects.requireNonNull(worldKey), WorldPoints::new);
    }

    /** Applies a bulk save from the waypoint editor GUI -- see {@code UpdateWaypointsFromGui}. */
    public void applyUpdatesFromGui(
            List<PointAddRemoveEntry> pointsAdded,
            List<PointAddRemoveEntry> pointsUpdated,
            List<UUID> pointsRemoved)
    {
        for (UUID id : pointsRemoved)
        {
            for (WorldPoints world : getAllWorlds())
                world.removePoint(id);
        }
        for (var entry : pointsAdded)
        {
            get(ResourceKey.create(Registries.DIMENSION, entry.worldKey())).addPoint(entry.point());
        }
        for (var entry : pointsUpdated)
        {
            get(ResourceKey.create(Registries.DIMENSION, entry.worldKey())).addPoint(entry.point());
        }
    }

    public class WorldPoints
    {
        private final ResourceKey<Level> worldKey;
        private final Map<UUID, PointInfo<?>> points = Maps.newHashMap();

        public WorldPoints(ResourceKey<Level> worldKey)
        {
            this.worldKey = worldKey;
        }

        public Collection<PointInfo<?>> getPoints()
        {
            return points.values();
        }

        private void tick(Player player)
        {
            for (PointInfo<?> point : points.values())
            {
                point.tick(player);
            }

            PointInfo<?> closest = null;
            double closestAngle = Double.POSITIVE_INFINITY;
            for (PointInfo<?> point : points.values())
            {
                Vec3 direction = point.getPosition(player, 1.0f).subtract(player.position());
                Vec3 look = player.getLookAngle();
                direction = direction.normalize();
                look = look.normalize();
                double dot = direction.x * look.x + direction.z * look.z;
                double m1 = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
                double m2 = Math.sqrt(look.x * look.x + look.z * look.z);
                double angle = Math.abs(Math.acos(dot / (m1 * m2)));
                if (angle < closestAngle)
                {
                    closest = point;
                    closestAngle = angle;
                }
            }

            if (closest != null && closestAngle < Math.toRadians(15))
            {
                setTargetted(closest);
            }
            else
            {
                setTargetted(null);
            }
        }

        public void addPoint(PointInfo<?> point)
        {
            point.setOwner(this);
            PointInfo<?> oldPoint = points.put(point.getInternalId(), point);
            if (oldPoint != null)
            {
                oldPoint.setOwner(null);
            }
            syncNumber++;
            if (!point.isDynamic())
                changeNumber++;
        }

        public void removePoint(PointInfo<?> point)
        {
            removePoint(point.getInternalId());
        }

        public void removePoint(UUID id)
        {
            PointInfo<?> point = points.get(id);
            if (point != null)
            {
                point.setOwner(null);
                points.remove(point.getInternalId());
                syncNumber++;
                if (!point.isDynamic())
                    changeNumber++;
            }
        }

        public void clear()
        {
            boolean nonDynamic = points.values().stream().anyMatch(point -> !point.isDynamic());
            boolean any = !points.isEmpty();
            points.clear();
            if (any)
                syncNumber++;
            if (nonDynamic)
                changeNumber++;
        }

        public void markDirty(PointInfo<?> point)
        {
            syncNumber++;
            if (!point.isDynamic())
                changeNumber++;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void write(ValueOutput.ValueOutputList outputList)
        {
            for (PointInfo point : points.values())
            {
                if (!point.isDynamic())
                {
                    var child = outputList.addChild();
                    child.store(PointInfo.DISPATCH_CODEC, point);
                }
            }
        }

        public void read(ValueInput.ValueInputList list)
        {
            points.clear();
            for (var child : list)
            {
                var pointOpt = child.read(PointInfo.DISPATCH_CODEC);
                pointOpt.ifPresent(point -> points.put(point.getInternalId(), point));
            }
        }

        /** Full-state wire format for multiplayer sync -- see {@code SyncWaypointData}. */
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void write(RegistryFriendlyByteBuf buffer)
        {
            buffer.writeVarInt(points.size());
            for (PointInfo point : points.values())
            {
                PointInfo.DISPATCH_STREAM_CODEC.encode(buffer, point);
            }
        }

        /**
         * Full-state wire format for multiplayer sync -- see {@code SyncWaypointData}. Only
         * clears out points already known to be server-managed, so purely client-local points
         * (e.g. {@code LocatorBarPoints} adapters mirroring vanilla waypoints) survive a sync.
         */
        public void read(RegistryFriendlyByteBuf buffer)
        {
            points.values().removeIf(PointInfo::isServerManaged);
            int numPoints = buffer.readVarInt();
            for (int i = 0; i < numPoints; i++)
            {
                PointInfo<?> point = PointInfo.DISPATCH_STREAM_CODEC.decode(buffer);
                point.setOwner(this);
                points.put(point.getInternalId(), point);
            }
        }

        public ResourceKey<Level> getWorldKey()
        {
            return worldKey;
        }

        public Optional<PointInfo<?>> find(UUID id)
        {
            return Optional.ofNullable(points.get(id));
        }
    }
}
