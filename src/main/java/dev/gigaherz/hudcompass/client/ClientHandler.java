package dev.gigaherz.hudcompass.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.integrations.server.PlayerTracker;
import dev.gigaherz.hudcompass.network.AddWaypoint;
import dev.gigaherz.hudcompass.network.RemoveWaypoint;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public class ClientHandler implements ClientModInitializer
{
    // On 1.20.1 a keybind category is just its translation key -- this is the same key the 26.x
    // build's registered KeyMapping.Category resolves to, so the existing lang entry covers both.
    public static final String CATEGORY = "key.category.hudcompass.hudcompass";
    public static KeyMapping ADD_WAYPOINT;
    public static KeyMapping REMOVE_WAYPOINT;
    public static KeyMapping EDIT_WAYPOINTS;

    @Override
    public void onInitializeClient()
    {
        ADD_WAYPOINT = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.hudcompass.add_waypoint", InputConstants.UNKNOWN.getValue(), CATEGORY));
        REMOVE_WAYPOINT = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.hudcompass.remove_waypoint", InputConstants.UNKNOWN.getValue(), CATEGORY));
        EDIT_WAYPOINTS = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.hudcompass.edit_waypoints", InputConstants.UNKNOWN.getValue(), CATEGORY));

        // 1.20.1's Fabric API has no HUD layer registry, only this end-of-HUD callback. The boss
        // bar push-down the 26.x build does here via replaceElement is BossHealthOverlayMixin.
        HudRenderCallback.EVENT.register((graphics, partialTick) -> HudOverlay.INSTANCE.render(graphics, partialTick));

        ClientWaypointDatabase.init();
        ClientWaypointSync.init();
        PlayerTracker.initClientRendering();

        ClientTickEvents.START_CLIENT_TICK.register(ClientHandler::handleKeys);
        ClientTickEvents.END_CLIENT_TICK.register(ClientHandler::clientTickEvent);
    }

    private static void handleKeys(Minecraft mc)
    {
        if (mc.player == null)
            return;

        if (ADD_WAYPOINT != null && ADD_WAYPOINT.consumeClick())
        {
            Vec3 position = mc.player.position();
            String label = String.format("{%1.2f, %1.2f, %1.2f}", position.x(), position.y(), position.z());
            var waypoint = new BasicWaypoint(position, label, BasicIconData.mapDecoration("player_off_limits"));

            if (PointsOfInterest.INSTANCE.otherSideHasMod)
            {
                ClientPlayNetworking.send(AddWaypoint.of(waypoint));
            }
            else
            {
                PointsOfInterest.INSTANCE.get(mc.player.level()).addPoint(waypoint);
            }

            //noinspection StatementWithEmptyBody
            while (ADD_WAYPOINT.consumeClick())
            {
                // eat
            }
        }

        if (REMOVE_WAYPOINT != null && REMOVE_WAYPOINT.consumeClick())
        {
            PointInfo<?> targetted = PointsOfInterest.INSTANCE.getTargetted();

            if (targetted != null && !targetted.isDynamic())
            {
                if (PointsOfInterest.INSTANCE.otherSideHasMod)
                {
                    ClientPlayNetworking.send(new RemoveWaypoint(targetted));
                }
                else
                {
                    PointsOfInterest.INSTANCE.get(mc.player.level()).removePoint(targetted);
                }
            }

            //noinspection StatementWithEmptyBody
            while (REMOVE_WAYPOINT.consumeClick())
            {
                // eat
            }
        }

        if (EDIT_WAYPOINTS != null && EDIT_WAYPOINTS.consumeClick())
        {
            if (mc.screen == null)
            {
                mc.setScreen(new ClientWaypointManagerScreen());
            }

            //noinspection StatementWithEmptyBody
            while (EDIT_WAYPOINTS.consumeClick())
            {
                // eat
            }
        }
    }

    private static void clientTickEvent(Minecraft mc)
    {
        LocalPlayer player = mc.player;
        if (player != null)
        {
            PointsOfInterest.INSTANCE.tick(player);
        }
    }
}
