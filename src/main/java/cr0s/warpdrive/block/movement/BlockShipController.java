package cr0s.warpdrive.block.movement;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.block.BlockAbstractContainer;
import cr0s.warpdrive.data.EnumShipControllerCommand;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.registry.GameRegistry;

public class BlockShipController extends BlockAbstractContainer {
	
	public static final PropertyEnum<EnumShipControllerCommand> COMMAND = PropertyEnum.create("command", EnumShipControllerCommand.class);
	
	public BlockShipController(final String registryName) {
		super(registryName, Material.IRON);
		setUnlocalizedName("warpdrive.movement.ship_controller");
		
		setDefaultState(getDefaultState()
				                .withProperty(COMMAND, EnumShipControllerCommand.OFFLINE)
		               );
		GameRegistry.registerTileEntity(TileEntityShipController.class, WarpDrive.PREFIX + registryName);
	}
	
	@Nonnull
	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, COMMAND);
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState getStateFromMeta(final int metadata) {
		return getDefaultState()
				       .withProperty(COMMAND, EnumShipControllerCommand.get(metadata));
	}
	
	@Override
	public int getMetaFromState(final IBlockState blockState) {
		return blockState.getValue(COMMAND).ordinal();
	}
	
	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull final World world, final int metadata) {
		return new TileEntityShipController();
	}
	
	@Override
	public boolean onBlockActivated(final World world, final BlockPos blockPos, final IBlockState blockState,
	                                final EntityPlayer entityPlayer, final EnumHand hand, @Nullable final ItemStack itemStackHeld,
	                                final EnumFacing side, final float hitX, final float hitY, final float hitZ) {
		if (world.isRemote) {
			return false;
		}
		
		if (hand != EnumHand.MAIN_HAND) {
			return true;
		}
		
		if (itemStackHeld == null) {
			final TileEntity tileEntity = world.getTileEntity(blockPos);
			if (tileEntity instanceof TileEntityShipController) {
				if (entityPlayer.isSneaking()) {
					Commons.addChatMessage(entityPlayer, ((TileEntityShipController) tileEntity).getStatus());
				} else {
					Commons.addChatMessage(entityPlayer, ((TileEntityShipController) tileEntity).attachPlayer(entityPlayer));
				}
				return true;
			}
		}
		
		return false;
	}
}