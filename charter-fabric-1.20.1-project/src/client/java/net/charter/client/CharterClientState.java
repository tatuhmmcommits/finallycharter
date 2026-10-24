package net.charter.client;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/** What the client has been told by the server: which players have lost their left arm. */
public final class CharterClientState {
	private static volatile Set<UUID> severed = Collections.emptySet();

	private CharterClientState() {
	}

	public static boolean isSevered(UUID id) {
		return severed.contains(id);
	}

	public static void setSevered(Set<UUID> ids) {
		severed = Collections.unmodifiableSet(ids);
	}

	public static void clear() {
		severed = Collections.emptySet();
	}
}
