package dev.gigaherz.hudcompass.icons.client;

import dev.gigaherz.hudcompass.client.HudOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Shared player-face icon drawing, split out of upstream's {@code PlayerTracker} (the
 * server-side player waypoint tracker, deferred to a follow-up port) since the locator-bar
 * integration needs the same face-icon rendering for player-owned vanilla waypoints without
 * depending on the server-tracking system.
 */
public class PlayerFaceRenderer
{
    public static boolean drawUUID(Player player, UUID uuid, GuiGraphicsExtractor graphics, int x, int y, int alpha)
    {
        if (uuid != null && player.level().getPlayerByUUID(uuid) instanceof AbstractClientPlayer clientPlayer)
        {
            var tex = clientPlayer.getSkin().body().texturePath();

            drawFaceLayer(graphics, tex, x - 4, y - 4, 8, 8, 8);
            drawFaceLayer(graphics, tex, x - 4.5f, y - 4.5f, 9, 9, 40);
            return true;
        }

        return false;
    }

    private static void drawFaceLayer(GuiGraphicsExtractor graphics, Identifier tex, float x1, float y1, float w, float h, int tx)
    {
        var x2 = x1 + w;
        var y2 = y1 + h;
        var u1 = tx / 64f;
        var u2 = (tx + 8) / 64f;

        HudOverlay.blitRaw(graphics, tex, x1, x2, y1, y2, u1, 8 / 64f, u2, 16 / 64f, 1, 1, 1, 1);
    }
}
