/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.network;

import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.network.packet.DimensionWrappingPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

import java.util.function.Consumer;

/**
 * Sends each wrapped dimension's wrapping bounds to the client during the
 * configuration phase (replaces Circumnavigate's ServerConfigurationPacketListenerImplMixin).
 */
public record WrapSettingsConfigurationTask(ServerConfigurationPacketListenerImpl listener) implements ICustomConfigurationTask {
	public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(
			ResourceLocation.fromNamespaceAndPath("planetworld", "wrap_settings").toString());

	@Override
	public void run(Consumer<CustomPacketPayload> sender) {
		if (listener != null) {
			for (ServerLevel level : listener.server.getAllLevels()) {
				DimensionTransformer transformer = level.getTransformer();
				if (transformer == null || !transformer.isWrapped()) {
					continue;
				}
				sender.accept(new DimensionWrappingPayload(level.dimension(), transformer.wrappingSettings));
			}
			// IMPORTANT: custom configuration tasks must be explicitly marked complete
			// on the server, otherwise clients can hang at world-loading.
			listener.finishCurrentTask(type());
		}
	}

	@Override
	public ConfigurationTask.Type type() {
		return TYPE;
	}
}
