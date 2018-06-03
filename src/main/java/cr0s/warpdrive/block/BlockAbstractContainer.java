package cr0s.warpdrive.block;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBlockBase;
import cr0s.warpdrive.api.IBlockUpdateDetector;
import cr0s.warpdrive.api.IVideoChannel;
import cr0s.warpdrive.client.ClientProxy;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.EnumComponentType;
import cr0s.warpdrive.item.ItemComponent;
import cr0s.warpdrive.data.BlockProperties;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Optional.InterfaceList({
	@Optional.Interface(iface = "defense.api.IEMPBlock", modid = "DefenseTech"),
	@Optional.Interface(iface = "resonant.api.explosion.IEMPBlock", modid = "icbmclassic")
})
public abstract class BlockAbstractContainer extends BlockContainer implements IBlockBase, defense.api.IEMPBlock, resonant.api.explosion.IEMPBlock {
	
	protected boolean hasSubBlocks = false;
	private static boolean isInvalidEMPreported = false;
	
	protected BlockAbstractContainer(final String registryName, final Material material) {
		super(material);
		setHardness(5.0F);
		setResistance(6.0F * 5 / 3);
		setSoundType(SoundType.METAL);
		setCreativeTab(WarpDrive.creativeTabWarpDrive);
		setRegistryName(registryName);
		WarpDrive.register(this);
		
		setDefaultState(blockState.getBaseState());
	}
	
