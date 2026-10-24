package net.charter.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.charter.client.CharterClientState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/**
 * Hides the left arm (and its sleeve) of players who have lost it, both in the world and in first person.
 * The pose is set up on every render, so hiding the parts once it is done is enough.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	// never used: the mixin only needs the generic super class to reach getModel()
	protected PlayerEntityRendererMixin(EntityRendererFactory.Context context, PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	@Inject(method = "setModelPose", at = @At("TAIL"))
	private void charter$hideSeveredArm(AbstractClientPlayerEntity player, CallbackInfo ci) {
		if (CharterClientState.isSevered(player.getUuid())) {
			PlayerEntityModel<AbstractClientPlayerEntity> model = this.getModel();
			model.leftArm.visible = false;
			model.leftSleeve.visible = false;
		}
	}
}
