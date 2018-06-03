package cr0s.warpdrive.data;

import javax.annotation.Nonnull;
import java.util.HashMap;

import net.minecraft.util.IStringSerializable;

public enum EnumShipControllerCommand implements IStringSerializable {
	
	OFFLINE     ("offline"),      // Offline allows to move sub-ships
	IDLE        ("idle"),         //
	MANUAL      ("manual"),       // Move ship around including take off and landing
	// AUTOPILOT("autopilot"),    // Move ship towards a far destination
	SUMMON      ("summon"),       // Summoning crew
	HYPERDRIVE  ("hyperdrive"),   // Jump to/from Hyperspace
	GATE        ("gate"),         // Jump via jumpgate
	MAINTENANCE ("maintenance");  // Maintenance mode
	
	private final String name;
	
	// cached values
	public static final int length;
	private static final HashMap<Integer, EnumShipControllerCommand> ID_MAP = new HashMap<>();
	
	static {
		length = EnumShipControllerCommand.values().length;
		for (final EnumShipControllerCommand forceFieldShape : values()) {
			ID_MAP.put(forceFieldShape.ordinal(), forceFieldShape);
		}
	}
	
	EnumShipControllerCommand(final String name) {
		this.name = name;
	}
	
	public static EnumShipControllerCommand get(final int damage) {
		return ID_MAP.get(damage);
	}
	
	@Nonnull
	@Override
	public String getName() {
		return name;
	}
}
