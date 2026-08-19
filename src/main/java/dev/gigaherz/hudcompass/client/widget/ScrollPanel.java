package dev.gigaherz.hudcompass.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.Collections;
import java.util.List;

/**
 * Fabric-native replacement for NeoForge's {@code net.neoforged.neoforge.client.gui.widget.ScrollPanel},
 * which {@code ClientWaypointManagerScreen} is built on and has no Fabric equivalent. The scrolling
 * algorithm (scrollbar math, drag/wheel handling, scissor bounds) is carried over faithfully from
 * NeoForge's own implementation (LGPL-2.1, Forge Development LLC and contributors) -- only the
 * rendering surface is adapted, since MC 26.2 replaced the old {@code GuiGraphics}/{@code render()}
 * split with {@code GuiGraphicsExtractor}/{@code extractRenderState()} (see {@code HudOverlay}'s
 * class javadoc for the same adaptation elsewhere in this port).
 */
public abstract class ScrollPanel extends AbstractContainerEventHandler implements Renderable, NarratableEntry
{
    protected final Minecraft minecraft;
    protected final int width;
    protected final int height;
    protected final int top;
    protected final int bottom;
    protected final int right;
    protected final int left;
    private boolean scrolling;
    protected float scrollDistance;
    protected final int border;

    private final int barWidth;
    private final int barLeft;
    private final int barBgColor;
    private final int barColor;
    private final int barBorderColor;

    public ScrollPanel(Minecraft minecraft, int width, int height, int top, int left)
    {
        this(minecraft, width, height, top, left, 4, 6, 0xFF000000, 0xFF808080, 0xFFC0C0C0);
    }

    public ScrollPanel(Minecraft minecraft, int width, int height, int top, int left, int border, int barWidth, int barBgColor, int barColor, int barBorderColor)
    {
        this.minecraft = minecraft;
        this.width = width;
        this.height = height;
        this.top = top;
        this.left = left;
        this.bottom = height + this.top;
        this.right = width + this.left;
        this.barLeft = this.left + this.width - barWidth;
        this.border = border;
        this.barWidth = barWidth;
        this.barBgColor = barBgColor;
        this.barColor = barColor;
        this.barBorderColor = barBorderColor;
    }

    protected abstract int getContentHeight();

    /** Runs AFTER the scissor is enabled. */
    protected void drawBackground(GuiGraphicsExtractor graphics, float partialTick)
    {
        Screen.extractMenuBackgroundTexture(graphics, Screen.MENU_BACKGROUND, left, top, 0.0F, 0.0F, width, height);
    }

    /**
     * Draw the panel's contents. The scissor is enabled for anything rendered outside the view
     * box -- do not touch it unless you support that.
     */
    protected abstract void drawPanel(GuiGraphicsExtractor graphics, int entryRight, int relativeY, int mouseX, int mouseY);

    protected boolean clickPanel(double mouseX, double mouseY, MouseButtonEvent event)
    {
        return false;
    }

    private int getMaxScroll()
    {
        return this.getContentHeight() - (this.height - this.border);
    }

    private void applyScrollLimits()
    {
        int max = getMaxScroll();
        if (max < 0)
            max = 0;
        if (this.scrollDistance < 0.0F)
            this.scrollDistance = 0.0F;
        if (this.scrollDistance > max)
            this.scrollDistance = max;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY)
    {
        if (deltaY != 0)
        {
            this.scrollDistance += (float) (-deltaY * getScrollAmount());
            applyScrollLimits();
            return true;
        }
        return false;
    }

    protected int getScrollAmount()
    {
        return 20;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY)
    {
        return mouseX >= this.left && mouseX < this.right && mouseY >= this.top && mouseY < this.bottom;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (super.mouseClicked(event, doubleClick))
            return true;

        this.scrolling = event.button() == 0 && event.x() >= barLeft && event.x() < right && event.y() >= top && event.y() < bottom;
        if (this.scrolling)
            return true;

        int mouseListY = ((int) event.y()) - this.top - this.getContentHeight() + (int) this.scrollDistance - border;
        if (event.x() >= left && event.x() < right && mouseListY < 0)
        {
            return this.clickPanel(event.x() - left, event.y() - this.top + (int) this.scrollDistance - border, event);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        if (super.mouseReleased(event))
            return true;
        boolean ret = this.scrolling;
        this.scrolling = false;
        return ret;
    }

    private int getBarHeight()
    {
        int barHeight = (height * height) / this.getContentHeight();
        if (barHeight < 32)
            barHeight = 32;
        if (barHeight > height - border * 2)
            barHeight = height - border * 2;
        return barHeight;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY)
    {
        if (this.scrolling)
        {
            int maxScroll = height - getBarHeight();
            double moved = deltaY / maxScroll;
            this.scrollDistance += getMaxScroll() * moved;
            applyScrollLimits();
            return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        graphics.enableScissor(this.left, this.top, this.right, this.bottom);

        this.drawBackground(graphics, partialTick);

        int baseY = this.top + border - (int) this.scrollDistance;
        this.drawPanel(graphics, right, baseY, mouseX, mouseY);

        int extraHeight = (this.getContentHeight() + border) - height;
        if (extraHeight > 0)
        {
            int barHeight = getBarHeight();

            int barTop = (int) this.scrollDistance * (height - barHeight) / extraHeight + this.top;
            if (barTop < this.top)
                barTop = this.top;

            graphics.fill(this.barLeft, this.top, this.barLeft + this.barWidth, this.bottom, this.barBgColor);
            graphics.fill(this.barLeft, barTop, this.barLeft + this.barWidth, barTop + barHeight, this.barColor);
            graphics.fill(this.barLeft, barTop, this.barLeft + this.barWidth - 1, barTop + barHeight - 1, this.barBorderColor);
        }

        graphics.disableScissor();
    }

    public int getTop()
    {
        return top;
    }

    public int getHeight()
    {
        return height;
    }

    public int getBottom()
    {
        return bottom;
    }

    public int getLeft()
    {
        return left;
    }

    public int getRight()
    {
        return right;
    }

    @Override
    public List<? extends GuiEventListener> children()
    {
        return Collections.emptyList();
    }
}
