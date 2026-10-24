package net.charter.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.charter.config.CharterConfig;
import net.charter.logic.Banisher;
import net.charter.logic.Contracts;
import net.charter.logic.Detonation;
import net.charter.logic.ExileManager;
import net.charter.logic.Judgement;
import net.charter.logic.Verdict;
import net.charter.state.CharterState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * {@code /charter ...}: operator commands (permission level 2) to bind, sever, judge, exile and pardon, to test
 * the sigil and the crater, and to reload the config.
 */
public final class CharterCommand {
	private CharterCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<ServerCommandSource> dispatcher) {
		LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("charter").requires(source -> source.hasPermissionLevel(2));

		root.then(CommandManager.literal("bind")
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(ctx -> bind(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets")))));

		root.then(CommandManager.literal("unbind")
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(ctx -> unbind(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets")))));

		root.then(CommandManager.literal("sever")
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(ctx -> sever(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets")))));

		root.then(CommandManager.literal("arm")
				.then(CommandManager.literal("remove")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(ctx -> removeArm(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets")))))
				.then(CommandManager.literal("restore")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(ctx -> restoreArm(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"))))));

		var judgeTargets = CommandManager.argument("targets", EntityArgumentType.players())
				.executes(ctx -> judge(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), Verdict.BAN));
		for (Verdict verdict : Verdict.values()) {
			judgeTargets.then(CommandManager.literal(verdict.name().toLowerCase(Locale.ROOT))
					.executes(ctx -> judge(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), verdict)));
		}
		root.then(CommandManager.literal("judge").then(judgeTargets));

		var sigilPos = CommandManager.argument("pos", Vec3ArgumentType.vec3());
		for (Verdict verdict : Verdict.values()) {
			sigilPos.then(CommandManager.literal(verdict.name().toLowerCase(Locale.ROOT))
					.executes(ctx -> sigil(ctx.getSource(), Vec3ArgumentType.getVec3(ctx, "pos"), CharterConfig.get().sigilRadius, verdict))
					.then(CommandManager.argument("radius", DoubleArgumentType.doubleArg(1.0, 64.0))
							.executes(ctx -> sigil(ctx.getSource(), Vec3ArgumentType.getVec3(ctx, "pos"), DoubleArgumentType.getDouble(ctx, "radius"), verdict))));
		}
		root.then(CommandManager.literal("sigil").then(sigilPos));

		root.then(CommandManager.literal("exile")
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(ctx -> exile(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets")))));

		root.then(CommandManager.literal("release")
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(ctx -> release(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), false))
						.then(CommandManager.literal("home")
								.executes(ctx -> release(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), true)))));

		root.then(CommandManager.literal("pardon")
				.then(CommandManager.argument("name", StringArgumentType.word())
						.executes(ctx -> pardon(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));

		root.then(CommandManager.literal("crater")
				.then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
						.executes(ctx -> crater(ctx.getSource(), Vec3ArgumentType.getVec3(ctx, "pos")))));

		root.then(CommandManager.literal("list").executes(ctx -> list(ctx.getSource())));
		root.then(CommandManager.literal("reload").executes(ctx -> reload(ctx.getSource())));

		dispatcher.register(root);
	}

	// ------------------------------------------------------------------ helpers

	private static void say(ServerCommandSource source, String text) {
		source.sendFeedback(() -> Text.literal(text).formatted(Formatting.GOLD), true);
	}

	private static void complain(ServerCommandSource source, String text) {
		source.sendError(Text.literal(text));
	}

	private static String nameOf(MinecraftServer server, UUID id) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
		if (online != null) {
			return online.getGameProfile().getName();
		}
		Optional<GameProfile> cached = Banisher.cachedByUuid(server, id);
		return cached.isPresent() ? cached.get().getName() : id.toString();
	}

	private static String join(MinecraftServer server, Collection<UUID> ids) {
		List<String> names = new ArrayList<>();
		for (UUID id : ids) {
			names.add(nameOf(server, id));
		}
		return names.isEmpty() ? "nobody" : String.join(", ", names);
	}

	// ------------------------------------------------------------------ contracts

	private static int bind(ServerCommandSource source, Collection<ServerPlayerEntity> targets) throws CommandSyntaxException {
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			if (Contracts.bind(player)) {
				count++;
			}
		}
		say(source, "Bound " + count + " player(s) to Charter.");
		return count;
	}

	private static int unbind(ServerCommandSource source, Collection<ServerPlayerEntity> targets) throws CommandSyntaxException {
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			if (Contracts.unbind(source.getServer(), player.getUuid())) {
				count++;
			}
		}
		say(source, "Lifted the contract of " + count + " player(s), at no price.");
		return count;
	}

	private static int sever(ServerCommandSource source, Collection<ServerPlayerEntity> targets) throws CommandSyntaxException {
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			if (Contracts.sever(source.getServer(), player.getUuid())) {
				count++;
			}
		}
		say(source, "Severed the contract of " + count + " player(s). Each paid with their left arm.");
		return count;
	}

	private static int removeArm(ServerCommandSource source, Collection<ServerPlayerEntity> targets) throws CommandSyntaxException {
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			if (Contracts.loseArm(source.getServer(), player.getUuid())) {
				count++;
			}
		}
		say(source, "Took the left arm of " + count + " player(s).");
		return count;
	}

	private static int restoreArm(ServerCommandSource source, Collection<ServerPlayerEntity> targets) throws CommandSyntaxException {
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			if (Contracts.restoreArm(source.getServer(), player.getUuid())) {
				count++;
			}
		}
		say(source, "Restored the left arm of " + count + " player(s).");
		return count;
	}

	// ------------------------------------------------------------------ judgement

	private static int judge(ServerCommandSource source, Collection<ServerPlayerEntity> targets, Verdict verdict) throws CommandSyntaxException {
		ServerPlayerEntity issuer = source.getPlayer();
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			Judgement.bindAndJudge(player, verdict, issuer == player ? null : issuer);
			count++;
		}
		say(source, "Charter will judge " + count + " player(s): " + verdict.name().toLowerCase(Locale.ROOT) + ".");
		return count;
	}

	private static int sigil(ServerCommandSource source, Vec3d pos, double radius, Verdict verdict) throws CommandSyntaxException {
		Judgement.sigilStrike(source.getWorld(), pos, radius, verdict, source.getPlayer(), null);
		say(source, "A sigil is drawn: everyone standing in it will receive the verdict " + verdict.name().toLowerCase(Locale.ROOT) + ".");
		return 1;
	}

	private static int crater(ServerCommandSource source, Vec3d pos) throws CommandSyntaxException {
		Detonation.detonate(source.getWorld(), BlockPos.ofFloored(pos));
		say(source, "The ground is torn open.");
		return 1;
	}

	// ------------------------------------------------------------------ exile and pardon

	private static int exile(ServerCommandSource source, Collection<ServerPlayerEntity> targets) throws CommandSyntaxException {
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			ExileManager.exile(player);
			count++;
		}
		say(source, "Cast out " + count + " player(s).");
		return count;
	}

	private static int release(ServerCommandSource source, Collection<ServerPlayerEntity> targets, boolean home) throws CommandSyntaxException {
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			if (ExileManager.release(player, home)) {
				count++;
			}
		}
		say(source, "Ended the exile of " + count + " player(s)" + (home ? ", and brought them home." : "."));
		return count;
	}

	private static int pardon(ServerCommandSource source, String name) throws CommandSyntaxException {
		MinecraftServer server = source.getServer();
		GameProfile profile = null;
		Optional<GameProfile> cached = Banisher.cachedByName(server, name);
		if (cached.isPresent()) {
			profile = cached.get();
		} else {
			for (Map.Entry<UUID, String> entry : CharterState.get(server).judgedPlayers().entrySet()) {
				if (entry.getValue().equalsIgnoreCase(name)) {
					profile = new GameProfile(entry.getKey(), entry.getValue());
					break;
				}
			}
		}
		if (profile == null) {
			complain(source, "Charter knows no player called " + name + ".");
			return 0;
		}
		if (Banisher.pardon(server, profile)) {
			say(source, profile.getName() + " has been pardoned. They may join again.");
			return 1;
		}
		complain(source, profile.getName() + " has not been judged.");
		return 0;
	}

	// ------------------------------------------------------------------ information

	private static int list(ServerCommandSource source) throws CommandSyntaxException {
		MinecraftServer server = source.getServer();
		CharterState state = CharterState.get(server);

		Set<UUID> bound = state.boundPlayers().keySet();
		Set<UUID> severed = state.severedPlayers();
		Set<UUID> exiled = state.exiledPlayers().keySet();
		Set<UUID> judged = state.judgedPlayers().keySet();

		say(source, "Bound to Charter (" + bound.size() + "): " + join(server, bound));
		say(source, "Without a left arm (" + severed.size() + "): " + join(server, severed));
		say(source, "In exile (" + exiled.size() + "): " + join(server, exiled));
		say(source, "Judged (" + judged.size() + "): " + join(server, judged));
		return bound.size() + severed.size() + exiled.size() + judged.size();
	}

	private static int reload(ServerCommandSource source) throws CommandSyntaxException {
		CharterConfig.load();
		say(source, "Charter's config has been reloaded.");
		return 1;
	}
}
