package cr0s.warpdrive.block.energy;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IWarpTool;
import cr0s.warpdrive.block.BlockAbstractContainer;
import cr0s.warpdrive.client.ClientProxy;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.BlockProperties;
import cr0s.warpdrive.data.EnumDisabledInputOutput;
import cr0s.warpdrive.data.EnumTier;
import cr0s.warpdrive.event.ModelBakeEventHandler;
import cr0s.warpdrive.render.BakedModelEnergyBank;
import ic2.api.energy.tile.IExplosionPowerOverride;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.common.property.Properties;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Optional.InterfaceList({
	@Optional.Interface(iface = "ic2.api.energy.tile.IExplosionPowerOverride", modid = "IC2")
})
public class BlockEnergyBank extends BlockAbstractContainer implements IExplosionPowerOverride {
	
	public static final IProperty<EnumDisabledInputOutput> CONFIG = PropertyEnum.create("config", EnumDisabledInputOutput.class);
	
	public static final IUnlistedProperty<EnumDisabledInputOutput> DOWN = Properties.toUnlisted(PropertyEnum.create("down", EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> UP = Properties.toUnlisted(PropertyEnum.create("up", EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> NORTH = Properties.toUnlisted(PropertyEnum.create("north", EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> SOUTH = Properties.toUnlisted(PropertyEnum.create("south", EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> WEST = Properties.toUnlisted(PropertyEnum.create("west", EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> EAST = Properties.toUnlisted(PropertyEnum.create("east", EnumDisabledInputOutput.class));
	
	public BlockEnergyBank(final String registryName) {
		super(registryName, Material.IRON);
		setUnlocalizedName("warpdrive.energy.energy_bank.");
		hasSubBlocks = true;
		
		setDefaultState(getDefaultState()
				                .withProperty(BlockProperties.TIER, EnumTier.BASIC)
				                .withProperty(CONFIG, EnumDisabledInputOutput.DISABLED)
		               );
		GameRegistry.registerTileEntity(TileEntityEnergyBank.class, WarpDrive.PREFIX + registryName);
	}
	
	@Nonnull
	@Override
	protected BlockStateContainer createBlockState() {
		return new ExtendedBlockState(this,
		                              new IProperty[] { BlockProperties.TIER, CONFIG },
		                              new IUnlistedProperty[] { DOWN, UP, NORTH, SOUTH, WEST, EAST });
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState getStateFromMeta(final int metadata) {
		return getDefaultState()
				       .withProperty(BlockProperties.TIER, EnumTier.get(metadata % 4));
	}
	
	@Override
	public int getMetaFromState(final IBlockState blockState) {
		return blockState.getValue(BlockProperties.TIER).getIndex();
	}
	
	@Nonnull
	@Override
	public IBlockState getExtendedState(@Nonnull final IBlockState blockState, final IBlockAccess blockAccess, final BlockPos blockPos) {
		if (!(blockState instanceof IExtendedBlockState)) {
			return blockState;
		}
		final TileEntity tileEntity = blockAccess.getTileEntity(blockPos);
		if (!(tileEntity instanceof TileEntityEnergyBank)) {
			return blockState;
		}
		final TileEntityEnergyBank tileEntityEnergyBank = (TileEntityEnergyBank) tileEntity;
		return ((IExtendedBlockState) blockState)
				       .withProperty(DOWN, tileEntityEnergyBank.getMode(EnumFacing.DOWN))
				       .withProperty(UP, tileEntityEnergyBank.getMode(EnumFacing.UP))
				       .withProperty(NORTH, tileEntityEnergyBank.getMode(EnumFacing.NORTH))
				       .withProperty(SOUTH, tileEntityEnergyBank.getMode(EnumFacing.SOUTH))
				       .withProperty(WEST, tileEntityEnergyBank.getMode(EnumFacing.WEST))
				       .withProperty(EAST, tileEntityEnergyBank.getMode(EnumFacing.EAST));
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState getActualState(@Nonnull final IBlockState blockState, final IBlockAccess blockAccess, final BlockPos pos) {
		return super.getActualState(blockState, blockAccess, pos);
	}
	
	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull final World world, final int metadata) {
		return new TileEntityEnergyBank((byte) (metadata % 4));
	}
	
	@Nullable
	@Override
	public ItemBlock createItemBlock() {
		return new ItemBlockEnergyBank(this);
	}
	
	@Override
	public int damageDropped(final IBlockState blockState) {
		return getMetaFromState(blockState);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void getSubBlocks(@Nonnull final Item item, final CreativeTabs creativeTab, final List<ItemStack> list) {
		for (byte tier = 0; tier < 4; tier++) {
			ItemStack itemStack = new ItemStack(item, 1, tier);
			list.add(itemStack);
			if (tier > 0) {
				itemStack = new ItemStack(item, 1, tier);
				final NBTTagCompound tagCompound = new NBTTagCompound();
				tagCompound.setByte("tier", tier);
				tagCompound.setInteger("energy", WarpDriveConfig.ENERGY_BANK_MAX_ENERGY_STORED[tier - 1]);
				itemStack.setTagCompound(tagCompound);
				list.add(itemStack);
			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public void modelInitialisation() {
		final Item item = Item.getItemFromBlock(this);
		ClientProxy.modelInitialisation(item);
		
		// register (smart) baked model
		for (final EnumDisabledInputOutput enumDisabledInputOutput : CONFIG.getAllowedValues()) {
			for (final EnumTier enumTier : BlockProperties.TIER.getAllowedValues()) {
				final String variant = String.format("%s=%s,%s=%s",
				                                     CONFIG.getName(), enumDisabledInputOutput,
				                                     BlockProperties.TIER.getName(), enumTier);
				ModelBakeEventHandler.registerBakedModel(new ModelResourceLocation(getRegistryName(), variant), BakedModelEnergyBank.class);
			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public byte getTier(final ItemStack itemStack) {
		if (itemStack == null || itemStack.getItem() != Item.getItemFromBlock(this)) {
			return 1;
		}
		final NBTTagCompound tagCompound = itemStack.getTagCompound();
		if (tagCompound != null && tagCompound.hasKey("tier")) {
			return tagCompound.getByte("tier");
		} else {
			return (byte) itemStack.getItemDamage();
		}
	}
	
	// IExplosionPowerOverride overrides
	@Override
	public boolean shouldExplode() {
		return false;
	}
	
	@Override
	public float getExplosionPower(int tier, float defaultPower) {
		return defaultPower;
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
		
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (!(tileEntity instanceof TileEntityEnergyBank)) {
			return false;
		}
		final TileEntityEnergyBank tileEntityEnergyBank = (TileEntityEnergyBank) tileEntity;
		
		if ( itemStackHeld != null
		  && itemStackHeld.getItem() instanceof IWarpTool ) {
			if (entityPlayer.isSneaking()) {
				tileEntityEnergyBank.setMode(side, tileEntityEnergyBank.getMode(side).getPrevious());
			} else {
				tileEntityEnergyBank.setMode(side, tileEntityEnergyBank.getMode(side).getNext());
			}
			final ItemStack itemStack = new ItemStack(Item.getItemFromBlock(this), 1, getMetaFromState(blockState));
			switch (tileEntityEnergyBank.getMode(side)) {
			case INPUT:
				Commons.addChatMessage(entityPlayer, Commons.getChatPrefix(itemStack)
				                                            .appendSibling(new TextComponentTranslation("warpdrive.energy.side.changed_to_input", side.name())));
				return true;
			case OUTPUT:
				Commons.addChatMessage(entityPlayer, Commons.getChatPrefix(itemStack)
				                                            .appendSibling(new TextComponentTranslation("warpdrive.energy.side.changed_to_output", side.name())));
				return true;
			case DISABLED:
			default:
				Commons.addChatMessage(entityPlayer, Commons.getChatPrefix(itemStack)
				                                            .appendSibling(new TextComponentTranslation("warpdrive.energy.side.changed_to_disabled", side.name())));
				return true;
			}
		}
		
		return super.onBlockActivated(world, blockPos, blockState, entityPlayer, hand, itemStackHeld, side, hitX, hitY, hitZ);
	}
}