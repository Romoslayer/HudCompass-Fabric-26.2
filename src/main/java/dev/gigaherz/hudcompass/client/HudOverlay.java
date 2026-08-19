package dev.gigaherz.hudcompass.client;

import com.google.common.collect.Lists;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import dev.gigaherz.hudcompass.waypoints.client.PointRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The compass HUD overlay itself. Ported from upstream's NeoForge {@code GuiLayer}
 * implementation -- the actual drawing code (fillRect/blitRaw) is vanilla Mojang API and carried
 * over close to verbatim, registered via Fabric API's HUD rendering hook in
 * {@link ClientHandler}.
 * <p>
 * Upstream's NeoForge version additionally hooked {@code RegisterGuiLayersEvent} +
 * Pre/Post events on the boss-overlay layer to push the boss health bar down by 28px, making
 * room above it for the compass. That boss-bar-avoidance trick IS ported, in {@link ClientHandler}
 * -- Fabric's equivalent turned out to be {@code HudElementRegistry.replaceElement}, which hands
 * you the original {@code HudElement} to wrap rather than firing separate Pre/Post events for a
 * named layer, but achieves the identical effect (translate the pose stack by (0, 28) around the
 * original boss bar's own render call, only while {@link #canRender()} is true).
 * <p>
 * NOTE: {@code Options.hideGui} (the F1 hide-HUD toggle) has no accessible replacement in MC
 * 26.2 (checked Options/Minecraft/Gui/Hud) -- the compass will keep rendering when the vanilla
 * HUD is hidden with F1. Known/accepted gap, same as encountered porting Dynamic Surroundings.
 */
public class HudOverlay
{
    public static final HudOverlay INSTANCE = new HudOverlay();

    private final Minecraft mc;
    private final Font font;
    private final TextureManager textureManager;

    private HudOverlay()
    {
        this.mc = Minecraft.getInstance();
        this.font = mc.font;
        this.textureManager = mc.getTextureManager();
    }

    public void render(GuiGraphicsExtractor graphics, DeltaTracker _partialTicks)
    {
        if (!canRender()) return;

        if (mc.player == null) return;

        renderCompass(graphics, _partialTicks);
    }

    private void renderCompass(GuiGraphicsExtractor graphics, DeltaTracker _partialTicks)
    {
        boolean isPaused = mc.isPaused();

        float elapsed = isPaused ? 0 : _partialTicks.getGameTimeDeltaTicks();
        float partialTicks = _partialTicks.getGameTimeDeltaPartialTick(true);

        int xPos = mc.getWindow().getGuiScaledWidth() / 2;
        float yaw = Mth.lerp(partialTicks, mc.player.yHeadRotO, mc.player.yHeadRot) % 360;
        if (yaw < 0) yaw += 360;

        fillRect(graphics, xPos - 90, 10, xPos + 90, 18, 0x3f000000);

        drawCardinalDirection(graphics, yaw, 0, xPos, "S");
        drawCardinalDirection(graphics, yaw, 90, xPos, "W");
        drawCardinalDirection(graphics, yaw, 180, xPos, "N");
        drawCardinalDirection(graphics, yaw, 270, xPos, "E");

        fillRect(graphics, xPos - 1.5f, 10, xPos - 0.5f, 18, 0x3FFFFFFF);
        fillRect(graphics, xPos + 0.5f, 10, xPos + 1.5f, 18, 0x3FFFFFFF);

        fillRect(graphics, xPos - 45 - 0.5f, 10, xPos - 45 + 0.5f, 18, 0x3FFFFFFF);
        fillRect(graphics, xPos + 45 - 0.5f, 10, xPos + 45 + 0.5f, 18, 0x3FFFFFFF);

        final Player player = mc.player;
        double playerPosX = Mth.lerp(partialTicks, mc.player.xo, mc.player.getX());
        double playerPosY = Mth.lerp(partialTicks, mc.player.yo, mc.player.getY());
        double playerPosZ = Mth.lerp(partialTicks, mc.player.zo, mc.player.getZ());

        var playerPosition = new Vec3(playerPosX, playerPosY, playerPosZ);

        final float yaw0 = yaw;
        var points = PointsOfInterest.INSTANCE.get(player.level());
        {
            List<PointInfo<?>> sortedPoints = Lists.newArrayList(points.getPoints());
            sortedPoints.sort((a, b) -> {
                Vec3 positionA = a.getPosition(player, partialTicks);
                Vec3 positionB = b.getPosition(player, partialTicks);
                float angleA = Math.abs(angleDistance(yaw0, angleFromPoint(positionA, playerPosX, playerPosY, playerPosZ).x));
                float angleB = Math.abs(angleDistance(yaw0, angleFromPoint(positionB, playerPosX, playerPosY, playerPosZ).x));
                return (int) Math.signum(angleB - angleA);
            });
            for (PointInfo<?> point : sortedPoints)
            {
                Vec3 position = point.getPosition(player, partialTicks);
                Vec2 angleYd = angleFromPoint(position, playerPosX, playerPosY, playerPosZ);
                drawPoi(player, graphics, yaw0, angleYd.x, angleYd.y, xPos, point, point == PointsOfInterest.INSTANCE.getTargetted(), elapsed, position.subtract(playerPosition));
            }
        }
    }

    public boolean canRender()
    {
        if (mc.player == null) return false;

        if (mc.options.keyPlayerList.isDown())
            return false;

        return switch (ConfigData.displayWhen)
        {
            case NEVER -> false;
            case ALWAYS -> true;
            case HAS_COMPASS -> findCompassInInventory();
            case HOLDING_COMPASS -> findCompassInHands();
        };
    }

    private static final TagKey<Item> MAKES_HUDCOMPASS_VISIBLE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("hudcompass", "makes_hudcompass_visible"));

    private boolean findCompassInHands()
    {
        if (mc.player == null) return false;

        return mc.player.getMainHandItem().is(MAKES_HUDCOMPASS_VISIBLE)
                || mc.player.getOffhandItem().is(MAKES_HUDCOMPASS_VISIBLE);
    }

    private boolean findCompassInInventory()
    {
        if (mc.player == null) return false;

        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
        {
            if (inv.getItem(i).is(MAKES_HUDCOMPASS_VISIBLE))
                return true;
        }
        return false;
    }

    private Vec2 angleFromPoint(Vec3 position, double playerPosX, double playerPosY, double playerPosZ)
    {
        double xd = position.x - playerPosX;
        double yd = position.y - playerPosY;
        double zd = position.z - playerPosZ;
        return new Vec2((float) Math.toDegrees(-Math.atan2(xd, zd)), (float) yd);
    }

    private void drawCardinalDirection(GuiGraphicsExtractor graphics, float yaw, float angle, int xPos, String text)
    {
        float nDist = angleDistance(yaw, angle);
        if (Math.abs(nDist) <= 90)
        {
            float nPos = xPos + nDist;
            fillRect(graphics, nPos - 0.5f, 10, nPos + 0.5f, 18, 0x7FFFFFFF);
            if (mc.options.backgroundForChatOnly().get())
                drawCenteredShadowString(graphics, font, text, nPos, 1, 0xFFFFFFFF);
            else
                drawCenteredBoxedString(graphics, font, text, nPos, 1, 0xFFFFFFFF);
        }
    }

    public void drawCenteredShadowString(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color)
    {
        float width = font.width(text);

        var xPos = (x - width / 2);
        var xInt = (int) xPos;
        var yInt = (int) y;
        var xFract = xPos - xInt;
        var yFract = y - yInt;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(xFract, yFract);
        graphics.text(font, text, xInt, yInt, color, true);
        pose.popMatrix();
    }

    public static void drawCenteredBoxedString(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color)
    {
        Minecraft mc = Minecraft.getInstance();
        float width = font.width(text);
        float height = font.lineHeight;
        float width1 = width + 4;
        float height1 = height + 3;
        float x0 = x - width1 / 2;

        int backgroundColor = ((int) Mth.clamp(mc.options.textBackgroundOpacity().get() * ((color >> 24) & 0xFF), 0, 255)) << 24;
        fillRect(graphics, x0, y, x0 + width1, y + height1, backgroundColor);

        var xPos = (x - width / 2);
        var yPos = y + 2;
        var xInt = (int) xPos;
        var yInt = (int) y;
        var xFract = xPos - xInt;
        var yFract = yPos - yInt;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(xFract, yFract);
        graphics.text(font, text, xInt, yInt, color, true);
        pose.popMatrix();
    }

    public static void drawCenteredBoxedString(GuiGraphicsExtractor graphics, Font font, Component text, float x, float y, int color)
    {
        FormattedCharSequence reodering = text.getVisualOrderText();
        Minecraft mc = Minecraft.getInstance();
        float width = font.width(reodering);
        float height = font.lineHeight;
        float width1 = width + 4;
        float height1 = height + 3;
        float x0 = x - width1 / 2;
        fillRect(graphics, x0, y, x0 + width1, y + height1, ((int) Mth.clamp(mc.options.textBackgroundOpacity().get() * ((color >> 24) & 0xFF), 0, 255)) << 24);
        graphics.text(font, reodering, (int) (x - width / 2), (int) (y + 2), color, true);
    }

    private void drawPoi(Player player, GuiGraphicsExtractor graphics, float yaw, float angle, float yDelta, int xPos, PointInfo<?> point, boolean isTargetted, float elapsed, Vec3 subtract)
    {
        var fadeSqr = ConfigData.waypointViewDistance * ConfigData.waypointViewDistance;
        double distance2 = subtract.lengthSqr();

        if (distance2 > fadeSqr)
        {
            return;
        }

        double distance = Math.sqrt(distance2);

        var distanceFade = 1 - Mth.clamp((distance - ConfigData.waypointFadeDistance) / (ConfigData.waypointViewDistance - ConfigData.waypointFadeDistance), 0, 1);

        var alpha = (int) (255 * distanceFade);

        float nDist = angleDistance(yaw, angle);
        if (alpha > 0 && Math.abs(nDist) <= 90)
        {
            float nPos = xPos + nDist;
            var poseStack = graphics.pose();
            poseStack.pushMatrix();
            poseStack.translate(nPos, 0);

            PointRenderer.renderIcon(point, player, textureManager, graphics, 0, 14, alpha);
            boolean showLabel =
                    ConfigData.alwaysShowLabels ||
                            (ConfigData.alwaysShowFocusedLabel && isTargetted) ||
                            (ConfigData.showAllLabelsOnSneak && Minecraft.getInstance().hasShiftDown());

            if (ConfigData.animateLabels)
            {
                if (showLabel && point.fade < 255)
                {
                    point.fade = Math.min(point.fade + 35 * elapsed, 255);
                }
                else if (!showLabel && point.fade > 0)
                {
                    point.fade = Math.max(point.fade - 35 * elapsed, 0);
                }
            }
            else
            {
                point.fade = showLabel ? 255 : 0;
            }

            var pointFade = distanceFade * point.fade;

            if (pointFade > 4)
                PointRenderer.renderLabel(point, font, graphics, 0, 20, (int) pointFade);

            if (point.displayVerticalDistance(player))
            {
                if (yDelta >= 2) drawAboveArrow(graphics, yDelta, alpha);
                if (yDelta <= -2) drawBelowArrow(graphics, yDelta, alpha);
            }

            poseStack.popMatrix();
        }
    }

    // x = -4.0f centers the 8px-wide arrow on the same x=0 the waypoint icon itself is centered
    // on (see BasicIconRenderer, which does x - ICON_WIDTH/2). Upstream hardcodes -4.5f here for
    // both arrows -- a half-pixel-left-of-center offset from the icon, invisible at 1x scale but
    // clearly visible once nearest-neighbor-scaled up at a higher GUI scale. Deliberately
    // corrected rather than matched, unlike most of this port's other upstream-parity choices.
    private void drawAboveArrow(GuiGraphicsExtractor graphics, float yDelta, int alpha)
    {
        var tex = yDelta > 10 ? "above" : "slightly_above";
        var x = -4.0f;
        var y = 4.0f;
        drawMapIcon(graphics, HudCompass.location(tex), x, x + 8, y, y + 8, 1, 1, 1, alpha / 255.0f);
    }

    private void drawBelowArrow(GuiGraphicsExtractor graphics, float yDelta, int alpha)
    {
        var tex = yDelta < -10 ? "below" : "slightly_below";
        var x = -4.0f;
        var y = 16.0f;
        drawMapIcon(graphics, HudCompass.location(tex), x, x + 8, y, y + 8, 1, 1, 1, alpha / 255.0f);
    }

    public static void drawMapIcon(GuiGraphicsExtractor graphics, Identifier spriteName,
                                    float x, float x2, float y, float y2, float r, float g, float b, float a)
    {
        var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.MAP_DECORATIONS).getSprite(spriteName);
        drawSprite(graphics, sprite, x, x2, y, y2, r, g, b, a);
    }

    public static void drawSprite(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
                                   float x, float x2, float y, float y2, float r, float g, float b, float a)
    {
        int color = ARGB.colorFromFloat(a, r, g, b);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                Math.round(x), Math.round(y), Math.round(x2 - x), Math.round(y2 - y), color);
    }

    private float angleDistance(float yaw, float other)
    {
        float dist = other - yaw;
        if (dist > 0)
        {
            return dist > 180 ? (dist - 360) : dist;
        }
        else
        {
            return dist < -180 ? (dist + 360) : dist;
        }
    }

    /**
     * Raw pixel-region blit (used for player-face icons -- a sub-rectangle of a skin texture,
     * not a whole {@code TextureAtlasSprite}). {@code u}/{@code v} are pixel offsets into the
     * texture, matching {@code texWidth}/{@code texHeight}.
     */
    public static void blitRaw(
            GuiGraphicsExtractor graphics,
            Identifier textureLocation,
            float x0, float x1, float y0, float y1,
            float u, float v,
            int texWidth, int texHeight
    )
    {
        graphics.blit(RenderPipelines.GUI_TEXTURED, textureLocation,
                Math.round(x0), Math.round(y0), u, v,
                Math.round(x1 - x0), Math.round(y1 - y0), texWidth, texHeight);
    }

    public static void fillRect(
            GuiGraphicsExtractor graphics,
            float x0, float y0,
            float x1, float y1,
            int color
    )
    {
        graphics.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), color);
    }
}
