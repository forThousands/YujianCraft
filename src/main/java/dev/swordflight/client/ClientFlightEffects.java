package dev.swordflight.client;

import dev.swordflight.Swordflight;
import dev.swordflight.entity.FlyingSwordEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client-local audiovisual state. No trail positions or looping sounds are networked. */
@Mod.EventBusSubscriber(modid = Swordflight.MOD_ID, value = Dist.CLIENT)
public final class ClientFlightEffects {
    private static final int MAX_TRAIL_SAMPLES = 12;
    private static final int MAX_SIMULTANEOUS_SOUNDS = 6;
    private static final double SOUND_DISTANCE_SQUARED = 48.0D * 48.0D;
    private static final Map<Integer, ArrayDeque<Vec3>> TRAILS = new HashMap<>();
    private static final Map<Integer, Integer> FLIGHT_AGES = new HashMap<>();
    private static final Map<Integer, FlyingSwordSoundInstance> SOUNDS = new HashMap<>();

    private ClientFlightEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clearAll();
            return;
        }

        List<FlyingSwordEntity> flying = new ArrayList<>();
        Set<Integer> present = new HashSet<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof FlyingSwordEntity sword)) continue;
            present.add(sword.getId());
            sword.setSharedFlag(6, ClientOptions.swordOutline());
            if (sword.isVisuallyDocked()) {
                TRAILS.remove(sword.getId());
                FLIGHT_AGES.remove(sword.getId());
            } else {
                flying.add(sword);
                FLIGHT_AGES.merge(sword.getId(), 1, (age, ignored) -> Math.min(age + 1, 200));
                recordTrail(sword);
            }
        }

        TRAILS.keySet().removeIf(id -> !present.contains(id));
        FLIGHT_AGES.keySet().removeIf(id -> !present.contains(id));
        updateSounds(minecraft, flying, present);
    }

    private static void recordTrail(FlyingSwordEntity sword) {
        ArrayDeque<Vec3> points = TRAILS.computeIfAbsent(sword.getId(), ignored -> new ArrayDeque<>());
        Vec3 position = sword.position();
        Vec3 newest = points.peekFirst();
        if (newest == null || newest.distanceToSqr(position) >= 0.0025D) {
            points.addFirst(position);
            while (points.size() > MAX_TRAIL_SAMPLES) points.removeLast();
        }
    }

    private static void updateSounds(Minecraft minecraft, List<FlyingSwordEntity> flying, Set<Integer> present) {
        SOUNDS.entrySet().removeIf(entry -> {
            if (!present.contains(entry.getKey()) || entry.getValue().isStopped()) {
                entry.getValue().requestStop();
                return true;
            }
            return false;
        });

        if (!ClientOptions.flightSound()) {
            SOUNDS.values().forEach(FlyingSwordSoundInstance::requestStop);
            SOUNDS.clear();
            return;
        }

        flying.removeIf(sword -> sword.distanceToSqr(minecraft.player) > SOUND_DISTANCE_SQUARED);
        flying.sort(Comparator.comparingDouble(sword -> sword.distanceToSqr(minecraft.player)));
        Set<Integer> audible = new HashSet<>();
        for (int index = 0; index < Math.min(MAX_SIMULTANEOUS_SOUNDS, flying.size()); index++) {
            FlyingSwordEntity sword = flying.get(index);
            audible.add(sword.getId());
            SOUNDS.computeIfAbsent(sword.getId(), ignored -> {
                FlyingSwordSoundInstance sound = new FlyingSwordSoundInstance(sword);
                minecraft.getSoundManager().play(sound);
                return sound;
            });
        }
        SOUNDS.entrySet().removeIf(entry -> {
            if (audible.contains(entry.getKey())) return false;
            entry.getValue().requestStop();
            return true;
        });
    }

    public static List<Vec3> trailPoints(FlyingSwordEntity sword) {
        if (sword.isVisuallyDocked()) return List.of();
        ArrayDeque<Vec3> points = TRAILS.get(sword.getId());
        return points == null ? List.of() : List.copyOf(points);
    }

    public static int flightAge(FlyingSwordEntity sword) {
        return FLIGHT_AGES.getOrDefault(sword.getId(), 0);
    }

    private static void clearAll() {
        TRAILS.clear();
        FLIGHT_AGES.clear();
        SOUNDS.values().forEach(FlyingSwordSoundInstance::requestStop);
        SOUNDS.clear();
    }

    private static final class FlyingSwordSoundInstance extends AbstractTickableSoundInstance {
        private final FlyingSwordEntity sword;
        private int age;

        private FlyingSwordSoundInstance(FlyingSwordEntity sword) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.sword = sword;
            looping = true;
            delay = 0;
            attenuation = SoundInstance.Attenuation.LINEAR;
            // SoundEngine resolves and starts a ticking sound using its constructor-time volume.
            // Vanilla ElytraOnPlayerSoundInstance likewise submits 0.1 rather than zero.
            volume = 0.10F;
            pitch = 0.9F;
            x = sword.getX();
            y = sword.getY();
            z = sword.getZ();
        }

        @Override
        public void tick() {
            if (sword.isRemoved() || sword.isVisuallyDocked() || !ClientOptions.flightSound()) {
                stop();
                return;
            }
            age++;
            x = sword.getX();
            y = sword.getY();
            z = sword.getZ();
            double tickDisplacement = sword.position().distanceTo(new Vec3(sword.xo, sword.yo, sword.zo));
            double speed = Math.max(tickDisplacement, sword.getDeltaMovement().length());
            float intensity = (float) Mth.clamp(speed / 1.15D, 0.0D, 1.0D);
            float fadeIn = Math.min(1.0F, age / 8.0F);
            volume = (0.075F + intensity * 0.205F) * fadeIn;
            pitch = 0.86F + intensity * 0.28F;
        }

        private void requestStop() {
            stop();
        }
    }
}
