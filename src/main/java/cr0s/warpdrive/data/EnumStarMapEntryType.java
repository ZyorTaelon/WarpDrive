package cr0s.warpdrive.data;

import net.minecraft.util.IStringSerializable;

import javax.annotation.Nonnull;
import java.util.HashMap;

public enum EnumStarMapEntryType implements IStringSerializable {
	
	UNDEFINED  (0, "-undefined-", true ),
	SHIP       (1, "ship"       , true ), // a ship core
	JUMPGATE   (2, "jumpgate"   , true ), // a jump gate
	PLANET     (3, "planet"     , true ), // a planet (a transition plane allowing to move to another dimension)
	STAR       (4, "star"       , true ), // a star
	STRUCTURE  (5, "structure"  , true ), // a structure from WorldGeneration (moon, asteroid field, etc.)
	WARP_ECHO  (6, "warp_echo"  , true ), // remains of a warp
	ACCELERATOR(7, "accelerator", false), // an accelerator setup
	TRANSPORTER(8, "transporter", true ); // a transporter room
	
	private final int id;
	private final String name;
	private final boolean hasRadarEcho;
	
	// cached values
	public static final int length;
	private static final HashMap<String, EnumStarMapEntryType> mapNames = new HashMap<>();
	
	static {
		length = EnumStarMapEntryType.values().length;
		for (final EnumStarMapEntryType enumStarMapEntryType : values()) {
			mapNames.put(enumStarMapEntryType.getName(), enumStarMapEntryType);
		}
	}
	
	EnumStarMapEntryType(final int id, final String name, final boolean hasRadarEcho) {
		this.id = id;
		this.name = name;
		this.hasRadarEcho = hasRadarEcho;
	}
	
	public int getId() {
		return id;
	}
	
	@Nonnull
	@Override
	public String getName() {
		return name;
	}
	
	public static EnumStarMapEntryType getByName(final String name) {
		return mapNames.get(name);
	}
	
	public boolean hasRadarEcho() {
		return hasRadarEcho;
	}
}
