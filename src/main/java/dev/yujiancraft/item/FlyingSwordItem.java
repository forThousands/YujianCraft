package dev.yujiancraft.item;

import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.formation.FormationMode;
import dev.yujiancraft.combat.SwordSettings;
import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.combat.SwordEffectEngine;
import dev.yujiancraft.combat.ManualGuidanceManager;
import dev.yujiancraft.upgrade.FlyingSwordModule;
import dev.yujiancraft.upgrade.SwordModuleData;
import dev.yujiancraft.client.ClientOptions;
import dev.yujiancraft.visual.FlyingSwordSeries;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import dev.yujiancraft.wanxiang.WanxiangWeaponCatalog;
import dev.yujiancraft.wanxiang.ManualSpiritTrialManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public final class FlyingSwordItem extends SwordItem {
    public static final int FORMATION_SIZE = 6;
    public static final String ENTITY_DISPLAY_TAG = "YujianCraftEntityDisplay";
    private static final String MODE_TAG = "FormationMode";
    private final FlyingSwordMaterial material;
    private final FlyingSwordSeries series;

    public FlyingSwordItem(FlyingSwordMaterial material, FlyingSwordSeries series, Properties properties) {
        // All vanilla sword tiers use an attack-damage modifier of 3. The dynamic Yujian modifier
        // replaces that damage later, while SwordItem keeps the correct 1.6 attack speed and
        // vanilla sword-enchantment behaviour.
        super(material.vanillaTier(), 3, -2.4F, properties);
        this.material = material;
        this.series = series;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        toggleSummonedFormation(serverPlayer, stack);
        player.getCooldowns().addCooldown(this, 10);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        SwordSettings settings = SwordSettings.read(stack);
        tooltip.add(Component.translatable("tooltip.yujiancraft.formation",
                Component.translatable(getFormationMode(stack).translationKey())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.yujiancraft.targeting",
                Component.translatable(settings.targetingMode().translationKey())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.yujiancraft.attack",
                Component.translatable(settings.attackMode().translationKey())).withStyle(ChatFormatting.GRAY));
        if (settings.targetingMode() == dev.yujiancraft.combat.TargetingMode.MANUAL_GUIDANCE) {
            tooltip.add(Component.translatable("tooltip.yujiancraft.manual_controls")
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.yujiancraft.minimum_dock",
                String.format(java.util.Locale.ROOT, "%.2f", settings.minimumDockTicks() / 20.0D))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.yujiancraft.automatic_radius",
                String.format(java.util.Locale.ROOT, "%.0f", settings.automaticTargetRadius()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.yujiancraft.lock_radius",
                String.format(java.util.Locale.ROOT, "%.0f", settings.crosshairLockRadius()))
                .withStyle(ChatFormatting.GRAY));
        for (FlyingSwordModule module : FlyingSwordModule.values()) {
            int moduleLevel = SwordModuleData.getLevel(stack, module);
            if (moduleLevel > 0) {
                tooltip.add(Component.translatable("tooltip.yujiancraft.module",
                        Component.translatable(module.translationKey()), romanLevel(moduleLevel))
                        .withStyle(module == FlyingSwordModule.UNBREAKABLE
                                ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
            }
        }
        tooltip.add(Component.translatable("tooltip.yujiancraft.switch_mode").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        int reinforcement = SwordModuleData.getLevel(stack, FlyingSwordModule.DURABILITY);
        return material.durability() + SwordEffectEngine.durabilityBonus(reinforcement);
    }

    @Override
    public boolean isValidRepairItem(ItemStack sword, ItemStack ingredient) {
        return material.isRepairIngredient(ingredient) || super.isValidRepairItem(sword, ingredient);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        boolean entityDisplay = stack.hasTag() && stack.getTag().getBoolean(ENTITY_DISPLAY_TAG);
        return super.isFoil(stack) || !entityDisplay && ClientOptions.inventoryGlint();
    }

    private static String romanLevel(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(level);
        };
    }

    public static FormationMode getFormationMode(ItemStack stack) {
        return stack.hasTag() ? FormationMode.fromName(stack.getTag().getString(MODE_TAG)) : FormationMode.FAN_ALIGNED;
    }

    public FlyingSwordMaterial getMaterialType() {
        return material;
    }

    public FlyingSwordSeries getSeries() {
        return series;
    }

    public static void toggleFormationMode(ServerPlayer player) {
        ItemStack stack = findFlyingSword(player);
        if (stack.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.no_sword"), true);
            return;
        }

        FormationMode mode = getFormationMode(stack).next();
        stack.getOrCreateTag().putString(MODE_TAG, mode.serializedName());
        getOwnedFormationSwords(player).forEach(sword -> sword.setFormationMode(mode));
        player.displayClientMessage(Component.translatable("message.yujiancraft.formation_changed",
                Component.translatable(mode.translationKey())), true);
    }

    public static SwordSettings getSettings(ServerPlayer player) {
        ItemStack stack = findFlyingSword(player);
        return stack.isEmpty() ? SwordSettings.defaults() : SwordSettings.read(stack);
    }

    public static void setSettings(ServerPlayer player, SwordSettings settings) {
        ItemStack stack = findFlyingSword(player);
        if (!stack.isEmpty()) {
            settings.write(stack);
        }
        getOwnedFormationSwords(player).forEach(sword -> sword.applySettings(settings));
    }

    public static List<FlyingSwordEntity> getOwnedFormationSwords(ServerPlayer player) {
        List<FlyingSwordEntity> result = new ArrayList<>();
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof FlyingSwordEntity sword && sword.isOwnedBy(player)
                    && sword.isFormationSword()) result.add(sword);
        }
        return result;
    }

    public static List<FlyingSwordEntity> ensureFormation(ServerPlayer player, ItemStack stack) {
        List<FlyingSwordEntity> existing = getOwnedFormationSwords(player);
        if (!existing.isEmpty() || !isUsableFlyingSword(stack) || !catalogueAllows(player, stack)) return existing;
        summonFormation(player, stack);
        return getOwnedFormationSwords(player);
    }

    public static boolean toggleSummonedFormation(ServerPlayer player, ItemStack requestedStack) {
        ItemStack stack = isUsableFlyingSword(requestedStack) ? requestedStack : findFlyingSword(player);
        if (stack.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.no_sword"), true);
            return false;
        }
        if (!catalogueAllows(player, stack)) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.wanxiang.disabled"), true);
            return false;
        }
        List<FlyingSwordEntity> activeSwords = getOwnedFormationSwords(player);
        if (!activeSwords.isEmpty()) {
            ManualGuidanceManager.cancel(player);
            activeSwords.forEach(Entity::discard);
        } else {
            summonFormation(player, stack);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN,
                SoundSource.PLAYERS, 0.8F, activeSwords.isEmpty() ? 1.4F : 0.9F);
        return true;
    }

    private static boolean catalogueAllows(ServerPlayer player, ItemStack stack) {
        return !WanxiangSwordData.isTempered(stack)
                || WanxiangWeaponCatalog.enabled(player.server, stack);
    }

    private static void summonFormation(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        WanxiangSwordData.ensureBinding(stack);
        FormationMode formationMode = getFormationMode(stack);
        SwordSettings settings = SwordSettings.read(stack);
        int formationSize = ManualSpiritTrialManager.formationSize(player, FORMATION_SIZE);
        for (int slot = 0; slot < formationSize; slot++) {
            FlyingSwordEntity sword = ModEntities.FLYING_SWORD.get().create(level);
            if (sword == null) continue;
            sword.bindTo(player, slot, formationMode, settings, stack);
            sword.moveTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), 0.0F);
            level.addFreshEntity(sword);
        }
    }

    public static boolean isUsableFlyingSword(ItemStack stack) {
        return WanxiangSwordData.isUsable(stack);
    }

    public static ItemStack findFlyingSword(ServerPlayer player) {
        if (isUsableFlyingSword(player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        if (isUsableFlyingSword(player.getOffhandItem())) {
            return player.getOffhandItem();
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isUsableFlyingSword(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findFlyingSword(ServerPlayer player, UUID bindingId) {
        if (bindingId == null) return ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isUsableFlyingSword(stack) && bindingId.equals(WanxiangSwordData.binding(stack))) return stack;
        }
        ItemStack offhand = player.getOffhandItem();
        return isUsableFlyingSword(offhand) && bindingId.equals(WanxiangSwordData.binding(offhand))
                ? offhand : ItemStack.EMPTY;
    }

    public static ItemStack findFlyingSword(ServerPlayer player, FlyingSwordMaterial material,
                                            FlyingSwordSeries series) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof FlyingSwordItem sword
                    && sword.material == material && sword.series == series) return stack;
        }
        if (player.getOffhandItem().getItem() instanceof FlyingSwordItem sword
                && sword.material == material && sword.series == series) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }
}
