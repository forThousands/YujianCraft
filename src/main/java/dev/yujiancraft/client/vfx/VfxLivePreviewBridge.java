package dev.yujiancraft.client.vfx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.yujiancraft.client.ClientModEvents;
import dev.yujiancraft.client.ClientTechniqueOverlayState;
import dev.yujiancraft.entity.SwordArrayFieldEntity;
import dev.yujiancraft.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Opt-in file bridge between the standalone VFX Studio and the normal Forge client. It is inert
 * unless a JVM property or environment variable explicitly enables it, and never opens a socket.
 */
public final class VfxLivePreviewBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PREVIEW_ENTITY_ID = -2_147_480_731;
    private static final boolean AVAILABLE = Boolean.getBoolean("yujiancraft.vfxLivePreview")
            || "true".equalsIgnoreCase(System.getenv("YUJIANCRAFT_VFX_LIVE"));
    private static final Path LIVE_FILE = resolveLiveFile();
    private static long lastCheckMillis;
    private static long lastModified = Long.MIN_VALUE;
    private static long lastHeartbeatMillis;
    private static long lastCreativeRequestMillis;
    private static boolean enabled;
    private static float authoredTick;
    private static Boolean requestedShaderEnabled;
    private static Boolean appliedShaderEnabled;
    private static boolean shaderBridgeUnavailableLogged;
    private static VfxTimelineDefinition timeline;
    private static SwordArrayFieldEntity previewEntity;
    private static ClientLevel previewLevel;
    private static Vec3 anchor;

    private VfxLivePreviewBridge() { }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static ClientTechniqueOverlayState.FinisherFrame sample(float partialTick) {
        if (!AVAILABLE) return null;
        Minecraft minecraft = Minecraft.getInstance();
        refreshIfNeeded();
        if (minecraft.level == null || minecraft.player == null) {
            removePreviewEntity();
            closeCursorReleaseScreen(minecraft);
            return null;
        }
        ensureCreativeTestPlayer(minecraft);
        applyRequestedShaderState(minecraft);
        if (!enabled || timeline == null) {
            removePreviewEntity();
            return null;
        }
        ensurePreviewEntity(minecraft);
        if (previewEntity == null) return null;
        previewEntity.configureClientPreview(anchor,
                dev.yujiancraft.registry.ModItems.getFlyingSword(
                        dev.yujiancraft.material.FlyingSwordMaterial.DIAMOND).getDefaultInstance(), authoredTick,
                timeline.worldStyle());
        Vec3 bottom = previewEntity.position();
        Vec3 top = previewEntity.topCentre();
        return new ClientTechniqueOverlayState.FinisherFrame(bottom, top,
                previewEntity.maximumBeamRadius(), authoredTick / 20.0F, authoredTick, timeline);
    }

    private static void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMillis < 40L) return;
        lastCheckMillis = now;
        try {
            if (!Files.isRegularFile(LIVE_FILE)) {
                enabled = false;
                timeline = null;
                return;
            }
            long modified = Files.getLastModifiedTime(LIVE_FILE).toMillis();
            if (modified == lastModified) {
                if (enabled && now - lastHeartbeatMillis > 3_000L) {
                    enabled = false;
                    timeline = null;
                }
                return;
            }
            lastModified = modified;
            try (Reader reader = Files.newBufferedReader(LIVE_FILE, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                enabled = root.has("enabled") && root.get("enabled").getAsBoolean();
                requestedShaderEnabled = root.has("shaderEnabled")
                        ? root.get("shaderEnabled").getAsBoolean() : null;
                lastHeartbeatMillis = root.has("updatedAtMillis")
                        ? root.get("updatedAtMillis").getAsLong() : modified;
                if (enabled && now - lastHeartbeatMillis > 3_000L) enabled = false;
                if (!enabled) {
                    timeline = null;
                    return;
                }
                authoredTick = Math.max(0.0F, root.get("tick").getAsFloat());
                timeline = VfxTimelineDefinition.parseProject(root.getAsJsonObject("project"));
                authoredTick = Math.min(authoredTick, timeline.durationTicks());
            }
        } catch (Exception exception) {
            LOGGER.warn("Unable to read VFX Studio live preview file {}", LIVE_FILE, exception);
        }
    }

    private static void ensurePreviewEntity(Minecraft minecraft) {
        if (previewEntity != null && previewLevel == minecraft.level && !previewEntity.isRemoved()) return;
        removePreviewEntity();
        previewLevel = minecraft.level;
        previewEntity = ModEntities.SWORD_ARRAY_FIELD.get().create(previewLevel);
        if (previewEntity == null) return;
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-5D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        horizontal = horizontal.normalize();
        anchor = minecraft.player.position().add(horizontal.scale(14.0D));
        previewEntity.configureClientPreview(anchor,
                dev.yujiancraft.registry.ModItems.getFlyingSword(
                        dev.yujiancraft.material.FlyingSwordMaterial.DIAMOND).getDefaultInstance(), authoredTick,
                timeline == null ? dev.yujiancraft.visual.SwordArrayVisualStyle.DEFAULT : timeline.worldStyle());
        previewLevel.putNonPlayerEntity(PREVIEW_ENTITY_ID, previewEntity);
    }

    private static void removePreviewEntity() {
        if (previewEntity != null && !previewEntity.isRemoved()) previewEntity.discard();
        previewEntity = null;
        previewLevel = null;
        anchor = null;
    }

    /** Toggle mouse ownership on demand. This method is unreachable in normal builds because its
     * key is only registered when the Studio explicitly enables the local bridge. */
    public static void toggleCursorCapture() {
        if (!AVAILABLE) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (minecraft.screen instanceof CursorReleaseScreen) {
            minecraft.setScreen(null);
            minecraft.mouseHandler.grabMouse();
            LOGGER.info("VFX Studio restored mouse capture");
        } else if (minecraft.screen == null) {
            minecraft.setScreen(new CursorReleaseScreen());
            minecraft.mouseHandler.releaseMouse();
            LOGGER.info("VFX Studio released mouse capture");
        }
    }

    private static void closeCursorReleaseScreen(Minecraft minecraft) {
        if (minecraft.screen instanceof CursorReleaseScreen) minecraft.setScreen(null);
    }

    /** The Studio-owned integrated test world is always creative, without touching public clients
     * or dedicated servers. This also grants flight for distant composition checks. */
    private static void ensureCreativeTestPlayer(Minecraft minecraft) {
        if (minecraft.player.isCreative() || minecraft.getSingleplayerServer() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCreativeRequestMillis < 1_000L) return;
        lastCreativeRequestMillis = now;
        UUID playerId = minecraft.player.getUUID();
        minecraft.getSingleplayerServer().execute(() -> {
            ServerPlayer serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);
            if (serverPlayer != null && !serverPlayer.isCreative()) {
                serverPlayer.setGameMode(GameType.CREATIVE);
                LOGGER.info("VFX Studio switched test player {} to creative mode",
                        serverPlayer.getGameProfile().getName());
            }
        });
    }

    /** Oculus/Iris is an optional test dependency. Reflection keeps it completely out of the
     * public mod's compile/runtime dependency graph. */
    private static void applyRequestedShaderState(Minecraft minecraft) {
        if (requestedShaderEnabled == null || requestedShaderEnabled.equals(appliedShaderEnabled)) return;
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Method inUse = iris.getMethod("isPackInUseQuick");
            boolean current = (boolean) inUse.invoke(null);
            if (current != requestedShaderEnabled) {
                iris.getMethod("toggleShaders", Minecraft.class, boolean.class)
                        .invoke(null, minecraft, requestedShaderEnabled);
            }
            appliedShaderEnabled = requestedShaderEnabled;
            shaderBridgeUnavailableLogged = false;
            LOGGER.info("VFX Studio shader test is now {}", requestedShaderEnabled ? "enabled" : "disabled");
        } catch (ClassNotFoundException exception) {
            if (!shaderBridgeUnavailableLogged) {
                LOGGER.warn("VFX Studio requested shader testing, but Oculus/Iris is not installed");
                shaderBridgeUnavailableLogged = true;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("Unable to switch Oculus/Iris shader state from VFX Studio", exception);
        }
    }

    private static final class CursorReleaseScreen extends Screen {
        private CursorReleaseScreen() {
            super(Component.literal("Yujian Craft VFX live preview"));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Intentionally transparent: only cursor ownership changes.
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (ClientModEvents.RELEASE_VFX_CURSOR.matches(keyCode, scanCode)) {
                toggleCursorCapture();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    private static Path resolveLiveFile() {
        String explicit = System.getProperty("yujiancraft.vfxLiveFile");
        if (explicit != null && !explicit.isBlank()) return Path.of(explicit).toAbsolutePath().normalize();
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(localAppData);
        return base.resolve("YujianCraftVfxStudio").resolve("live-preview.json");
    }
}
