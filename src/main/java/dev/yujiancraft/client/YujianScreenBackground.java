package dev.yujiancraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Consistent, blur-free background used by YujianCraft's full-screen and menu interfaces. */
final class YujianScreenBackground {
    private YujianScreenBackground() {
    }

    static void render(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xC0101419);
    }

    static void renderSlotFrames(GuiGraphics graphics, AbstractContainerMenu menu,
                                 int left, int top) {
        for (Slot slot : menu.slots) {
            int x = left + slot.x;
            int y = top + slot.y;
            // One-pixel jade rim, a restrained inner bevel and a dark translucent well.
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xD065A9AD);
            graphics.fill(x, y, x + 16, y + 16, 0xB00A1116);
            graphics.fill(x, y, x + 16, y + 1, 0xC0B2DDD2);
            graphics.fill(x, y, x + 1, y + 16, 0xC0B2DDD2);
            graphics.fill(x, y + 15, x + 16, y + 16, 0xC0386269);
            graphics.fill(x + 15, y, x + 16, y + 16, 0xC0386269);
        }
    }
}
