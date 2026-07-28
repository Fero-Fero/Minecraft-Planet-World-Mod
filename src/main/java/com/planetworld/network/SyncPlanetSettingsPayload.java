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

    // StreamCodec.composite supports at most 6 fields in 1.21.1 — use a manual codec.
    public static final StreamCodec<ByteBuf, SyncPlanetSettingsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.circumference());
                ByteBufCodecs.FLOAT.encode(buf, payload.curvatureIntensity());
                ByteBufCodecs.BOOL.encode(buf, payload.localizedTime());
                ByteBufCodecs.BOOL.encode(buf, payload.localizedWeather());
                ByteBufCodecs.BOOL.encode(buf, payload.entityWrap());
                ByteBufCodecs.BOOL.encode(buf, payload.curvatureShader());
                ByteBufCodecs.VAR_INT.encode(buf, payload.worldGenStyleOrdinal());
            },
            buf -> new SyncPlanetSettingsPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)
            )
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
