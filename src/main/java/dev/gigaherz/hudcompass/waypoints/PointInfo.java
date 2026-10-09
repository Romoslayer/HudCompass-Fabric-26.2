package dev.gigaherz.hudcompass.waypoints;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gigaherz.hudcompass.icons.IIconData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import dev.gigaherz.hudcompass.network.codec.ByteBufCodecs;
import dev.gigaherz.hudcompass.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public abstract class PointInfo<T extends PointInfo<T>>
{
    public static final MapCodec<PointInfo<?>>
            DISPATCH_CODEC = PointInfoType.BY_ID_CODEC.dispatchMap(PointInfo::getType, type -> type.codec().codec());

    public static final StreamCodec<FriendlyByteBuf, PointInfo<?>>
            DISPATCH_STREAM_CODEC = PointInfoType.BY_ID_STREAM_CODEC.dispatch(PointInfo::getType, PointInfoType::streamCodec);

    /**
     * Stand-in for {@code Vec3.STREAM_CODEC}, which vanilla only added in 1.21.2 -- same wire
     * format (three doubles, x/y/z), shared by every waypoint type that sends a position.
     */
    public static final StreamCodec<FriendlyByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new
    );

    private final PointInfoType<? extends T> type;
    @Nullable
    private PointsOfInterest.WorldPoints owner;
    private UUID internalId;
    @Nullable
    private Component label;
    private IIconData<?> iconData;
    private boolean displayVerticalDistance = true;
    private boolean serverManaged = true; // whether a full multiplayer sync should replace this point
    private boolean isDynamic; // will not be saved to disk

    // For rendering purposes...
    public float fade;

    public PointInfo(PointInfoType<? extends T> type, boolean isDynamic)
    {
        this.isDynamic = isDynamic;
        this.type = type;
        this.internalId = UUID.randomUUID();
    }

    public PointInfo(PointInfoType<T> type, boolean isDynamic, @Nullable Component label, IIconData<?> iconData)
    {
        this(type, isDynamic);
        this.label = label;
        this.iconData = iconData;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected PointInfo(PointInfoType<T> type, boolean isDynamic, UUID internalId, Optional<Component> label, boolean displayVerticalDistance, IIconData<?> iconData)
    {
        this(type, isDynamic);
        this.displayVerticalDistance = displayVerticalDistance;
        this.internalId = internalId;
        this.label = label.orElse(null);
        this.iconData = iconData;
    }

    public PointInfoType<? extends T> getType()
    {
        return type;
    }

    @Nullable
    public final PointsOfInterest.WorldPoints getOwner()
    {
        return owner;
    }

    public UUID getInternalId()
    {
        return internalId;
    }

    public void setInternalId(UUID uuid)
    {
        internalId = uuid;
    }

    public abstract Vec3 getPosition(Player player, float partialTicks);

    @Nullable
    public Component getLabel()
    {
        return this.label;
    }

    public void setLabel(@Nullable Component text)
    {
        if (!Objects.equals(label, text))
            markDirty();
        this.label = text;
    }

    public IIconData<?> getIconData()
    {
        return iconData;
    }

    protected void setIconData(IIconData<?> iconData)
    {
        this.iconData = iconData;
    }

    @SuppressWarnings("unchecked")
    public T dynamic()
    {
        this.isDynamic = true;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public final T noVerticalDistance()
    {
        this.displayVerticalDistance = false;
        return (T) this;
    }

    public boolean displayVerticalDistance(@Nullable Player player)
    {
        return displayVerticalDistance;
    }

    public boolean isDynamic()
    {
        return isDynamic;
    }

    /**
     * Marks this point as purely client-local (e.g. a {@code JmWaypoint} mirroring a JourneyMap
     * waypoint), so a full multiplayer sync replacing the server-managed points must
     * not wipe it out from under whatever is maintaining it.
     */
    @SuppressWarnings("unchecked")
    public T clientPoint()
    {
        this.serverManaged = false;
        return (T) this;
    }

    public boolean isServerManaged()
    {
        return serverManaged;
    }

    public void tick(Player player)
    {
    }

    void setOwner(@Nullable PointsOfInterest.WorldPoints owner)
    {
        this.owner = owner;
    }

    public void markDirty()
    {
        if (owner != null)
        {
            owner.markDirty(this);
        }
    }

    protected static <T extends PointInfo<T>> Products.P4<RecordCodecBuilder.Mu<T>, UUID, Optional<Component>, Boolean, IIconData<?>>
    codecFragment(RecordCodecBuilder.Instance<T> i)
    {
        return i.group(
                UUIDUtil.CODEC.fieldOf("ic").forGetter(PointInfo::getInternalId),
                ExtraCodecs.COMPONENT.optionalFieldOf("label").forGetter(e -> Optional.ofNullable(e.getLabel())),
                Codec.BOOL.fieldOf("displayVerticalDistance").forGetter(e -> e.displayVerticalDistance(null)),
                IIconData.DISPATCH_CODEC.fieldOf("icon").forGetter(PointInfo::getIconData)
        );
    }
}
