package dev.gigaherz.hudcompass.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.integrations.client.LocatorBarPoints;
import dev.gigaherz.hudcompass.integrations.server.PlayerTracker;
import dev.gigaherz.hudcompass.network.AddWaypoint;
import dev.gigaherz.hudcompass.network.RemoveWaypoint;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public class ClientHandler implements ClientModInitializer
{
    public static KeyMapping.Category CATEGORY = KeyMapping.Category.register(HudCompass.location("hudcompass"));
    public static KeyMapping ADD_WAYPOINT;
    public static KeyMapping REMOVE_WAYPOINT;
    public static KeyMapping EDIT_WAYPOINTS;

    @Override
    public void onInitializeClient()
    {
        ADD_WAYPOINT = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.hudcompass.add_waypoint", InputConstants.UNKNOWN.getValue(), CATEGORY));
        REMOVE_WAYPOINT = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.hudcompass.remove_waypoint", InputConstants.UNKNOWN.getValue(), CATEGORY));
        EDIT_WAYPOINTS = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.hudcompass.edit_waypoints", InputConstants.UNKNOWN.getValue(), CATEGORY));

        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, HudCompass.location("compass"),
                (HudElement) (GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) ->
                        HudOverlay.INSTANCE.render(graphics, deltaTracker));

        // Matches upstream's NeoForge RenderGuiLayerEvent.Pre/Post hook on the boss-overlay layer:
        // push the vanilla boss bar down 28px while the compass is visible, so the two don't
        // overlap. Fabric's equivalent to "run code before/after a named HUD layer renders" is
        // wrapping it via replaceElement, given the original element to delegate to.
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original -> (graphics, deltaTracker) -> {
            if (!HudOverlay.INSTANCE.canRender())
            {
                original.extractRenderState(graphics, deltaTracker);
                return;
            }

            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(0, 28);
            original.extractRenderState(graphics, deltaTracker);
            pose.popMatrix();
        });

        LocatorBarPoints.init();
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
            if (mc.gui.screen() == null)
            {
                mc.setScreenAndShow(new ClientWaypointManagerScreen());
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
