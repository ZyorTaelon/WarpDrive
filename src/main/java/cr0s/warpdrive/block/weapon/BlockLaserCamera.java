package cr0s.warpdrive.block.weapon;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.block.BlockAbstractContainer;
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

public class BlockLaserCamera extends BlockAbstractContainer {
	
	public BlockLaserCamera(final String registryName) {
		super(registryName, Material.IRON);
		setHardness(50.0F);
		setResistance(20.0F * 5 / 3);
		setUnlocalizedName("warpdrive.weapon.LaserCamera");
		GameRegistry.registerTileEntity(TileEntityLaserCamera.class, WarpDrive.PREFIX + registryName);
	}
	
	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull final World world, final int metadata) {
		return new TileEntityLaserCamera();
	}
	
	@Override
	public boolean isVisuallyOpaque() {
		return false;
	}
	
	@Override
	public boolean onBlockActivated(final World world, final BlockPos blockPos, final IBlockState blockState,
	                                final EntityPlayer entityPlayer, final EnumHand hand, @Nullable final ItemStack itemStackHeld,
	                                final EnumFacing side, final float hitX, final float hitY, final float hitZ) {
		if (hand != EnumHand.MAIN_HAND) {
			return true;
		}
		
		if (itemStackHeld == null) {
			final TileEntity tileEntity = world.getTileEntity(blockPos);
			if (!ClientCameraHandler.isOverlayEnabled) {
				if (tileEntity instanceof TileEntityLaserCamera) {
					Commons.addChatMessage(entityPlayer, ((TileEntityLaserCamera) tileEntity).getStatus());
				} else {
					Commons.addChatMessage(entityPlayer, Commons.getChatPrefix(this)
						.appendSibling(new TextComponentTranslation("warpdrive.error.bad_tile_entity")));
					WarpDrive.logger.error("Block " + this + " with invalid tile entity " + tileEntity);
				}
				return false;
			}
		}
		
		return false;
	}
}