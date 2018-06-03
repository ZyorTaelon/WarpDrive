package cr0s.warpdrive.block.atomic;

import cr0s.warpdrive.api.IBlockBase;
import cr0s.warpdrive.block.BlockAbstractBase;

import javax.annotation.Nonnull;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class BlockAbstractAccelerator extends BlockAbstractBase implements IBlockBase {
	
	public final byte tier;
	
	BlockAbstractAccelerator(final String registryName, final byte tier) {
		super(registryName, Material.IRON);
		this.tier = tier;
		setHardness(4 + tier);
		setResistance((2 + 2 * tier) * 5 / 3);
	}
	
	@Override
	public byte getTier(final ItemStack itemStack) {
		return tier;
	}
	
	@Override
	public boolean canCreatureSpawn(@Nonnull IBlockState blockState, @Nonnull IBlockAccess world, @Nonnull BlockPos blockPos, SpawnPlacementType type) {
		return false;
	}
}
