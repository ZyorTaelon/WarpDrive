package cr0s.warpdrive.data;

import net.minecraft.util.IStringSerializable;

import javax.annotation.Nonnull;
import java.util.HashMap;

public enum EnumDecorativeType implements IStringSerializable {
	
	PLAIN              ("plain"),
	ENERGIZED          ("energized"),
	NETWORK            ("network");
	
	private final String name;
	
	// cached values
	public static final int length;
	private static final HashMap<Integer, EnumDecorativeType> ID_MAP = new HashMap<>();
	
	static {
		length = EnumDecorativeType.values().length;
		for (final EnumDecorativeType decorativeType : values()) {
			ID_MAP.put(decorativeType.ordinal(), decorativeType);
		}
	}
	
	EnumDecorativeType(final String name) {
		this.name = name;
	}
	
	public static EnumDecorativeType get(final int damage) {
		return ID_MAP.get(damage);
	}
	
	@Nonnull
	@Override
	public String getName() {
	 return name;
	}
}
