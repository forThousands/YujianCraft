package dev.yujiancraft.network;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.client.ClientSettingsState;
import dev.yujiancraft.client.ClientBalanceState;
import dev.yujiancraft.client.ClientTargetState;
import dev.yujiancraft.client.ClientManualGuidanceState;
import dev.yujiancraft.client.ClientSwordRidingState;
import dev.yujiancraft.combat.SwordSettings;
import dev.yujiancraft.combat.TargetProtectionManager;
import dev.yujiancraft.config.SwordBalanceConfig;
import dev.yujiancraft.config.EffectBalanceConfig;
import dev.yujiancraft.config.EffectParameter;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.combat.TargetLockManager;
import dev.yujiancraft.combat.ManualGuidanceManager;
import dev.yujiancraft.combat.technique.ArtifactActionManager;
import dev.yujiancraft.combat.combo.SwordComboManager;
import dev.yujiancraft.flight.SwordRidingManager;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import dev.yujiancraft.wanxiang.WanxiangWeaponCatalog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ModNetwork {
    private static final String PROTOCOL = "27";
    private static final Map<Class<?>, CustomPacketPayload.Type<?>> PAYLOAD_TYPES = new HashMap<>();

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        toServer(registrar, "toggle_formation", ToggleFormationPacket.class,
                unit(new ToggleFormationPacket()), ModNetwork::handleToggleFormation);
        toServer(registrar, "request_settings", RequestSettingsPacket.class,
                unit(new RequestSettingsPacket()), ModNetwork::handleRequestSettings);
        toServer(registrar, "update_settings", UpdateSettingsPacket.class,
                codec(UpdateSettingsPacket::encode, UpdateSettingsPacket::decode), ModNetwork::handleUpdateSettings);
        toClient(registrar, "sync_settings", SyncSettingsPacket.class,
                codec(SyncSettingsPacket::encode, SyncSettingsPacket::decode), ModNetwork::handleSyncSettings);
        toClient(registrar, "locked_target", LockedTargetPacket.class,
                codec(LockedTargetPacket::encode, LockedTargetPacket::decode), ModNetwork::handleLockedTarget);
        toServer(registrar, "lock_crosshair_now", LockCrosshairNowPacket.class,
                codec(LockCrosshairNowPacket::encode, LockCrosshairNowPacket::decode), ModNetwork::handleLockCrosshairNow);
        toServer(registrar, "request_balance", RequestBalancePacket.class,
                unit(new RequestBalancePacket()), ModNetwork::handleRequestBalance);
        toClient(registrar, "sync_balance", SyncBalancePacket.class,
                codec(SyncBalancePacket::encode, SyncBalancePacket::decode), ModNetwork::handleSyncBalance);
        toServer(registrar, "update_balance", UpdateBalancePacket.class,
                codec(UpdateBalancePacket::encode, UpdateBalancePacket::decode), ModNetwork::handleUpdateBalance);
        toServer(registrar, "update_effect_balance", UpdateEffectBalancePacket.class,
                codec(UpdateEffectBalancePacket::encode, UpdateEffectBalancePacket::decode), ModNetwork::handleUpdateEffectBalance);
        toServer(registrar, "toggle_sword_riding", ToggleSwordRidingPacket.class,
                codec(ToggleSwordRidingPacket::encode, ToggleSwordRidingPacket::decode), ModNetwork::handleToggleSwordRiding);
        toClient(registrar, "sword_riding_state", SwordRidingStatePacket.class,
                codec(SwordRidingStatePacket::encode, SwordRidingStatePacket::decode), ModNetwork::handleSwordRidingState);
        toServer(registrar, "manual_launch", ManualLaunchPacket.class,
                codec(ManualLaunchPacket::encode, ManualLaunchPacket::decode), ModNetwork::handleManualLaunch);
        toServer(registrar, "manual_aim", ManualAimPacket.class,
                codec(ManualAimPacket::encode, ManualAimPacket::decode), ModNetwork::handleManualAim);
        toServer(registrar, "manual_lock", ManualLockPacket.class,
                codec(ManualLockPacket::encode, ManualLockPacket::decode), ModNetwork::handleManualLock);
        toClient(registrar, "manual_guidance_state", ManualGuidanceStatePacket.class,
                codec(ManualGuidanceStatePacket::encode, ManualGuidanceStatePacket::decode), ModNetwork::handleManualGuidanceState);
        toClient(registrar, "sword_impact", SwordImpactPacket.class,
                codec(SwordImpactPacket::encode, SwordImpactPacket::decode), ModNetwork::handleSwordImpact);
        toServer(registrar, "toggle_summoned_swords", ToggleSummonedSwordsPacket.class,
                unit(new ToggleSummonedSwordsPacket()), ModNetwork::handleToggleSummonedSwords);
        toClient(registrar, "open_guide", OpenGuidePacket.class,
                unit(new OpenGuidePacket()), ModNetwork::handleOpenGuide);
        toClient(registrar, "manual_trial_result", ManualTrialResultPacket.class,
                codec(ManualTrialResultPacket::encode, ManualTrialResultPacket::decode), ModNetwork::handleManualTrialResult);
        toServer(registrar, "artifact_action", ArtifactActionPacket.class,
                codec(ArtifactActionPacket::encode, ArtifactActionPacket::decode), ModNetwork::handleArtifactAction);
        toServer(registrar, "cycle_technique", CycleTechniquePacket.class,
                unit(new CycleTechniquePacket()), ModNetwork::handleCycleTechnique);
        toClient(registrar, "trial_countdown", TrialCountdownPacket.class,
                codec(TrialCountdownPacket::encode, TrialCountdownPacket::decode), ModNetwork::handleTrialCountdown);
        toClient(registrar, "technique_notice", TechniqueNoticePacket.class,
                codec(TechniqueNoticePacket::encode, TechniqueNoticePacket::decode), ModNetwork::handleTechniqueNotice);
        toClient(registrar, "sword_array_finisher", SwordArrayFinisherPacket.class,
                codec(SwordArrayFinisherPacket::encode, SwordArrayFinisherPacket::decode), ModNetwork::handleSwordArrayFinisher);
        toServer(registrar, "activate_sword_array", ActivateSwordArrayPacket.class,
                codec(ActivateSwordArrayPacket::encode, ActivateSwordArrayPacket::decode), ModNetwork::handleActivateSwordArray);
        toServer(registrar, "toggle_sword_array_style", ToggleSwordArrayStylePacket.class,
                unit(new ToggleSwordArrayStylePacket()), ModNetwork::handleToggleSwordArrayStyle);
        toServer(registrar, "toggle_combo", ToggleComboPacket.class,
                unit(new ToggleComboPacket()), ModNetwork::handleToggleCombo);
        toServer(registrar, "combo_attack", ComboAttackPacket.class,
                codec(ComboAttackPacket::encode, ComboAttackPacket::decode), ModNetwork::handleComboAttack);
        toClient(registrar, "combo_state", ComboStatePacket.class,
                codec(ComboStatePacket::encode, ComboStatePacket::decode), ModNetwork::handleComboState);
        toServer(registrar, "cycle_combo_style", CycleComboStylePacket.class,
                unit(new CycleComboStylePacket()), ModNetwork::handleCycleComboStyle);
        toClient(registrar, "formation_state", FormationStatePacket.class,
                codec(FormationStatePacket::encode, FormationStatePacket::decode), ModNetwork::handleFormationState);
        toServer(registrar, "toggle_target_protection", ToggleTargetProtectionPacket.class,
                codec(ToggleTargetProtectionPacket::encode, ToggleTargetProtectionPacket::decode),
                ModNetwork::handleToggleTargetProtection);
        toServer(registrar, "remove_target_protection", RemoveTargetProtectionPacket.class,
                codec(RemoveTargetProtectionPacket::encode, RemoveTargetProtectionPacket::decode),
                ModNetwork::handleRemoveTargetProtection);
        toServer(registrar, "request_target_protections", RequestTargetProtectionsPacket.class,
                unit(new RequestTargetProtectionsPacket()), ModNetwork::handleRequestTargetProtections);
        toClient(registrar, "sync_target_protections", SyncTargetProtectionsPacket.class,
                codec(SyncTargetProtectionsPacket::encode, SyncTargetProtectionsPacket::decode),
                ModNetwork::handleSyncTargetProtections);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static <T extends YujianPayload> void toServer(PayloadRegistrar registrar, String path,
                                                            Class<T> payloadClass,
                                                            StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                            IPayloadHandler<T> handler) {
        CustomPacketPayload.Type<T> type = registerType(path, payloadClass);
        registrar.playToServer(type, codec, handler);
    }

    private static <T extends YujianPayload> void toClient(PayloadRegistrar registrar, String path,
                                                            Class<T> payloadClass,
                                                            StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                            IPayloadHandler<T> handler) {
        CustomPacketPayload.Type<T> type = registerType(path, payloadClass);
        registrar.playToClient(type, codec, handler);
    }

    private static <T extends YujianPayload> CustomPacketPayload.Type<T> registerType(
            String path, Class<T> payloadClass) {
        CustomPacketPayload.Type<T> type = new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(YujianCraft.MOD_ID, path));
        PAYLOAD_TYPES.put(payloadClass, type);
        return type;
    }

    @SuppressWarnings("unchecked")
    private static CustomPacketPayload.Type<? extends CustomPacketPayload> typeFor(Class<?> payloadClass) {
        CustomPacketPayload.Type<?> type = PAYLOAD_TYPES.get(payloadClass);
        if (type == null) throw new IllegalStateException("Unregistered Yujian payload " + payloadClass.getName());
        return (CustomPacketPayload.Type<? extends CustomPacketPayload>) type;
    }

    private static <T extends YujianPayload> StreamCodec<RegistryFriendlyByteBuf, T> codec(
            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder) {
        return StreamCodec.of((buffer, payload) -> encoder.accept(payload, buffer), decoder::apply);
    }

    private static <T extends YujianPayload> StreamCodec<RegistryFriendlyByteBuf, T> unit(T payload) {
        return StreamCodec.unit(payload);
    }

    public interface YujianPayload extends CustomPacketPayload {
        @Override
        default Type<? extends CustomPacketPayload> type() {
            return ModNetwork.typeFor(getClass());
        }
    }

    private static void handleToggleCombo(ToggleComboPacket message,
                                          IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) SwordComboManager.toggle(sender);
        });
    }

    private static void handleComboAttack(ComboAttackPacket message,
                                          IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) SwordComboManager.attack(sender, message.targetId(), message.look());
        });
    }

    private static void handleCycleComboStyle(CycleComboStylePacket message,
                                              IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) SwordComboManager.cycleStyle(sender);
        });
    }

    private static void handleComboState(ComboStatePacket message,
                                         IPayloadContext context) {
        context.enqueueWork(() -> dev.yujiancraft.client.ClientComboState.accept(message));
    }

    private static void handleFormationState(FormationStatePacket message,
                                              IPayloadContext context) {
        context.enqueueWork(() -> dev.yujiancraft.client.ClientInputEvents.onFormationState(message.deployed));
    }

    private static void handleToggleTargetProtection(ToggleTargetProtectionPacket message,
                                                       IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            Entity raw = sender.level().getEntity(message.entityId());
            if (!(raw instanceof LivingEntity target) || target == sender
                    || sender.distanceToSqr(target) > 4096.0D) {
                sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.yujiancraft.target_protection.no_target"), true);
                return;
            }
            if (TargetProtectionManager.isNaturallyProtected(target)) {
                sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.yujiancraft.target_protection.natural", target.getDisplayName()), true);
            } else {
                boolean protectedAfter = TargetProtectionManager.toggle(sender, target);
                if (protectedAfter) {
                    target.removeEffect(dev.yujiancraft.registry.ModEffects.SWORD_BURN);
                    target.removeEffect(dev.yujiancraft.registry.ModEffects.SWORD_POISON);
                }
                sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        protectedAfter ? "message.yujiancraft.target_protection.disabled"
                                : "message.yujiancraft.target_protection.enabled", target.getDisplayName()), true);
            }
            sendTargetProtections(sender);
        });
    }

    private static void handleRemoveTargetProtection(RemoveTargetProtectionPacket message,
                                                       IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            TargetProtectionManager.remove(sender, message.targetId());
            sendTargetProtections(sender);
        });
    }

    private static void handleRequestTargetProtections(RequestTargetProtectionsPacket message,
                                                         IPayloadContext context) {
        context.enqueueWork(() -> sendTargetProtections((ServerPlayer) context.player()));
    }

    private static void handleSyncTargetProtections(SyncTargetProtectionsPacket message,
                                                      IPayloadContext context) {
        context.enqueueWork(() -> dev.yujiancraft.client.ClientTargetProtectionState.accept(message.entries()));
    }

    private static void handleCycleTechnique(CycleTechniquePacket message,
                                             IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) {
                SwordSettings settings = FlyingSwordItem.cycleTechnique(sender);
                sendSettings(sender, settings);
            }
        });
    }

    private static void handleActivateSwordArray(ActivateSwordArrayPacket message,
                                                  IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender == null) return;
            if (FlyingSwordItem.getSettings(sender).techniqueMode()
                    != dev.yujiancraft.combat.technique.TechniqueMode.SWORD_ARRAY) {
                sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.yujiancraft.sword_array.wrong_technique"), true);
                return;
            }
            Entity rawTarget = sender.level().getEntity(message.targetId());
            LivingEntity target = rawTarget instanceof LivingEntity living ? living : null;
            double range = FlyingSwordItem.getSettings(sender).crosshairLockRadius();
            boolean inRange = target != null && sender.distanceToSqr(target)
                    <= Math.pow(range + target.getBbWidth(), 2.0D);
            if (!inRange || !FlyingSwordEntity.activateCompleteSwordArray(sender, target)) {
                sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.yujiancraft.sword_array.not_ready"), true);
            }
        });
    }

    private static void handleToggleSwordArrayStyle(ToggleSwordArrayStylePacket message,
                                                     IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender == null) return;
            String key = "YujianCraftSwordArrayStyle";
            int next = sender.getPersistentData().getInt(key) == 0 ? 1 : 0;
            sender.getPersistentData().putInt(key, next);
            sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    next == 0 ? "message.yujiancraft.sword_array.style.tricolor"
                            : "message.yujiancraft.sword_array.style.gold"), true);
        });
    }

    private static void handleTrialCountdown(TrialCountdownPacket message,
                                             IPayloadContext context) {
        context.enqueueWork(() -> dev.yujiancraft.client.ClientTrialCountdownState.show(message.seconds));
    }

    private static void handleTechniqueNotice(TechniqueNoticePacket message,
                                              IPayloadContext context) {
        context.enqueueWork(() -> dev.yujiancraft.client.ClientTechniqueOverlayState.showTechnique(message.technique));
    }

    private static void handleSwordArrayFinisher(SwordArrayFinisherPacket message,
                                                  IPayloadContext context) {
        context.enqueueWork(() -> dev.yujiancraft.client.ClientTechniqueOverlayState.showFinisherFlash(
                message.startGameTick, message.bottom, message.top, message.maximumRadius,
                message.chargeTicks, message.holdTicks, message.expandTicks, message.sustainTicks));
    }

    private static void handleToggleFormation(ToggleFormationPacket message,
                                               IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) FlyingSwordItem.toggleFormationMode(sender);
        });
    }

    private static void handleArtifactAction(ArtifactActionPacket message,
                                             IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) ArtifactActionManager.handleAction(sender,
                    message.hasBlock ? message.blockPos : null, message.face);
        });
    }

    private static void handleOpenGuide(OpenGuidePacket message,
                                        IPayloadContext context) {
        context.enqueueWork(dev.yujiancraft.client.YujianGuideScreen::open);
    }

    private static void handleManualTrialResult(ManualTrialResultPacket message,
                                                IPayloadContext context) {
        context.enqueueWork(() -> dev.yujiancraft.client.ManualSpiritTrialResultScreen.open(
                message.damage, message.dps, message.mode));
    }


    private static void handleToggleSummonedSwords(ToggleSummonedSwordsPacket message,
                                                   IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) FlyingSwordItem.toggleSummonedFormation(sender, sender.getMainHandItem());
        });
    }

    private static void handleRequestBalance(RequestBalancePacket message,
                                             IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null && sender.hasPermissions(2)) sendBalances(sender);
        });
    }

    private static void handleUpdateBalance(UpdateBalancePacket message,
                                            IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender == null || !sender.hasPermissions(2)) return;
            if (message.material < 0 || message.material >= FlyingSwordMaterial.values().length) return;
            FlyingSwordMaterial material = FlyingSwordMaterial.fromOrdinal(message.material);
            if (message.reset) SwordBalanceConfig.reset(material);
            else SwordBalanceConfig.update(material, message.damage, message.flightSpeed);
            sendBalances(sender);
        });
    }

    private static void handleSyncBalance(SyncBalancePacket message,
                                          IPayloadContext context) {
        context.enqueueWork(() -> {
            EffectBalanceConfig.acceptRemoteSnapshot(message.effectValues);
            ClientBalanceState.acceptFromServer(message.balances, message.effectValues);
        });
    }

    private static void handleUpdateEffectBalance(UpdateEffectBalancePacket message,
                                                  IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender == null || !sender.hasPermissions(2)) return;
            if (message.parameter < 0 || message.parameter >= EffectParameter.values().length) return;
            EffectParameter parameter = EffectParameter.values()[message.parameter];
            if (message.reset) EffectBalanceConfig.reset(parameter);
            else EffectBalanceConfig.update(parameter, message.value);
            sendBalances(sender);
        });
    }

    private static void handleRequestSettings(RequestSettingsPacket message,
                                              IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) sendSettings(sender, FlyingSwordItem.getSettings(sender));
        });
    }

    private static void handleUpdateSettings(UpdateSettingsPacket message,
                                             IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
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
    }

    private static void handleSyncSettings(SyncSettingsPacket message,
                                           IPayloadContext context) {
        context.enqueueWork(() -> ClientSettingsState.acceptFromServer(message.toSettings(), message.canEditBalance));
    }

    private static void handleLockedTarget(LockedTargetPacket message,
                                           IPayloadContext context) {
        context.enqueueWork(() -> ClientTargetState.setLockedTargetId(message.targetId));
    }

    private static void handleLockCrosshairNow(LockCrosshairNowPacket message,
                                               IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) TargetLockManager.lockCrosshairNow(sender, message.entityId);
        });
    }

    private static void handleToggleSwordRiding(ToggleSwordRidingPacket message,
                                                IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) SwordRidingManager.setRiding(sender, message.active);
        });
    }

    private static void handleSwordRidingState(SwordRidingStatePacket message,
                                               IPayloadContext context) {
        context.enqueueWork(() -> ClientSwordRidingState.applyServerState(message.active,
                message.mayfly, message.flying, message.flyingSpeed));
    }

    private static void handleManualLaunch(ManualLaunchPacket message,
                                           IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) ManualGuidanceManager.launchReadySalvo(sender, message.direction());
        });
    }

    private static void handleManualAim(ManualAimPacket message,
                                        IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) ManualGuidanceManager.acceptAim(sender, message.direction());
        });
    }

    private static void handleManualLock(ManualLockPacket message,
                                         IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) ManualGuidanceManager.lockSalvoTarget(sender, message.entityId);
        });
    }

    private static void handleManualGuidanceState(ManualGuidanceStatePacket message,
                                                  IPayloadContext context) {
        context.enqueueWork(() -> ClientManualGuidanceState.setGuiding(message.guiding));
    }

    private static void handleSwordImpact(SwordImpactPacket message,
                                          IPayloadContext context) {
        context.enqueueWork(() -> dev.yujiancraft.client.ClientImpactEffects.accept(message.position(),
                message.direction(), message.visualModules, message.material));
    }

    public static void sendSettings(ServerPlayer player, SwordSettings settings) {
        PacketDistributor.sendToPlayer(player,
                SyncSettingsPacket.from(settings, player.hasPermissions(2)));
    }

    public static void sendTargetProtections(ServerPlayer player) {
        List<TargetProtectionEntry> entries = TargetProtectionManager.entries(player).stream()
                .map(entry -> new TargetProtectionEntry(entry.uuid(), entry.name(), entry.typeId()))
                .toList();
        PacketDistributor.sendToPlayer(player, new SyncTargetProtectionsPacket(entries));
    }

    public static void sendFormationState(ServerPlayer player, boolean deployed) {
        PacketDistributor.sendToPlayer(player, new FormationStatePacket(deployed));
    }

    private static void sendBalances(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new SyncBalancePacket(SwordBalanceConfig.snapshot(), EffectBalanceConfig.snapshot()));
    }

    public static void sendLockedTarget(ServerPlayer player, UUID targetId) {
        PacketDistributor.sendToPlayer(player, new LockedTargetPacket(targetId));
    }

    public static void sendSwordRidingState(ServerPlayer player, boolean active) {
        net.minecraft.world.entity.player.Abilities abilities = player.getAbilities();
        PacketDistributor.sendToPlayer(player, new SwordRidingStatePacket(active,
                abilities.mayfly, abilities.flying, abilities.getFlyingSpeed()));
    }

    public static void sendManualGuidanceState(ServerPlayer player, boolean guiding) {
        PacketDistributor.sendToPlayer(player, new ManualGuidanceStatePacket(guiding));
    }

    public static void openGuide(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenGuidePacket());
    }

    public static void sendManualTrialResult(ServerPlayer player, double damage, double dps, int mode) {
        PacketDistributor.sendToPlayer(player,
                new ManualTrialResultPacket(damage, dps, mode));
    }

    public static void sendTrialCountdown(ServerPlayer player, int seconds) {
        PacketDistributor.sendToPlayer(player, new TrialCountdownPacket(seconds));
    }

    public static void sendTechniqueNotice(ServerPlayer player,
                                           dev.yujiancraft.combat.technique.TechniqueMode technique) {
        PacketDistributor.sendToPlayer(player,
                new TechniqueNoticePacket(technique.ordinal()));
    }

    public static void sendSwordArrayFinisher(ServerPlayer player, long startGameTick,
                                               Vec3 bottom, Vec3 top,
                                               float maximumRadius, int chargeTicks, int holdTicks,
                                               int expandTicks, int sustainTicks) {
        PacketDistributor.sendToPlayer(player, new SwordArrayFinisherPacket(
                startGameTick, bottom, top, maximumRadius, chargeTicks, holdTicks, expandTicks,
                sustainTicks));
    }

    public static void sendComboState(ServerPlayer player, boolean active, String styleId, int stage,
                                      long startGameTick, int durationTicks, int targetId,
                                      Vec3 playerAnchor, Vec3 targetAnchor, Vec3 warpDestination,
                                      float warpYaw) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new ComboStatePacket(player.getId(), active, styleId, stage, startGameTick, durationTicks,
                        targetId, playerAnchor, targetAnchor, warpDestination, warpYaw));
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
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sword,
                new SwordImpactPacket(position, safeDirection, sword.getVisualModuleMask(),
                        sword.getMaterialType().ordinal()));
    }

    public record ToggleFormationPacket() implements YujianPayload {
    }

    public record ToggleTargetProtectionPacket(int entityId) implements YujianPayload {
        private static void encode(ToggleTargetProtectionPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entityId());
        }
        private static ToggleTargetProtectionPacket decode(FriendlyByteBuf buffer) {
            return new ToggleTargetProtectionPacket(buffer.readVarInt());
        }
    }

    public record RemoveTargetProtectionPacket(UUID targetId) implements YujianPayload {
        private static void encode(RemoveTargetProtectionPacket message, FriendlyByteBuf buffer) {
            buffer.writeUUID(message.targetId());
        }
        private static RemoveTargetProtectionPacket decode(FriendlyByteBuf buffer) {
            return new RemoveTargetProtectionPacket(buffer.readUUID());
        }
    }

    public record RequestTargetProtectionsPacket() implements YujianPayload {
    }

    public record TargetProtectionEntry(UUID uuid, String name, String typeId) {
        private static void encode(TargetProtectionEntry entry, FriendlyByteBuf buffer) {
            buffer.writeUUID(entry.uuid()); buffer.writeUtf(entry.name(), 128); buffer.writeUtf(entry.typeId(), 128);
        }
        private static TargetProtectionEntry decode(FriendlyByteBuf buffer) {
            return new TargetProtectionEntry(buffer.readUUID(), buffer.readUtf(128), buffer.readUtf(128));
        }
    }

    public record SyncTargetProtectionsPacket(List<TargetProtectionEntry> entries) implements YujianPayload {
        private static void encode(SyncTargetProtectionsPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entries().size());
            message.entries().forEach(entry -> TargetProtectionEntry.encode(entry, buffer));
        }
        private static SyncTargetProtectionsPacket decode(FriendlyByteBuf buffer) {
            int size = Math.min(buffer.readVarInt(), 1024);
            List<TargetProtectionEntry> entries = new ArrayList<>(size);
            for (int index = 0; index < size; index++) entries.add(TargetProtectionEntry.decode(buffer));
            return new SyncTargetProtectionsPacket(List.copyOf(entries));
        }
    }

    public record ToggleSummonedSwordsPacket() implements YujianPayload {
    }

    public record FormationStatePacket(boolean deployed) implements YujianPayload {
        private static void encode(FormationStatePacket message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.deployed);
        }

        private static FormationStatePacket decode(FriendlyByteBuf buffer) {
            return new FormationStatePacket(buffer.readBoolean());
        }
    }

    public record OpenGuidePacket() implements YujianPayload {
    }

    public record ManualTrialResultPacket(double damage, double dps, int mode) implements YujianPayload {
        private static void encode(ManualTrialResultPacket message, FriendlyByteBuf buffer) {
            buffer.writeDouble(message.damage);
            buffer.writeDouble(message.dps);
            buffer.writeVarInt(message.mode);
        }

        private static ManualTrialResultPacket decode(FriendlyByteBuf buffer) {
            return new ManualTrialResultPacket(buffer.readDouble(), buffer.readDouble(), buffer.readVarInt());
        }
    }


    public record RequestSettingsPacket() implements YujianPayload {
    }

    public record CycleTechniquePacket() implements YujianPayload {
    }

    public record ActivateSwordArrayPacket(int targetId) implements YujianPayload {
        private static void encode(ActivateSwordArrayPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.targetId);
        }

        private static ActivateSwordArrayPacket decode(FriendlyByteBuf buffer) {
            return new ActivateSwordArrayPacket(buffer.readVarInt());
        }
    }

    public record ToggleSwordArrayStylePacket() implements YujianPayload {
    }

    public record ToggleComboPacket() implements YujianPayload { }

    public record CycleComboStylePacket() implements YujianPayload { }

    public record ComboAttackPacket(int targetId, float lookX, float lookY, float lookZ) implements YujianPayload {
        public ComboAttackPacket(int targetId, Vec3 look) {
            this(targetId, (float) look.x, (float) look.y, (float) look.z);
        }

        public Vec3 look() { return new Vec3(lookX, lookY, lookZ); }

        private static void encode(ComboAttackPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.targetId);
            buffer.writeFloat(message.lookX);
            buffer.writeFloat(message.lookY);
            buffer.writeFloat(message.lookZ);
        }

        private static ComboAttackPacket decode(FriendlyByteBuf buffer) {
            return new ComboAttackPacket(buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readFloat());
        }
    }

    public record ComboStatePacket(int playerId, boolean active, String styleId, int stage, long startGameTick,
                                   int durationTicks, int targetId, Vec3 playerAnchor,
                                   Vec3 targetAnchor, Vec3 warpDestination, float warpYaw) implements YujianPayload {
        private static void encode(ComboStatePacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.playerId);
            buffer.writeBoolean(message.active);
            buffer.writeUtf(message.styleId, 48);
            buffer.writeVarInt(message.stage);
            buffer.writeLong(message.startGameTick);
            buffer.writeVarInt(message.durationTicks);
            buffer.writeVarInt(message.targetId);
            writeVec(buffer, message.playerAnchor);
            writeVec(buffer, message.targetAnchor);
            writeVec(buffer, message.warpDestination);
            buffer.writeFloat(message.warpYaw);
        }

        private static ComboStatePacket decode(FriendlyByteBuf buffer) {
            return new ComboStatePacket(buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(48),
                    buffer.readVarInt(), buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(),
                    readVec(buffer), readVec(buffer), readVec(buffer), buffer.readFloat());
        }

        private static void writeVec(FriendlyByteBuf buffer, Vec3 value) {
            buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z);
        }

        private static Vec3 readVec(FriendlyByteBuf buffer) {
            return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
    }

    public record TrialCountdownPacket(int seconds) implements YujianPayload {
        private static void encode(TrialCountdownPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.seconds);
        }

        private static TrialCountdownPacket decode(FriendlyByteBuf buffer) {
            return new TrialCountdownPacket(buffer.readVarInt());
        }
    }

    public record TechniqueNoticePacket(int technique) implements YujianPayload {
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
                                           int expandTicks, int sustainTicks) implements YujianPayload {
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
                                       net.minecraft.core.Direction face) implements YujianPayload {
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

    public record LockCrosshairNowPacket(int entityId) implements YujianPayload {
        private static void encode(LockCrosshairNowPacket message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.entityId);
        }

        private static LockCrosshairNowPacket decode(FriendlyByteBuf buffer) {
            return new LockCrosshairNowPacket(buffer.readInt());
        }
    }

    public record ToggleSwordRidingPacket(boolean active) implements YujianPayload {
        private static void encode(ToggleSwordRidingPacket message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.active);
        }

        private static ToggleSwordRidingPacket decode(FriendlyByteBuf buffer) {
            return new ToggleSwordRidingPacket(buffer.readBoolean());
        }
    }

    public record SwordRidingStatePacket(boolean active, boolean mayfly, boolean flying,
                                         float flyingSpeed) implements YujianPayload {
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

    public record ManualLaunchPacket(float x, float y, float z) implements YujianPayload {
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

    public record ManualAimPacket(float x, float y, float z) implements YujianPayload {
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

    public record ManualLockPacket(int entityId) implements YujianPayload {
        private static void encode(ManualLockPacket message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.entityId);
        }

        private static ManualLockPacket decode(FriendlyByteBuf buffer) {
            return new ManualLockPacket(buffer.readInt());
        }
    }

    public record ManualGuidanceStatePacket(boolean guiding) implements YujianPayload {
        private static void encode(ManualGuidanceStatePacket message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.guiding);
        }

        private static ManualGuidanceStatePacket decode(FriendlyByteBuf buffer) {
            return new ManualGuidanceStatePacket(buffer.readBoolean());
        }
    }

    public record SwordImpactPacket(double x, double y, double z,
                                    float directionX, float directionY, float directionZ,
                                    int visualModules, int material) implements YujianPayload {
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

    public record RequestBalancePacket() implements YujianPayload {
    }

    public record UpdateSettingsPacket(int minimumDockTicks, double automaticRadius, double lockRadius,
                                       int targetingMode, int attackMode, int techniqueMode) implements YujianPayload {
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
                                     boolean canEditBalance) implements YujianPayload {
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

    public record UpdateBalancePacket(int material, double damage, double flightSpeed, boolean reset) implements YujianPayload {
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

    public record UpdateEffectBalancePacket(int parameter, double value, boolean reset) implements YujianPayload {
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
                                    Map<EffectParameter, Double> effectValues) implements YujianPayload {
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

    public record LockedTargetPacket(UUID targetId) implements YujianPayload {
        private static void encode(LockedTargetPacket message, FriendlyByteBuf buffer) {
            buffer.writeBoolean(message.targetId != null);
            if (message.targetId != null) buffer.writeUUID(message.targetId);
        }

        private static LockedTargetPacket decode(FriendlyByteBuf buffer) {
            return new LockedTargetPacket(buffer.readBoolean() ? buffer.readUUID() : null);
        }
    }
}
