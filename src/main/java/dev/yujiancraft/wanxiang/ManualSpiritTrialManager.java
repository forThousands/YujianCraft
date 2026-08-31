package dev.yujiancraft.wanxiang;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.blockentity.SpiritTemperingTableBlockEntity;
import dev.yujiancraft.combat.technique.ArtifactRole;
import dev.yujiancraft.combat.SwordSettings;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.entity.SpiritTrialDummyEntity;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.network.ModNetwork;
import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.upgrade.SwordModuleData;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The only tempering trial: a player-driven, ten-second, source-verified DPS ritual. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ManualSpiritTrialManager {
    public static final ResourceKey<Level> TRIAL_LEVEL = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(YujianCraft.MOD_ID, "spirit_trial"));
    private static final String COPY_TAG = "YujianCraftSpiritTrialCopy";
    private static final String PROJECTILE_TAG = "YujianCraftSpiritTrialProjectile";
    private static final String RECOVERY_TAG = "YujianCraftSpiritTrialRecovery";
    private static final int DPS_DURATION_TICKS = 200;
    private static final int LIGHTNING_INTERVAL_TICKS = 20;
    private static final int ENTRY_CONFIRM_TICKS = 5;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Session> DUMMIES = new HashMap<>();

    private ManualSpiritTrialManager() {
    }

    public record Shape(WanxiangRenderPreset preset, WanxiangGlowMode glowMode, boolean flipped,
                        int scalePercent, int auraRadiusPercent, int auraLengthPercent,
                        ArtifactRole artifactRole) {
    }

    public static boolean start(ServerPlayer player, SpiritTemperingTableBlockEntity table, Shape shape) {
        if (SESSIONS.containsKey(player.getUUID())) return false;
        ItemStack source = table.inventory().getStackInSlot(0);
        ItemStack core = table.inventory().getStackInSlot(1);
        if (!WanxiangSwordData.canTemperAgain(source)
                || !(core.getItem() instanceof FlyingSwordItem coreSword)) return false;
        int cost = WanxiangSwordData.experienceCost(coreSword.getMaterialType());
        if (!player.getAbilities().instabuild && player.experienceLevel < cost) {
            player.displayClientMessage(Component.translatable(
                    "message.yujiancraft.tempering.need_experience", cost), true);
            return false;
        }
        ServerLevel trial = player.server.getLevel(TRIAL_LEVEL);
        if (trial == null || table.getLevel() == null) return false;

        UUID copyId = UUID.randomUUID();
        SpiritTrialArenaPool.Arena arena;
        try {
            arena = SpiritTrialArenaPool.acquire(trial, copyId);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to prepare a spirit trial arena for {}", player.getGameProfile().getName(), exception);
            player.displayClientMessage(Component.translatable("message.yujiancraft.trial.entry_failed"), false);
            return false;
        }
        if (arena == null) {
            player.displayClientMessage(Component.translatable(
                    "message.yujiancraft.trial.busy", SpiritTrialArenaPool.ARENA_COUNT), false);
            return false;
        }

        ItemStack sourceWeapon = table.inventory().extractItem(0, 1, false);
        ItemStack consumedCore = table.inventory().extractItem(1, 1, false);
        if (sourceWeapon.isEmpty() || consumedCore.isEmpty()) {
            if (!sourceWeapon.isEmpty()) table.inventory().insertItem(0, sourceWeapon, false);
            if (!consumedCore.isEmpty()) table.inventory().insertItem(1, consumedCore, false);
            SpiritTrialArenaPool.release(trial, arena, copyId);
            return false;
        }

        int selectedSlot = player.getInventory().selected;
        ItemStack savedMain = player.getInventory().getItem(selectedSlot).copy();
        ItemStack trialCopy = sourceWeapon.copy();
        trialCopy.setCount(1);
        // Vanilla and third-party enchantments are part of the weapon and remain active throughout
        // the trial. Only reversible Yujian module cores are dispersed above.
        WanxiangSwordData.applyShape(trialCopy, shape.preset(), shape.glowMode(), shape.flipped(),
                shape.scalePercent(), shape.auraRadiusPercent(), shape.auraLengthPercent());
        WanxiangSwordData.setRole(trialCopy, shape.artifactRole());
        trialCopy.getOrCreateTag().putUUID(COPY_TAG, copyId);
        if (WanxiangSwordData.isUsable(trialCopy)) {
            SwordModuleData.clearAll(trialCopy);
            WanxiangSwordData.ensureBinding(trialCopy);
        }

        Session session = new Session(player, player.level().dimension(), player.position(),
                player.getYRot(), player.getXRot(), sourceWeapon, consumedCore, coreSword.getMaterialType(),
                cost, shape, selectedSlot, savedMain, trialCopy, copyId, arena);
        SESSIONS.put(player.getUUID(), session);
        try {
            player.getInventory().setItem(selectedSlot, trialCopy.copy());
            session.inventorySwapped = true;
            persistRecovery(session, true);
            player.closeContainer();
            if (!session.spawnDummy(trial)) {
                finish(session, false);
                return false;
            }
            player.teleportTo(trial, arena.playerX(), 127.0D, arena.playerZ(), 0.0F, 0.0F);
            if (!session.isInArena()) {
                finish(session, false);
                player.displayClientMessage(Component.translatable("message.yujiancraft.trial.entry_failed"), false);
                return false;
            }
            table.setChanged();
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to enter the spirit trial for {}", player.getGameProfile().getName(), exception);
            finish(session, false);
            player.displayClientMessage(Component.translatable("message.yujiancraft.trial.entry_failed"), false);
            return false;
        }
    }

    public static boolean isParticipant(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Trial formations use one sword so a formation total can never be written back per sword. */
    public static int formationSize(ServerPlayer player, int normalSize) {
        return isParticipant(player) ? 1 : normalSize;
    }

    public static boolean beginFlyingSwordDamage(ServerPlayer owner,
                                                 net.minecraft.world.entity.LivingEntity target,
                                                 ItemStack sourceStack) {
        Session session = SESSIONS.get(owner.getUUID());
        if (session == null || target != session.dummy || !isTrialCopy(sourceStack, session.copyId)) return false;
        session.flyingDamageDepth++;
        return true;
    }

    public static void endFlyingSwordDamage(ServerPlayer owner) {
        Session session = SESSIONS.get(owner.getUUID());
        if (session != null) session.flyingDamageDepth = Math.max(0, session.flyingDamageDepth - 1);
    }

    @SubscribeEvent
    public static void markTrialProjectile(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || SESSIONS.isEmpty()
                || event.getEntity() instanceof ServerPlayer
                || event.getEntity() instanceof SpiritTrialDummyEntity
                || event.getEntity() instanceof FlyingSwordEntity
                || event.getEntity() instanceof LightningBolt) return;
        Entity spawned = event.getEntity();
        if (spawned instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner) {
            Session session = SESSIONS.get(owner.getUUID());
            if (session != null && isTrialCopy(owner.getMainHandItem(), session.copyId)) {
                spawned.getPersistentData().putUUID(PROJECTILE_TAG, session.copyId);
                return;
            }
        }
        // Some weapon mods use custom entities rather than Projectile. The trial dimension has no
        // ambient spawns, so a fresh entity near a participant holding the ritual item is traceable.
        for (Session session : SESSIONS.values()) {
            if (session.player.level() == event.getLevel()
                    && session.player.distanceToSqr(spawned) <= 16.0D * 16.0D
                    && isTrialCopy(session.player.getMainHandItem(), session.copyId)) {
                spawned.getPersistentData().putUUID(PROJECTILE_TAG, session.copyId);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onDummyDamage(LivingDamageEvent event) {
        Session session = DUMMIES.get(event.getEntity().getUUID());
        if (session == null || session.finished || !Float.isFinite(event.getAmount())
                || event.getAmount() <= 0.0F) return;
        DamageChannel channel = session.classify(event.getSource().getDirectEntity(),
                event.getSource().getEntity());
        if (channel == DamageChannel.NONE) return;
        if (session.channel == DamageChannel.NONE) session.channel = channel;
        if (session.channel != channel) return;
        if (session.elapsed < 0) {
            session.elapsed = 0;
            showCountdown(session.player, 10);
        }
        session.totalDamage += event.getAmount();
    }

    @SubscribeEvent
    public static void keepDummyAlive(LivingDeathEvent event) {
        Session session = DUMMIES.get(event.getEntity().getUUID());
        if (session == null) return;
        event.setCanceled(true);
        event.getEntity().setHealth(event.getEntity().getMaxHealth());
    }

    @SubscribeEvent
    public static void protectParticipant(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void preventTrialCopyToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !isTrialCopy(event.getEntity().getItem(), session.copyId)) return;
        event.setCanceled(true);
        event.getEntity().discard();
        if (!session.containsTrialCopy()) player.getInventory().setItem(session.selectedSlot, session.trialCopy.copy());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        for (Session session : java.util.List.copyOf(SESSIONS.values())) session.tick();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.get(player.getUUID());
            if (session != null) finish(session, false);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) recoverInterruptedEntry(player);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Session session : java.util.List.copyOf(SESSIONS.values())) finish(session, false);
    }

    private static void showInstructions(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 45, 12));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("title.yujiancraft.trial.enter")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("title.yujiancraft.trial.dps_hint")));
    }

    private static void showCountdown(ServerPlayer player, int seconds) {
        ModNetwork.sendTrialCountdown(player, seconds);
    }

    private static void complete(Session session) {
        session.finished = true;
        double dps = session.totalDamage / (DPS_DURATION_TICKS / 20.0D);
        ItemStack result = session.sourceWeapon;
        boolean firstConversion = !WanxiangSwordData.isUsable(result);
        WanxiangSwordData.temper(result, session.coreMaterial, session.shape.preset(), session.shape.glowMode(),
                session.shape.flipped(), session.shape.scalePercent(), session.shape.auraRadiusPercent(),
                session.shape.auraLengthPercent(), dps, session.shape.artifactRole());
        if (firstConversion) {
            SwordSettings old = SwordSettings.read(result);
            dev.yujiancraft.combat.technique.TechniqueMode initialTechnique =
                    WanxiangSwordData.isTempered(result)
                            ? WanxiangWeaponCatalog.defaultTechnique(session.player.server, result)
                            : session.shape.artifactRole().recommendedTechnique();
            new SwordSettings(old.minimumDockTicks(), old.automaticTargetRadius(), old.crosshairLockRadius(),
                    old.targetingMode(), old.attackMode(), initialTechnique)
                    .write(result);
        }
        if (WanxiangSwordData.isTempered(result)) WanxiangWeaponCatalog.register(session.player.server, result);
        int attempt = WanxiangSwordData.temperCount(result);
        persistRecovery(session, false);
        finish(session, true);
        session.player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 55, 12));
        session.player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(
                "title.yujiancraft.trial.dps_result", format(dps))));
        session.player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                "title.yujiancraft.trial.total_result", format(session.totalDamage))));
        ModNetwork.sendManualTrialResult(session.player, session.totalDamage, dps, attempt);
    }

    private static void finish(Session session, boolean completed) {
        if (session.closed) return;
        session.closed = true;
        SESSIONS.remove(session.player.getUUID());
        FlyingSwordItem.getOwnedFormationSwords(session.player).forEach(Entity::discard);
        if (session.dummy != null) {
            DUMMIES.remove(session.dummy.getUUID());
            session.dummy.discard();
        }
        ServerLevel trial = session.player.server.getLevel(TRIAL_LEVEL);
        SpiritTrialArenaPool.release(trial, session.arena, session.copyId);
        session.restoreMainHand(session.sourceWeapon);
        if (!session.entryCommitted) giveOrDrop(session.player, session.consumedCore.copy());
        ServerLevel origin = session.player.server.getLevel(session.originDimension);
        if (origin != null) {
            session.player.teleportTo(origin, session.origin.x, session.origin.y, session.origin.z,
                    session.originYaw, session.originPitch);
        }
        clearRecovery(session.player);
        if (!completed && session.entryCommitted) {
            session.player.displayClientMessage(Component.translatable(
                    "message.yujiancraft.trial.interrupted"), false);
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static boolean isTrialCopy(ItemStack stack, UUID copyId) {
        return stack.hasTag() && stack.getTag().hasUUID(COPY_TAG)
                && copyId.equals(stack.getTag().getUUID(COPY_TAG));
    }

    private static void persistRecovery(Session session, boolean flushToDisk) {
        CompoundTag recovery = new CompoundTag();
        recovery.put("Source", session.sourceWeapon.copy().save(new CompoundTag()));
        recovery.put("Core", session.consumedCore.copy().save(new CompoundTag()));
        recovery.put("SavedMain", session.savedMain.copy().save(new CompoundTag()));
        recovery.putInt("SelectedSlot", session.selectedSlot);
        recovery.putUUID("CopyId", session.copyId);
        recovery.putString("OriginDimension", session.originDimension.location().toString());
        recovery.putDouble("OriginX", session.origin.x);
        recovery.putDouble("OriginY", session.origin.y);
        recovery.putDouble("OriginZ", session.origin.z);
        recovery.putFloat("OriginYaw", session.originYaw);
        recovery.putFloat("OriginPitch", session.originPitch);
        recovery.putBoolean("InventorySwapped", session.inventorySwapped);
        recovery.putBoolean("Committed", session.entryCommitted);
        recovery.putInt("ExperienceLevel", session.initialExperienceLevel);
        recovery.putFloat("ExperienceProgress", session.initialExperienceProgress);
        recovery.putInt("TotalExperience", session.initialTotalExperience);
        CompoundTag persisted = persisted(session.player);
        persisted.put(RECOVERY_TAG, recovery);
        session.player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        if (flushToDisk) session.player.server.getPlayerList().saveAll();
    }

    private static void clearRecovery(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(RECOVERY_TAG, Tag.TAG_COMPOUND)) return;
        persisted.remove(RECOVERY_TAG);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.server.getPlayerList().saveAll();
    }

    private static void recoverInterruptedEntry(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(RECOVERY_TAG, Tag.TAG_COMPOUND)) return;
        CompoundTag recovery = persisted.getCompound(RECOVERY_TAG);
        if (!recovery.hasUUID("CopyId")) {
            clearRecovery(player);
            return;
        }
        UUID copyId = recovery.getUUID("CopyId");
        ItemStack source = ItemStack.of(recovery.getCompound("Source"));
        ItemStack core = ItemStack.of(recovery.getCompound("Core"));
        ItemStack savedMain = ItemStack.of(recovery.getCompound("SavedMain"));
        int selectedSlot = recovery.getInt("SelectedSlot");
        boolean inventorySwapped = recovery.getBoolean("InventorySwapped");
        restoreInventory(player, selectedSlot, inventorySwapped ? savedMain : ItemStack.EMPTY, source, copyId);
        if (!recovery.getBoolean("Committed")) {
            giveOrDrop(player, core);
            player.experienceLevel = recovery.getInt("ExperienceLevel");
            player.experienceProgress = recovery.getFloat("ExperienceProgress");
            player.totalExperience = recovery.getInt("TotalExperience");
            player.connection.send(new ClientboundSetExperiencePacket(
                    player.experienceProgress, player.totalExperience, player.experienceLevel));
        }
        ResourceLocation originId = ResourceLocation.tryParse(recovery.getString("OriginDimension"));
        if (originId != null) {
            ServerLevel origin = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, originId));
            if (origin != null) {
                player.teleportTo(origin, recovery.getDouble("OriginX"), recovery.getDouble("OriginY"),
                        recovery.getDouble("OriginZ"), recovery.getFloat("OriginYaw"),
                        recovery.getFloat("OriginPitch"));
            }
        }
        clearRecovery(player);
        player.displayClientMessage(Component.translatable("message.yujiancraft.trial.recovered"), false);
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void restoreInventory(ServerPlayer player, int selectedSlot, ItemStack savedMain,
                                         ItemStack result, UUID copyId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isTrialCopy(player.getInventory().getItem(slot), copyId)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        int safeSlot = Math.max(0, Math.min(selectedSlot, player.getInventory().getContainerSize() - 1));
        ItemStack occupant = player.getInventory().getItem(safeSlot);
        if (!occupant.isEmpty()) {
            player.getInventory().setItem(safeSlot, ItemStack.EMPTY);
            giveOrDrop(player, occupant);
        }
        player.getInventory().setItem(safeSlot, result);
        giveOrDrop(player, savedMain);
    }

    private enum DamageChannel { NONE, MELEE, PROJECTILE, FLYING_SWORD }

    private static final class Session {
        private final ServerPlayer player;
        private final ResourceKey<Level> originDimension;
        private final net.minecraft.world.phys.Vec3 origin;
        private final float originYaw;
        private final float originPitch;
        private final ItemStack sourceWeapon;
        private final ItemStack consumedCore;
        private final FlyingSwordMaterial coreMaterial;
        private final int experienceCost;
        private final int initialExperienceLevel;
        private final float initialExperienceProgress;
        private final int initialTotalExperience;
        private final Shape shape;
        private final int selectedSlot;
        private final ItemStack savedMain;
        private final ItemStack trialCopy;
        private final UUID copyId;
        private final SpiritTrialArenaPool.Arena arena;
        private SpiritTrialDummyEntity dummy;
        private int elapsed = -1;
        private int atmosphereTicks;
        private int lightningIndex;
        private int flyingDamageDepth;
        private double totalDamage;
        private DamageChannel channel = DamageChannel.NONE;
        private boolean finished;
        private boolean closed;
        private boolean inventorySwapped;
        private boolean entryCommitted;
        private int entryConfirmTicks;

        private Session(ServerPlayer player, ResourceKey<Level> originDimension,
                        net.minecraft.world.phys.Vec3 origin, float originYaw, float originPitch,
                        ItemStack sourceWeapon, ItemStack consumedCore, FlyingSwordMaterial coreMaterial,
                        int experienceCost, Shape shape, int selectedSlot, ItemStack savedMain,
                        ItemStack trialCopy, UUID copyId, SpiritTrialArenaPool.Arena arena) {
            this.player = player;
            this.originDimension = originDimension;
            this.origin = origin;
            this.originYaw = originYaw;
            this.originPitch = originPitch;
            this.sourceWeapon = sourceWeapon;
            this.consumedCore = consumedCore;
            this.coreMaterial = coreMaterial;
            this.experienceCost = experienceCost;
            this.initialExperienceLevel = player.experienceLevel;
            this.initialExperienceProgress = player.experienceProgress;
            this.initialTotalExperience = player.totalExperience;
            this.shape = shape;
            this.selectedSlot = selectedSlot;
            this.savedMain = savedMain;
            this.trialCopy = trialCopy;
            this.copyId = copyId;
            this.arena = arena;
        }

        private boolean spawnDummy(ServerLevel level) {
            dummy = new SpiritTrialDummyEntity(ModEntities.SPIRIT_TRIAL_DUMMY.get(), level);
            dummy.setPos(arena.playerX(), 127.0D, arena.dummyZ());
            dummy.setYRot(180.0F);
            if (!level.addFreshEntity(dummy)) {
                dummy = null;
                return false;
            }
            DUMMIES.put(dummy.getUUID(), this);
            return true;
        }

        private DamageChannel classify(Entity direct, Entity owner) {
            if (flyingDamageDepth > 0) return DamageChannel.FLYING_SWORD;
            if (direct == player && isTrialCopy(player.getMainHandItem(), copyId)) {
                return DamageChannel.MELEE;
            }
            if (direct != null && direct.getPersistentData().hasUUID(PROJECTILE_TAG)
                    && copyId.equals(direct.getPersistentData().getUUID(PROJECTILE_TAG))) {
                return DamageChannel.PROJECTILE;
            }
            return DamageChannel.NONE;
        }

        private void tick() {
            if (!player.isAlive() || !isInArena()) {
                finish(this, false);
                return;
            }
            if (!entryCommitted) {
                if (++entryConfirmTicks >= ENTRY_CONFIRM_TICKS) commitEntry();
                return;
            }
            if (player.getY() < 112.0D) {
                player.teleportTo((ServerLevel) player.level(), arena.playerX(), 127.0D, arena.playerZ(),
                        0.0F, 0.0F);
            }
            if (!containsTrialCopy()) player.getInventory().setItem(selectedSlot, trialCopy.copy());
            if (++atmosphereTicks % LIGHTNING_INTERVAL_TICKS == 0) spawnAtmosphereLightning();
            if (finished || elapsed < 0) return;
            if (dummy != null) {
                dummy.setHealth(dummy.getMaxHealth());
                dummy.invulnerableTime = 0;
            }
            elapsed++;
            if (elapsed >= DPS_DURATION_TICKS) {
                complete(this);
            } else if (elapsed % 20 == 0) {
                int remaining = Math.max(0, (DPS_DURATION_TICKS - elapsed) / 20);
                if (remaining > 0) showCountdown(player, remaining);
            }
        }

        private void spawnAtmosphereLightning() {
            if (!(player.level() instanceof ServerLevel level)) return;
            double angle = Math.PI * 2.0D * (lightningIndex++ % 8) / 8.0D;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) return;
            bolt.moveTo(arena.playerX() + Math.cos(angle) * 7.0D, 127.0D,
                    arena.atmosphereZ() + Math.sin(angle) * 7.0D);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }

        private boolean containsTrialCopy() {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (isTrialCopy(player.getInventory().getItem(slot), copyId)) return true;
            }
            return false;
        }

        private void restoreMainHand(ItemStack result) {
            restoreInventory(player, selectedSlot, inventorySwapped ? savedMain : ItemStack.EMPTY,
                    result, copyId);
        }

        private boolean isInArena() {
            return player.level().dimension().equals(TRIAL_LEVEL)
                    && arena.contains(player.getX(), player.getY(), player.getZ());
        }

        private void commitEntry() {
            if (!player.getAbilities().instabuild && player.experienceLevel < experienceCost) {
                finish(this, false);
                return;
            }
            if (!player.getAbilities().instabuild) player.giveExperienceLevels(-experienceCost);
            if (WanxiangSwordData.isUsable(sourceWeapon)) {
                FlyingSwordItem.getOwnedFormationSwords(player).forEach(Entity::discard);
                SwordModuleData.clearAll(sourceWeapon);
            }
            entryCommitted = true;
            persistRecovery(this, false);
            showInstructions(player);
        }
    }
}
