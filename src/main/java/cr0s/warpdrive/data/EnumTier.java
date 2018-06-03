package cr0s.warpdrive.data;

import javax.annotation.Nonnull;
import java.util.HashMap;

import net.minecraft.util.IStringSerializable;

public enum EnumTier implements IStringSerializable {
	
	CREATIVE ("creative",  0),
	BASIC    ("basic"   ,  1),
	ADVANCED ("advanced",  2),
	SUPERIOR ("superior",  3);
	
	private final String name;
	private final int index;
	
	// cached values
	public static final int length;
	private static final HashMap<Integer, EnumTier> ID_MAP = new HashMap<>();
	
	static {
		length = EnumTier.values().length;
		for (final EnumTier tier : values()) {
			ID_MAP.put(tier.index, tier);
		}
	}
	
	EnumTier(final String name, final int index) {
		this.name = name;
		this.index = index;
	}
	
	@Nonnull
	@Override
	public String getName() {
		return name;
	}
	
	public static EnumTier get(final int index) {
		return ID_MAP.get(index);
	}
	
	public int getIndex() {
		return index;
	}
}
