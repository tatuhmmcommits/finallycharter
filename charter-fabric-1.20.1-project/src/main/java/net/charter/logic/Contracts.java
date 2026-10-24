package net.charter.logic;

import java.util.UUID;

import net.charter.CharterMod;
import net.charter.config.CharterConfig;
import net.charter.fx.Fx;
import net.charter.net.CharterNetworking;
import net.charter.state.CharterState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * Contracts bind a soul to Charter. Whoever signs one is "under contract": wreathed in golden chains and able
 * to walk through Divine Dominance. A contract cannot simply be dropped. Severing it frees the one who bore
 * it, but the price is their left arm, and the burden does not vanish: it moves to whoever takes it up.
 */
public final class Contracts {
	private Contracts() {
	}

	public static boolean isBound(MinecraftServer server, UUID id) {
		return CharterState.get(server).isBound(id);
	}

	public static boolean isSevered(MinecraftServer server, UUID id) {
		return CharterState.get(server).isSevered(id);
	}

	/** Binds the player to Charter. Returns false if they were already bound. */
	public static boolean bind(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return false;
		}
		CharterState state = CharterState.get(server);
		boolean already = state.isBound(player.getUuid());
		state.bind(player.getUuid(), player.getGameProfile().getName());
		if (!already) {
			ServerWorld world = player.getServerWorld();
			Vec3d at = player.getPos();
			Fx.sparks(world, at.add(0.0, 1.0, 0.0), 40, 0.5);
			Fx.sound(world, at, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.4f);
			Fx.sound(world, at, SoundEvents.BLOCK_CHAIN_PLACE, 2.0f, 0.7f);
			CharterMod.LOGGER.info("{} is now bound to Charter", player.getGameProfile().getName());
		}
		return !already;
	}

	/** Frees the player from Charter with no price at all. Meant for operators. */
	public static boolean unbind(MinecraftServer server, UUID id) {
		boolean freed = CharterState.get(server).unbind(id);
		if (freed) {
			ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
			if (online != null) {
				online.sendMessage(Text.literal("Your contract with Charter has been lifted.").formatted(Formatting.GOLD), false);
			}
		}
		return freed;
	}

	/** The left arm is lost: it disappears from the model and the off hand can no longer be used. */
	public static boolean loseArm(MinecraftServer server, UUID id) {
		boolean changed = CharterState.get(server).sever(id);
		if (changed) {
			syncSevered(server);
			ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
			if (online != null) {
				ServerWorld world = online.getServerWorld();
				Vec3d at = online.getPos().add(0.0, 1.2, 0.0);
				Fx.sound(world, at, SoundEvents.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
				Fx.sound(world, at, SoundEvents.BLOCK_CHAIN_BREAK, 1.5f, 0.6f);
				Fx.sparks(world, at, 30, 0.4);
				online.sendMessage(Text.literal("Your left arm is gone. You can no longer use your off hand.").formatted(Formatting.RED, Formatting.ITALIC), false);
			}
		}
		return changed;
	}

	public static boolean restoreArm(MinecraftServer server, UUID id) {
		boolean changed = CharterState.get(server).restore(id);
		if (changed) {
			syncSevered(server);
			ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
			if (online != null) {
				online.sendMessage(Text.literal("Your left arm has been restored.").formatted(Formatting.GOLD), false);
			}
		}
		return changed;
	}

	/**
	 * Severs a contract: whoever bore it is free, and pays with their left arm. Returns false when they were not
	 * bound in the first place (nothing happens then, so no arm is lost for nothing).
	 */
	public static boolean sever(MinecraftServer server, UUID id) {
		boolean wasBound = CharterState.get(server).unbind(id);
		if (!wasBound) {
			return false;
		}
		loseArm(server, id);
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
		if (online != null) {
			online.sendMessage(Text.literal("Your contract with Charter is severed. You are free.").formatted(Formatting.GOLD), false);
		}
		return true;
	}

	/**
	 * Taking up someone else's burden: their contract is severed (they lose their left arm) and it now binds the
	 * taker instead. "All you've done is move it to yourself."
	 */
	public static void takeOver(ServerPlayerEntity taker, ServerPlayerEntity freed) {
		MinecraftServer server = taker.getServer();
		if (server == null) {
			return;
		}
		sever(server, freed.getUuid());
		bind(taker);
		Fx.chains(taker.getServerWorld(), taker, 50);
		Fx.sound(taker.getServerWorld(), taker.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 2.0f, 0.6f);
		taker.sendMessage(Text.literal("You have taken " + freed.getGameProfile().getName() + "'s contract upon yourself.").formatted(Formatting.GOLD), false);
		freed.sendMessage(Text.literal(taker.getGameProfile().getName() + " has taken your contract. You are free, but not whole.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
	}

	/** A signer tears up their own contract: they are free, and the severing takes their left arm. */
	public static boolean tearUp(ServerPlayerEntity signer) {
		MinecraftServer server = signer.getServer();
		if (server == null || !CharterConfig.get().contractMayBeTornUp) {
			return false;
		}
		return sever(server, signer.getUuid());
	}

	/** Tells every client which players have lost an arm so their models can be drawn that way. */
	public static void syncSevered(MinecraftServer server) {
		CharterNetworking.broadcastSevered(server, CharterState.get(server).severedPlayers());
	}

	public static boolean offhandLocked(MinecraftServer server, UUID id) {
		return CharterConfig.get().severedLocksOffhand && CharterState.get(server).isSevered(id);
	}
}
