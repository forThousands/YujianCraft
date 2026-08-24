package dev.yujiancraft.client;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.upgrade.FlyingSwordModule;
import dev.yujiancraft.upgrade.SwordModuleData;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import dev.yujiancraft.wanxiang.FlyingSwordDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds Yujian Craft information to tempered items without replacing their original item class. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class WanxiangClientEvents {
    private WanxiangClientEvents() {
    }

    @SubscribeEvent
    public static void addTemperedTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!WanxiangSwordData.isUsable(stack)) return;
        double currentDamage = FlyingSwordDamage.currentDamage(event.getEntity(), stack);
        rewriteAttackDamageTooltip(event, currentDamage);
        event.getToolTip().add(Component.translatable("tooltip.yujiancraft.pierce_damage",
                        formatAttributeValue(currentDamage))
                .withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(Component.translatable("tooltip.yujiancraft.temper_count",
                        WanxiangSwordData.temperCount(stack), WanxiangSwordData.MAX_TEMPERINGS)
                .withStyle(ChatFormatting.DARK_PURPLE));
        if (!WanxiangSwordData.isTempered(stack)) return;
        event.getToolTip().add(Component.translatable("tooltip.yujiancraft.wanxiang.title")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        event.getToolTip().add(Component.translatable("tooltip.yujiancraft.wanxiang.core",
                        Component.translatable(WanxiangSwordData.material(stack).translationKey()))
                .withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable("tooltip.yujiancraft.wanxiang.render",
                        Component.translatable(WanxiangSwordData.renderPreset(stack).translationKey()))
                .withStyle(ChatFormatting.DARK_GRAY));
        for (FlyingSwordModule module : FlyingSwordModule.values()) {
            int level = SwordModuleData.getLevel(stack, module);
            if (level > 0) {
                event.getToolTip().add(Component.translatable("tooltip.yujiancraft.module",
                                Component.translatable(module.translationKey()), romanLevel(level))
                        .withStyle(module == FlyingSwordModule.UNBREAKABLE
                                ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
            }
        }
        int virtualDurability = SwordModuleData.virtualDurabilityRemaining(stack);
        if (virtualDurability > 0) {
            event.getToolTip().add(Component.translatable(
                            "tooltip.yujiancraft.wanxiang.virtual_durability", virtualDurability)
                    .withStyle(ChatFormatting.GREEN));
        }
        event.getToolTip().add(Component.translatable("tooltip.yujiancraft.wanxiang.summon")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String romanLevel(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(level);
        };
    }

    /**
     * Vanilla's attribute section only displays the raw ATTACK_DAMAGE modifier and therefore omits
     * Sharpness. Combat and piercing already use {@link FlyingSwordDamage#currentDamage}; replace
     * only the matching tooltip line so the visible main-hand damage follows that same live value.
     */
    private static void rewriteAttackDamageTooltip(ItemTooltipEvent event, double currentDamage) {
        for (int index = 0; index < event.getToolTip().size(); index++) {
            Component line = event.getToolTip().get(index);
            if (!isAttackDamageModifierLine(line)) continue;
            Component replacement = Component.translatable(
                    "attribute.modifier.equals.0",
                    formatAttributeValue(currentDamage),
                    Component.translatable(Attributes.ATTACK_DAMAGE.getDescriptionId()))
                    .setStyle(line.getStyle());
            event.getToolTip().set(index, replacement);
            return;
        }
    }

    private static boolean isAttackDamageModifierLine(Component line) {
        if (!(line.getContents() instanceof TranslatableContents modifier)) return false;
        String key = modifier.getKey();
        if (!key.startsWith("attribute.modifier.")) return false;
        for (Object argument : modifier.getArgs()) {
            if (argument instanceof Component component
                    && component.getContents() instanceof TranslatableContents attribute
                    && Attributes.ATTACK_DAMAGE.getDescriptionId().equals(attribute.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static String formatAttributeValue(double value) {
        return new java.text.DecimalFormat("#.##",
                java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT)).format(value);
    }
}
