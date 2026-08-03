package com.planetworld;

import com.planetworld.client.WrappedWorldCustomizeScreen;
import com.planetworld.compat.SodiumCompat;
import com.planetworld.config.PlanetSettingsAccess;
import com.planetworld.wrap.storage.TransformerRequests;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.render.CurvatureRenderer;
import com.planetworld.render.LocalSkyHandler;
import com.planetworld.worldgen.PlanetWorldPresets;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = PlanetWorld.MOD_ID, dist = Dist.CLIENT)
public class PlanetWorldClient {
    public PlanetWorldClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterPresetEditors);
        NeoForge.EVENT_BUS.register(CurvatureRenderer.class);
        NeoForge.EVENT_BUS.register(LocalSkyHandler.class);
        NeoForge.EVENT_BUS.addListener(this::onClientLogin);
        NeoForge.EVENT_BUS.addListener(this::onClientLogout);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        PlanetWorld.LOGGER.info(
                "Planet World client ready (curvature={}, sodium={}, terrainCurve={})",
                PlanetWorldConfig.enableCurvatureShader(),
                SodiumCompat.isSodiumLoaded(),
                SodiumCompat.isTerrainCurvatureLive()
        );
    }

    private void onRegisterPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(PlanetWorldPresets.WRAPPED, (createWorldScreen, context) ->
                new WrappedWorldCustomizeScreen(createWorldScreen));
    }

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!SodiumCompat.shouldShowCurvatureDegradeTip()) {
            return;
        }
        if (event.getPlayer() == null) {
            return;
        }
        event.getPlayer().displayClientMessage(
                Component.translatable("planetworld.compat.sodium.curvature_degraded"),
                false
        );
        SodiumCompat.markCurvatureTipShown();
    }

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TransformerRequests.clearChunkMapTransformer();
        PlanetSettingsAccess.clearActive();
        SodiumCompat.resetSessionTips();
    }
}
