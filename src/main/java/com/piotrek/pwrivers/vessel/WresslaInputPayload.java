package com.piotrek.pwrivers.vessel;

import com.piotrek.pwrivers.PeterwolfsRiversMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WresslaInputPayload(
	int entityId,
	float rudderInput,
	float throttleInput,
	boolean toggleEngine,
	boolean dismountHelm
) implements CustomPacketPayload {
	public static final Type<WresslaInputPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(PeterwolfsRiversMod.MOD_ID, "wressla_input")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, WresslaInputPayload> CODEC = new StreamCodec<>() {
		@Override
		public WresslaInputPayload decode(RegistryFriendlyByteBuf buf) {
			return new WresslaInputPayload(
				buf.readVarInt(),
				buf.readFloat(),
				buf.readFloat(),
				buf.readBoolean(),
				buf.readBoolean()
			);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, WresslaInputPayload p) {
			buf.writeVarInt(p.entityId);
			buf.writeFloat(p.rudderInput);
			buf.writeFloat(p.throttleInput);
			buf.writeBoolean(p.toggleEngine);
			buf.writeBoolean(p.dismountHelm);
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
