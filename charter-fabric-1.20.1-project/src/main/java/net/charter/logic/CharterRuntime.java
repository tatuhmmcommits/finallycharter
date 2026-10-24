package net.charter.logic;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.charter.block.DivineDominanceBlockEntity;
import net.charter.config.CharterConfig;
import net.charter.fx.Fx;
import net.charter.item.DuskEpitaphItem;
import net.charter.net.CharterNetworking;
import net.charter.registry.ModBlocks;
import net.charter.state.CharterState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Wires the mod into the game: the per-tick work (judgements, exile, severed arms, golden chains on the bound),
 * the protection of players being judged, the exile that cannot be died out of, Dusk's Epitaph, and everything
 * that happens when somebody joins or respawns. No mixins are needed for any of it.
 */
public final class CharterRuntime {
	private static long tickCount;

	private CharterRuntime() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> Judgement.reset());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Judgement.reset());
		ServerTickEvents.END_SERVER_TICK.register(CharterRuntime::tick);

		ServerPlayConnectionEvents.JOIN.register(CharterRuntime::onJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(CharterRuntime::onRespawn);

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CharterRuntime::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(CharterRuntime::allowDeath);

		AttackEntityCallback.EVENT.register(CharterRuntime::onAttackEntity);
		AttackBlockCallback.EVENT.register(CharterRuntime::onAttackBlock);
		UseItemCallback.EVENT.register(CharterRuntime::onUseItem);
		UseBlockCallback.EVENT.register(CharterRuntime::onUseBlock);
		UseEntityCallback.EVENT.register(CharterRuntime::onUseEntity);
		PlayerBlockBreakEvents.BEFORE.register(CharterRuntime::beforeBreak);
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer server) {
		tickCount++;
		Judgement.tick(server);
		if (tickCount % 20L == 0L) {
			ExileManager.tick(server);
		}
		if (server.getPlayerManager().getCurrentPlayerCount() == 0) {
			return;
		}
		CharterConfig config = CharterConfig.get();
		CharterState state = CharterState.get(server);
		boolean chainParticles = config.boundParticles && tickCount % 5L == 0L;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (config.severedLocksOffhand && state.isSevered(id)) {
				lockOffhand(player);
			}
			if (chainParticles && state.isBound(id) && !player.isSpectator()) {
				bindParticles(player);
			}
		}
	}

	/** A player without a left arm cannot hold anything in their off hand: whatever lands there is put away. */
	private static void lockOffhand(ServerPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		ItemStack held = inventory.offHand.get(0);
		if (held.isEmpty()) {
			return;
		}
		ItemStack moved = held.copy();
		inventory.offHand.set(0, ItemStack.EMPTY);
		inventory.insertStack(moved);
		if (!moved.isEmpty()) {
			player.dropItem(moved, false);
		}
		player.sendMessage(Text.literal("You have no left hand to hold that with.").formatted(Formatting.RED, Formatting.ITALIC), true);
	}

	/** Three golden motes circling the body: the chains of the contract. */
	private static void bindParticles(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		double base = (double) (tickCount % 360L) * 0.3;
		for (int i = 0; i < 3; i++) {
			double angle = base + (double) i * (Math.PI * 2.0 / 3.0);
			double height = 0.1 + (double) ((tickCount / 5L + (long) (i * 5)) % 16L) * 0.11;
			world.spawnParticles(Fx.GOLD_DUST_SMALL, player.getX() + Math.cos(angle) * 0.6, player.getY() + height, player.getZ() + Math.sin(angle) * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	// ------------------------------------------------------------------ joining and respawning

	private static void onJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
		ServerPlayerEntity player = handler.player;
		CharterConfig config = CharterConfig.get();
		CharterState state = CharterState.get(server);
		UUID id = player.getUuid();

		if (config.enforceOnJoin && state.isJudged(id)) {
			if (Banisher.isExempt(server, player.getGameProfile())) {
				// the host of a local world cannot be thrown out of it; the old judgement is simply forgotten
				state.pardon(id);
			} else {
				player.networkHandler.disconnect(Text.literal(config.kickMessage).formatted(Formatting.GOLD));
				return;
			}
		}

		CharterNetworking.sendSevered(player, state.severedPlayers());

		if (state.isPendingExile(id)) {
			server.execute(() -> {
				if (!player.isRemoved()) {
					ExileManager.exile(player);
				}
			});
		}
	}

	private static void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
		MinecraftServer server = newPlayer.getServer();
		if (server == null) {
			return;
		}
		CharterState state = CharterState.get(server);
		if (!alive) {
			if (CharterConfig.get().severedClearsOnDeath) {
				Contracts.restoreArm(server, newPlayer.getUuid());
			}
			if (state.isExiled(newPlayer.getUuid())) {
				// they died close enough to home for death to count: the respawn brings them back
				ExileManager.release(newPlayer, false);
			}
		}
		CharterNetworking.sendSevered(newPlayer, state.severedPlayers());
	}

	// ------------------------------------------------------------------ damage and death

	/** Someone held in golden chains cannot be touched until the strike lands. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayerEntity && Judgement.isCondemned(entity.getUuid())) {
			return false;
		}
		return true;
	}

	/** An exiled player cannot die: lethal damage leaves them on half a heart. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayerEntity)) {
			return true;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) entity;
		if (ExileManager.protects(player)) {
			player.setHealth(CharterConfig.get().exileMinHealth);
			return false;
		}
		return true;
	}

	// ------------------------------------------------------------------ interaction

	private static boolean exileLocksBlocks(MinecraftServer server, UUID id) {
		return CharterConfig.get().exileLocksBlocks && CharterState.get(server).isExiled(id);
	}

	private static ActionResult onAttackEntity(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (world.isClient || !(player instanceof ServerPlayerEntity)) {
			return ActionResult.PASS;
		}
		ServerPlayerEntity attacker = (ServerPlayerEntity) player;
		MinecraftServer server = attacker.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		if (Judgement.isCondemned(attacker.getUuid())) {
			return ActionResult.FAIL;
		}

		// Dusk's Epitaph: one blow, and the target is seized by chains and judged
		ItemStack stack = attacker.getStackInHand(hand);
		if (hand != Hand.MAIN_HAND || !(stack.getItem() instanceof DuskEpitaphItem) || !(entity instanceof ServerPlayerEntity)) {
			return ActionResult.PASS;
		}
		ServerPlayerEntity target = (ServerPlayerEntity) entity;
		if (target.isSpectator() || Judgement.isCondemned(target.getUuid())) {
			return ActionResult.PASS;
		}
		CharterConfig config = CharterConfig.get();
		if (config.weaponsRequireContract && !Contracts.isBound(server, attacker.getUuid())) {
			attacker.sendMessage(Text.literal("The Epitaph does not answer to you.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return ActionResult.PASS;
		}
		if ((double) attacker.getAttackCooldownProgress(0.5f) < config.epitaphMinCharge) {
			// a weak, half-charged swing is just a heavy sword
			return ActionResult.PASS;
		}

		attacker.resetLastAttackedTicks();
		attacker.swingHand(Hand.MAIN_HAND, true);
		attacker.getItemCooldownManager().set(stack.getItem(), 40);
		ServerWorld serverWorld = attacker.getServerWorld();
		Fx.sound(serverWorld, target.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 2.0f, 0.5f);
		Fx.sound(serverWorld, target.getPos(), SoundEvents.ITEM_TRIDENT_THUNDER, 3.0f, 0.5f);
		Fx.sparks(serverWorld, target.getPos().add(0.0, 1.0, 0.0), 30, 0.4);
		Judgement.bindAndJudge(target, Verdict.BAN, attacker);
		return ActionResult.SUCCESS;
	}

	private static ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
		if (world.isClient) {
			return ActionResult.PASS;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		UUID id = player.getUuid();
		if (Judgement.isCondemned(id) || exileLocksBlocks(server, id)) {
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
	}

	private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.pass(stack);
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return TypedActionResult.pass(stack);
		}
		UUID id = player.getUuid();
		if (Judgement.isCondemned(id)) {
			return TypedActionResult.fail(stack);
		}
		if (hand == Hand.OFF_HAND && Contracts.offhandLocked(server, id)) {
			return TypedActionResult.fail(stack);
		}
		return TypedActionResult.pass(stack);
	}

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.PASS;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		UUID id = player.getUuid();
		if (Judgement.isCondemned(id)) {
			return ActionResult.FAIL;
		}
		if (hand == Hand.OFF_HAND && Contracts.offhandLocked(server, id)) {
			return ActionResult.FAIL;
		}
		if (exileLocksBlocks(server, id)) {
			player.sendMessage(Text.literal("In exile you may look, but not touch.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
	}

	private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (world.isClient) {
			return ActionResult.PASS;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		UUID id = player.getUuid();
		if (Judgement.isCondemned(id)) {
			return ActionResult.FAIL;
		}
		if (hand == Hand.OFF_HAND && Contracts.offhandLocked(server, id)) {
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
	}

	private static boolean beforeBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		MinecraftServer server = world.getServer();
		if (world.isClient || server == null) {
			return true;
		}
		UUID id = player.getUuid();
		if (Judgement.isCondemned(id) || exileLocksBlocks(server, id)) {
			return false;
		}
		if (state.isOf(ModBlocks.DIVINE_DOMINANCE) && CharterConfig.get().dominanceProtectedTablet) {
			boolean owner = blockEntity instanceof DivineDominanceBlockEntity && ((DivineDominanceBlockEntity) blockEntity).isOwner(id);
			if (!owner && !Contracts.isBound(server, id) && !player.hasPermissionLevel(2)) {
				player.sendMessage(Text.literal("The tablet does not yield to you.").formatted(Formatting.GOLD, Formatting.ITALIC), true);
				return false;
			}
		}
		return true;
	}
}
