package dev.gigaherz.hudcompass.test.client;

import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.client.ClientWaypointManagerScreen;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.integrations.server.PlayerTracker;
import dev.gigaherz.hudcompass.server.ServerWaypointSync;
import dev.gigaherz.hudcompass.test.TestPlayers;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * End-to-end client check, run with {@code gradlew runClientGameTest} (one game window, on the
 * secondary monitor, unfocused -- see the mixins in this package).
 * <p>
 * The headless GameTests cover the far-away-player fix at the codec level; this one reproduces the
 * actual crash scenario in a real client: a teammate 1000 blocks away (far beyond entity-tracking
 * range, so the client world doesn't contain them) is synced to the client by the real handshake
 * and sync packets, and the client then ticks and renders the HUD with that waypoint. Before the
 * fix the decoded waypoint had no position and the client crashed right here. Also checks the
 * position follows the teammate as they move, that label fade survives the resync, and takes
 * screenshots of the HUD (boss bar pushed below the compass) and the waypoint editor.
 */
public final class HudCompassClientGameTest implements FabricClientGameTest
{
    private static final String FAR_NAME = "FarTeammate";

    private record Far(UUID id, Vec3 position)
    {
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        // Statics only (shared by the client and the integrated server in this JVM), never saved.
        ConfigData.displayWhen = ConfigData.DisplayWhen.ALWAYS;
        ConfigData.alwaysShowLabels = true;
        ConfigData.playerDisplay = ConfigData.PlayerDisplay.ALL;
        // Draw waypoints out to 5000 blocks (default 200), so the far teammate is actually rendered
        // on the HUD too -- the crash happened regardless (client tick + HUD sort run on every
        // point), but this also exercises drawing it.
        ConfigData.waypointViewDistance = 5000;
        ConfigData.waypointFadeDistance = 4950;

        try (TestSingleplayerContext world = context.worldBuilder().create())
        {
            TestServerContext server = world.getServer();
            context.waitFor(client -> PointsOfInterest.INSTANCE.otherSideHasMod, 200);

            UUID self = context.computeOnClient(client -> client.player.getUUID());
            faceSouth(context);

            // A teammate 1000 blocks straight ahead.
            Far far = server.computeOnServer(s -> {
                ServerPlayer me = s.getPlayerList().getPlayer(self);
                ServerPlayer teammate = TestPlayers.survival(s, FAR_NAME);
                Vec3 p = me.position().add(0, 0, 1000);
                teammate.teleportTo(p.x, p.y, p.z);
                return new Far(teammate.getUUID(), teammate.position());
            });

            context.waitFor(client -> farPoint(client).isPresent(), 200);
            context.runOnClient(client -> {
                check(client.level.getPlayerByUUID(far.id()) == null,
                        "test setup: the far player is inside the client's tracking range, so this proves nothing");
                Vec3 pos = farPoint(client).orElseThrow().getPosition(client.player, 1f);
                check(pos != null, "the far player's waypoint reached the client with no position (the crash bug)");
                check(pos.distanceTo(far.position()) < 0.5, "far player's waypoint is at " + pos + ", expected " + far.position());
            });

            // Before the fix, the client crashed within a tick of the sync above. Let it tick and
            // render the HUD with that waypoint for a few seconds.
            context.waitTicks(60);
            context.takeScreenshot("hudcompass-far-player");

            // The regular HUD: waypoints ahead and above/below, plus a boss bar to check the
            // compass pushes it down instead of overlapping it.
            server.runOnServer(s -> {
                ServerPlayer me = s.getPlayerList().getPlayer(self);
                Vec3 p = me.position();
                var points = ServerWaypointSync.get(me).get(me.level());
                points.addPoint(new BasicWaypoint(p.add(-8, 0, 40), "Ahead", BasicIconData.generic()));
                points.addPoint(new BasicWaypoint(p.add(-30, 25, 40), "High", BasicIconData.mapDecoration("red_x")));
                points.addPoint(new BasicWaypoint(p.add(30, -20, 40), "Low", BasicIconData.mapDecoration("player_off_limits")));
                ServerBossEvent boss = new ServerBossEvent(UUID.randomUUID(), Component.literal("HUD Compass test boss"),
                        BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
                boss.addPlayer(me);
            });
            // Labels fade in once shown; wait until the near ones are fully faded in.
            context.waitFor(client -> point(client, "Ahead").map(p -> p.fade >= 255).orElse(false), 200);
            context.takeScreenshot("hudcompass-hud");

            // The far teammate moves. The server must send the new position, and the resync that
            // carries it must not restart the visible labels' fade-in -- that's the label blinking
            // a moving far-away teammate would otherwise cause, roughly once a second.
            Vec3 moved = server.computeOnServer(s -> {
                ServerPlayer teammate = s.getPlayerList().getPlayer(far.id());
                teammate.teleportTo(far.position().x + 300, far.position().y, far.position().z);
                return teammate.position();
            });
            context.waitFor(client -> farPoint(client)
                    .map(p -> p.getPosition(client.player, 1f).distanceTo(moved) < 0.5)
                    .orElse(false), 200);
            context.runOnClient(client -> {
                for (String name : new String[]{"Ahead", FAR_NAME})
                {
                    float fade = point(client, name).orElseThrow().fade;
                    check(fade > 200, "'" + name + "' label fade was reset to " + fade + " by the resync (labels would blink)");
                }
            });
            context.takeScreenshot("hudcompass-after-resync");

            context.setScreen(ClientWaypointManagerScreen::new);
            context.waitTicks(20);
            context.takeScreenshot("hudcompass-editor");
            context.setScreen(() -> null);

            server.runOnServer(s -> {
                ServerPlayer teammate = s.getPlayerList().getPlayer(far.id());
                if (teammate != null)
                    s.getPlayerList().remove(teammate);
            });
        }
    }

    private static void faceSouth(ClientGameTestContext context)
    {
        context.runOnClient(client -> {
            var player = client.player;
            player.setYRot(0);
            player.yRotO = 0;
            player.setYHeadRot(0);
            player.yHeadRotO = 0;
            player.setXRot(0);
            player.xRotO = 0;
        });
    }

    private static Optional<PointInfo<?>> farPoint(Minecraft client)
    {
        return PointsOfInterest.INSTANCE.get(client.level).getPoints().stream()
                .filter(p -> p instanceof PlayerTracker.PlayerWaypoint && p.getLabel() != null && FAR_NAME.equals(p.getLabel().getString()))
                .findFirst();
    }

    private static Optional<PointInfo<?>> point(Minecraft client, String label)
    {
        return PointsOfInterest.INSTANCE.get(client.level).getPoints().stream()
                .filter(p -> p.getLabel() != null && label.equals(p.getLabel().getString()))
                .findFirst();
    }

    private static void check(boolean ok, String message)
    {
        if (!ok)
            throw new AssertionError(message);
    }
}
