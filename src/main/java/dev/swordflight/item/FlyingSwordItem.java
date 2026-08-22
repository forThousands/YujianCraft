package dev.swordflight.item;

import dev.swordflight.entity.FlyingSwordEntity;
import dev.swordflight.formation.FormationMode;
import dev.swordflight.combat.SwordSettings;
import dev.swordflight.registry.ModEntities;
import dev.swordflight.material.FlyingSwordMaterial;
import dev.swordflight.combat.SwordEffectEngine;
import dev.swordflight.combat.ManualGuidanceManager;
import dev.swordflight.upgrade.FlyingSwordModule;
import dev.swordflight.upgrade.SwordModuleData;
import dev.swordflight.client.ClientOptions;
import dev.swordflight.visual.FlyingSwordSeries;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.ArrayList;

public final class FlyingSwordItem extends Item {
    public static final int FORMATION_SIZE = 6;
    public static final String ENTITY_DISPLAY_TAG = "SwordflightEntityDisplay";
    private static final String MODE_TAG = "FormationMode";
    private final FlyingSwordMaterial material;
    private final FlyingSwordSeries series;

    public FlyingSwordItem(FlyingSwordMaterial material, FlyingSwordSeries series, Properties properties) {
        super(properties);
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

        List<FlyingSwordEntity> activeSwords = getOwnedFormationSwords(serverPlayer);

        if (!activeSwords.isEmpty()) {
            ManualGuidanceManager.cancel(serverPlayer);
            activeSwords.forEach(Entity::discard);
        } else {
            summonFormation(serverPlayer, stack);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN,
                SoundSource.PLAYERS, 0.8F, activeSwords.isEmpty() ? 1.4F : 0.9F);
        player.getCooldowns().addCooldown(this, 10);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        SwordSettings settings = SwordSettings.read(stack);
        tooltip.add(Component.translatable("tooltip.swordflight.formation",
                Component.translatable(getFormationMode(stack).translationKey())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.swordflight.targeting",
                Component.translatable(settings.targetingMode().translationKey())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.swordflight.attack",
                Component.translatable(settings.attackMode().translationKey())).withStyle(ChatFormatting.GRAY));
        if (settings.targetingMode() == dev.swordflight.combat.TargetingMode.MANUAL_GUIDANCE) {
            tooltip.add(Component.translatable("tooltip.swordflight.manual_controls")
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.swordflight.minimum_dock",
                String.format(java.util.Locale.ROOT, "%.2f", settings.minimumDockTicks() / 20.0D))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.swordflight.automatic_radius",
                String.format(java.util.Locale.ROOT, "%.0f", settings.automaticTargetRadius()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.swordflight.lock_radius",
                String.format(java.util.Locale.ROOT, "%.0f", settings.crosshairLockRadius()))
                .withStyle(ChatFormatting.GRAY));
        for (FlyingSwordModule module : FlyingSwordModule.values()) {
            int moduleLevel = SwordModuleData.getLevel(stack, module);
            if (moduleLevel > 0) {
                tooltip.add(Component.translatable("tooltip.swordflight.module",
                        Component.translatable(module.translationKey()), romanLevel(moduleLevel))
                        .withStyle(module == FlyingSwordModule.UNBREAKABLE
                                ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
            }
        }
        tooltip.add(Component.translatable("tooltip.swordflight.switch_mode").withStyle(ChatFormatting.DARK_GRAY));
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
            player.displayClientMessage(Component.translatable("message.swordflight.no_sword"), true);
            return;
        }

        FormationMode mode = getFormationMode(stack).next();
        stack.getOrCreateTag().putString(MODE_TAG, mode.serializedName());
        getOwnedFormationSwords(player).forEach(sword -> sword.setFormationMode(mode));
        player.displayClientMessage(Component.translatable("message.swordflight.formation_changed",
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
        if (!existing.isEmpty() || !(stack.getItem() instanceof FlyingSwordItem item)) return existing;
        item.summonFormation(player, stack);
        return getOwnedFormationSwords(player);
    }

    private void summonFormation(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        for (int slot = 0; slot < FORMATION_SIZE; slot++) {
            FlyingSwordEntity sword = ModEntities.FLYING_SWORD.get().create(level);
            if (sword == null) continue;
            sword.bindTo(player, slot, getFormationMode(stack), SwordSettings.read(stack), material,
                    series, SwordModuleData.copyModules(stack));
            sword.moveTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), 0.0F);
            level.addFreshEntity(sword);
        }
    }

    public static ItemStack findFlyingSword(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof FlyingSwordItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof FlyingSwordItem) {
            return player.getOffhandItem();
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof FlyingSwordItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
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
