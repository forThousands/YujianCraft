package dev.swordflight.combat;

import dev.swordflight.Swordflight;
import dev.swordflight.entity.FlyingSwordEntity;
import dev.swordflight.formation.FormationGeometry;
import dev.swordflight.item.FlyingSwordItem;
import dev.swordflight.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative snapshot salvo used by the manual-guidance targeting mode. */
@Mod.EventBusSubscriber(modid = Swordflight.MOD_ID)
public final class ManualGuidanceManager {
    private static final Map<UUID, GuidanceState> STATES = new HashMap<>();

    private ManualGuidanceManager() {
    }

    public static void launchReadySalvo(ServerPlayer player, Vec3 requestedDirection) {
        if (!(player.getMainHandItem().getItem() instanceof FlyingSwordItem)) return;
        ItemStack stack = player.getMainHandItem();
        if (SwordSettings.read(stack).targetingMode() != TargetingMode.MANUAL_GUIDANCE) return;
        if (STATES.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.swordflight.manual_busy"), true);
            return;
        }

        Vec3 aimDirection = sanitizeDirection(requestedDirection, player.getLookAngle());
        Set<UUID> readySnapshot = new HashSet<>();
        for (FlyingSwordEntity sword : FlyingSwordItem.ensureFormation(player, stack)) {
            if (!sword.isReadyForManualLaunch()) continue;
            Vec3 launchDirection = FormationGeometry.dockDirection(player, sword.position(),
                    sword.getFormationModeType());
            sword.beginManualGuidance(aimDirection, launchDirection);
            readySnapshot.add(sword.getUUID());
        }
        if (readySnapshot.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.swordflight.manual_none_ready"), true);
            return;
        }

        STATES.put(player.getUUID(), new GuidanceState(readySnapshot, aimDirection));
        ModNetwork.sendManualGuidanceState(player, true);
        ModNetwork.sendLockedTarget(player, null);
        player.displayClientMessage(Component.translatable("message.swordflight.manual_launched",
                readySnapshot.size()), true);
    }

    public static void acceptAim(ServerPlayer player, Vec3 requestedDirection) {
        GuidanceState state = STATES.get(player.getUUID());
        if (state == null || !state.guiding) return;
        state.aimDirection = sanitizeDirection(requestedDirection, state.aimDirection);
    }

    public static void lockSalvoTarget(ServerPlayer player, int requestedEntityId) {
        GuidanceState state = STATES.get(player.getUUID());
        if (state == null || !state.guiding) return;
        LivingEntity target = validateUnlimitedTarget(player, requestedEntityId);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.swordflight.manual_no_target"), true);
            return;
        }

        pruneMissing(player, state);
        if (state.members.isEmpty()) {
            clear(player, false);
            return;
        }
        for (UUID swordId : state.members) {
            Entity entity = player.serverLevel().getEntity(swordId);
            if (entity instanceof FlyingSwordEntity sword) sword.lockManualTarget(target.getUUID());
        }
        state.guiding = false;
        state.targetId = target.getUUID();
        ModNetwork.sendManualGuidanceState(player, false);
        ModNetwork.sendLockedTarget(player, target.getUUID());
        player.displayClientMessage(Component.translatable("message.swordflight.manual_locked"), true);
    }

    public static Vec3 getAimDirection(ServerPlayer player, FlyingSwordEntity sword) {
        GuidanceState state = STATES.get(player.getUUID());
        return state != null && state.guiding && state.members.contains(sword.getUUID())
                ? state.aimDirection : null;
    }

    public static boolean isGuiding(ServerPlayer player) {
        GuidanceState state = STATES.get(player.getUUID());
        return state != null && state.guiding;
    }

    public static void onSwordReturning(ServerPlayer player, FlyingSwordEntity sword) {
        GuidanceState state = STATES.get(player.getUUID());
        if (state == null || !state.members.remove(sword.getUUID())) return;
        if (state.members.isEmpty()) clear(player, false);
    }

    public static void cancel(ServerPlayer player) {
        GuidanceState state = STATES.remove(player.getUUID());
        if (state == null) return;
        for (UUID swordId : state.members) {
            if (player.serverLevel().getEntity(swordId) instanceof FlyingSwordEntity sword) {
                sword.cancelManualFlight();
            }
        }
        ModNetwork.sendManualGuidanceState(player, false);
        ModNetwork.sendLockedTarget(player, null);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
                || !(event.player instanceof ServerPlayer player)) return;
        GuidanceState state = STATES.get(player.getUUID());
        if (state == null) return;
        if (!(player.getMainHandItem().getItem() instanceof FlyingSwordItem)
                || SwordSettings.read(player.getMainHandItem()).targetingMode()
                != TargetingMode.MANUAL_GUIDANCE || !player.isAlive()) {
            cancel(player);
            return;
        }
        pruneMissing(player, state);
        if (state.members.isEmpty()) clear(player, false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player, false);
    }

    private static void pruneMissing(ServerPlayer player, GuidanceState state) {
        Iterator<UUID> iterator = state.members.iterator();
        while (iterator.hasNext()) {
            Entity entity = player.serverLevel().getEntity(iterator.next());
            if (!(entity instanceof FlyingSwordEntity) || !entity.isAlive()) iterator.remove();
        }
    }

    private static LivingEntity validateUnlimitedTarget(ServerPlayer player, int entityId) {
        if (entityId < 0 || !(player.serverLevel().getEntity(entityId) instanceof LivingEntity target)
                || !SwordTargetingRules.canActivelyTarget(player, target)
                || !player.hasLineOfSight(target)) return null;
        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
        Vec3 toTarget = targetCenter.subtract(player.getEyePosition());
        return toTarget.lengthSqr() > 1.0E-6D
                && toTarget.normalize().dot(player.getLookAngle().normalize()) >= 0.975D ? target : null;
    }

    private static Vec3 sanitizeDirection(Vec3 requested, Vec3 fallback) {
        if (requested == null || !Double.isFinite(requested.x) || !Double.isFinite(requested.y)
                || !Double.isFinite(requested.z) || requested.lengthSqr() < 1.0E-6D) {
            return fallback.normalize();
        }
        return requested.normalize();
    }

    private static void clear(ServerPlayer player, boolean recall) {
        GuidanceState removed = STATES.remove(player.getUUID());
        if (removed == null) return;
        if (recall) {
            for (UUID swordId : removed.members) {
                if (player.serverLevel().getEntity(swordId) instanceof FlyingSwordEntity sword) {
                    sword.cancelManualFlight();
                }
            }
        }
        ModNetwork.sendManualGuidanceState(player, false);
        ModNetwork.sendLockedTarget(player, null);
    }

    private static final class GuidanceState {
        private final Set<UUID> members;
        private Vec3 aimDirection;
        private boolean guiding = true;
        private UUID targetId;

        private GuidanceState(Set<UUID> members, Vec3 aimDirection) {
            this.members = members;
            this.aimDirection = aimDirection;
        }
    }
}
