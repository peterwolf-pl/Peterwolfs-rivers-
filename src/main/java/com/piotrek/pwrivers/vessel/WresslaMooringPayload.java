package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.PeterwolfsRiversMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Mooring interaction packet sent from client to server when clicking a cleat or dock fence/wall post.
 */
public record WresslaMooringPayload(
	boolean cleat,
	int vesselEntityId,
	int cleatIndex,
	BlockPos fencePos
) implements CustomPacketPayload {
	public static final Type<WresslaMooringPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(PeterwolfsRiversMod.MOD_ID, "wressla_mooring")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, WresslaMooringPayload> CODEC = new StreamCodec<>() {
		@Override
		public WresslaMooringPayload decode(RegistryFriendlyByteBuf buf) {
			boolean cleat = buf.readBoolean();
			int vesselId = buf.readVarInt();
			int cleatIndex = buf.readByte();
			BlockPos fence = buf.readBlockPos();
			return new WresslaMooringPayload(cleat, vesselId, cleatIndex, fence);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, WresslaMooringPayload p) {
			buf.writeBoolean(p.cleat());
			buf.writeVarInt(p.vesselEntityId());
			buf.writeByte(p.cleatIndex());
			buf.writeBlockPos(p.fencePos());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
