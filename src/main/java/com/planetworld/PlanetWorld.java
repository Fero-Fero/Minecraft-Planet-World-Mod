package com.planetworld;

import com.mojang.logging.LogUtils;
import com.planetworld.config.PlanetSettingsLifecycle;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.network.SyncPlanetSettingsPayload;
import com.planetworld.sleep.SleepHandler;
import com.planetworld.time.LocalTimeHandler;
import com.planetworld.weather.LocalizedWeatherHandler;
import com.planetworld.wrap.client.storage.TransformersStorage;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.network.WrapSettingsConfigurationTask;
import com.planetworld.wrap.network.packet.DimensionWrappingPayload;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(PlanetWorld.MOD_ID)
public class PlanetWorld {
    public static final String MOD_ID = "planetworld";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PlanetWorld(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerConfigurationTasks);
        modContainer.registerConfig(ModConfig.Type.COMMON, PlanetWorldConfig.SPEC, "planetworld-common.toml");

        NeoForge.EVENT_BUS.register(LocalTimeHandler.class);
        NeoForge.EVENT_BUS.register(SleepHandler.class);
        NeoForge.EVENT_BUS.register(LocalizedWeatherHandler.class);
        NeoForge.EVENT_BUS.register(PlanetSettingsLifecycle.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info(
                "Planet World ready — default circumference {} blocks (no border teleport)",
                PlanetWorldConfig.PLANET_CIRCUMFERENCE.getAsInt()
        ));
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                SyncPlanetSettingsPayload.TYPE,
                SyncPlanetSettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    com.planetworld.config.PlanetSettingsAccess.setActive(payload.toSettings());
                    LOGGER.info("Synced planet settings: circumference={}", payload.circumference());
                })
        );
        registrar.configurationToClient(
                DimensionWrappingPayload.TYPE,
                DimensionWrappingPayload.STREAM_CODEC,
                (payload, context) -> {
                    TransformersStorage.setTransformer(payload.levelKey(),
                            new DimensionTransformer(payload.wrappingSettings(), true));
                    LOGGER.info("Received wrapping bounds for {}: {}", payload.levelKey().location(), payload.wrappingSettings());
                }
        );
    }

    private void registerConfigurationTasks(RegisterConfigurationTasksEvent event) {
        if (event.getListener() instanceof ServerConfigurationPacketListenerImpl listener) {
            event.register(new WrapSettingsConfigurationTask(listener));
        }
    }
}
