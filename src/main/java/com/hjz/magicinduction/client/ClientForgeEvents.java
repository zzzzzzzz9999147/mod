package com.hjz.magicinduction.client;

import com.hjz.magicinduction.MagicInduction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端运行时事件：把"鼠标悬停 + 按住 W"翻译成"打开 Ponder 界面"。
 *
 * purpose: 在不接管鼠标的前提下，给玩家一条发现物品用途的捷径。
 * design note: 怎么知道玩家现在悬停哪个物品——这是本功能唯一的难点。
 *   AbstractContainerScreen.getSlotUnderMouse() 是 protected，用反射或 AccessTransformer
 *   都要引入额外维护成本。这里改用 ItemTooltipEvent：tooltip 每帧渲染一次，
 *   顺手记下最近一次被渲染 tooltip 的物品栈，就等于拿到了"鼠标下的物品"，
 *   而且完全不碰 protected 成员。
 * edge cases:
 *   - 记录会过期：鼠标移开后 tooltip 不再渲染，但字段还留着旧值，
 *     所以用 300ms 的时间窗判定"这个悬停还算数"。
 *   - 创造模式搜索框里打字也想用 W 键：检测到有 EditBox 持有焦点时直接放行，
 *     否则玩家永远打不出字母 W。
 * failure states: 若不做上述两点，会出现"移开鼠标后按 W 仍然弹窗"和"搜索框打不出 W"。
 */
@Mod.EventBusSubscriber(modid = MagicInduction.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {

    /** 悬停记录的保鲜期：超过这个时间就认为鼠标已经不在那个物品上了。 */
    private static final long HOVER_FRESH_MS = 300L;

    private static ItemStack lastHovered = ItemStack.EMPTY;
    private static long lastHoverStamp = 0L;

    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        lastHovered = stack;
        lastHoverStamp = System.currentTimeMillis();

        // 提示行必须存在，否则这个功能等于不存在——玩家没有理由去猜"按 W"。
        if (PonderEntries.hasPonder(stack)) {
            event.getToolTip().add(Component.translatable("ponder.magicinduction.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() != GLFW.GLFW_KEY_W) {
            return;
        }
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        // 搜索框 / 铁砧命名框有焦点时把 W 还给输入框
        if (hasFocusedTextBox(screen)) {
            return;
        }
        if (System.currentTimeMillis() - lastHoverStamp > HOVER_FRESH_MS) {
            return;
        }
        if (!PonderEntries.hasPonder(lastHovered)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new PonderScreen(screen, lastHovered.copy()));
        event.setCanceled(true);
    }

    private static boolean hasFocusedTextBox(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isFocused()) {
                return true;
            }
        }
        return false;
    }
}
