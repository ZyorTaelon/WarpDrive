package cr0s.warpdrive.data;

import javax.annotation.Nonnull;
import java.util.HashMap;

import net.minecraft.util.IStringSerializable;

public enum EnumReactorReleaseMode implements IStringSerializable {
	
	OFF        ("off"),
	UNLIMITED  ("unlimited"),
	ABOVE      ("above"),
	AT_RATE    ("at_rate");
	
	private final String name;
	
	// cached values
	public static final int length;
	private static final HashMap<Integer, EnumReactorReleaseMode> ID_MAP = new HashMap<>();
	
	static {
		length = EnumReactorReleaseMode.values().length;
		for (final EnumReactorReleaseMode reactorReleaseMode : values()) {
			ID_MAP.put(reactorReleaseMode.ordinal(), reactorReleaseMode);
		}
	}
	
	EnumReactorReleaseMode(final String name) {
		this.name = name;
	}
	
	public static EnumReactorReleaseMode get(final int ordinal) {
		return ID_MAP.get(ordinal);
	}
	
	@Nonnull
	@Override
	public String getName() { return name; }
}
