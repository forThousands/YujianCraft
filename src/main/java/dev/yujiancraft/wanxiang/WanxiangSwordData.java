package dev.yujiancraft.wanxiang;

import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.config.SwordBalanceConfig;
import dev.yujiancraft.visual.FlyingSwordSeries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.UUID;

/** Namespaced per-stack data that turns an arbitrary single item into a Myriad Flying Sword. */
public final class WanxiangSwordData {
    public static final String ROOT_TAG = "YujianCraftWanxiang";
    private static final String TEMPERED_TAG = "Tempered";
    private static final String BINDING_TAG = "BindingId";
    private static final String CORE_TAG = "CoreMaterial";
    private static final String PRESET_TAG = "RenderPreset";
    private static final String GLOW_TAG = "GlowMode";
    private static final String FLIP_TAG = "FlipAxis";
    private static final String SCALE_TAG = "ScalePercent";
    private static final String AURA_RADIUS_TAG = "AuraRadiusPercent";
    private static final String AURA_LENGTH_TAG = "AuraLengthPercent";
    private static final String PIERCE_DAMAGE_TAG = "PierceDamage";
    private static final String ENCHANTMENT_DAMAGE_BASELINE_TAG = "EnchantmentDamageBaseline";
    private static final String TEMPER_COUNT_TAG = "TemperCount";
    // Read-only migration keys used by the unreleased 0.11.0 development build.
    private static final String LEGACY_INHERITED_DAMAGE_TAG = "InheritedDamage";
    private static final String LEGACY_NATIVE_DAMAGE_TAG = "NativeReforgedDamage";
    public static final int MAX_TEMPERINGS = 2;
    private static final int MIN_SCALE = 50;
    private static final int MAX_SCALE = 200;
    private static final int MIN_AURA_SCALE = 50;
    private static final int MAX_AURA_SCALE = 250;

    private WanxiangSwordData() {
    }

    public static boolean isTempered(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().contains(ROOT_TAG)
                && stack.getTag().getCompound(ROOT_TAG).getBoolean(TEMPERED_TAG);
    }

