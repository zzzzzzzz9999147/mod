package com.hjz.magicinduction.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 「按住 W 思考」弹出的用途说明书界面。
 *
 * purpose: 玩家在物品栏里悬停任意本 mod 物品、按住 W，就能看到它在整个循环里的位置，
 *          不必退出游戏去翻文档。
 * player experience goal: 像翻一本薄手册——左页是物品本体，右页是它的一句话结论和操作步骤。
 * inputs: 键盘 ← → / A D 翻页，ESC 关闭；鼠标悬停物品 + W 打开（由 ClientForgeEvents 触发）。
 * outputs: 关闭后返回打开前的界面（物品栏），而不是退回游戏画面。
 * edge cases: 单页内容不显示翻页箭头；正文超长时截断而不是溢出面板。
 * failure states: 若某物品没有 Ponder 数据，ClientForgeEvents 根本不会打开这个界面。
 */
public class PonderScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 180;
    private static final int TEXT_WIDTH = 188;
    private static final int LINE_HEIGHT = 10;

    private final Screen previousScreen;
    private final ItemStack stack;
    private final List<PonderEntries.Page> pages;
    private int page;

    public PonderScreen(Screen previousScreen, ItemStack stack) {
        super(Component.translatable("ponder.magicinduction.title"));
        this.previousScreen = previousScreen;
        this.stack = stack;
        this.pages = PonderEntries.pagesFor(stack);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC00B0812);

        int x0 = (this.width - PANEL_W) / 2;
        int y0 = (this.height - PANEL_H) / 2;

        // 面板与描边：上左亮、下右暗，制造一点厚度
        graphics.fill(x0, y0, x0 + PANEL_W, y0 + PANEL_H, 0xF0181426);
        graphics.fill(x0, y0, x0 + PANEL_W, y0 + 1, 0xFF7C4DD8);
        graphics.fill(x0, y0, x0 + 1, y0 + PANEL_H, 0xFF7C4DD8);
        graphics.fill(x0, y0 + PANEL_H - 1, x0 + PANEL_W, y0 + PANEL_H, 0xFF3A2160);
        graphics.fill(x0 + PANEL_W - 1, y0, x0 + PANEL_W, y0 + PANEL_H, 0xFF3A2160);

        graphics.drawString(this.font, this.stack.getHoverName(), x0 + 16, y0 + 11, 0xFFD8B0FF, false);
        graphics.fill(x0 + 12, y0 + 25, x0 + PANEL_W - 12, y0 + 26, 0xFF3A2160);

        // 左页：物品图标放大 4 倍
        graphics.pose().pushPose();
        graphics.pose().translate(x0 + 22.0F, y0 + 50.0F, 120.0F);
        graphics.pose().scale(4.0F, 4.0F, 4.0F);
        graphics.renderItem(this.stack, 0, 0);
        graphics.pose().popPose();

        if (this.pages.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("ponder.magicinduction.empty"),
                    x0 + 108, y0 + 44, 0xFF9A8AB0, false);
        } else {
            PonderEntries.Page current = this.pages.get(this.page);
            graphics.drawString(this.font, current.title(), x0 + 106, y0 + 38, 0xFF9BE0C0, false);

            List<FormattedCharSequence> lines = this.font.split(current.body(), TEXT_WIDTH);
            int lineY = y0 + 54;
            int bottomLimit = y0 + PANEL_H - 28;
            for (FormattedCharSequence line : lines) {
                if (lineY > bottomLimit) {
                    break;
                }
                graphics.drawString(this.font, line, x0 + 106, lineY, 0xFFCFC6DE, false);
                lineY += LINE_HEIGHT;
            }
        }

        // 页码
        String counter = (this.page + 1) + " / " + this.pages.size();
        graphics.drawString(this.font, counter,
                x0 + PANEL_W - 14 - this.font.width(counter), y0 + PANEL_H - 15, 0xFF8A7BA8, false);

        // 只有多页时才提示翻页，单页显示关闭提示，避免承诺不存在的操作
        Component hint = this.pages.size() > 1
                ? Component.translatable("ponder.magicinduction.hint_pages")
                : Component.translatable("ponder.magicinduction.hint_close");
        graphics.drawString(this.font, hint, x0 + 14, y0 + PANEL_H - 15, 0xFF6E6288, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
            if (this.page > 0) {
                this.page--;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
            if (this.page < this.pages.size() - 1) {
                this.page++;
            }
            return true;
        }
        // 吞掉其他按键：翻手册时按 E / W 不应该顺手把背包关了或触发原界面快捷键
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.previousScreen);
        }
    }

    /** 不暂停游戏：多人环境下打开说明书不该冻结服务端 tick。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
