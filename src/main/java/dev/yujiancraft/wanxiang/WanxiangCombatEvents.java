package dev.yujiancraft.wanxiang;

import dev.yujiancraft.YujianCraft;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/** Makes every usable flying sword retain an ordinary vanilla left-click attack. */
@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID)
public final class WanxiangCombatEvents {
    private WanxiangCombatEvents() {
    }

    @SubscribeEvent
    public static void applyPierceDamageToMainHand(ItemAttributeModifierEvent event) {
        if (!WanxiangSwordData.isUsable(event.getItemStack())) return;
        event.removeAllModifiersFor(Attributes.ATTACK_DAMAGE);
        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                FlyingSwordDamage.PIERCE_DAMAGE_MODIFIER_ID,
                FlyingSwordDamage.itemBaseDamage(event.getItemStack()) - 1.0D,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
    }
}
