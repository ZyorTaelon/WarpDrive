package cr0s.warpdrive.block;

import cr0s.warpdrive.CommonProxy;
import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBeamFrequency;
import cr0s.warpdrive.api.IBlockUpdateDetector;
import cr0s.warpdrive.api.IVideoChannel;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.CameraRegistryItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class TileEntityAbstractBase extends TileEntity implements IBlockUpdateDetector, ITickable {
	
	private boolean isFirstTick = true;
	private boolean isDirty = false;
	
	protected void onFirstUpdateTick() {
		// No operation
	}
	
	@Override
	public void update() {
		if (isFirstTick) {
			isFirstTick = false;
			onFirstUpdateTick();
		}
		
		if (isDirty) {
			markDirty();
		}
	}
	
	protected boolean isFirstTick() {
		return isFirstTick;
	}
	
	@Override
	public void onBlockUpdateDetected() {
	}
	
	protected <T extends Comparable<T>, V extends T> void updateBlockState(final IBlockState blockState_in, IProperty<T> property, V value) {
		IBlockState blockState = blockState_in;
		if (blockState == null) {
			blockState = worldObj.getBlockState(pos);
		}
		try {
			if (property != null) {
				blockState = blockState.withProperty(property, value);
			}
			if (getBlockMetadata() != blockState.getBlock().getMetaFromState(blockState)) {
				worldObj.setBlockState(pos, blockState, 2);
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			WarpDrive.logger.error("Exception in " + this);
		}
	}
	
	protected void updateBlockState(final IBlockState blockState_in, @Nonnull final IBlockState blockState_new) {
		IBlockState blockState_old = blockState_in;
		if (blockState_old == null) {
			blockState_old = worldObj.getBlockState(pos);
		}
		try {
			final int metadata_old = blockState_old.getBlock().getMetaFromState(blockState_old);
			final int metadata_new = blockState_new.getBlock().getMetaFromState(blockState_new);
			if (metadata_old != metadata_new) {
				worldObj.setBlockState(pos, blockState_new, 2);
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			WarpDrive.logger.error("Exception in " + this);
		}
	}
	
	@Deprecated
	protected void updateMetadata(final int metadata) {
		if (getBlockMetadata() != metadata) {
			worldObj.setBlockState(pos, getBlockType().getStateFromMeta(metadata), 2);
		}
	}
	
	@Override
	public void markDirty() {
		if ( hasWorldObj()
		  && Commons.isSafeThread() ) {
			super.markDirty();
			isDirty = false;
			final IBlockState blockState = worldObj.getBlockState(pos);
			worldObj.notifyBlockUpdate(pos, blockState, blockState, 3);
			WarpDrive.starMap.onBlockUpdated(worldObj, pos, blockState);
		} else {
			isDirty = true;
		}
	}
	
	@Override
	public boolean shouldRefresh(World world, BlockPos pos, @Nonnull IBlockState blockStateOld, @Nonnull IBlockState blockStateNew) {
		return blockStateOld.getBlock() != blockStateNew.getBlock();
	}
	
	// Inventory management methods
	protected boolean addToConnectedInventories(final ItemStack itemStack) {
		final List<ItemStack> itemStacks = new ArrayList<>(1);
		itemStacks.add(itemStack);
		return addToInventories(itemStacks, Commons.getConnectedInventories(this));
	}
	
	protected boolean addToConnectedInventories(final List<ItemStack> itemStacks) {
		return addToInventories(itemStacks, Commons.getConnectedInventories(this));
	}
	
	protected boolean addToInventories(final List<ItemStack> itemStacks, final Collection<IInventory> inventories) {
		boolean overflow = false;
		if (itemStacks != null) {
			for (final ItemStack itemStack : itemStacks) {
				if (itemStack.getItem() == null) {
					WarpDrive.logger.error(this + "Invalid itemStack with null item...");
					continue;
				}
				int qtyLeft = itemStack.stackSize;
				final ItemStack itemStackLeft = itemStack.copy();
				for (final IInventory inventory : inventories) {
					qtyLeft = addToInventory(itemStack, inventory);
					if (qtyLeft > 0) {
						itemStackLeft.stackSize = qtyLeft;
					} else {
						break;
					}
				}
				if (qtyLeft > 0) {
					if (WarpDriveConfig.LOGGING_COLLECTION) {
						WarpDrive.logger.info(this + " Overflow detected");
					}
					overflow = true;
					int transfer;
					while (qtyLeft > 0) {
						transfer = Math.min(qtyLeft, itemStackLeft.getMaxStackSize());
						final ItemStack itemStackDrop = Commons.copyWithSize(itemStackLeft, transfer);
						final EntityItem entityItem = new EntityItem(worldObj, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, itemStackDrop);
						worldObj.spawnEntityInWorld(entityItem);
						qtyLeft -= transfer;
					}
				}
			}
		}
		return overflow;
	}
	
	private static int addToInventory(final ItemStack itemStackSource, final IInventory inventory) {
		if (itemStackSource == null || itemStackSource.getItem() == null) {
			return 0;
		}
		
		int qtyLeft = itemStackSource.stackSize;
		int transfer;
		
		if (inventory != null) {
			// fill existing stacks first
			for (int i = 0; i < inventory.getSizeInventory(); i++) {
				if (!inventory.isItemValidForSlot(i, itemStackSource)) {
					continue;
				}
				
				final ItemStack itemStack = inventory.getStackInSlot(i);
				if (itemStack == null || !itemStack.isItemEqual(itemStackSource)) {
					continue;
				}
				
				transfer = Math.min(qtyLeft, itemStack.getMaxStackSize() - itemStack.stackSize);
				itemStack.stackSize += transfer;
				qtyLeft -= transfer;
				if (qtyLeft <= 0) {
					return 0;
				}
			}
			
			// put remaining in empty slot
			for (int i = 0; i < inventory.getSizeInventory(); i++) {
				if (!inventory.isItemValidForSlot(i, itemStackSource)) {
					continue;
				}
				
				final ItemStack itemStack = inventory.getStackInSlot(i);
				if (itemStack != null) {
					continue;
				}
				
				transfer = Math.min(qtyLeft, itemStackSource.getMaxStackSize());
				final ItemStack dest = Commons.copyWithSize(itemStackSource, transfer);
				inventory.setInventorySlotContents(i, dest);
				qtyLeft -= transfer;
				
				if (qtyLeft <= 0) {
					return 0;
				}
			}
		}
		
		return qtyLeft;
	}
	
	
	// area protection
	protected boolean isBlockBreakCanceled(final UUID uuidPlayer, final World world, final BlockPos blockPosEvent) {
		return CommonProxy.isBlockBreakCanceled(uuidPlayer, pos, world, blockPosEvent);
	}
	
	protected boolean isBlockPlaceCanceled(final UUID uuidPlayer, final World world, final BlockPos blockPosEvent, final IBlockState blockState) {
		return CommonProxy.isBlockPlaceCanceled(uuidPlayer, pos, world, blockPosEvent, blockState);
	}
	
	// saved properties
	@Override
	public void readFromNBT(final NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		if (tagCompound.hasKey("upgrades")) {
			final NBTTagCompound nbtTagCompoundUpgrades = tagCompound.getCompoundTag("upgrades");
			final Set<String> keys = nbtTagCompoundUpgrades.getKeySet();
			for (final String key : keys) {
				Object object = getUpgradeFromString(key);
				final int value = nbtTagCompoundUpgrades.getByte(key);
				if (object == null) {
					WarpDrive.logger.error("Found an unknown upgrade named '" + key + "' in " + this);
					object = key;
				}
				installedUpgrades.put(object, value);
			}
		}
	}
	
	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeToNBT(tagCompound);
		if (!installedUpgrades.isEmpty()) {
			final NBTTagCompound nbtTagCompoundUpgrades = new NBTTagCompound();
			for (final Entry<Object, Integer> entry : installedUpgrades.entrySet()) {
				final String key = getUpgradeAsString(entry.getKey());
				nbtTagCompoundUpgrades.setByte(key, (byte)(int)entry.getValue());
			}
			tagCompound.setTag("upgrades", nbtTagCompoundUpgrades);
		}
		return tagCompound;
	}
	
	public NBTTagCompound writeItemDropNBT(final NBTTagCompound tagCompound) {
		writeToNBT(tagCompound);
		tagCompound.removeTag("x");
		tagCompound.removeTag("y");
		tagCompound.removeTag("z");
		return tagCompound;
	}
	
	@Nullable
	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		return new SPacketUpdateTileEntity(pos, getBlockMetadata(), getUpdateTag());
	}
	
	// status
	protected ITextComponent getUpgradeStatus() {
		final String strUpgrades = getUpgradesAsString();
		if (strUpgrades.isEmpty()) {
			return new TextComponentTranslation("warpdrive.upgrade.status_line.none",
				strUpgrades);
		} else {
			return new TextComponentTranslation("warpdrive.upgrade.status_line.valid",
				strUpgrades);
		}
	}
	
	protected ITextComponent getStatusPrefix() {
		if (worldObj != null) {
			final Item item = Item.getItemFromBlock(getBlockType());
			if (item != null) {
				final ItemStack itemStack = new ItemStack(item, 1, getBlockMetadata());
				return Commons.getChatPrefix(itemStack);
			}
		}
		return new TextComponentString("");
	}
	
	protected ITextComponent getBeamFrequencyStatus(final int beamFrequency) {
		if (beamFrequency == -1) {
			return new TextComponentTranslation("warpdrive.beam_frequency.status_line.undefined");
		} else if (beamFrequency < 0) {
			return new TextComponentTranslation("warpdrive.beam_frequency.status_line.invalid", beamFrequency);
		} else {
			return new TextComponentTranslation("warpdrive.beam_frequency.status_line.valid", beamFrequency);
		}
	}
	
	protected ITextComponent getVideoChannelStatus(final int videoChannel) {
		if (videoChannel == -1) {
			return new TextComponentTranslation("warpdrive.video_channel.status_line.undefined");
		} else if (videoChannel < 0) {
			return new TextComponentTranslation("warpdrive.video_channel.status_line.invalid", videoChannel);
		} else {
			final CameraRegistryItem camera = WarpDrive.cameras.getCameraByVideoChannel(worldObj, videoChannel);
			if (camera == null) {
				return new TextComponentTranslation("warpdrive.video_channel.status_line.not_loaded", videoChannel);
			} else if (camera.isTileEntity(this)) {
				return new TextComponentTranslation("warpdrive.video_channel.status_line.valid", videoChannel);
			} else {
				return new TextComponentTranslation("warpdrive.video_channel.status_line.validCamera",
				                                    videoChannel,
				                                    camera.position.getX(),
				                                    camera.position.getY(),
				                                    camera.position.getZ());
			}
		}
	}
	
	public ITextComponent getStatusHeader() {
		return new TextComponentString("");
	}
	
	public ITextComponent getStatus() {
		final ITextComponent message = getStatusPrefix().appendSibling( getStatusHeader() );
		
		if (this instanceof IBeamFrequency) {
			// only show in item form or from server side
			if ( worldObj == null
			  || !worldObj.isRemote ) {
				message.appendSibling(new TextComponentString("\n"))
				       .appendSibling( getBeamFrequencyStatus(((IBeamFrequency) this).getBeamFrequency()) );
			}
		}
		
		if (this instanceof IVideoChannel) {
			// only show in item form or from client side
			if ( worldObj == null
			  || worldObj.isRemote ) {
				message.appendSibling(new TextComponentString("\n"))
				       .appendSibling( getVideoChannelStatus(((IVideoChannel) this).getVideoChannel()) );
			}
		}
		
		if (isUpgradeable()) {
			message.appendSibling(new TextComponentString("\n"))
			       .appendSibling( getUpgradeStatus() );
		}
		
		return message;
	}
	
	public String getStatusHeaderInPureText() {
		return Commons.removeFormatting( getStatusHeader().getUnformattedText() );
	}
	
	// upgrade system
	private final HashMap<Object, Integer> installedUpgrades = new HashMap<>(10);
	private final HashMap<Object, Integer> maxUpgrades = new HashMap<>(10);
	public boolean isUpgradeable() {
		return !maxUpgrades.isEmpty();
	}
	public boolean hasUpgrade(final Object upgrade) {
		return getUpgradeCount(upgrade) > 0;
	}
	
	private String getUpgradeAsString(final Object object) {
		if (object instanceof Item) {
			return Item.REGISTRY.getNameForObject((Item)object).toString();
		} else if (object instanceof Block) {
			return Block.REGISTRY.getNameForObject((Block)object).toString();
		} else if (object instanceof ItemStack) {
			return Item.REGISTRY.getNameForObject(((ItemStack) object).getItem()) + ":" + ((ItemStack) object).getItemDamage();
		} else {
			return object.toString();
		}
	}
	
	private Object getUpgradeFromString(final String name) {
		for (final Object object : maxUpgrades.keySet()) {
			if (getUpgradeAsString(object).equals(name)) {
				return object;
			}
		}
		return null;
	}
	
	public Object getFirstUpgradeOfType(final Class clazz, final Object defaultValue) {
		for (final Object object : installedUpgrades.keySet()) {
			if (clazz != null && clazz.isInstance(object)) {
				return object;
			}
		}
		return defaultValue;
	}
	
	public Map<Object, Integer> getUpgradesOfType(final Class clazz) {
		if (clazz == null) {
			return installedUpgrades;
		}
		final Map<Object, Integer> mapResult = new HashMap<>(installedUpgrades.size());
		for (final Entry<Object, Integer> entry : installedUpgrades.entrySet()) {
			if (clazz.isInstance(entry.getKey())) {
				mapResult.put(entry.getKey(), entry.getValue());
			}
		}
		return mapResult;
	}
	
	public int getValidUpgradeCount(final Object upgrade) {
		return Math.min(getUpgradeMaxCount(upgrade), getUpgradeCount(upgrade));
	}
	
	public int getUpgradeCount(final Object upgrade) {
		final Integer value = installedUpgrades.get(upgrade);
		return value == null ? 0 : value;
	}
	
	public int getUpgradeMaxCount(final Object upgrade) {
		final Integer value = maxUpgrades.get(upgrade);
		return value == null ? 0 : value;
	}
	
	protected String getUpgradesAsString() {
		final StringBuilder message = new StringBuilder();
		for (final Entry<Object, Integer> entry : installedUpgrades.entrySet()) {
			if (message.length() > 0) {
				message.append(", ");
			}
			final Object key = entry.getKey();
			String keyName = key.toString();
			if (key instanceof Item) {
				keyName = ((Item) key).getUnlocalizedName();
			} else if (key instanceof Block) {
				keyName = ((Block) key).getUnlocalizedName();
			}
			if (entry.getValue() == 1) {
				message.append(keyName);
			} else {
				message.append(entry.getValue()).append(" x ").append(keyName);
			}
		}
		return message.toString();
	}
	
	protected void setUpgradeMaxCount(final Object upgrade, final int value) {
		maxUpgrades.put(upgrade, value);
	}
	
	public boolean canUpgrade(final Object upgrade) {
		return getUpgradeMaxCount(upgrade) >= getUpgradeCount(upgrade) + 1;
	}
	
	public boolean mountUpgrade(final Object upgrade) {
		if (canUpgrade(upgrade)) {
			installedUpgrades.put(upgrade, getUpgradeCount(upgrade) + 1);
			markDirty();
			return true;
		}
		return false;
	}
	
	public boolean dismountUpgrade(final Object upgrade) {
		final int count = getUpgradeCount(upgrade);
		if (count > 1) {
			installedUpgrades.put(upgrade, count - 1);
			markDirty();
			return true;
			
		} else if (count > 0) {
			installedUpgrades.remove(upgrade);
			markDirty();
			return true;
		}
		return false;
	}
	
	@Override
	public String toString() {
		return String.format("%s @ %s (%d %d %d)",
		                     getClass().getSimpleName(),
		                     worldObj == null ? "~NULL~" : worldObj.provider.getSaveFolder(),
		                     pos.getX(), pos.getY(), pos.getZ());
	}
}
