/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DimensionWrappingRequest() implements CustomPacketPayload {
	public static final StreamCodec<FriendlyByteBuf, DimensionWrappingRequest> STREAM_CODEC = CustomPacketPayload.codec(DimensionWrappingRequest::write, DimensionWrappingRequest::new);
	public static final CustomPacketPayload.Type<DimensionWrappingRequest> TYPE = new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("planetworld", "wrapping_data_request"));

	private DimensionWrappingRequest(FriendlyByteBuf buffer) {
		this();
	}

	private void write(FriendlyByteBuf buffer) {
	}

	@Override
	public CustomPacketPayload.Type<DimensionWrappingRequest> type() {
		return TYPE;
	}
}
