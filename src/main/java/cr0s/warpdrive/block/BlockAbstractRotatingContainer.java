package cr0s.warpdrive.block;

import cr0s.warpdrive.data.BlockProperties;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public abstract class BlockAbstractRotatingContainer extends BlockAbstractContainer {
	
	protected BlockAbstractRotatingContainer(final String registryName, final Material material) {
		super(registryName, material);
		
		setDefaultState(blockState.getBaseState()
		                .withProperty(BlockProperties.FACING, EnumFacing.NORTH));
	}
	
	@Nonnull
	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, BlockProperties.FACING);
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState getStateFromMeta(final int metadata) {
		return getDefaultState()
				.withProperty(BlockProperties.FACING, EnumFacing.getFront(metadata & 7));
	}
	
	@Override
	public int getMetaFromState(final IBlockState blockState) {
		return blockState.getValue(BlockProperties.FACING).getIndex();
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState onBlockPlaced(final World worldIn, final BlockPos pos, final EnumFacing facing, final float hitX, final float hitY, final float hitZ, final int meta, final EntityLivingBase entityLiving) {
		final EnumFacing enumFacing = BlockAbstractBase.getFacingFromEntity(pos, entityLiving);
		return this.getDefaultState().withProperty(BlockProperties.FACING, enumFacing);
	}
}
