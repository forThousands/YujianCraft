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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
    private static final int DPS_DURATION_TICKS = 200;
    private static final int LIGHTNING_INTERVAL_TICKS = 20;
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

        ItemStack sourceWeapon = table.inventory().extractItem(0, 1, false);
        ItemStack consumedCore = table.inventory().extractItem(1, 1, false);
        if (sourceWeapon.isEmpty() || consumedCore.isEmpty()) {
            if (!sourceWeapon.isEmpty()) table.inventory().insertItem(0, sourceWeapon, false);
            if (!consumedCore.isEmpty()) table.inventory().insertItem(1, consumedCore, false);
            return false;
        }
        if (!player.getAbilities().instabuild) player.giveExperienceLevels(-cost);

        if (WanxiangSwordData.isUsable(sourceWeapon)) {
            FlyingSwordItem.getOwnedFormationSwords(player).forEach(Entity::discard);
            // Entering the realm disperses every installed Yujian core immediately.
            SwordModuleData.clearAll(sourceWeapon);
        }

        int selectedSlot = player.getInventory().selected;
        ItemStack savedMain = player.getInventory().getItem(selectedSlot).copy();
        UUID copyId = UUID.randomUUID();
        ItemStack trialCopy = sourceWeapon.copy();
        trialCopy.setCount(1);
        // Vanilla and third-party enchantments are part of the weapon and remain active throughout
        // the trial. Only reversible Yujian module cores are dispersed above.
        WanxiangSwordData.applyShape(trialCopy, shape.preset(), shape.glowMode(), shape.flipped(),
                shape.scalePercent(), shape.auraRadiusPercent(), shape.auraLengthPercent());
        WanxiangSwordData.setRole(trialCopy, shape.artifactRole());
        trialCopy.getOrCreateTag().putUUID(COPY_TAG, copyId);
        if (WanxiangSwordData.isUsable(trialCopy)) WanxiangSwordData.ensureBinding(trialCopy);

        double laneX = player.getId() * 64.0D;
        Session session = new Session(player, player.level().dimension(), player.position(),
                player.getYRot(), player.getXRot(), sourceWeapon, coreSword.getMaterialType(), shape,
                selectedSlot, savedMain, trialCopy, copyId, laneX);
        SESSIONS.put(player.getUUID(), session);
        player.getInventory().setItem(selectedSlot, trialCopy.copy());
        player.closeContainer();
        buildPlatform(trial, laneX);
        session.spawnDummy(trial);
        player.teleportTo(trial, laneX + 0.5D, 127.0D, 0.5D, 0.0F, 0.0F);
        showInstructions(player);
        table.setChanged();
        return true;
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
        finish(session, true);
        session.player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 55, 12));
        session.player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(
                "title.yujiancraft.trial.dps_result", format(dps))));
        session.player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                "title.yujiancraft.trial.total_result", format(session.totalDamage))));
        ModNetwork.sendManualTrialResult(session.player, session.totalDamage, dps, attempt);
    }

    private static void finish(Session session, boolean completed) {
        SESSIONS.remove(session.player.getUUID());
        FlyingSwordItem.getOwnedFormationSwords(session.player).forEach(Entity::discard);
        if (session.dummy != null) {
            DUMMIES.remove(session.dummy.getUUID());
            session.dummy.discard();
        }
        session.restoreMainHand(session.sourceWeapon);
        ServerLevel origin = session.player.server.getLevel(session.originDimension);
        if (origin != null) {
            session.player.teleportTo(origin, session.origin.x, session.origin.y, session.origin.z,
                    session.originYaw, session.originPitch);
        }
        if (!completed) {
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

    private static void buildPlatform(ServerLevel level, double laneX) {
        int centerX = (int) Math.floor(laneX + 0.5D);
        BlockPos center = new BlockPos(centerX, 126, 3);
        level.getChunkAt(center);
        for (int yOffset = 0; yOffset >= -3; yOffset--) {
            int radius = 8 + yOffset;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius + 2) continue;
                    level.setBlockAndUpdate(center.offset(x, yOffset, z), yOffset == 0
                            ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                            : Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
        }
        for (int index = 0; index < 8; index++) {
            double radians = Math.PI * 2.0D * index / 8.0D;
            BlockPos light = center.offset((int) Math.round(Math.cos(radians) * 7.0D), 1,
                    (int) Math.round(Math.sin(radians) * 7.0D));
            level.setBlockAndUpdate(light, Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    private enum DamageChannel { NONE, MELEE, PROJECTILE, FLYING_SWORD }

    private static final class Session {
        private final ServerPlayer player;
        private final ResourceKey<Level> originDimension;
        private final net.minecraft.world.phys.Vec3 origin;
        private final float originYaw;
        private final float originPitch;
        private final ItemStack sourceWeapon;
        private final FlyingSwordMaterial coreMaterial;
        private final Shape shape;
        private final int selectedSlot;
        private final ItemStack savedMain;
        private final ItemStack trialCopy;
        private final UUID copyId;
        private final double laneX;
        private SpiritTrialDummyEntity dummy;
        private int elapsed = -1;
        private int atmosphereTicks;
        private int lightningIndex;
        private int flyingDamageDepth;
        private double totalDamage;
        private DamageChannel channel = DamageChannel.NONE;
        private boolean finished;

        private Session(ServerPlayer player, ResourceKey<Level> originDimension,
                        net.minecraft.world.phys.Vec3 origin, float originYaw, float originPitch,
                        ItemStack sourceWeapon, FlyingSwordMaterial coreMaterial, Shape shape,
                        int selectedSlot, ItemStack savedMain, ItemStack trialCopy, UUID copyId, double laneX) {
            this.player = player;
            this.originDimension = originDimension;
            this.origin = origin;
            this.originYaw = originYaw;
            this.originPitch = originPitch;
            this.sourceWeapon = sourceWeapon;
            this.coreMaterial = coreMaterial;
            this.shape = shape;
            this.selectedSlot = selectedSlot;
            this.savedMain = savedMain;
            this.trialCopy = trialCopy;
            this.copyId = copyId;
            this.laneX = laneX;
        }

        private void spawnDummy(ServerLevel level) {
            dummy = new SpiritTrialDummyEntity(ModEntities.SPIRIT_TRIAL_DUMMY.get(), level);
            dummy.setPos(laneX + 0.5D, 127.0D, 8.5D);
            dummy.setYRot(180.0F);
            level.addFreshEntity(dummy);
            DUMMIES.put(dummy.getUUID(), this);
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
            if (!player.isAlive() || !player.level().dimension().equals(TRIAL_LEVEL)) {
                finish(this, false);
                return;
            }
            if (player.getY() < 112.0D) {
                player.teleportTo((ServerLevel) player.level(), laneX + 0.5D, 127.0D, 0.5D,
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
            bolt.moveTo(laneX + 0.5D + Math.cos(angle) * 7.0D, 127.0D,
                    3.5D + Math.sin(angle) * 7.0D);
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
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (isTrialCopy(player.getInventory().getItem(slot), copyId)) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
            ItemStack occupant = player.getInventory().getItem(selectedSlot);
            if (!occupant.isEmpty()) {
                player.getInventory().setItem(selectedSlot, ItemStack.EMPTY);
                if (!player.getInventory().add(occupant)) player.drop(occupant, false);
            }
            player.getInventory().setItem(selectedSlot, result);
            if (!savedMain.isEmpty()) {
                if (!player.getInventory().add(savedMain)) player.drop(savedMain, false);
            }
        }
    }
}
