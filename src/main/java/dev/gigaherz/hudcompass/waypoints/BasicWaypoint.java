package dev.gigaherz.hudcompass.waypoints;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class BasicWaypoint extends PointInfo<BasicWaypoint>
{
    public static final MapCodec<BasicWaypoint> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecFragment(instance).and(Vec3.CODEC.fieldOf("pos").forGetter(BasicWaypoint::getPosition)).apply(instance, BasicWaypoint::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BasicWaypoint> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PointInfo::getInternalId,
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), e -> Optional.ofNullable(e.getLabel()),
            ByteBufCodecs.BOOL, e -> e.displayVerticalDistance(null),
            IIconData.DISPATCH_STREAM_CODEC, BasicWaypoint::getIconData,
            Vec3.STREAM_CODEC, BasicWaypoint::getPosition,
            ByteBufCodecs.BOOL, PointInfo::isDynamic,
            BasicWaypoint::new
    );

    private Vec3 position;

    public BasicWaypoint(Vec3 exactPosition, @Nullable String label, IIconData<?> iconData)
    {
        this(HudCompass.BASIC_WAYPOINT_TYPE, exactPosition, label != null ? Component.literal(label) : null, iconData);
    }

    protected BasicWaypoint(PointInfoType<BasicWaypoint> type, Vec3 exactPosition, @Nullable String label, IIconData<?> iconData)
    {
        this(type, exactPosition, label != null ? Component.literal(label) : null, iconData);
    }

    protected BasicWaypoint(PointInfoType<BasicWaypoint> type, Vec3 exactPosition, @Nullable Component label, IIconData<?> iconData)
    {
        super(type, false, label, iconData);
        this.position = exactPosition;
    }

    /**
     * NBT-decode constructor ({@link #CODEC}), always {@code isDynamic=false} -- correctly so,
     * since dynamic points are never written to NBT in the first place (see
     * {@code PointsOfInterest.WorldPoints#write(ValueOutput.ValueOutputList)}), so this path never
     * actually needs to reconstruct a dynamic one. Kept separate from the network-decode
     * constructor below specifically because that one's hardcoded {@code false} was the actual bug
     * -- see its own javadoc.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected BasicWaypoint(UUID internalId, Optional<Component> label, boolean displayVerticalDistance, IIconData<?> iconData, Vec3 exactPosition)
    {
        this(internalId, label, displayVerticalDistance, iconData, exactPosition, false);
    }

    /**
     * Network-decode constructor ({@link #STREAM_CODEC}). {@code isDynamic} is a real, encoded
     * field here (unlike upstream's original, which hardcodes {@code false} on this exact
     * constructor) -- {@code SpawnPointPoints}' "Home" marker is the one {@code BasicWaypoint}
     * that's genuinely created {@link #dynamic()}, and upstream's hardcoded {@code false} silently
     * dropped that flag on every sync to a client, making the auto-generated Home marker look like
     * (and behave as) an ordinary, user-editable waypoint once it arrived there -- including
     * showing up in the waypoint editor's list, which is only ever supposed to show real,
     * user-owned waypoints. Deleting it there would appear to work, only for
     * {@code SpawnPointPoints}' own self-healing re-add logic (see that class) to silently
     * recreate it within about a second, since from the server's perspective nothing about the
     * actual spawn point had changed -- confirmed via live testing, repeatable on demand.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected BasicWaypoint(UUID internalId, Optional<Component> label, boolean displayVerticalDistance, IIconData<?> iconData, Vec3 exactPosition, boolean isDynamic)
    {
        super(HudCompass.BASIC_WAYPOINT_TYPE, isDynamic, internalId, label, displayVerticalDistance, iconData);
        this.position = exactPosition;
    }

    public Vec3 getPosition()
    {
        return position;
    }

    @Override
    public Vec3 getPosition(Player player, float partialTicks)
    {
        return position;
    }

    public void setPosition(Vec3 position)
    {
        if (!Mth.equal(this.position.distanceToSqr(position), 0))
        {
            this.position = position;
            markDirty();
        }
    }
}
