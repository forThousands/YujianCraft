package dev.yujiancraft.wanxiang;

import dev.yujiancraft.YujianCraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Makes every usable flying sword retain an ordinary vanilla left-click attack. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WanxiangCombatEvents {
    private WanxiangCombatEvents() {
    }

    @SubscribeEvent
    public static void applyPierceDamageToMainHand(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND
                || !WanxiangSwordData.isUsable(event.getItemStack())) return;
        event.removeAttribute(Attributes.ATTACK_DAMAGE);
        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                FlyingSwordDamage.PIERCE_DAMAGE_MODIFIER_ID,
                "Yujian Craft piercing damage",
                FlyingSwordDamage.itemBaseDamage(event.getItemStack()) - 1.0D,
                AttributeModifier.Operation.ADDITION));
    }
}
