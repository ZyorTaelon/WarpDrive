package cr0s.warpdrive.data;

import javax.annotation.Nonnull;
import java.util.HashMap;

import net.minecraft.util.IStringSerializable;

public enum EnumTransporterBeaconState implements IStringSerializable {
	
	// item form
	PACKED_INACTIVE   (0, "packed_inactive"),
	PACKED_ACTIVE     (1, "packed_active"),
	// block form
	DEPLOYED_INACTIVE (2, "deployed_inactive"),
	DEPLOYED_ACTIVE   (3, "deployed_active");
	
	private final int metadata;
	private final String name;
	
	// cached values
	public static final int length;
	private static final HashMap<Integer, EnumTransporterBeaconState> ID_MAP = new HashMap<>();
	
	static {
		length = EnumTransporterBeaconState.values().length;
		for (final EnumTransporterBeaconState transporterBeaconState : values()) {
			ID_MAP.put(transporterBeaconState.ordinal(), transporterBeaconState);
		}
	}
	
	EnumTransporterBeaconState(final int metadata, final String name) {
		this.metadata = metadata;
		this.name = name;
	}
	
	public int getMetadata() {
		return metadata;
	}
	
	public static EnumTransporterBeaconState get(final int id) {
		return ID_MAP.get(id);
	}
	
	@Nonnull
	@Override
	public String getName() { return name; }
}
