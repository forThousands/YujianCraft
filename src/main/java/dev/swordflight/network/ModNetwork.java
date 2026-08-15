package dev.swordflight.network;

import dev.swordflight.Swordflight;
import dev.swordflight.client.ClientSettingsState;
import dev.swordflight.client.ClientBalanceState;
import dev.swordflight.client.ClientTargetState;
import dev.swordflight.combat.SwordSettings;
import dev.swordflight.config.SwordBalanceConfig;
import dev.swordflight.config.EffectBalanceConfig;
import dev.swordflight.config.EffectParameter;
import dev.swordflight.item.FlyingSwordItem;
import dev.swordflight.combat.TargetLockManager;
import dev.swordflight.material.FlyingSwordMaterial;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
    private static final String PROTOCOL = "6";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Swordflight.MOD_ID, "main"),
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
        CHANNEL.registerMessage(10, ClientAimTargetPacket.class,
                ClientAimTargetPacket::encode, ClientAimTargetPacket::decode,
                ModNetwork::handleClientAimTarget, Optional.of(NetworkDirection.PLAY_TO_SERVER));
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
                SwordSettings requested = message.toSettings();
                SwordSettings applied = requested;
                if (!sender.hasPermissions(2)) {
                    SwordSettings current = FlyingSwordItem.getSettings(sender);
                    applied = new SwordSettings(current.minimumDockTicks(), current.automaticTargetRadius(),
                            current.crosshairLockRadius(), requested.targetingMode(), requested.attackMode());
                }
                FlyingSwordItem.setSettings(sender, applied);
                sendSettings(sender, applied);
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

    private static void handleClientAimTarget(ClientAimTargetPacket message,
                                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) TargetLockManager.acceptClientAim(sender, message.entityId);
        });
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

    public record ToggleFormationPacket() {
    }

    public record RequestSettingsPacket() {
    }

    public record LockCrosshairNowPacket(int entityId) {
        private static void encode(LockCrosshairNowPacket message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.entityId);
        }

        private static LockCrosshairNowPacket decode(FriendlyByteBuf buffer) {
            return new LockCrosshairNowPacket(buffer.readInt());
        }
    }

    public record ClientAimTargetPacket(int entityId) {
        private static void encode(ClientAimTargetPacket message, FriendlyByteBuf buffer) {
            buffer.writeInt(message.entityId);
        }

        private static ClientAimTargetPacket decode(FriendlyByteBuf buffer) {
            return new ClientAimTargetPacket(buffer.readInt());
        }
    }

    public record RequestBalancePacket() {
    }

    public record UpdateSettingsPacket(int minimumDockTicks, double automaticRadius, double lockRadius,
                                       int targetingMode, int attackMode) {
        public static UpdateSettingsPacket from(SwordSettings settings) {
            return new UpdateSettingsPacket(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                    settings.crosshairLockRadius(), settings.targetingMode().ordinal(), settings.attackMode().ordinal());
        }

        public SwordSettings toSettings() {
            return SwordSettings.fromNetwork(minimumDockTicks, automaticRadius, lockRadius, targetingMode, attackMode);
        }

        private static void encode(UpdateSettingsPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.minimumDockTicks);
            buffer.writeDouble(message.automaticRadius);
            buffer.writeDouble(message.lockRadius);
            buffer.writeVarInt(message.targetingMode);
            buffer.writeVarInt(message.attackMode);
        }

        private static UpdateSettingsPacket decode(FriendlyByteBuf buffer) {
            return new UpdateSettingsPacket(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record SyncSettingsPacket(int minimumDockTicks, double automaticRadius, double lockRadius,
                                     int targetingMode, int attackMode, boolean canEditBalance) {
        public static SyncSettingsPacket from(SwordSettings settings, boolean canEditBalance) {
            return new SyncSettingsPacket(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                    settings.crosshairLockRadius(), settings.targetingMode().ordinal(), settings.attackMode().ordinal(),
                    canEditBalance);
        }

        public SwordSettings toSettings() {
            return SwordSettings.fromNetwork(minimumDockTicks, automaticRadius, lockRadius, targetingMode, attackMode);
        }

        private static void encode(SyncSettingsPacket message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.minimumDockTicks);
            buffer.writeDouble(message.automaticRadius);
            buffer.writeDouble(message.lockRadius);
            buffer.writeVarInt(message.targetingMode);
            buffer.writeVarInt(message.attackMode);
            buffer.writeBoolean(message.canEditBalance);
        }

        private static SyncSettingsPacket decode(FriendlyByteBuf buffer) {
            return new SyncSettingsPacket(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
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
