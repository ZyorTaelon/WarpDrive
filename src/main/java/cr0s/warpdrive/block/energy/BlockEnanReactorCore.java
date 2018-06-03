package cr0s.warpdrive.block.energy;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.block.BlockAbstractContainer;
import cr0s.warpdrive.data.EnumReactorFace;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class BlockEnanReactorCore extends BlockAbstractContainer {
	
	public static final PropertyInteger ENERGY = PropertyInteger.create("energy", 0, 3);
	public static final PropertyInteger INSTABILITY = PropertyInteger.create("stability", 0, 3);
	
	public BlockEnanReactorCore(final String registryName) {
		super(registryName, Material.IRON);
		setUnlocalizedName("warpdrive.energy.enan_reactor_core");
		
		setDefaultState(getDefaultState()
				                .withProperty(ENERGY, 0)
				                .withProperty(INSTABILITY, 0)
		               );
		GameRegistry.registerTileEntity(TileEntityEnanReactorCore.class, WarpDrive.PREFIX + registryName);
	}
	
	@Nonnull
	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, ENERGY, INSTABILITY);
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState getStateFromMeta(final int metadata) {
		return getDefaultState()
				       .withProperty(ENERGY, metadata & 0x3)
				       .withProperty(INSTABILITY, metadata >> 2);
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public int getMetaFromState(final IBlockState blockState) {
		return blockState.getValue(ENERGY) + (blockState.getValue(INSTABILITY) << 2);
	}
	
	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull final World world, final int metadata) {
		return new TileEntityEnanReactorCore();
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public void breakBlock(World world, @Nonnull BlockPos blockPos, @Nonnull IBlockState blockState) {
		super.breakBlock(world, blockPos, blockState);
		
		for (final EnumReactorFace reactorFace : EnumReactorFace.values()) {
			if (reactorFace.indexStability < 0) {
				continue;
			}
			
			final TileEntity tileEntity = world.getTileEntity(blockPos);
			if (tileEntity instanceof TileEntityEnanReactorLaser) {
				if (((TileEntityEnanReactorLaser) tileEntity).getReactorFace() == reactorFace) {
					((TileEntityEnanReactorLaser) tileEntity).setReactorFace(EnumReactorFace.UNKNOWN, null);
				}
			}
		}
	}
	
	@Override

	public byte getTier(final ItemStack itemStack) {
		return 3;
	}
}