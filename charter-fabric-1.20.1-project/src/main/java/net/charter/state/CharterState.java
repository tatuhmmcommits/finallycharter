package net.charter.state;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * World-wide saved data of the mod (stored in {@code data/charter_state.dat} of the overworld):
 * who is bound by a contract, who has lost an arm, who is exiled and who Charter has judged.
 */
public final class CharterState extends PersistentState {
	private static final String ID = "charter_state";

	/** Where an exiled player came from, so the exile can end when they walk home. */
	public static final class ExileRecord {
		public String name = "";
		public String originDimension = "minecraft:overworld";
		public double originX;
		public double originY;
		public double originZ;
		public float originYaw;
		public float originPitch;
		public String destDimension = "minecraft:overworld";
		public double destX;
		public double destZ;
		public long since;

		NbtCompound toNbt(UUID id) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Id", id);
			tag.putString("Name", name);
			tag.putString("OriginDim", originDimension);
			tag.putDouble("OriginX", originX);
			tag.putDouble("OriginY", originY);
			tag.putDouble("OriginZ", originZ);
			tag.putFloat("OriginYaw", originYaw);
			tag.putFloat("OriginPitch", originPitch);
			tag.putString("DestDim", destDimension);
			tag.putDouble("DestX", destX);
			tag.putDouble("DestZ", destZ);
			tag.putLong("Since", since);
			return tag;
		}

