package dev.gigaherz.hudcompass.test;

import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.integrations.server.PlayerTracker;
import dev.gigaherz.hudcompass.integrations.server.VanillaMapPoints;
import dev.gigaherz.hudcompass.network.SyncWaypointData;
import dev.gigaherz.hudcompass.network.UpdateWaypointsFromGui;
import dev.gigaherz.hudcompass.server.ServerWaypointSync;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointAddRemoveEntry;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Headless checks for the server half of the 1.20.1 backport: run with {@code gradlew runGameTest}
 * (dev names) and {@code gradlew runProductionGameTest} (the remapped jar in a real Fabric server).
 * Each test gets its own batch so tests that change the shared {@link ConfigData} statics, or add
 * players that the per-player trackers then see, never overlap.
 */
public final class HudCompassGameTests implements FabricGameTest
{
    // ---------------------------------------------------------------------------------------
    // Persistence: ServerPlayerWaypointDataMixin + the CompoundTag/NbtOps rewrite of
    // PointsOfInterest's disk format (26.x used ValueOutput/ValueInput, absent on 1.20.1).
    // ---------------------------------------------------------------------------------------

    @GameTest(template = EMPTY_STRUCTURE, batch = "hudcompass_save")
    public void playerSaveDataRoundTrip(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.survival(server, "SaveTester");
        try
        {
            PointsOfInterest points = ServerWaypointSync.get(player);
            BasicWaypoint base = new BasicWaypoint(new Vec3(10.5, 64, -20.25), "Base", BasicIconData.basic("home"));
            BasicWaypoint nether = new BasicWaypoint(new Vec3(1, 2, 3), null, BasicIconData.mapDecoration("player_off_limits"));
            BasicWaypoint transient_ = new BasicWaypoint(new Vec3(5, 5, 5), "Transient", BasicIconData.generic()).dynamic();
            points.get(Level.OVERWORLD).addPoint(base);
            points.get(Level.NETHER).addPoint(nether);
            points.get(Level.OVERWORLD).addPoint(transient_);

            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            helper.assertTrue(saved.contains("hudcompass", Tag.TAG_COMPOUND),
                    "ServerPlayer save data has no 'hudcompass' compound -- the save mixin did not run");

            points.clear();
            player.load(saved);

            List<PointInfo<?>> overworld = persistent(points.get(Level.OVERWORLD));
            helper.assertTrue(overworld.size() == 1, "expected 1 saved overworld waypoint after reload, got " + overworld.size());
            BasicWaypoint loaded = (BasicWaypoint) overworld.get(0);
            helper.assertTrue(loaded.getInternalId().equals(base.getInternalId()), "overworld waypoint id changed across save/load");
            helper.assertTrue(labelOf(loaded).equals("Base"), "overworld label was '" + labelOf(loaded) + "'");
            helper.assertTrue(loaded.getPosition().equals(base.getPosition()), "overworld position was " + loaded.getPosition());
            helper.assertTrue(spriteOf(loaded).equals(new ResourceLocation("hudcompass", "home")), "overworld icon was " + spriteOf(loaded));
            helper.assertTrue(loaded.getOwner() == points.get(Level.OVERWORLD), "a point loaded from disk has no owner, so later edits wouldn't mark it dirty");

            List<PointInfo<?>> netherPoints = persistent(points.get(Level.NETHER));
            helper.assertTrue(netherPoints.size() == 1, "expected 1 saved nether waypoint after reload, got " + netherPoints.size());
            helper.assertTrue(netherPoints.get(0).getLabel() == null, "an unlabelled waypoint came back with a label");
            helper.assertTrue(spriteOf(netherPoints.get(0)).equals(new ResourceLocation("player_off_limits")), "nether icon was " + spriteOf(netherPoints.get(0)));

            helper.assertTrue(points.get(Level.OVERWORLD).find(transient_.getInternalId()).isEmpty(), "a dynamic waypoint was saved to disk");
            helper.succeed();
        }
        finally
        {
            removeIfPresent(server, player);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Networking: the payloads' StreamCodecs, incl. the VEC3_STREAM_CODEC stand-in for
    // Vec3.STREAM_CODEC (added in 1.21.2) that every positioned waypoint type now uses -- on
    // this branch built on the StreamCodec stand-in, since vanilla's only arrived in 1.20.5.
    // ---------------------------------------------------------------------------------------

    @GameTest(template = EMPTY_STRUCTURE, batch = "hudcompass_sync")
    public void syncPacketRoundTrip(GameTestHelper helper)
    {
        RegistryAccess registries = helper.getLevel().registryAccess();

        PointsOfInterest source = new PointsOfInterest();
        BasicWaypoint camp = new BasicWaypoint(new Vec3(-100.25, 70, 33.5), "Camp", BasicIconData.basic("generic"));
        MapItemSavedData mapData = MapItemSavedData.createFresh(0, 0, (byte) 1, false, false, Level.OVERWORLD);
        var decoration = new VanillaMapPoints.MapDecorationWaypoint(mapData,
                new MapDecoration(MapDecoration.Type.RED_X, (byte) 20, (byte) -40, (byte) 0, null));
        BasicWaypoint end = new BasicWaypoint(new Vec3(0, 80, 0), "End", BasicIconData.basic("home"));
        source.get(Level.OVERWORLD).addPoint(camp);
        source.get(Level.OVERWORLD).addPoint(decoration);
        source.get(Level.END).addPoint(end);

        PointsOfInterest dest = roundTrip(source, registries);

        BasicWaypoint campBack = (BasicWaypoint) dest.get(Level.OVERWORLD).find(camp.getInternalId()).orElse(null);
        helper.assertTrue(campBack != null, "Camp waypoint missing after sync");
        helper.assertTrue(labelOf(campBack).equals("Camp"), "Camp label was '" + labelOf(campBack) + "'");
        helper.assertTrue(campBack.getPosition().equals(camp.getPosition()), "Camp position was " + campBack.getPosition());

        PointInfo<?> decorationBack = dest.get(Level.OVERWORLD).find(decoration.getInternalId()).orElse(null);
        helper.assertTrue(decorationBack instanceof VanillaMapPoints.MapDecorationWaypoint, "map decoration waypoint missing or wrong type after sync: " + decorationBack);
        helper.assertTrue(decorationBack.getPosition(null, 1f).equals(decoration.getPosition(null, 1f)),
                "map decoration position was " + decorationBack.getPosition(null, 1f) + ", expected " + decoration.getPosition(null, 1f));
        helper.assertTrue(!decorationBack.displayVerticalDistance(null), "map decoration lost its noVerticalDistance flag");
        helper.assertTrue(decorationBack.isDynamic(), "map decoration lost its dynamic flag over the wire");
        helper.assertTrue(spriteOf(decorationBack).equals(new ResourceLocation("red_x")),
                "red X decoration's icon was " + spriteOf(decorationBack) + ", expected minecraft:red_x");

        helper.assertTrue(dest.get(Level.END).find(end.getInternalId()).isPresent(), "End-dimension waypoint missing after sync");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "hudcompass_gui")
    public void editorSavePacketRoundTrip(GameTestHelper helper)
    {
        RegistryAccess registries = helper.getLevel().registryAccess();

        PointsOfInterest target = new PointsOfInterest();
        BasicWaypoint doomed = new BasicWaypoint(new Vec3(1, 1, 1), "Doomed", BasicIconData.generic());
        BasicWaypoint edited = new BasicWaypoint(new Vec3(2, 2, 2), "Before", BasicIconData.generic());
        target.get(Level.OVERWORLD).addPoint(doomed);
        target.get(Level.OVERWORLD).addPoint(edited);

        BasicWaypoint added = new BasicWaypoint(new Vec3(-5, 60, 9), "Added", BasicIconData.basic("home"));
        BasicWaypoint editedVersion = new BasicWaypoint(new Vec3(3, 4, 5), "After", BasicIconData.generic());
        editedVersion.setInternalId(edited.getInternalId());

        var payload = UpdateWaypointsFromGui.of(
                List.of(new PointAddRemoveEntry(Level.NETHER.location(), added)),
                List.of(new PointAddRemoveEntry(Level.OVERWORLD.location(), editedVersion)),
                List.of(doomed.getInternalId()),
                registries);

        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        UpdateWaypointsFromGui.STREAM_CODEC.encode(wire, payload);
        var decoded = UpdateWaypointsFromGui.STREAM_CODEC.decode(wire).decode(registries);
        target.applyUpdatesFromGui(decoded.pointsAdded(), decoded.pointsUpdated(), decoded.pointsRemoved());

        helper.assertTrue(target.get(Level.OVERWORLD).find(doomed.getInternalId()).isEmpty(), "removed waypoint is still there");
        BasicWaypoint after = (BasicWaypoint) target.get(Level.OVERWORLD).find(edited.getInternalId()).orElse(null);
        helper.assertTrue(after != null && labelOf(after).equals("After") && after.getPosition().equals(new Vec3(3, 4, 5)),
                "edited waypoint not updated: " + (after == null ? "missing" : labelOf(after) + " @ " + after.getPosition()));
        PointInfo<?> addedBack = target.get(Level.NETHER).find(added.getInternalId()).orElse(null);
        helper.assertTrue(addedBack != null && labelOf(addedBack).equals("Added"), "added waypoint missing from the nether");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------
    // Server-side waypoint sources, driven by the real server tick.
    // ---------------------------------------------------------------------------------------

    /**
     * Spawn point -> dynamic "Home" waypoint, using 1.20.1's separate respawn getters (the 26.x
     * build reads a RespawnConfig record). Also re-checks the relog bug fixed during the 26.2
     * playtest: a relog runs PointsOfInterest#deserialize, which wipes the (never-saved, dynamic)
     * Home point, and SpawnPointPoints must notice and add it back.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "hudcompass_spawn", timeoutTicks = 400)
    public void spawnPointHomeWaypoint(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryAccess registries = helper.getLevel().registryAccess();
        ServerPlayer player = TestPlayers.survival(server, "SpawnTester");
        PointsOfInterest points = ServerWaypointSync.get(player);
        BlockPos bed = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setRespawnPosition(Level.OVERWORLD, bed, 0f, true, false);

        helper.startSequence()
                .thenWaitUntil(() -> assertHomeAt(helper, points, bed))
                .thenExecute(() -> {
                    CompoundTag tag = new CompoundTag();
                    points.serialize(tag, registries);
                    points.deserialize(tag, registries);
                    helper.assertTrue(findHome(points).isEmpty(), "Home survived a simulated relog -- it is dynamic and should not be saved");
                })
                .thenWaitUntil(() -> assertHomeAt(helper, points, bed))
                .thenExecute(() -> player.setRespawnPosition(Level.OVERWORLD, null, 0f, false, false))
                .thenWaitUntil(() -> helper.assertTrue(findHome(points).isEmpty(), "Home not removed after the respawn point was cleared"))
                .thenExecute(() -> removeIfPresent(server, player))
                .thenSucceed();
    }

    /** A carried filled map's target decoration shows up as a waypoint, and goes away with the map. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "hudcompass_map", timeoutTicks = 400)
    public void carriedMapDecorationBecomesWaypoint(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = TestPlayers.survival(server, "MapTester");
        PointsOfInterest points = ServerWaypointSync.get(player);

        ItemStack map = MapItem.create(player.serverLevel(), 0, 0, (byte) 0, false, false);
        BlockPos target = new BlockPos(16, 64, 32);
        MapItemSavedData.addTargetDecoration(map, target, "hudcompass_test", MapDecoration.Type.RED_X);
        player.getInventory().setItem(0, map);
        // Test players have no ticking connection, so tick the map in the inventory by hand once;
        // that's what copies the item's target decoration into the map's saved data.
        map.inventoryTick(player.serverLevel(), player, 0, true);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    var found = points.get(Level.OVERWORLD).getPoints().stream()
                            .filter(p -> p instanceof VanillaMapPoints.MapDecorationWaypoint)
                            .findFirst();
                    helper.assertTrue(found.isPresent(), "no waypoint for the map's target decoration yet");
                    Vec3 pos = found.get().getPosition(player, 1f);
                    helper.assertTrue(Math.abs(pos.x - target.getX()) <= 1.5 && Math.abs(pos.z - target.getZ()) <= 1.5,
                            "map decoration waypoint at " + pos + ", expected near " + target);
                })
                .thenExecute(() -> player.getInventory().clearContent())
                .thenWaitUntil(() -> helper.assertTrue(points.get(Level.OVERWORLD).getPoints().stream()
                                .noneMatch(p -> p instanceof VanillaMapPoints.MapDecorationWaypoint),
                        "map decoration waypoint not removed after the map left the inventory"))
                .thenExecute(() -> removeIfPresent(server, player))
                .thenSucceed();
    }

    /** Other players appear as waypoints under playerDisplay=ALL and disappear under NONE. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "hudcompass_players", timeoutTicks = 400)
    public void playerTrackerFollowsConfig(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ConfigData.PlayerDisplay previous = ConfigData.playerDisplay;
        ServerPlayer observer = TestPlayers.survival(server, "Observer");
        ServerPlayer other = TestPlayers.survival(server, "Teammate");
        PointsOfInterest points = ServerWaypointSync.get(observer);
        ConfigData.playerDisplay = ConfigData.PlayerDisplay.ALL;

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(playerPointFor(points, "Teammate").isPresent(),
                        "no waypoint for the other player under playerDisplay=ALL"))
                .thenExecute(() -> ConfigData.playerDisplay = ConfigData.PlayerDisplay.NONE)
                .thenWaitUntil(() -> helper.assertTrue(playerPointFor(points, "Teammate").isEmpty(),
                        "player waypoint not removed under playerDisplay=NONE"))
                .thenExecute(() -> {
                    ConfigData.playerDisplay = previous;
                    removeIfPresent(server, observer);
                    removeIfPresent(server, other);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------
    // Far-away players (fixed 2026-10-09, all versions). On the client, PlayerWaypoint follows
    // the live player entity -- but the client world only holds players within entity-tracking
    // range, while PlayerTracker adds every player in the dimension. For a teammate farther away
    // the client falls back to the position the server sent; before the fix none was sent, the
    // decoded point's position was null, and the client crashed (per-tick targeting + HUD sort).
    // "Not in the observer's world" is simulated by removing the target from the server before
    // asking the decoded point where it is -- the same lookup miss the client hits.
    // ---------------------------------------------------------------------------------------

    /** A synced player waypoint still has a position when its target can't be looked up. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "hudcompass_far_player")
    public void decodedPlayerWaypointHasPositionWhenTargetNotTracked(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer observer = TestPlayers.survival(server, "NearObserver");
        ServerPlayer target = TestPlayers.survival(server, "FarTeammate");
        try
        {
            PointsOfInterest source = new PointsOfInterest();
            source.get(Level.OVERWORLD).addPoint(new PlayerTracker.PlayerWaypoint(target));
            PointsOfInterest dest = roundTrip(source, helper.getLevel().registryAccess());

            removeIfPresent(server, target);

            PointInfo<?> decoded = dest.get(Level.OVERWORLD).getPoints().iterator().next();
            Vec3 position = decoded.getPosition(observer, 1f);
            helper.assertTrue(position != null,
                    "decoded PlayerWaypoint has a null position when its target isn't in the observer's world "
                            + "(i.e. a teammate beyond entity-tracking range) -- the client would NPE");
            helper.assertTrue(position.distanceTo(target.position()) < 0.01,
                    "decoded PlayerWaypoint fell back to " + position + ", expected the target's position " + target.position());
            helper.succeed();
        }
        finally
        {
            removeIfPresent(server, observer);
            removeIfPresent(server, target);
        }
    }

    /** The server keeps that fallback position current as the far-away player moves. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "hudcompass_far_player_moves", timeoutTicks = 400)
    public void farPlayerWaypointFollowsItsTarget(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryAccess registries = helper.getLevel().registryAccess();
        ConfigData.PlayerDisplay previous = ConfigData.playerDisplay;
        ServerPlayer observer = TestPlayers.survival(server, "Watcher");
        ServerPlayer target = TestPlayers.survival(server, "Wanderer");
        PointsOfInterest points = ServerWaypointSync.get(observer);
        Vec3 destination = target.position().add(300, 0, -200);
        ConfigData.playerDisplay = ConfigData.PlayerDisplay.ALL;

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(playerPointFor(points, "Wanderer").isPresent(),
                        "no waypoint for the other player yet"))
                .thenExecute(() -> {
                    target.teleportTo(destination.x, destination.y, destination.z);
                    points.syncedNumber = points.syncNumber;
                })
                // PlayerTracker scans every 20 ticks; give it at least two scans.
                .thenIdle(50)
                .thenExecute(() -> {
                    helper.assertTrue(points.syncNumber > points.syncedNumber,
                            "the far player's move didn't mark the observer's waypoints for resync");
                    PointsOfInterest client = roundTrip(points, registries);
                    removeIfPresent(server, target);
                    Vec3 position = playerPointFor(client, "Wanderer").orElseThrow().getPosition(observer, 1f);
                    helper.assertTrue(position.distanceTo(destination) < 0.01,
                            "client fallback position is " + position + " after the move, expected " + destination);
                })
                .thenExecute(() -> {
                    ConfigData.playerDisplay = previous;
                    removeIfPresent(server, observer);
                    removeIfPresent(server, target);
                })
                .thenSucceed();
    }

    /**
     * A resync rebuilds every point object; each point's label-fade state must carry over by id,
     * or showing labels restart their fade-in and blink on every sync -- which, since the fix
     * above, can come about once a second while a far-away player moves.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "hudcompass_fade")
    public void resyncKeepsLabelFade(GameTestHelper helper)
    {
        RegistryAccess registries = helper.getLevel().registryAccess();
        PointsOfInterest server = new PointsOfInterest();
        BasicWaypoint point = new BasicWaypoint(new Vec3(1, 2, 3), "Faded", BasicIconData.generic());
        server.get(Level.OVERWORLD).addPoint(point);

        PointsOfInterest client = new PointsOfInterest();
        syncInto(server, client, registries);
        PointInfo<?> before = client.get(Level.OVERWORLD).find(point.getInternalId()).orElseThrow();
        before.fade = 200;

        syncInto(server, client, registries);
        PointInfo<?> after = client.get(Level.OVERWORLD).find(point.getInternalId()).orElseThrow();
        helper.assertTrue(after != before, "the resync didn't replace the point, so this test proves nothing");
        helper.assertTrue(after.fade == 200, "label fade reset to " + after.fade + " by a resync (expected 200)");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------

    private static PointsOfInterest roundTrip(PointsOfInterest source, RegistryAccess registries)
    {
        PointsOfInterest dest = new PointsOfInterest();
        syncInto(source, dest, registries);
        return dest;
    }

    /** One full sync, encoded and decoded through the real payload codec, as a client receives it. */
    private static void syncInto(PointsOfInterest source, PointsOfInterest dest, RegistryAccess registries)
    {
        var payload = SyncWaypointData.of(source, registries);
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        SyncWaypointData.STREAM_CODEC.encode(wire, payload);
        var received = SyncWaypointData.STREAM_CODEC.decode(wire);
        dest.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(received.bytes())));
    }

    private static List<PointInfo<?>> persistent(PointsOfInterest.WorldPoints world)
    {
        return world.getPoints().stream().filter(p -> !p.isDynamic()).toList();
    }

    private static String labelOf(PointInfo<?> point)
    {
        Component label = point.getLabel();
        return label == null ? "<none>" : label.getString();
    }

    private static ResourceLocation spriteOf(PointInfo<?> point)
    {
        return ((BasicIconData) point.getIconData()).spriteName;
    }

    private static Optional<BasicWaypoint> findHome(PointsOfInterest points)
    {
        return points.get(Level.OVERWORLD).getPoints().stream()
                .filter(p -> p instanceof BasicWaypoint && p.isDynamic() && "Home".equals(labelOf(p)))
                .map(p -> (BasicWaypoint) p)
                .findFirst();
    }

    private static void assertHomeAt(GameTestHelper helper, PointsOfInterest points, BlockPos bed)
    {
        var home = findHome(points);
        helper.assertTrue(home.isPresent(), "no Home waypoint yet");
        helper.assertTrue(home.get().getPosition().equals(Vec3.atCenterOf(bed)), "Home at " + home.get().getPosition() + ", expected " + Vec3.atCenterOf(bed));
    }

    private static Optional<PointInfo<?>> playerPointFor(PointsOfInterest points, String name)
    {
        return points.get(Level.OVERWORLD).getPoints().stream()
                .filter(p -> p instanceof PlayerTracker.PlayerWaypoint && name.equals(labelOf(p)))
                .findFirst();
    }

    private static void removeIfPresent(MinecraftServer server, ServerPlayer player)
    {
        UUID id = player.getUUID();
        if (server.getPlayerList().getPlayer(id) != null)
            server.getPlayerList().remove(player);
    }
}
