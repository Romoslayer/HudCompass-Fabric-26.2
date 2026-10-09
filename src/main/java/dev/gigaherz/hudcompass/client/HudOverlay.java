package dev.gigaherz.hudcompass.client;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.MapDecorationSprites;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import dev.gigaherz.hudcompass.waypoints.client.PointRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The compass HUD overlay itself. Ported from upstream's NeoForge {@code GuiLayer}
 * implementation, registered via Fabric API's {@code HudRenderCallback} in {@link ClientHandler}.
 * <p>
 * 1.20.1 backport notes, versus the 26.x build this was ported down from:
 * <ul>
 *     <li>{@link #fillRect} and {@link #blitRaw} draw immediate-mode quads through the
 *     {@code Tesselator}, the way upstream's own 1.20.1 branch does. 1.20.1 has no GUI
 *     render-state system to submit custom elements to (that arrived in 1.21.6), but this keeps
 *     the same true sub-pixel float positioning the 26.x build's custom render states give.</li>
 *     <li>Icons are drawn by sprite name without an atlas -- see {@link #drawMapIcon}.</li>
 *     <li>The boss-bar push-down lives in {@code BossHealthOverlayMixin}, not a
 *     {@code HudElementRegistry.replaceElement} wrapper -- 1.20.1's Fabric API has no HUD layer
 *     registry.</li>
 *     <li>The F1 hide-HUD toggle is honoured here ({@link #canRender()}), which the 26.x build
 *     can't do: {@code Options.hideGui} is still public on 1.20.1, and Fabric's
 *     {@code HudRenderCallback} fires at the tail of {@code Gui#render}, outside vanilla's own
 *     hideGui check, so this callback would otherwise keep drawing under F1.</li>
 * </ul>
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

    public void render(GuiGraphics graphics, float _partialTicks)
    {
        if (!canRender()) return;

        if (mc.player == null) return;

        renderCompass(graphics, _partialTicks);
    }

    private void renderCompass(GuiGraphics graphics, float _partialTicks)
    {
        boolean isPaused = mc.isPaused();

        // No DeltaTracker on 1.20.1: same values, from where upstream's own 1.20.1 branch reads
        // them -- the frame's length in ticks, and the partial tick the HUD callback passes in.
        float elapsed = isPaused ? 0 : mc.getDeltaFrameTime();
        float partialTicks = isPaused ? 0 : _partialTicks;

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

        if (mc.options.hideGui)
            return false;

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

    private static final TagKey<Item> MAKES_HUDCOMPASS_VISIBLE = TagKey.create(Registries.ITEM, new ResourceLocation("hudcompass", "makes_hudcompass_visible"));

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

    private void drawCardinalDirection(GuiGraphics graphics, float yaw, float angle, int xPos, String text)
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

    // GuiGraphics#drawString only takes int coordinates on 1.20.1 (Forge patches in a float
    // overload, which is what upstream's own 1.20.1 branch relies on -- vanilla/Fabric don't have
    // it), so the fractional part is applied as a pose translation around an int-positioned draw.
    public void drawCenteredShadowString(GuiGraphics graphics, Font font, String text, float x, float y, int color)
    {
        float width = font.width(text);

        var xPos = (x - width / 2);
        var xInt = (int) xPos;
        var yInt = (int) y;
        var xFract = xPos - xInt;
        var yFract = y - yInt;

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(xFract, yFract, 0);
        graphics.drawString(font, text, xInt, yInt, color, true);
        pose.popPose();
    }

    public static void drawCenteredBoxedString(GuiGraphics graphics, Font font, String text, float x, float y, int color)
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
        pose.pushPose();
        pose.translate(xFract, yFract, 0);
        graphics.drawString(font, text, xInt, yInt, color, true);
        pose.popPose();
    }

    public static void drawCenteredBoxedString(GuiGraphics graphics, Font font, Component text, float x, float y, int color)
    {
        FormattedCharSequence reodering = text.getVisualOrderText();
        Minecraft mc = Minecraft.getInstance();
        float width = font.width(reodering);
        float height = font.lineHeight;
        float width1 = width + 4;
        float height1 = height + 3;
        float x0 = x - width1 / 2;
        fillRect(graphics, x0, y, x0 + width1, y + height1, ((int) Mth.clamp(mc.options.textBackgroundOpacity().get() * ((color >> 24) & 0xFF), 0, 255)) << 24);
        graphics.drawString(font, reodering, (int) (x - width / 2), (int) (y + 2), color, true);
    }

    private void drawPoi(Player player, GuiGraphics graphics, float yaw, float angle, float yDelta, int xPos, PointInfo<?> point, boolean isTargetted, float elapsed, Vec3 subtract)
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
            poseStack.pushPose();
            poseStack.translate(nPos, 0, 0);

            PointRenderer.renderIcon(point, player, textureManager, graphics, 0, 14, alpha);
            boolean showLabel =
                    ConfigData.alwaysShowLabels ||
                            (ConfigData.alwaysShowFocusedLabel && isTargetted) ||
                            (ConfigData.showAllLabelsOnSneak && Screen.hasShiftDown());

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

            poseStack.popPose();
        }
    }

    // x = -4.5f, NOT -4.0f (which would just match the icon's own x - ICON_WIDTH/2 centering):
    // an earlier pass "corrected" this to -4.0f assuming both sprites were symmetric the same way
    // within their 8px canvas, which was wrong. Pixel-inspecting the actual PNGs
    // (above/below/slightly_above/slightly_below, all 8x8) shows their triangle content is
    // consistently drawn off-center -- content spans columns 2-6 (local x [2,7), content-center
    // 4.5) rather than the canvas's own geometric center (4.0), unlike generic.png's diamond,
    // whose content genuinely is centered at 4.0. -4.5f is upstream's deliberate compensation for
    // that 0.5px artwork bias, producing correctly-centered final output -- verified by computing
    // both sprites' actual content-center from their pixel data, not by eyeballing a screenshot.
    private void drawAboveArrow(GuiGraphics graphics, float yDelta, int alpha)
    {
        var tex = yDelta > 10 ? "above" : "slightly_above";
        var x = -4.5f;
        var y = 4.0f;
        drawMapIcon(graphics, HudCompass.location(tex), x, x + 8, y, y + 8, 1, 1, 1, alpha / 255.0f);
    }

    private void drawBelowArrow(GuiGraphics graphics, float yDelta, int alpha)
    {
        var tex = yDelta < -10 ? "below" : "slightly_below";
        var x = -4.5f;
        var y = 16.0f;
        drawMapIcon(graphics, HudCompass.location(tex), x, x + 8, y, y + 8, 1, 1, 1, alpha / 255.0f);
    }

    private static final ResourceLocation MAP_ICONS = new ResourceLocation("textures/map/map_icons.png");
    private static final Map<ResourceLocation, ResourceLocation> ICON_TEXTURES = new HashMap<>();

    /**
     * Draws an icon by sprite name. 1.20.1 has no map-decorations atlas (it arrived in 1.20.5), so
     * a name is resolved here instead of looked up in one:
     * <ul>
     *     <li>a vanilla map decoration ({@code minecraft:red_x}, ...) draws its 8x8 cell of
     *     {@code textures/map/map_icons.png}, the 16x16-cell sheet 1.20.1's map renderer uses --
     *     see {@link MapDecorationSprites};</li>
     *     <li>anything else is one of this mod's icons and draws its own
     *     {@code <namespace>:textures/map/decorations/<path>.png} whole -- the same PNG that later
     *     versions stitch into the atlas.</li>
     * </ul>
     */
    public static void drawMapIcon(GuiGraphics graphics, ResourceLocation spriteName,
                                   float x, float x2, float y, float y2, float r, float g, float b, float a)
    {
        MapDecoration.Type vanilla = MapDecorationSprites.typeFor(spriteName);
        if (vanilla != null)
        {
            int index = vanilla.getIcon();
            float u0 = (index % 16) / 16.0f;
            float v0 = (index / 16) / 16.0f;
            blitRaw(graphics, MAP_ICONS, x, x2, y, y2, u0, v0, u0 + 1 / 16.0f, v0 + 1 / 16.0f, r, g, b, a);
            return;
        }

        ResourceLocation texture = ICON_TEXTURES.computeIfAbsent(spriteName,
                name -> new ResourceLocation(name.getNamespace(), "textures/map/decorations/" + name.getPath() + ".png"));
        blitRaw(graphics, texture, x, x2, y, y2, 0, 0, 1, 1, r, g, b, a);
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
     * True sub-pixel-precision blit: an immediate-mode textured quad at float coordinates, as in
     * upstream's own 1.20.1 branch.
     * <p>
     * UV parameters are {@code (u0, v0, u1, v1)} -- min corner then max corner -- which is what
     * every caller passes. The 26.x build names these {@code (u0, u1, v0, v1)} and compensates
     * with an argument shuffle in its render-state record's constructor (inherited verbatim from
     * upstream); the net effect there is this same mapping, so here the parameters are simply
     * named for what they are.
     */
    public static void blitRaw(
            GuiGraphics graphics,
            ResourceLocation atlasLocation,
            float x0, float x1, float y0, float y1,
            float u0, float v0, float u1, float v1,
            float r, float g, float b, float a
    )
    {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, atlasLocation);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(matrix, x0, y0, 0).uv(u0, v0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y1, 0).uv(u0, v1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, 0).uv(u1, v1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y0, 0).uv(u1, v0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.disableBlend();
    }

    public static void fillRect(
            GuiGraphics graphics,
            float x0, float y0,
            float x1, float y1,
            int color
    )
    {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, x0, y0, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y0, 0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.disableBlend();
    }
}