		static ExileRecord fromNbt(NbtCompound tag) {
			ExileRecord record = new ExileRecord();
			record.name = tag.getString("Name");
			record.originDimension = tag.getString("OriginDim");
			record.originX = tag.getDouble("OriginX");
			record.originY = tag.getDouble("OriginY");
			record.originZ = tag.getDouble("OriginZ");
			record.originYaw = tag.getFloat("OriginYaw");
			record.originPitch = tag.getFloat("OriginPitch");
			record.destDimension = tag.getString("DestDim");
			record.destX = tag.getDouble("DestX");
			record.destZ = tag.getDouble("DestZ");
			record.since = tag.getLong("Since");
			return record;
		}
	}

	private final Map<UUID, String> bound = new LinkedHashMap<>();
	private final Set<UUID> severed = new LinkedHashSet<>();
	private final Map<UUID, ExileRecord> exiled = new LinkedHashMap<>();
	private final Map<UUID, String> judged = new LinkedHashMap<>();
	private final Set<UUID> pendingExile = new LinkedHashSet<>();

	public static CharterState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(CharterState::fromNbt, CharterState::new, ID);
	}

	// ------------------------------------------------------------------ bound (contract signed)
	public boolean isBound(UUID id) {
		return bound.containsKey(id);
	}

	public void bind(UUID id, String name) {
		bound.put(id, name);
		markDirty();
	}

	public boolean unbind(UUID id) {
		boolean had = bound.remove(id) != null;
		if (had) {
			markDirty();
		}
		return had;
	}

	public Map<UUID, String> boundPlayers() {
		return Collections.unmodifiableMap(bound);
	}

	// ------------------------------------------------------------------ severed (left arm lost)
	public boolean isSevered(UUID id) {
		return severed.contains(id);
	}

	public boolean sever(UUID id) {
		boolean added = severed.add(id);
		if (added) {
			markDirty();
		}
		return added;
	}

	public boolean restore(UUID id) {
		boolean removed = severed.remove(id);
		if (removed) {
			markDirty();
		}
		return removed;
	}

	public Set<UUID> severedPlayers() {
		return Collections.unmodifiableSet(severed);
	}

	// ------------------------------------------------------------------ exiled
	public boolean isExiled(UUID id) {
		return exiled.containsKey(id);
	}

	public ExileRecord exileRecord(UUID id) {
		return exiled.get(id);
	}

	public void putExile(UUID id, ExileRecord record) {
		exiled.put(id, record);
		markDirty();
	}

	public ExileRecord removeExile(UUID id) {
		ExileRecord record = exiled.remove(id);
		if (record != null) {
			markDirty();
		}
		return record;
	}

	public Map<UUID, ExileRecord> exiledPlayers() {
		return Collections.unmodifiableMap(exiled);
	}

	/** Players who were judged to exile while offline; the exile happens when they next join. */
	public boolean isPendingExile(UUID id) {
		return pendingExile.contains(id);
	}

	public void setPendingExile(UUID id, boolean pending) {
		boolean changed = pending ? pendingExile.add(id) : pendingExile.remove(id);
		if (changed) {
			markDirty();
		}
	}

	// ------------------------------------------------------------------ judged (permanently banned)
	public boolean isJudged(UUID id) {
		return judged.containsKey(id);
	}

	public void judge(UUID id, String name) {
		judged.put(id, name);
		markDirty();
	}

	public boolean pardon(UUID id) {
		boolean had = judged.remove(id) != null;
		if (had) {
			markDirty();
		}
		return had;
	}

	public Map<UUID, String> judgedPlayers() {
		return Collections.unmodifiableMap(judged);
	}

	// ------------------------------------------------------------------ saving
	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList boundList = new NbtList();
		for (Map.Entry<UUID, String> entry : bound.entrySet()) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Id", entry.getKey());
			tag.putString("Name", entry.getValue());
			boundList.add(tag);
		}
		nbt.put("Bound", boundList);

		NbtList severedList = new NbtList();
		for (UUID id : severed) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Id", id);
			severedList.add(tag);
		}
		nbt.put("Severed", severedList);

		NbtList exiledList = new NbtList();
		for (Map.Entry<UUID, ExileRecord> entry : exiled.entrySet()) {
			exiledList.add(entry.getValue().toNbt(entry.getKey()));
		}
		nbt.put("Exiled", exiledList);

		NbtList judgedList = new NbtList();
		for (Map.Entry<UUID, String> entry : judged.entrySet()) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Id", entry.getKey());
			tag.putString("Name", entry.getValue());
			judgedList.add(tag);
		}
		nbt.put("Judged", judgedList);

		NbtList pendingList = new NbtList();
		for (UUID id : pendingExile) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Id", id);
			pendingList.add(tag);
		}
		nbt.put("PendingExile", pendingList);
		return nbt;
	}

	public static CharterState fromNbt(NbtCompound nbt) {
		CharterState state = new CharterState();
		NbtList boundList = nbt.getList("Bound", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < boundList.size(); i++) {
			NbtCompound tag = boundList.getCompound(i);
			if (tag.containsUuid("Id")) {
				state.bound.put(tag.getUuid("Id"), tag.getString("Name"));
			}
		}
		NbtList severedList = nbt.getList("Severed", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < severedList.size(); i++) {
			NbtCompound tag = severedList.getCompound(i);
			if (tag.containsUuid("Id")) {
				state.severed.add(tag.getUuid("Id"));
			}
		}
		NbtList exiledList = nbt.getList("Exiled", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < exiledList.size(); i++) {
			NbtCompound tag = exiledList.getCompound(i);
			if (tag.containsUuid("Id")) {
				state.exiled.put(tag.getUuid("Id"), ExileRecord.fromNbt(tag));
			}
		}
		NbtList judgedList = nbt.getList("Judged", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < judgedList.size(); i++) {
			NbtCompound tag = judgedList.getCompound(i);
			if (tag.containsUuid("Id")) {
				state.judged.put(tag.getUuid("Id"), tag.getString("Name"));
			}
		}
		NbtList pendingList = nbt.getList("PendingExile", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < pendingList.size(); i++) {
			NbtCompound tag = pendingList.getCompound(i);
			if (tag.containsUuid("Id")) {
				state.pendingExile.add(tag.getUuid("Id"));
			}
		}
		return state;
	}
}
