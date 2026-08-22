package dev.yujiancraft.client;

import com.mojang.logging.LogUtils;
import dev.yujiancraft.YujianCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;

/** Adapts only the brightness profile; the player's independent glow on/off choice is preserved. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientShaderAdaptation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static BooleanSupplier shaderProbe;
    private static Boolean lastShaderState;
    private static int ticksUntilCheck;
    private static boolean safetyNoticeShown;

    private ClientShaderAdaptation() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        if (ticksUntilCheck-- > 0) return;
        ticksUntilCheck = CHECK_INTERVAL_TICKS - 1;

        boolean shadersEnabled = probe().getAsBoolean();
        if (lastShaderState != null && lastShaderState == shadersEnabled) return;
        lastShaderState = shadersEnabled;

        ClientOptions.setGlowBrightness(shadersEnabled
                ? SwordGlowBrightness.SOFT
                : SwordGlowBrightness.DEFAULT);
        minecraft.player.displayClientMessage(prefixedMessage(shadersEnabled
                        ? "message.yujiancraft.shader_adapted_on"
                        : "message.yujiancraft.shader_adapted_off",
                ChatFormatting.AQUA, ClientModEvents.OPEN_CONFIG.getTranslatedKeyMessage()), false);
        if (!safetyNoticeShown) {
            safetyNoticeShown = true;
            minecraft.player.displayClientMessage(prefixedMessage(
                    "message.yujiancraft.visual_safety_notice", ChatFormatting.YELLOW), false);
        }
    }

    private static Component prefixedMessage(String messageKey, ChatFormatting color, Object... arguments) {
        String englishName = ModList.get().getModContainerById(YujianCraft.MOD_ID)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(YujianCraft.MOD_ID);
        return Component.translatable("message.yujiancraft.prefix",
                        Component.translatable("mod.yujiancraft.chinese_name"), englishName)
                .append(Component.translatable(messageKey, arguments))
                .withStyle(color);
    }

    private static BooleanSupplier probe() {
        if (shaderProbe == null) shaderProbe = createProbe();
        return shaderProbe;
    }

    private static BooleanSupplier createProbe() {
        for (String apiClassName : new String[]{
                "net.irisshaders.iris.api.v0.IrisApi",
                "net.coderbot.iris.api.v0.IrisApi"
        }) {
            try {
                Class<?> apiClass = Class.forName(apiClassName, false,
                        ClientShaderAdaptation.class.getClassLoader());
                Method getInstance = apiClass.getMethod("getInstance");
                Method isShaderPackInUse = apiClass.getMethod("isShaderPackInUse");
                LOGGER.info("YujianCraft shader adaptation is using {}", apiClassName);
                return () -> invokeBoolean(isShaderPackInUse, invoke(getInstance, null));
            } catch (ClassNotFoundException ignored) {
                // Try the next supported shader loader.
            } catch (ReflectiveOperationException exception) {
                LOGGER.warn("Could not initialize YujianCraft shader detection through {}", apiClassName, exception);
            } catch (LinkageError error) {
                LOGGER.warn("Shader API {} could not be linked", apiClassName, error);
            }
        }

        try {
            Class<?> shadersClass = Class.forName("net.optifine.shaders.Shaders", false,
                    ClientShaderAdaptation.class.getClassLoader());
            try {
                Method isShaderPackLoaded = shadersClass.getDeclaredMethod("isShaderPackLoaded");
                isShaderPackLoaded.setAccessible(true);
                LOGGER.info("YujianCraft shader adaptation is using OptiFine's shader method");
                return () -> invokeBoolean(isShaderPackLoaded, null);
            } catch (NoSuchMethodException ignored) {
                Field shaderPackLoaded = shadersClass.getDeclaredField("shaderPackLoaded");
                shaderPackLoaded.setAccessible(true);
                if (!Modifier.isStatic(shaderPackLoaded.getModifiers())) throw new NoSuchFieldException();
                LOGGER.info("YujianCraft shader adaptation is using OptiFine's shader field");
                return () -> readBoolean(shaderPackLoaded);
            }
        } catch (ClassNotFoundException ignored) {
            LOGGER.info("No supported shader loader detected; YujianCraft will use its default glow profile");
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Could not initialize YujianCraft OptiFine shader detection", exception);
        } catch (LinkageError error) {
            LOGGER.warn("OptiFine shader classes could not be linked", error);
        }
        return () -> false;
    }

    private static Object invoke(Method method, Object receiver) {
        try {
            return method.invoke(receiver);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static boolean invokeBoolean(Method method, Object receiver) {
        Object result = invoke(method, receiver);
        return result instanceof Boolean value && value;
    }

    private static boolean readBoolean(Field field) {
        try {
            return field.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }
}
