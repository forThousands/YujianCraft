package dev.yujiancraft.network;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.client.ClientSettingsState;
import dev.yujiancraft.client.ClientBalanceState;
import dev.yujiancraft.client.ClientTargetState;
import dev.yujiancraft.client.ClientManualGuidanceState;
import dev.yujiancraft.client.ClientSwordRidingState;
import dev.yujiancraft.combat.SwordSettings;
import dev.yujiancraft.config.SwordBalanceConfig;
import dev.yujiancraft.config.EffectBalanceConfig;
import dev.yujiancraft.config.EffectParameter;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.combat.TargetLockManager;
import dev.yujiancraft.combat.ManualGuidanceManager;
import dev.yujiancraft.combat.technique.ArtifactActionManager;
import dev.yujiancraft.flight.SwordRidingManager;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import dev.yujiancraft.wanxiang.WanxiangWeaponCatalog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTOCOL = "18";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(YujianCraft.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, ToggleFormationPacket.class,
                (message, buffer) -> { }, buffer -> new ToggleFormationPacket(),
                ModNetwork::handleToggleFormation, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, RequestSettingsPacket.class,
                (message, buffer) -> { }, buffer -> new RequestSettingsPacket(),
                ModNetwork::handleRequestSettings, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, UpdateSettingsPacket.class,
                UpdateSettingsPacket::encode, UpdateSettingsPacket::decode,
                ModNetwork::handleUpdateSettings, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, SyncSettingsPacket.class,
                SyncSettingsPacket::encode, SyncSettingsPacket::decode,
                ModNetwork::handleSyncSettings, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, LockedTargetPacket.class,
                LockedTargetPacket::encode, LockedTargetPacket::decode,
                ModNetwork::handleLockedTarget, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, LockCrosshairNowPacket.class,
                LockCrosshairNowPacket::encode, LockCrosshairNowPacket::decode,
                ModNetwork::handleLockCrosshairNow, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, RequestBalancePacket.class,
                (message, buffer) -> { }, buffer -> new RequestBalancePacket(),
                ModNetwork::handleRequestBalance, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(7, SyncBalancePacket.class,
                SyncBalancePacket::encode, SyncBalancePacket::decode,
                ModNetwork::handleSyncBalance, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8, UpdateBalancePacket.class,
                UpdateBalancePacket::encode, UpdateBalancePacket::decode,
                ModNetwork::handleUpdateBalance, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(9, UpdateEffectBalancePacket.class,
                UpdateEffectBalancePacket::encode, UpdateEffectBalancePacket::decode,
                ModNetwork::handleUpdateEffectBalance, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(11, ToggleSwordRidingPacket.class,
                ToggleSwordRidingPacket::encode, ToggleSwordRidingPacket::decode,
                ModNetwork::handleToggleSwordRiding, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(12, SwordRidingStatePacket.class,
                SwordRidingStatePacket::encode, SwordRidingStatePacket::decode,
                ModNetwork::handleSwordRidingState, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(13, ManualLaunchPacket.class,
                ManualLaunchPacket::encode, ManualLaunchPacket::decode,
                ModNetwork::handleManualLaunch, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(14, ManualAimPacket.class,
                ManualAimPacket::encode, ManualAimPacket::decode,
                ModNetwork::handleManualAim, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(15, ManualLockPacket.class,
                ManualLockPacket::encode, ManualLockPacket::decode,
                ModNetwork::handleManualLock, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(16, ManualGuidanceStatePacket.class,
                ManualGuidanceStatePacket::encode, ManualGuidanceStatePacket::decode,
                ModNetwork::handleManualGuidanceState, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(17, SwordImpactPacket.class,
                SwordImpactPacket::encode, SwordImpactPacket::decode,
                ModNetwork::handleSwordImpact, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(18, ToggleSummonedSwordsPacket.class,
                (message, buffer) -> { }, buffer -> new ToggleSummonedSwordsPacket(),
                ModNetwork::handleToggleSummonedSwords, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(19, OpenGuidePacket.class,
                (message, buffer) -> { }, buffer -> new OpenGuidePacket(),
                ModNetwork::handleOpenGuide, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(20, ManualTrialResultPacket.class,
                ManualTrialResultPacket::encode, ManualTrialResultPacket::decode,
                ModNetwork::handleManualTrialResult, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(21, ArtifactActionPacket.class,
                ArtifactActionPacket::encode, ArtifactActionPacket::decode,
                ModNetwork::handleArtifactAction, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(22, CycleTechniquePacket.class,
                (message, buffer) -> { }, buffer -> new CycleTechniquePacket(),
                ModNetwork::handleCycleTechnique, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(23, TrialCountdownPacket.class,
                TrialCountdownPacket::encode, TrialCountdownPacket::decode,
                ModNetwork::handleTrialCountdown, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(24, TechniqueNoticePacket.class,
                TechniqueNoticePacket::encode, TechniqueNoticePacket::decode,
                ModNetwork::handleTechniqueNotice, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(25, SwordArrayFinisherPacket.class,
                SwordArrayFinisherPacket::encode, SwordArrayFinisherPacket::decode,
                ModNetwork::handleSwordArrayFinisher, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void handleCycleTechnique(CycleTechniquePacket message,
                                             Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                SwordSettings settings = FlyingSwordItem.cycleTechnique(sender);
                sendSettings(sender, settings);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleTrialCountdown(TrialCountdownPacket message,
                                             Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.yujiancraft.client.ClientTrialCountdownState.show(message.seconds)));
        context.setPacketHandled(true);
    }

    private static void handleTechniqueNotice(TechniqueNoticePacket message,
                                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.yujiancraft.client.ClientTechniqueOverlayState.showTechnique(message.technique)));
        context.setPacketHandled(true);
    }

    private static void handleSwordArrayFinisher(SwordArrayFinisherPacket message,
                                                  Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.yujiancraft.client.ClientTechniqueOverlayState.showFinisherFlash(
                        message.startGameTick, message.bottom, message.top, message.maximumRadius,
                        message.chargeTicks, message.holdTicks, message.expandTicks, message.sustainTicks)));
        context.setPacketHandled(true);
    }

    private static void handleToggleFormation(ToggleFormationPacket message,
                                               Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) FlyingSwordItem.toggleFormationMode(sender);
        });
        context.setPacketHandled(true);
    }

    private static void handleArtifactAction(ArtifactActionPacket message,
                                             Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) ArtifactActionManager.handleAction(sender,
                    message.hasBlock ? message.blockPos : null, message.face);
        });
        context.setPacketHandled(true);
    }

    private static void handleOpenGuide(OpenGuidePacket message,
                                        Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.yujiancraft.client.YujianGuideScreen.open()));
        context.setPacketHandled(true);
    }

    private static void handleManualTrialResult(ManualTrialResultPacket message,
                                                Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.yujiancraft.client.ManualSpiritTrialResultScreen.open(
                        message.damage, message.dps, message.mode)));
        context.setPacketHandled(true);
    }


    private static void handleToggleSummonedSwords(ToggleSummonedSwordsPacket message,
                                                   Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) FlyingSwordItem.toggleSummonedFormation(sender, sender.getMainHandItem());
        });
        context.setPacketHandled(true);
    }

    private static void handleRequestBalance(RequestBalancePacket message,
                                             Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null && sender.hasPermissions(2)) sendBalances(sender);
        });
        context.setPacketHandled(true);
    }

    private static void handleUpdateBalance(UpdateBalancePacket message,
                                            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            if (message.material < 0 || message.material >= FlyingSwordMaterial.values().length) return;
            FlyingSwordMaterial material = FlyingSwordMaterial.fromOrdinal(message.material);
            if (message.reset) SwordBalanceConfig.reset(material);
            else SwordBalanceConfig.update(material, message.damage, message.flightSpeed);
            sendBalances(sender);
        });
        context.setPacketHandled(true);
    }

    private static void handleSyncBalance(SyncBalancePacket message,
                                          Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    EffectBalanceConfig.acceptRemoteSnapshot(message.effectValues);
                    ClientBalanceState.acceptFromServer(message.balances, message.effectValues);
                }));
        context.setPacketHandled(true);
    }

    private static void handleUpdateEffectBalance(UpdateEffectBalancePacket message,
                                                  Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            if (message.parameter < 0 || message.parameter >= EffectParameter.values().length) return;
            EffectParameter parameter = EffectParameter.values()[message.parameter];
            if (message.reset) EffectBalanceConfig.reset(parameter);
            else EffectBalanceConfig.update(parameter, message.value);
            sendBalances(sender);
        });
        context.setPacketHandled(true);
    }

    private static void handleRequestSettings(RequestSettingsPacket message,
                                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) sendSettings(sender, FlyingSwordItem.getSettings(sender));
        });
        context.setPacketHandled(true);
    }

    private static void handleUpdateSettings(UpdateSettingsPacket message,
                                             Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                SwordSettings before = FlyingSwordItem.getSettings(sender);
                SwordSettings requested = message.toSettings();
                SwordSettings applied = requested;
                if (!sender.hasPermissions(2)) {
                    applied = new SwordSettings(before.minimumDockTicks(), before.automaticTargetRadius(),
                            before.crosshairLockRadius(), requested.targetingMode(), requested.attackMode(),
                            requested.techniqueMode());
                }
                net.minecraft.world.item.ItemStack controlled = FlyingSwordItem.findFlyingSword(sender);
                if (WanxiangSwordData.isTempered(controlled)) {
                    dev.yujiancraft.combat.technique.TechniqueMode effective =
                            WanxiangWeaponCatalog.effectiveTechnique(sender.server, controlled,
                                    applied.techniqueMode());
                    applied = new SwordSettings(applied.minimumDockTicks(), applied.automaticTargetRadius(),
                            applied.crosshairLockRadius(), applied.targetingMode(), applied.attackMode(),
                            effective);
                }
                FlyingSwordItem.setSettings(sender, applied);
                sendSettings(sender, applied);
                if (before.techniqueMode() != applied.techniqueMode()) {
                    sender.level().playSound(null, sender.blockPosition(), net.minecraft.sounds.SoundEvents.TRIDENT_RETURN,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.42F);
                    sendTechniqueNotice(sender, applied.techniqueMode());
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSyncSettings(SyncSettingsPacket message,
                                           Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSettingsState.acceptFromServer(message.toSettings(), message.canEditBalance)));
        context.setPacketHandled(true);
    }

    private static void handleLockedTarget(LockedTargetPacket message,
                                           Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientTargetState.setLockedTargetId(message.targetId)));
        context.setPacketHandled(true);
    }

    private static void handleLockCrosshairNow(LockCrosshairNowPacket message,
                                               Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) TargetLockManager.lockCrosshairNow(sender, message.entityId);
        });
        context.setPacketHandled(true);
    }

    private static void handleToggleSwordRiding(ToggleSwordRidingPacket message,
                                                Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) SwordRidingManager.setRiding(sender, message.active);
        });
        context.setPacketHandled(true);
    }

    private static void handleSwordRidingState(SwordRidingStatePacket message,
                                               Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSwordRidingState.applyServerState(message.active,
                        message.mayfly, message.flying, message.flyingSpeed)));
        context.setPacketHandled(true);
    }

    private static void handleManualLaunch(ManualLaunchPacket message,
                                           Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) ManualGuidanceManager.launchReadySalvo(sender, message.direction());
        });
        context.setPacketHandled(true);
    }

    private static void handleManualAim(ManualAimPacket message,
                                        Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) ManualGuidanceManager.acceptAim(sender, message.direction());
        });
        context.setPacketHandled(true);
    }

    private static void handleManualLock(ManualLockPacket message,
                                         Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) ManualGuidanceManager.lockSalvoTarget(sender, message.entityId);
        });
        context.setPacketHandled(true);
    }

    private static void handleManualGuidanceState(ManualGuidanceStatePacket message,
                                                  Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientManualGuidanceState.setGuiding(message.guiding)));
        context.setPacketHandled(true);
    }

    private static void handleSwordImpact(SwordImpactPacket message,
                                          Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.yujiancraft.client.ClientImpactEffects.accept(message.position(),
                        message.direction(), message.visualModules, message.material)));
        context.setPacketHandled(true);
    }

    public static void sendSettings(ServerPlayer player, SwordSettings settings) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                SyncSettingsPacket.from(settings, player.hasPermissions(2)));
    }

    private static void sendBalances(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncBalancePacket(SwordBalanceConfig.snapshot(), EffectBalanceConfig.snapshot()));
    }

    public static void sendLockedTarget(ServerPlayer player, UUID targetId) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new LockedTargetPacket(targetId));
    }

    public static void sendSwordRidingState(ServerPlayer player, boolean active) {
        net.minecraft.world.entity.player.Abilities abilities = player.getAbilities();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SwordRidingStatePacket(active,
                abilities.mayfly, abilities.flying, abilities.getFlyingSpeed()));
    }

    public static void sendManualGuidanceState(ServerPlayer player, boolean guiding) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ManualGuidanceStatePacket(guiding));
    }

    public static void openGuide(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenGuidePacket());
    }

    public static void sendManualTrialResult(ServerPlayer player, double damage, double dps, int mode) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ManualTrialResultPacket(damage, dps, mode));
    }

    public static void sendTrialCountdown(ServerPlayer player, int seconds) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TrialCountdownPacket(seconds));
    }

    public static void sendTechniqueNotice(ServerPlayer player,
                                           dev.yujiancraft.combat.technique.TechniqueMode technique) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TechniqueNoticePacket(technique.ordinal()));
    }

    public static void sendSwordArrayFinisher(ServerPlayer player, long startGameTick,
                                               Vec3 bottom, Vec3 top,
                                               float maximumRadius, int chargeTicks, int holdTicks,
                                               int expandTicks, int sustainTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SwordArrayFinisherPacket(
                startGameTick, bottom, top, maximumRadius, chargeTicks, holdTicks, expandTicks,
                sustainTicks));
    }

    public static void sendSwordImpact(FlyingSwordEntity sword, LivingEntity target, Vec3 direction) {
        Vec3 safeDirection = direction.lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, 1.0D, 0.0D) : direction.normalize();
        // Put the flash on the entry surface instead of at the target's hidden centre. This keeps
        // the module-independent base effect visible through ordinary entity depth testing.
        Vec3 centre = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
        Vec3 traceStart = sword.position().subtract(safeDirection.scale(2.5D));
        Vec3 traceEnd = sword.position().add(safeDirection.scale(2.5D));
        Vec3 position = target.getBoundingBox().inflate(0.035D).clip(traceStart, traceEnd)
                .orElse(centre.subtract(safeDirection.scale(Math.max(0.22D, target.getBbWidth() * 0.5D))));
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sword),
                new SwordImpactPacket(position, safeDirection, sword.getVisualModuleMask(),
                        sword.getMaterialType().ordinal()));
    }

    public record ToggleFormationPacket() {
    }

    public record ToggleSummonedSwordsPacket() {
    }

    public record OpenGuidePacket() {
    }

    public record ManualTrialResultPacket(double damage, double dps, int mode) {
        private static void encode(ManualTrialResultPacket message, FriendlyByteBuf buffer) {
            buffer.writeDouble(message.damage);
            buffer.writeDouble(message.dps);
            buffer.writeVarInt(message.mode);
        }

        private static ManualTrialResultPacket decode(FriendlyByteBuf buffer) {
            return new ManualTrialResultPacket(buffer.readDouble(), buffer.readDouble(), buffer.readVarInt());
        }
    }


    public record RequestSettingsPacket() {
    }

    public record CycleTechniquePacket() {
    }

    public record TrialCountdownPacket(int seconds) {
        private static void encode(TrialCountdownPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.seconds);
        }

        private static TrialCountdownPacket decode(FriendlyByteBuf buffer) {
            return new TrialCountdownPacket(buffer.readVarInt());
        }
    }

    public record TechniqueNoticePacket(int technique) {
        private static void encode(TechniqueNoticePacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.technique);
        }

        private static TechniqueNoticePacket decode(FriendlyByteBuf buffer) {
            return new TechniqueNoticePacket(buffer.readVarInt());
        }
    }

    public record SwordArrayFinisherPacket(long startGameTick, Vec3 bottom, Vec3 top,
                                           float maximumRadius,
                                           int chargeTicks, int holdTicks,
                                           int expandTicks, int sustainTicks) {
        private static void encode(SwordArrayFinisherPacket message, FriendlyByteBuf buffer) {
            buffer.writeLong(message.startGameTick);
            buffer.writeDouble(message.bottom.x);
            buffer.writeDouble(message.bottom.y);
            buffer.writeDouble(message.bottom.z);
            buffer.writeDouble(message.top.x);
            buffer.writeDouble(message.top.y);
            buffer.writeDouble(message.top.z);
            buffer.writeFloat(message.maximumRadius);
            buffer.writeVarInt(message.chargeTicks);
            buffer.writeVarInt(message.holdTicks);
            buffer.writeVarInt(message.expandTicks);
            buffer.writeVarInt(message.sustainTicks);
        }

        private static SwordArrayFinisherPacket decode(FriendlyByteBuf buffer) {
            long startGameTick = buffer.readLong();
            Vec3 bottom = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            Vec3 top = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            return new SwordArrayFinisherPacket(startGameTick, bottom, top, buffer.readFloat(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record ArtifactActionPacket(boolean hasBlock, net.minecraft.core.BlockPos blockPos,
                                       net.minecraft.core.Direction face) {
        public static ArtifactActionPacket miss() {
            return new ArtifactActionPacket(false, net.minecraft.core.BlockPos.ZERO,
                    net.minecraft.core.Direction.UP);
        }

        private static void encode(ArtifactActionPacket message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.hasBlock);
            if (message.hasBlock) {
                buffer.writeBlockPos(message.blockPos);
                buffer.writeEnum(message.face);
            }
        }

        private static ArtifactActionPacket decode(FriendlyByteBuf buffer) {
            if (!buffer.readBoolean()) return miss();
            return new ArtifactActionPacket(true, buffer.readBlockPos(),
                    buffer.readEnum(net.minecraft.core.Direction.class));
        }
    }

    public record LockCrosshairNowPacket(int entityId) {
        private static void encode(LockCrosshairNowPacket message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.entityId);
        }

        private static LockCrosshairNowPacket decode(FriendlyByteBuf buffer) {
            return new LockCrosshairNowPacket(buffer.readInt());
        }
    }

    public record ToggleSwordRidingPacket(boolean active) {
        private static void encode(ToggleSwordRidingPacket message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.active);
        }

        private static ToggleSwordRidingPacket decode(FriendlyByteBuf buffer) {
            return new ToggleSwordRidingPacket(buffer.readBoolean());
        }
    }

    public record SwordRidingStatePacket(boolean active, boolean mayfly, boolean flying,
                                         float flyingSpeed) {
        private static void encode(SwordRidingStatePacket message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.active);
            buffer.writeBoolean(message.mayfly);
            buffer.writeBoolean(message.flying);
            buffer.writeFloat(message.flyingSpeed);
        }

        private static SwordRidingStatePacket decode(FriendlyByteBuf buffer) {
            return new SwordRidingStatePacket(buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readFloat());
        }
    }

    public record ManualLaunchPacket(float x, float y, float z) {
        public ManualLaunchPacket(Vec3 direction) {
            this((float) direction.x, (float) direction.y, (float) direction.z);
        }

        private Vec3 direction() { return new Vec3(x, y, z); }

        private static void encode(ManualLaunchPacket message, FriendlyByteBuf buffer) {
            buffer.writeFloat(message.x);
            buffer.writeFloat(message.y);
            buffer.writeFloat(message.z);
        }

        private static ManualLaunchPacket decode(FriendlyByteBuf buffer) {
            return new ManualLaunchPacket(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }
    }

    public record ManualAimPacket(float x, float y, float z) {
        public ManualAimPacket(Vec3 direction) {
            this((float) direction.x, (float) direction.y, (float) direction.z);
        }

        private Vec3 direction() { return new Vec3(x, y, z); }

        private static void encode(ManualAimPacket message, FriendlyByteBuf buffer) {
            buffer.writeFloat(message.x);
            buffer.writeFloat(message.y);
            buffer.writeFloat(message.z);
        }

        private static ManualAimPacket decode(FriendlyByteBuf buffer) {
            return new ManualAimPacket(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }
    }

    public record ManualLockPacket(int entityId) {
        private static void encode(ManualLockPacket message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.entityId);
        }

        private static ManualLockPacket decode(FriendlyByteBuf buffer) {
            return new ManualLockPacket(buffer.readInt());
        }
    }

    public record ManualGuidanceStatePacket(boolean guiding) {
        private static void encode(ManualGuidanceStatePacket message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.guiding);
        }

        private static ManualGuidanceStatePacket decode(FriendlyByteBuf buffer) {
            return new ManualGuidanceStatePacket(buffer.readBoolean());
        }
    }

    public record SwordImpactPacket(double x, double y, double z,
                                    float directionX, float directionY, float directionZ,
                                    int visualModules, int material) {
        public SwordImpactPacket(Vec3 position, Vec3 direction, int visualModules, int material) {
            this(position.x, position.y, position.z, (float) direction.x, (float) direction.y,
                    (float) direction.z, visualModules, material);
        }

        public Vec3 position() { return new Vec3(x, y, z); }
        public Vec3 direction() { return new Vec3(directionX, directionY, directionZ); }

        private static void encode(SwordImpactPacket message, FriendlyByteBuf buffer) {
            buffer.writeDouble(message.x);
            buffer.writeDouble(message.y);
            buffer.writeDouble(message.z);
            buffer.writeFloat(message.directionX);
            buffer.writeFloat(message.directionY);
            buffer.writeFloat(message.directionZ);
            buffer.writeVarInt(message.visualModules);
            buffer.writeVarInt(message.material);
        }

        private static SwordImpactPacket decode(FriendlyByteBuf buffer) {
            return new SwordImpactPacket(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(),
                    buffer.readVarInt());
        }
    }

    public record RequestBalancePacket() {
    }

    public record UpdateSettingsPacket(int minimumDockTicks, double automaticRadius, double lockRadius,
                                       int targetingMode, int attackMode, int techniqueMode) {
        public static UpdateSettingsPacket from(SwordSettings settings) {
            return new UpdateSettingsPacket(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                    settings.crosshairLockRadius(), settings.targetingMode().ordinal(), settings.attackMode().ordinal(),
                    settings.techniqueMode().ordinal());
        }

        public SwordSettings toSettings() {
            return SwordSettings.fromNetwork(minimumDockTicks, automaticRadius, lockRadius,
                    targetingMode, attackMode, techniqueMode);
        }

        private static void encode(UpdateSettingsPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.minimumDockTicks);
            buffer.writeDouble(message.automaticRadius);
            buffer.writeDouble(message.lockRadius);
            buffer.writeVarInt(message.targetingMode);
            buffer.writeVarInt(message.attackMode);
            buffer.writeVarInt(message.techniqueMode);
        }

        private static UpdateSettingsPacket decode(FriendlyByteBuf buffer) {
            return new UpdateSettingsPacket(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record SyncSettingsPacket(int minimumDockTicks, double automaticRadius, double lockRadius,
                                     int targetingMode, int attackMode, int techniqueMode,
                                     boolean canEditBalance) {
        public static SyncSettingsPacket from(SwordSettings settings, boolean canEditBalance) {
            return new SyncSettingsPacket(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                    settings.crosshairLockRadius(), settings.targetingMode().ordinal(), settings.attackMode().ordinal(),
                    settings.techniqueMode().ordinal(), canEditBalance);
        }

        public SwordSettings toSettings() {
            return SwordSettings.fromNetwork(minimumDockTicks, automaticRadius, lockRadius,
                    targetingMode, attackMode, techniqueMode);
        }

        private static void encode(SyncSettingsPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.minimumDockTicks);
            buffer.writeDouble(message.automaticRadius);
            buffer.writeDouble(message.lockRadius);
            buffer.writeVarInt(message.targetingMode);
            buffer.writeVarInt(message.attackMode);
            buffer.writeVarInt(message.techniqueMode);
            buffer.writeBoolean(message.canEditBalance);
        }

        private static SyncSettingsPacket decode(FriendlyByteBuf buffer) {
            return new SyncSettingsPacket(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }
    }

    public record UpdateBalancePacket(int material, double damage, double flightSpeed, boolean reset) {
        private static void encode(UpdateBalancePacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.material);
            buffer.writeDouble(message.damage);
            buffer.writeDouble(message.flightSpeed);
            buffer.writeBoolean(message.reset);
        }

        private static UpdateBalancePacket decode(FriendlyByteBuf buffer) {
            return new UpdateBalancePacket(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readBoolean());
        }
    }

    public record UpdateEffectBalancePacket(int parameter, double value, boolean reset) {
        private static void encode(UpdateEffectBalancePacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.parameter);
            buffer.writeDouble(message.value);
            buffer.writeBoolean(message.reset);
        }

        private static UpdateEffectBalancePacket decode(FriendlyByteBuf buffer) {
            return new UpdateEffectBalancePacket(buffer.readVarInt(), buffer.readDouble(), buffer.readBoolean());
        }
    }

    public record SyncBalancePacket(Map<FlyingSwordMaterial, SwordBalanceConfig.Balance> balances,
                                    Map<EffectParameter, Double> effectValues) {
        private static void encode(SyncBalancePacket message, FriendlyByteBuf buffer) {
            for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
                SwordBalanceConfig.Balance balance = message.balances.get(material);
                buffer.writeDouble(balance.damage());
                buffer.writeDouble(balance.flightSpeed());
            }
            for (EffectParameter parameter : EffectParameter.values()) {
                buffer.writeDouble(message.effectValues.get(parameter));
            }
        }

        private static SyncBalancePacket decode(FriendlyByteBuf buffer) {
            EnumMap<FlyingSwordMaterial, SwordBalanceConfig.Balance> balances =
                    new EnumMap<>(FlyingSwordMaterial.class);
            for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
                balances.put(material, new SwordBalanceConfig.Balance(buffer.readDouble(), buffer.readDouble()));
            }
            EnumMap<EffectParameter, Double> effectValues = new EnumMap<>(EffectParameter.class);
            for (EffectParameter parameter : EffectParameter.values()) {
                effectValues.put(parameter, buffer.readDouble());
            }
            return new SyncBalancePacket(balances, effectValues);
        }
    }

    public record LockedTargetPacket(UUID targetId) {
        private static void encode(LockedTargetPacket message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.targetId != null);
            if (message.targetId != null) buffer.writeUUID(message.targetId);
        }

        private static LockedTargetPacket decode(FriendlyByteBuf buffer) {
            return new LockedTargetPacket(buffer.readBoolean() ? buffer.readUUID() : null);
        }
    }
}