    public static boolean isUsable(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof FlyingSwordItem || isTempered(stack));
    }

    public static boolean canTemper(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() == 1 && stack.getMaxStackSize() == 1;
    }

    public static boolean canTemperAgain(ItemStack stack) {
        return canTemper(stack) && temperCount(stack) < MAX_TEMPERINGS;
    }

    public static ItemStack temper(ItemStack stack, FlyingSwordMaterial core, WanxiangRenderPreset preset,
                                   WanxiangGlowMode glowMode, boolean flip, int scalePercent,
                                   int auraRadiusPercent, int auraLengthPercent, double inheritedDamage) {
        CompoundTag data = stack.getOrCreateTag().getCompound(ROOT_TAG);
        if (!(stack.getItem() instanceof FlyingSwordItem)) data.putBoolean(TEMPERED_TAG, true);
        if (!data.hasUUID(BINDING_TAG)) data.putUUID(BINDING_TAG, UUID.randomUUID());
        data.putInt(CORE_TAG, core.ordinal());
        data.putInt(PRESET_TAG, preset.ordinal());
        data.putInt(GLOW_TAG, glowMode.ordinal());
        data.putBoolean(FLIP_TAG, flip);
        data.putInt(SCALE_TAG, Mth.clamp(scalePercent, MIN_SCALE, MAX_SCALE));
        data.putInt(AURA_RADIUS_TAG, Mth.clamp(auraRadiusPercent, MIN_AURA_SCALE, MAX_AURA_SCALE));
        data.putInt(AURA_LENGTH_TAG, Mth.clamp(auraLengthPercent, MIN_AURA_SCALE, MAX_AURA_SCALE));
        data.putDouble(PIERCE_DAMAGE_TAG, Math.max(0.0D, Double.isFinite(inheritedDamage)
                ? inheritedDamage : 0.0D));
        data.putDouble(ENCHANTMENT_DAMAGE_BASELINE_TAG,
                safeDamage(EnchantmentHelper.getDamageBonus(stack, MobType.UNDEFINED)));
        data.putInt(TEMPER_COUNT_TAG, Math.min(MAX_TEMPERINGS, temperCount(stack) + 1));
        data.remove(LEGACY_INHERITED_DAMAGE_TAG);
        data.remove(LEGACY_NATIVE_DAMAGE_TAG);
        stack.getOrCreateTag().put(ROOT_TAG, data);
        return stack;
    }

    /** Applies only visual calibration and never spends one of the two tempering opportunities. */
    public static ItemStack applyShape(ItemStack stack, WanxiangRenderPreset preset,
                                       WanxiangGlowMode glowMode, boolean flip, int scalePercent,
                                       int auraRadiusPercent, int auraLengthPercent) {
        CompoundTag data = stack.getOrCreateTag().getCompound(ROOT_TAG);
        data.putInt(PRESET_TAG, preset.ordinal());
        data.putInt(GLOW_TAG, glowMode.ordinal());
        data.putBoolean(FLIP_TAG, flip);
        data.putInt(SCALE_TAG, Mth.clamp(scalePercent, MIN_SCALE, MAX_SCALE));
        data.putInt(AURA_RADIUS_TAG, Mth.clamp(auraRadiusPercent, MIN_AURA_SCALE, MAX_AURA_SCALE));
        data.putInt(AURA_LENGTH_TAG, Mth.clamp(auraLengthPercent, MIN_AURA_SCALE, MAX_AURA_SCALE));
        stack.getOrCreateTag().put(ROOT_TAG, data);
        return stack;
    }

    public static ItemStack preview(ItemStack source, FlyingSwordMaterial core, WanxiangRenderPreset preset,
                                    WanxiangGlowMode glowMode, boolean flip, int scalePercent,
                                    int auraRadiusPercent, int auraLengthPercent, double inheritedDamage) {
        ItemStack preview = source.copy();
        preview.setCount(1);
        applyShape(preview, preset, glowMode, flip, scalePercent, auraRadiusPercent, auraLengthPercent);
        CompoundTag data = preview.getOrCreateTag().getCompound(ROOT_TAG);
        if (!(preview.getItem() instanceof FlyingSwordItem)) data.putBoolean(TEMPERED_TAG, true);
        data.putInt(CORE_TAG, core.ordinal());
        data.putDouble(PIERCE_DAMAGE_TAG, Math.max(0.0D,
                Double.isFinite(inheritedDamage) ? inheritedDamage : 0.0D));
        data.putDouble(ENCHANTMENT_DAMAGE_BASELINE_TAG,
                safeDamage(EnchantmentHelper.getDamageBonus(preview, MobType.UNDEFINED)));
        preview.getOrCreateTag().put(ROOT_TAG, data);
        return preview;
    }

    public static UUID ensureBinding(ItemStack stack) {
        CompoundTag data = stack.getOrCreateTag().getCompound(ROOT_TAG);
        if (!data.hasUUID(BINDING_TAG)) data.putUUID(BINDING_TAG, UUID.randomUUID());
        stack.getOrCreateTag().put(ROOT_TAG, data);
        return data.getUUID(BINDING_TAG);
    }

    public static UUID binding(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(ROOT_TAG)) return null;
        CompoundTag data = stack.getTag().getCompound(ROOT_TAG);
        return data.hasUUID(BINDING_TAG) ? data.getUUID(BINDING_TAG) : null;
    }

    public static FlyingSwordMaterial material(ItemStack stack) {
        if (stack.getItem() instanceof FlyingSwordItem nativeSword) return nativeSword.getMaterialType();
        if (!isTempered(stack)) return FlyingSwordMaterial.IRON;
        return FlyingSwordMaterial.fromOrdinal(stack.getTag().getCompound(ROOT_TAG).getInt(CORE_TAG));
    }

    public static FlyingSwordSeries series(ItemStack stack) {
        return stack.getItem() instanceof FlyingSwordItem nativeSword
                ? nativeSword.getSeries() : FlyingSwordSeries.STANDARD;
    }

    public static WanxiangRenderPreset renderPreset(ItemStack stack) {
        CompoundTag data = data(stack);
        if (data.contains(PRESET_TAG)) {
            return WanxiangRenderPreset.fromOrdinal(data.getInt(PRESET_TAG));
        }
        if (stack.getItem() instanceof FlyingSwordItem nativeSword) {
            return nativeSword.getSeries() == FlyingSwordSeries.SPIRITFORGED
                    ? WanxiangRenderPreset.AXIAL_3D : WanxiangRenderPreset.VANILLA_FLAT;
        }
        return WanxiangRenderPreset.VANILLA_FLAT;
    }

    public static WanxiangGlowMode glowMode(ItemStack stack) {
        CompoundTag data = data(stack);
        return data.contains(GLOW_TAG)
                ? WanxiangGlowMode.fromOrdinal(data.getInt(GLOW_TAG)) : WanxiangGlowMode.FULL_BODY;
    }

    public static boolean flipAxis(ItemStack stack) {
        return data(stack).getBoolean(FLIP_TAG);
    }

    public static int scalePercent(ItemStack stack) {
        int value = data(stack).getInt(SCALE_TAG);
        return value == 0 ? 100 : Mth.clamp(value, MIN_SCALE, MAX_SCALE);
    }

    public static int auraRadiusPercent(ItemStack stack) {
        return percentOrDefault(stack, AURA_RADIUS_TAG, MIN_AURA_SCALE, MAX_AURA_SCALE);
    }

    public static int auraLengthPercent(ItemStack stack) {
        return percentOrDefault(stack, AURA_LENGTH_TAG, MIN_AURA_SCALE, MAX_AURA_SCALE);
    }

    public static boolean hasPierceDamage(ItemStack stack) {
        CompoundTag data = data(stack);
        return data.contains(PIERCE_DAMAGE_TAG) || data.contains(LEGACY_INHERITED_DAMAGE_TAG)
                || data.contains(LEGACY_NATIVE_DAMAGE_TAG);
    }

    public static double pierceDamage(ItemStack stack) {
        CompoundTag data = data(stack);
        if (data.contains(PIERCE_DAMAGE_TAG)) return safeDamage(data.getDouble(PIERCE_DAMAGE_TAG));
        if (data.contains(LEGACY_INHERITED_DAMAGE_TAG)) {
            return safeDamage(data.getDouble(LEGACY_INHERITED_DAMAGE_TAG));
        }
        if (data.contains(LEGACY_NATIVE_DAMAGE_TAG)) {
            return safeDamage(data.getDouble(LEGACY_NATIVE_DAMAGE_TAG));
        }
        if (stack.getItem() instanceof FlyingSwordItem sword) {
            return safeDamage(SwordBalanceConfig.get(sword.getMaterialType()).damage());
        }
        return 0.0D;
    }

    /** Enchantment damage already represented by the last trial result; live changes apply as a delta. */
    public static double enchantmentDamageBaseline(ItemStack stack) {
        CompoundTag data = data(stack);
        return data.contains(ENCHANTMENT_DAMAGE_BASELINE_TAG)
                ? safeDamage(data.getDouble(ENCHANTMENT_DAMAGE_BASELINE_TAG)) : 0.0D;
    }

    public static int temperCount(ItemStack stack) {
        CompoundTag data = data(stack);
        if (!data.contains(TEMPER_COUNT_TAG) && (data.contains(LEGACY_INHERITED_DAMAGE_TAG)
                || data.contains(LEGACY_NATIVE_DAMAGE_TAG))) return 1;
        return Mth.clamp(data.getInt(TEMPER_COUNT_TAG), 0, MAX_TEMPERINGS);
    }

    private static int percentOrDefault(ItemStack stack, String tag, int minimum, int maximum) {
        int value = data(stack).getInt(tag);
        return value == 0 ? 100 : Mth.clamp(value, minimum, maximum);
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(ROOT_TAG)
                ? stack.getTag().getCompound(ROOT_TAG) : new CompoundTag();
    }

    private static double safeDamage(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0E9D, value)) : 0.0D;
    }

    public static int experienceCost(FlyingSwordMaterial material) {
        return switch (material) {
            case WOODEN -> 3;
            case STONE -> 5;
            case IRON -> 10;
            case GOLDEN -> 12;
            case DIAMOND -> 20;
            case NETHERITE -> 30;
        };
    }
}