	@Nullable
	@Override
	public ItemBlock createItemBlock() {
		return new ItemBlockAbstractBase(this);
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public void modelInitialisation() {
		final Item item = Item.getItemFromBlock(this);
		ClientProxy.modelInitialisation(item);
	}
	
	@Nonnull
	@Override
	public EnumBlockRenderType getRenderType(final IBlockState state) {
		return EnumBlockRenderType.MODEL;
	}
	
	@Override
	public void onBlockAdded(final World world, final BlockPos pos, final IBlockState state) {
		super.onBlockAdded(world, pos, state);
		final TileEntity tileEntity = world.getTileEntity(pos);
		if (tileEntity instanceof IBlockUpdateDetector) {
			((IBlockUpdateDetector) tileEntity).onBlockUpdateDetected();
		}
	}
	
	@Override
	public void onBlockPlacedBy(final World world, final BlockPos blockPos, final IBlockState blockState,
	                            final EntityLivingBase entityLiving, final ItemStack itemStack) {
		super.onBlockPlacedBy(world, blockPos, blockState, entityLiving, itemStack);
		final boolean isRotating = blockState.getProperties().containsKey(BlockProperties.FACING);
		if (isRotating) {
			final EnumFacing enumFacing = BlockAbstractBase.getFacingFromEntity(blockPos, entityLiving);
			world.setBlockState(blockPos, blockState.withProperty(BlockProperties.FACING, enumFacing));
		}
		
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (tileEntity != null && itemStack.getTagCompound() != null) {
			final NBTTagCompound tagCompound = itemStack.getTagCompound().copy();
			tagCompound.setInteger("x", blockPos.getX());
			tagCompound.setInteger("y", blockPos.getY());
			tagCompound.setInteger("z", blockPos.getZ());
			tileEntity.readFromNBT(tagCompound);
			world.notifyBlockUpdate(blockPos, blockState, blockState, 3);
		}
	}
	
	@Override
	public boolean removedByPlayer(@Nonnull final IBlockState blockState, final World world, @Nonnull final BlockPos blockPos,
	                               @Nonnull final EntityPlayer player, final boolean willHarvest) {
		return willHarvest || super.removedByPlayer(blockState, world, blockPos, player, false);
	}
	
	@Override
	public void dropBlockAsItemWithChance(final World world, @Nonnull final BlockPos blockPos, @Nonnull final IBlockState blockState, final float chance, final int fortune) {
		final ItemStack itemStack = new ItemStack(this);
		itemStack.setItemDamage(damageDropped(blockState));
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (tileEntity == null) {
			WarpDrive.logger.error("Missing tile entity for " + this + " at " + world + " " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ());
		} else if (tileEntity instanceof TileEntityAbstractBase) {
			final NBTTagCompound tagCompound = new NBTTagCompound();
			((TileEntityAbstractBase) tileEntity).writeItemDropNBT(tagCompound);
			itemStack.setTagCompound(tagCompound);
		}
		world.setBlockToAir(blockPos);
		super.dropBlockAsItemWithChance(world, blockPos, blockState, chance, fortune);
	}
	
	@Nonnull
	@Override
	public ItemStack getPickBlock(@Nonnull final IBlockState state, final RayTraceResult target, @Nonnull final World world, @Nonnull final BlockPos blockPos, final EntityPlayer entityPlayer) {
		final ItemStack itemStack = super.getPickBlock(state, target, world, blockPos, entityPlayer);
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		final NBTTagCompound tagCompound = new NBTTagCompound();
		if (tileEntity instanceof TileEntityAbstractBase) {
			((TileEntityAbstractBase) tileEntity).writeItemDropNBT(tagCompound);
			itemStack.setTagCompound(tagCompound);
		}
		return itemStack;
	}
	
	@Override
	public boolean rotateBlock(final World world, @Nonnull final BlockPos blockPos, final EnumFacing axis) {
		// already handled by vanilla
		return super.rotateBlock(world, blockPos, axis);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void neighborChanged(final IBlockState blockState, final World world, final BlockPos blockPos, final Block block) {
		super.neighborChanged(blockState, world, blockPos, block);
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (tileEntity instanceof IBlockUpdateDetector) {
			((IBlockUpdateDetector) tileEntity).onBlockUpdateDetected();
		}
	}
	
	@Override
	@Optional.Method(modid = "DefenseTech")
	public void onEMP(final World world, final int x, final int y, final int z, final defense.api.IExplosion explosiveEMP) {
		if (WarpDriveConfig.LOGGING_WEAPON) {
			WarpDrive.logger.info(String.format("EMP received @ %s (%d %d %d) from %s with energy %d and radius %.1f",
			                                    world.provider.getSaveFolder(), x, y, z,
			                                    explosiveEMP, explosiveEMP.getEnergy(), explosiveEMP.getRadius()));
		}
		// EMP tower = 3k Energy, 60 radius
		// EMP explosive = 3k Energy, 50 radius
		if (explosiveEMP.getRadius() == 60.0F) {// compensate tower stacking effect
			onEMP(world, new BlockPos(x, y, z), 0.02F);
		} else if (explosiveEMP.getRadius() == 50.0F) {
			onEMP(world, new BlockPos(x, y, z), 0.70F);
		} else {
			if (!isInvalidEMPreported) {
				isInvalidEMPreported = true;
				WarpDrive.logger.warn(String.format("EMP received @ %s (%d %d %d) from %s with energy %d and unsupported radius %.1f",
			                                      world.provider.getSaveFolder(), x, y, z,
				                                    explosiveEMP, explosiveEMP.getEnergy(), explosiveEMP.getRadius()));
				Commons.dumpAllThreads();
			}
			onEMP(world, new BlockPos(x, y, z), 0.02F);
		}
	}
	
	@Override
	@Optional.Method(modid = "icbmclassic")
	public void onEMP(final World world, final int x, final int y, final int z, final resonant.api.explosion.IExplosion explosiveEMP) {
		if (WarpDriveConfig.LOGGING_WEAPON) {
			WarpDrive.logger.info(String.format("EMP received @ %s (%d %d %d) from %s with energy %d and radius %.1f",
			                                    world.provider.getSaveFolder(), x, y, z,
			                                    explosiveEMP, explosiveEMP.getEnergy(), explosiveEMP.getRadius()));
		}
		// EMP tower = 3k Energy, 60 radius
		// EMP explosive = 3k Energy, 50 radius
		if (explosiveEMP.getRadius() == 60.0F) {// compensate tower stacking effect
			onEMP(world, new BlockPos(x, y, z), 0.02F);
		} else if (explosiveEMP.getRadius() == 50.0F) {
			onEMP(world, new BlockPos(x, y, z), 0.70F);
		} else {
			if (!isInvalidEMPreported) {
				isInvalidEMPreported = true;
				WarpDrive.logger.warn(String.format("EMP received @ %s (%d %d %d) from %s with energy %d and unsupported radius %.1f",
			                                      world.provider.getSaveFolder(), x, y, z,
				                                    explosiveEMP, explosiveEMP.getEnergy(), explosiveEMP.getRadius()));
				Commons.dumpAllThreads();
			}
			onEMP(world, new BlockPos(x, y, z), 0.02F);
		}
	}
	
	public void onEMP(final World world, final BlockPos blockPos, final float efficiency) {
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (tileEntity instanceof TileEntityAbstractEnergy) {
			final TileEntityAbstractEnergy tileEntityAbstractEnergy = (TileEntityAbstractEnergy) tileEntity;
			if (tileEntityAbstractEnergy.energy_getMaxStorage() > 0) {
				tileEntityAbstractEnergy.energy_consume(Math.round(tileEntityAbstractEnergy.energy_getEnergyStored() * efficiency), false);
			}
		}
	}
	
	@Override
	public byte getTier(final ItemStack itemStack) {
		return 1;
	}
	
	@Override
	public EnumRarity getRarity(final ItemStack itemStack, final EnumRarity rarity) {
		switch (getTier(itemStack)) {
			case 0:	return EnumRarity.EPIC;
			case 1:	return EnumRarity.COMMON;
			case 2:	return EnumRarity.UNCOMMON;
			case 3:	return EnumRarity.RARE;
			default: return rarity;
		}
	}
	
	@Override
	public boolean onBlockActivated(final World world, final BlockPos blockPos, final IBlockState blockState,
	                                final EntityPlayer entityPlayer, final EnumHand hand, @Nullable final ItemStack itemStackHeld,
	                                final EnumFacing side, final float hitX, final float hitY, final float hitZ) {
		if (hand != EnumHand.MAIN_HAND) {
			return true;
		}
		
		// get context
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (!(tileEntity instanceof TileEntityAbstractBase)) {
			return false;
		}
		final TileEntityAbstractBase tileEntityAbstractBase = (TileEntityAbstractBase) tileEntity;
		final boolean hasVideoChannel = tileEntity instanceof IVideoChannel;
		
		// video channel is reported client side, everything else is reported server side
		if ( world.isRemote
		  && !hasVideoChannel ) {
			return false;
		}
		
		EnumComponentType enumComponentType = null;
		if ( itemStackHeld != null
		  && itemStackHeld.getItem() instanceof ItemComponent ) {
			enumComponentType = EnumComponentType.get(itemStackHeld.getItemDamage());
		}
		
		// sneaking with an empty hand or an upgrade item in hand to dismount current upgrade
		if ( !world.isRemote
		  && entityPlayer.isSneaking() ) {
			// using an upgrade item or an empty hand means dismount upgrade
			if ( tileEntityAbstractBase.isUpgradeable()
			  && ( itemStackHeld == null
			    || enumComponentType != null ) ) {
				// find a valid upgrade to dismount
				if ( itemStackHeld == null
				  || !tileEntityAbstractBase.hasUpgrade(enumComponentType) ) {
					enumComponentType = (EnumComponentType) tileEntityAbstractBase.getFirstUpgradeOfType(EnumComponentType.class, null);
				}
				
				if (enumComponentType == null) {
					// no more upgrades to dismount
					Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.no_upgrade_to_dismount"));
					return true;
				}
				
				if (!entityPlayer.capabilities.isCreativeMode) {
					// dismount the current upgrade item
					final ItemStack itemStackDrop = ItemComponent.getItemStackNoCache(enumComponentType, 1);
					final EntityItem entityItem = new EntityItem(world, entityPlayer.posX, entityPlayer.posY + 0.5D, entityPlayer.posZ, itemStackDrop);
					entityItem.setNoPickupDelay();
					world.spawnEntityInWorld(entityItem);
				}
				
				tileEntityAbstractBase.dismountUpgrade(enumComponentType);
				// upgrade dismounted
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.dismounted", enumComponentType.name()));
				return true;
			}
			
		} else if ( !entityPlayer.isSneaking()
		         && itemStackHeld == null ) {// no sneaking and no item in hand => show status
			Commons.addChatMessage(entityPlayer, tileEntityAbstractBase.getStatus());
			return true;
			
		} else if ( !world.isRemote
		         && tileEntityAbstractBase.isUpgradeable()
		         && enumComponentType != null ) {// no sneaking and an upgrade in hand => mounting an upgrade
			// validate type
			if (tileEntityAbstractBase.getUpgradeMaxCount(enumComponentType) <= 0) {
				// invalid upgrade type
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.invalid_upgrade"));
				return true;
			}
			if (!tileEntityAbstractBase.canUpgrade(enumComponentType)) {
				// too many upgrades
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.too_many_upgrades",
				                                                                             tileEntityAbstractBase.getUpgradeMaxCount(enumComponentType)));
				return true;
			}
			
			if (!entityPlayer.capabilities.isCreativeMode) {
				// validate quantity
				if (itemStackHeld.stackSize < 1) {
					// not enough upgrade items
					Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.not_enough_upgrades"));
					return true;
				}
				
				// update player inventory
				itemStackHeld.stackSize -= 1;
			}
			
			// mount the new upgrade item
			tileEntityAbstractBase.mountUpgrade(enumComponentType);
			// upgrade mounted
			Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.mounted", enumComponentType.name()));
			return true;
		}
		
		return false;
	}
}
