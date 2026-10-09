package dev.gigaherz.hudcompass.test.client;

import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.client.ClientWaypointManagerScreen;
import dev.gigaherz.hudcompass.client.HudOverlay;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.integrations.server.PlayerTracker;
import dev.gigaherz.hudcompass.server.ServerWaypointSync;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Visual smoke test for the client half of the 1.20.1 backport -- the parts a headless GameTest
 * can't see: the compass HUD (rewritten onto immediate-mode Tesselator quads), the boss-bar
 * push-down mixin, F1 hiding, and the waypoint editor screen (rewritten input/render signatures).
 * Fabric API has no client GameTest framework before 1.21.4, so this drives a real client from
 * its own tick loop instead.
 * <p>
 * Only active with {@code -Dhudcompass.clientSmokeTest=true} ({@code gradlew runClientSmoke}).
 * Creates a flat creative world, puts waypoints in through the integrated server (so they reach
 * the HUD over the mod's real handshake + sync packets), takes screenshots into
 * {@code build/run/clientSmoke/screenshots}, writes {@code hudcompass-smoke-report.txt}, then
 * quits. The window opens hidden, unfocused and on the secondary monitor (see the mixins in this
 * package) so the run never takes the user's focus or mouse.
 */
public final class HudCompassClientSmokeTest implements ClientModInitializer
{
    public static final boolean ENABLED = Boolean.getBoolean("hudcompass.clientSmokeTest");

    private static final String WORLD = "hudcompass-smoke";
    private static final int WATCHDOG_TICKS = 20 * 60 * 4;

    private final List<String> report = new ArrayList<>();
    private boolean failed;
    private int phase;
    private int wait;
    private int ticks;

    @Override
    public void onInitializeClient()
    {
        if (!ENABLED)
            return;

        // Statics only, never written back to config/hudcompass.json.
        ConfigData.displayWhen = ConfigData.DisplayWhen.ALWAYS;
        ConfigData.alwaysShowLabels = true;

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc)
    {
        if (++ticks > WATCHDOG_TICKS && phase < 99)
        {
            fail("watchdog: stuck in phase " + phase);
            finish(mc);
            return;
        }
        if (wait > 0)
        {
            wait--;
            return;
        }

        try
        {
            switch (phase)
            {
                case 0 -> {
                    if (mc.screen instanceof TitleScreen)
                    {
                        createWorld(mc);
                        phase = 1;
                    }
                }
                case 1 -> {
                    if (mc.level != null && mc.player != null && mc.screen == null && mc.getSingleplayerServer() != null)
                    {
                        phase = 2;
                        wait = 40;
                    }
                }
                case 2 -> {
                    setUpScene(mc);
                    phase = 3;
                    wait = 60;
                }
                case 3 -> {
                    faceSouth(mc);
                    checkSynced(mc);
                    check(HudOverlay.INSTANCE.canRender(), "compass renders (displayWhen=ALWAYS)");
                    // The boss bar has no public "is showing" accessor; screenshot 1 is the check
                    // that it sits below the compass rather than under it.
                    screenshot(mc, "1-hud.png");
                    mc.options.hideGui = true;
                    phase = 4;
                    wait = 5;
                }
                case 4 -> {
                    check(!HudOverlay.INSTANCE.canRender(), "compass hidden while F1 (hideGui) is on");
                    screenshot(mc, "2-hud-f1-hidden.png");
                    mc.options.hideGui = false;
                    mc.setScreen(new ClientWaypointManagerScreen());
                    phase = 5;
                    wait = 20;
                }
                case 5 -> {
                    check(mc.screen instanceof ClientWaypointManagerScreen, "waypoint editor opened");
                    screenshot(mc, "3-editor.png");
                    // The first world row's fold button: panel left (8) + button (1,1) + half its
                    // 20px size, panel top (36) + border (4) + the same. Goes through the
                    // rewritten ScrollPanel -> list item -> Button mouse path.
                    boolean handled = mc.screen.mouseClicked(8 + 1 + 10, 36 + 4 + 1 + 10, 0);
                    check(handled, "click on the first world row's fold button was handled");
                    phase = 6;
                    wait = 10;
                }
                case 6 -> {
                    screenshot(mc, "4-editor-folded.png");
                    mc.setScreen(null);
                    phase = 7;
                    wait = 5;
                }
                case 7 -> finish(mc);
                default -> {
                }
            }
        }
        catch (Throwable t)
        {
            fail("exception in phase " + phase + ": " + t);
            t.printStackTrace();
            finish(mc);
        }
    }

    private void createWorld(Minecraft mc)
    {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1234L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        report.add("world creation requested");
    }

    private void setUpScene(Minecraft mc)
    {
        faceSouth(mc);
        IntegratedServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            Vec3 p = sp.position();
            var points = ServerWaypointSync.get(sp).get(sp.level());
            points.addPoint(new BasicWaypoint(p.add(-8, 0, 40), "Ahead", BasicIconData.generic()));
            points.addPoint(new BasicWaypoint(p.add(-30, 25, 40), "High", BasicIconData.mapDecoration("red_x")));
            points.addPoint(new BasicWaypoint(p.add(30, -20, 40), "Low", BasicIconData.mapDecoration("player_off_limits")));
            points.addPoint(new BasicWaypoint(p.add(45, 5, 25), "Slightly up", BasicIconData.generic()));
            // The local player's own face icon (PlayerFaceRenderer -> blitRaw UVs), dead ahead.
            points.addPoint(new PlayerTracker.PlayerWaypoint(sp));
            // Bed -> the dynamic Home waypoint, via SpawnPointPoints on the server tick.
            sp.setRespawnPosition(sp.level().dimension(), BlockPos.containing(p.add(15, 0, 50)), 0f, true, false);

            ServerBossEvent boss = new ServerBossEvent(Component.literal("HUD Compass smoke test boss"),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            boss.addPlayer(sp);
        });
        report.add("scene set up on the integrated server");
    }

    private void checkSynced(Minecraft mc)
    {
        check(PointsOfInterest.INSTANCE.otherSideHasMod, "client/server handshake completed (otherSideHasMod)");
        Set<String> labels = PointsOfInterest.INSTANCE.get(mc.level).getPoints().stream()
                .map(PointInfo::getLabel)
                .filter(l -> l != null)
                .map(Component::getString)
                .collect(Collectors.toSet());
        report.add("client-side labels after sync: " + labels);
        for (String expected : List.of("Ahead", "High", "Low", "Slightly up", "Home", mc.player.getGameProfile().getName()))
            check(labels.contains(expected), "'" + expected + "' waypoint reached the client through SyncWaypointData");
    }

    private static void faceSouth(Minecraft mc)
    {
        var player = mc.player;
        player.setYRot(0);
        player.yRotO = 0;
        player.setYHeadRot(0);
        player.yHeadRotO = 0;
        player.setXRot(0);
        player.xRotO = 0;
    }

    private void screenshot(Minecraft mc, String name)
    {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> report.add("screenshot " + name + ": " + msg.getString()));
    }

    private void check(boolean ok, String what)
    {
        report.add((ok ? "PASS " : "FAIL ") + what);
        if (!ok)
            failed = true;
    }

    private void fail(String what)
    {
        report.add("FAIL " + what);
        failed = true;
    }

    private void finish(Minecraft mc)
    {
        phase = 99;
        report.add(failed ? "RESULT: FAILED" : "RESULT: all checks passed");
        try
        {
            Files.write(mc.gameDirectory.toPath().resolve("hudcompass-smoke-report.txt"), report, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        mc.stop();
    }
}
