package cr0s.warpdrive.block.detection;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IVideoChannel;
import cr0s.warpdrive.block.BlockAbstractRotatingContainer;
import cr0s.warpdrive.data.CameraRegistryItem;
import cr0s.warpdrive.render.ClientCameraHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.registry.GameRegistry;

public class BlockMonitor extends BlockAbstractRotatingContainer {
	
	public BlockMonitor(final String registryName) {
		super(registryName, Material.IRON);
		setUnlocalizedName("warpdrive.detection.monitor");
		GameRegistry.registerTileEntity(TileEntityMonitor.class, WarpDrive.PREFIX + registryName);
	}

	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull final World world, final int metadata) {
		return new TileEntityMonitor();
	}

	@Override
	public boolean onBlockActivated(final World world, final BlockPos blockPos, final IBlockState blockState,
	                                final EntityPlayer entityPlayer, final EnumHand hand, @Nullable final ItemStack itemStackHeld,
	                                final EnumFacing side, final float hitX, final float hitY, final float hitZ) {
		// Monitor is only reacting client side
		if (!world.isRemote) {
			return false;
		}
		
		if (hand != EnumHand.MAIN_HAND) {
			return true;
		}
		
		if (itemStackHeld == null) {
			final TileEntity tileEntity = world.getTileEntity(blockPos);
			
			if (tileEntity instanceof TileEntityMonitor) {
				// validate video channel
				final int videoChannel = ((TileEntityMonitor) tileEntity).getVideoChannel();
				if ( videoChannel < IVideoChannel.VIDEO_CHANNEL_MIN
				  || videoChannel > IVideoChannel.VIDEO_CHANNEL_MAX ) {
					Commons.addChatMessage(entityPlayer, ((TileEntityMonitor) tileEntity).getStatus());
					return true;
				}
				
				// validate camera
				final CameraRegistryItem camera = WarpDrive.cameras.getCameraByVideoChannel(world, videoChannel);
				if ( camera == null
				  || entityPlayer.isSneaking() ) {
					Commons.addChatMessage(entityPlayer, ((TileEntityMonitor) tileEntity).getStatus());
					return true;
				}
				
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.monitor.viewing_camera",
						videoChannel,
						camera.position.getX(),
						camera.position.getY(),
						camera.position.getZ() ));
				ClientCameraHandler.setupViewpoint(
						camera.type, entityPlayer, entityPlayer.rotationYaw, entityPlayer.rotationPitch,
						blockPos, blockState,
						camera.position, world.getBlockState(camera.position));
			}
		}
		
		return false;
	}
}