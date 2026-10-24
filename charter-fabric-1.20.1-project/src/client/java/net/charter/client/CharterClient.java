package net.charter.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.charter.net.CharterNetworking;
import net.charter.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class CharterClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.CHARTER_FX, CharterFxRenderer::new);

		// the server tells us who has lost their left arm
		ClientPlayNetworking.registerGlobalReceiver(CharterNetworking.SEVERED, (client, handler, buf, responseSender) -> {
			int count = buf.readVarInt();
			Set<UUID> ids = new HashSet<>();
			for (int i = 0; i < count; i++) {
				ids.add(buf.readUuid());
			}
			client.execute(() -> CharterClientState.setSevered(ids));
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CharterClientState.clear());
	}
}
