package net.charter.net;

import java.util.Set;
import java.util.UUID;

import net.charter.CharterMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** The only thing the client needs to be told: which players have lost their left arm. */
public final class CharterNetworking {
	/** Server to client: varint count, then that many UUIDs. */
	public static final Identifier SEVERED = CharterMod.id("severed");

	private CharterNetworking() {
	}

	public static void sendSevered(ServerPlayerEntity player, Set<UUID> severed) {
		ServerPlayNetworking.send(player, SEVERED, write(severed));
	}

	public static void broadcastSevered(MinecraftServer server, Set<UUID> severed) {
		for (ServerPlayerEntity player : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(player, SEVERED, write(severed));
		}
	}

	private static PacketByteBuf write(Set<UUID> severed) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(severed.size());
		for (UUID id : severed) {
			buf.writeUuid(id);
		}
		return buf;
	}
}
