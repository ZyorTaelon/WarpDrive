package cr0s.warpdrive.config.structures;

import cr0s.warpdrive.config.InvalidXmlException;
import org.w3c.dom.Element;

import java.util.Random;

import net.minecraft.world.World;

public class StructureReference extends AbstractStructure {
	
	public StructureReference(final String group, final String name) {
		super(group, name);
	}
	
	@Override
	public boolean loadFromXmlElement(final Element element) throws InvalidXmlException {
		super.loadFromXmlElement(element);
		
		return true;
	}
	
	@Override
	public boolean generate(final World world, final Random random, final int x, final int y, final int z) {
		return instantiate(random).generate(world, random, x, y, z);
	}
	
	@Override
	public AbstractStructureInstance instantiate(final Random random) {
		return StructureManager.getStructure(random, group, name).instantiate(random);
	}
}
