package com.planetworld.network;

import com.planetworld.PlanetWorld;
import com.planetworld.config.PlanetSettings;
import com.planetworld.config.WorldGenStyle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncPlanetSettingsPayload(
        int circumference,
        float curvatureIntensity,
        boolean localizedTime,
        boolean localizedWeather,
        boolean entityWrap,
        boolean curvatureShader,
        int worldGenStyleOrdinal
) implements CustomPacketPayload {
    public static final Type<SyncPlanetSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlanetWorld.MOD_ID, "sync_settings"));

    public static final StreamCodec<ByteBuf, SyncPlanetSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncPlanetSettingsPayload::circumference,
            ByteBufCodecs.FLOAT, SyncPlanetSettingsPayload::curvatureIntensity,
            ByteBufCodecs.BOOL, SyncPlanetSettingsPayload::localizedTime,
            ByteBufCodecs.BOOL, SyncPlanetSettingsPayload::localizedWeather,
            ByteBufCodecs.BOOL, SyncPlanetSettingsPayload::entityWrap,
            ByteBufCodecs.BOOL, SyncPlanetSettingsPayload::curvatureShader,
            ByteBufCodecs.VAR_INT, SyncPlanetSettingsPayload::worldGenStyleOrdinal,
            SyncPlanetSettingsPayload::new
    );

    public SyncPlanetSettingsPayload(PlanetSettings settings) {
        this(
                settings.circumference(),
                settings.curvatureIntensity(),
                settings.localizedTime(),
                settings.localizedWeather(),
                settings.entityWrap(),
                settings.curvatureShader(),
                settings.worldGenStyle().ordinal()
        );
    }

    public PlanetSettings toSettings() {
        return new PlanetSettings(
                circumference,
                curvatureIntensity,
                localizedTime,
                localizedWeather,
                entityWrap,
                curvatureShader,
                WorldGenStyle.fromOrdinalSafe(worldGenStyleOrdinal)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
