package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.PeterwolfsRiversMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;

public record WresslaMooringPayload(
	int vesselEntityId,
	int cleatIndex,
	Optional<BlockPos> fencePos
) implements CustomPacketPayload {
	public static final Type<WresslaMooringPayload> TYPE = new Type<>(
		PeterwolfsRiversMod.id("wressla_mooring")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, WresslaMooringPayload> CODEC = new StreamCodec<>() {
		@Override
		public WresslaMooringPayload decode(RegistryFriendlyByteBuf buf) {
			int vesselId = buf.readVarInt();
			int cleat = buf.readVarInt();
			boolean hasPos = buf.readBoolean();
			Optional<BlockPos> pos = hasPos ? Optional.of(buf.readBlockPos()) : Optional.empty();
			return new WresslaMooringPayload(vesselId, cleat, pos);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, WresslaMooringPayload p) {
			buf.writeVarInt(p.vesselEntityId);
			buf.writeVarInt(p.cleatIndex);
			buf.writeBoolean(p.fencePos.isPresent());
			p.fencePos.ifPresent(buf::writeBlockPos);
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
