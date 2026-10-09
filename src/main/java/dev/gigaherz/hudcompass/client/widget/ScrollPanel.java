package dev.gigaherz.hudcompass.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collections;
import java.util.List;

/**
 * Fabric-native replacement for NeoForge's {@code net.neoforged.neoforge.client.gui.widget.ScrollPanel},
 * which {@code ClientWaypointManagerScreen} is built on and has no Fabric equivalent. The scrolling
 * algorithm (scrollbar math, drag/wheel handling, scissor bounds) is carried over faithfully from
 * NeoForge's own implementation (LGPL-2.1, Forge Development LLC and contributors) -- only the
 * rendering and input surface is adapted, to 1.20.1's {@code GuiGraphics}/{@code render()} and
 * {@code (mouseX, mouseY, button)} event methods (the 26.x build targets {@code extractRenderState()}
 * and {@code MouseButtonEvent} records instead).
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

    /**
     * Runs AFTER the scissor is enabled. 1.20.1 predates the menu-background textures the newer
     * builds use here, so this draws what Forge's 1.20.1 {@code ScrollPanel} (the one upstream's
     * 1.20.1 editor was built on) draws: a dark translucent gradient in-world, dark dirt otherwise.
     */
    protected void drawBackground(GuiGraphics graphics, float partialTick)
    {
        if (minecraft.level != null)
        {
            graphics.fillGradient(left, top, right, bottom, 0xC0101010, 0xD0101010);
        }
        else
        {
            graphics.setColor(0.125F, 0.125F, 0.125F, 1.0F);
            graphics.blit(Screen.BACKGROUND_LOCATION, left, top, 0.0F, scrollDistance, width, height, 32, 32);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * Draw the panel's contents. The scissor is enabled for anything rendered outside the view
     * box -- do not touch it unless you support that.
     */
    protected abstract void drawPanel(GuiGraphics graphics, int entryRight, int relativeY, int mouseX, int mouseY);

    protected boolean clickPanel(double mouseX, double mouseY, int button)
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (delta != 0)
        {
            this.scrollDistance += (float) (-delta * getScrollAmount());
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
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (super.mouseClicked(mouseX, mouseY, button))
            return true;

        this.scrolling = button == 0 && mouseX >= barLeft && mouseX < right && mouseY >= top && mouseY < bottom;
        if (this.scrolling)
            return true;

        int mouseListY = ((int) mouseY) - this.top - this.getContentHeight() + (int) this.scrollDistance - border;
        if (mouseX >= left && mouseX < right && mouseListY < 0)
        {
            return this.clickPanel(mouseX - left, mouseY - this.top + (int) this.scrollDistance - border, button);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (super.mouseReleased(mouseX, mouseY, button))
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
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
