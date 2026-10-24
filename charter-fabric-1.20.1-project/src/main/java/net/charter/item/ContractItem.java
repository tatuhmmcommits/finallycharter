package net.charter.item;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.charter.config.CharterConfig;
import net.charter.logic.Contracts;
import net.charter.registry.ModItems;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A contract with Charter, in two forms.
 *
 * <p>A blank contract is signed by using it: the signer is bound to Charter. Used on a bound soul instead, it
 * takes that soul's burden: they are freed, and lose their left arm for it. A signed contract records who
 * signed it. Its signer can tear it up, which severs the contract the same way.
 */
public class ContractItem extends Item {
	public static final String SIGNER = "Signer";
	public static final String SIGNER_NAME = "SignerName";

	private final boolean signed;

	public ContractItem(Settings settings, boolean signed) {
		super(settings);
		this.signed = signed;
	}

	public boolean isSigned() {
		return signed;
	}

	/** A fresh signed contract in the name of the player. */
	public static ItemStack signedBy(ServerPlayerEntity player) {
		ItemStack stack = new ItemStack(ModItems.SIGNED_CONTRACT);
		NbtCompound nbt = stack.getOrCreateNbt();
		nbt.putUuid(SIGNER, player.getUuid());
		nbt.putString(SIGNER_NAME, player.getGameProfile().getName());
		return stack;
	}

	@Nullable
	public static UUID signerOf(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.containsUuid(SIGNER)) {
			return nbt.getUuid(SIGNER);
		}
		return null;
	}

	public static String signerNameOf(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.contains(SIGNER_NAME)) {
			return nbt.getString(SIGNER_NAME);
		}
		return "";
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return signed;
	}

	/** Swaps one contract of the held stack for the given item, putting it into the inventory. */
	private static void exchange(ServerPlayerEntity player, ItemStack held, ItemStack replacement) {
		held.decrement(1);
		player.getInventory().insertStack(replacement);
		if (!replacement.isEmpty()) {
			player.dropItem(replacement, false);
		}
	}

	private static void tell(PlayerEntity player, String text, Formatting... formats) {
		player.sendMessage(Text.literal(text).formatted(formats), true);
	}

	// ------------------------------------------------------------------ using it

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient || !(user instanceof ServerPlayerEntity)) {
			return TypedActionResult.success(stack);
		}
		ServerPlayerEntity player = (ServerPlayerEntity) user;
		MinecraftServer server = player.getServer();
		if (server == null) {
			return TypedActionResult.pass(stack);
		}

		if (!signed) {
			if (Contracts.isBound(server, player.getUuid())) {
				tell(player, "You are already bound to Charter.", Formatting.GRAY, Formatting.ITALIC);
				return TypedActionResult.fail(stack);
			}
			Contracts.bind(player);
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 0.7f);
			tell(player, "You sign. You are bound to Charter.", Formatting.GOLD);
			exchange(player, stack, signedBy(player));
			return TypedActionResult.success(stack);
		}

		UUID signer = signerOf(stack);
		String name = signerNameOf(stack);
		if (signer == null || !signer.equals(player.getUuid())) {
			tell(player, "Signed by " + (name.isEmpty() ? "a stranger" : name) + ".", Formatting.GOLD);
			return TypedActionResult.fail(stack);
		}
		if (!player.isSneaking()) {
			boolean mayTear = CharterConfig.get().contractMayBeTornUp;
			tell(player, mayTear ? "Your own signature. Sneak and use it to tear it up: it will cost you your left arm." : "Your own signature.", Formatting.GOLD);
			return TypedActionResult.fail(stack);
		}
		if (!CharterConfig.get().contractMayBeTornUp) {
			tell(player, "This contract cannot be torn up.", Formatting.GRAY, Formatting.ITALIC);
			return TypedActionResult.fail(stack);
		}
		boolean severed = Contracts.tearUp(player);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
		if (!severed) {
			tell(player, "The ink has already faded: you are not bound.", Formatting.GRAY, Formatting.ITALIC);
		}
		stack.decrement(1);
		return TypedActionResult.success(stack);
	}

	/** A blank contract used on a bound player takes their contract over. */
	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (signed || !(entity instanceof PlayerEntity)) {
			return ActionResult.PASS;
		}
		if (user.getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(user instanceof ServerPlayerEntity) || !(entity instanceof ServerPlayerEntity)) {
			return ActionResult.PASS;
		}
		ServerPlayerEntity taker = (ServerPlayerEntity) user;
		ServerPlayerEntity target = (ServerPlayerEntity) entity;
		MinecraftServer server = taker.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		String targetName = target.getGameProfile().getName();

		if (Contracts.isBound(server, taker.getUuid())) {
			tell(taker, "You are already bound to Charter.", Formatting.GRAY, Formatting.ITALIC);
			return ActionResult.FAIL;
		}
		if (!Contracts.isBound(server, target.getUuid())) {
			tell(taker, targetName + " is not under contract.", Formatting.GRAY, Formatting.ITALIC);
			return ActionResult.FAIL;
		}
		if (CharterConfig.get().contractTakeoverNeedsConsent && !target.isSneaking()) {
			tell(taker, targetName + " must crouch to accept.", Formatting.GRAY, Formatting.ITALIC);
			tell(target, taker.getGameProfile().getName() + " offers to take your contract. Crouch to accept: it will cost you your left arm.", Formatting.GOLD);
			return ActionResult.FAIL;
		}

		Contracts.takeOver(taker, target);
		exchange(taker, stack, signedBy(taker));
		return ActionResult.SUCCESS;
	}

	// ------------------------------------------------------------------ tooltip

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		if (signed) {
			String name = signerNameOf(stack);
			tooltip.add(Text.translatable("tooltip.charter.signed_contract.signer", name.isEmpty() ? "?" : name).formatted(Formatting.GOLD));
			tooltip.add(Text.translatable("tooltip.charter.signed_contract.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.signed_contract.2").formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.translatable("tooltip.charter.charter_contract.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.charter_contract.2").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.charter_contract.3").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		}
	}
}
