package cr0s.warpdrive.block.forcefield;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.block.BlockAbstractBase;
import cr0s.warpdrive.client.ClientProxy;
import cr0s.warpdrive.data.BlockProperties;
import cr0s.warpdrive.data.EnumForceFieldShape;
import cr0s.warpdrive.data.EnumForceFieldState;
import cr0s.warpdrive.data.EnumForceFieldUpgrade;
import cr0s.warpdrive.item.ItemForceFieldShape;
import cr0s.warpdrive.item.ItemForceFieldUpgrade;
import cr0s.warpdrive.render.TileEntityForceFieldProjectorRenderer;

import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.common.property.Properties;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockForceFieldProjector extends BlockAbstractForceField {
	
	public static final PropertyBool IS_DOUBLE_SIDED = PropertyBool.create("is_double_sided");
	public static final IUnlistedProperty<EnumForceFieldShape> SHAPE = Properties.toUnlisted(PropertyEnum.create("shape", EnumForceFieldShape.class));
	public static final IUnlistedProperty<EnumForceFieldState> STATE = Properties.toUnlisted(PropertyEnum.create("state", EnumForceFieldState.class));
	
	private static final AxisAlignedBB AABB_DOWN  = new AxisAlignedBB(0.00D, 0.27D, 0.00D, 1.00D, 0.73D, 1.00D);
	private static final AxisAlignedBB AABB_UP    = new AxisAlignedBB(0.00D, 0.27D, 0.00D, 1.00D, 0.73D, 1.00D);
	private static final AxisAlignedBB AABB_NORTH = new AxisAlignedBB(0.00D, 0.00D, 0.27D, 1.00D, 1.00D, 0.73D);
	private static final AxisAlignedBB AABB_SOUTH = new AxisAlignedBB(0.00D, 0.00D, 0.27D, 1.00D, 1.00D, 0.73D);
	private static final AxisAlignedBB AABB_WEST  = new AxisAlignedBB(0.27D, 0.00D, 0.00D, 0.73D, 1.00D, 1.00D);
	private static final AxisAlignedBB AABB_EAST  = new AxisAlignedBB(0.27D, 0.00D, 0.00D, 0.73D, 1.00D, 1.00D);
	
	public BlockForceFieldProjector(final String registryName, final byte tier) {
		super(registryName, tier, Material.IRON);
		setUnlocalizedName("warpdrive.forcefield.projector" + tier);
		
		setDefaultState(getDefaultState()
				.withProperty(BlockProperties.FACING, EnumFacing.NORTH)
				.withProperty(IS_DOUBLE_SIDED, false)
				);
		GameRegistry.registerTileEntity(TileEntityForceFieldProjector.class, WarpDrive.PREFIX + registryName);
		// ModelLoader.setCustomMeshDefinition();
	}
	
	@Nonnull
	@Override
	protected BlockStateContainer createBlockState() {
		return new ExtendedBlockState(this,
				new IProperty[] { BlockProperties.FACING, IS_DOUBLE_SIDED },
				new IUnlistedProperty[] { SHAPE, STATE });
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState getStateFromMeta(final int metadata) {
		return getDefaultState()
				.withProperty(BlockProperties.FACING, EnumFacing.getFront(metadata & 7))
				.withProperty(IS_DOUBLE_SIDED, metadata > 7);
	}
	
	@Override
	public int getMetaFromState(final IBlockState blockState) {
		return blockState.getValue(BlockProperties.FACING).getIndex()
		       + (blockState.getValue(IS_DOUBLE_SIDED) ? 8 : 0);
	}
	
	@Nonnull
	@Override
	public IBlockState getExtendedState(@Nonnull final IBlockState blockState, final IBlockAccess blockAccess, final BlockPos blockPos) {
		if (!(blockState instanceof IExtendedBlockState)) {
			return blockState;
		}
		final TileEntity tileEntity = blockAccess.getTileEntity(blockPos);
		EnumForceFieldShape forceFieldShape = EnumForceFieldShape.NONE;
		EnumForceFieldState forceFieldState = EnumForceFieldState.NOT_CONNECTED;
		if (tileEntity instanceof TileEntityForceFieldProjector) {
			final TileEntityForceFieldProjector tileEntityForceFieldProjector = (TileEntityForceFieldProjector) tileEntity;
			forceFieldShape = tileEntityForceFieldProjector.getShape();
			forceFieldState = tileEntityForceFieldProjector.getState();
		}
		
		return ((IExtendedBlockState) blockState)
				.withProperty(SHAPE, forceFieldShape)
				.withProperty(STATE, forceFieldState);
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public void modelInitialisation() {
		final Item item = Item.getItemFromBlock(this);
		ClientProxy.modelInitialisation(item);
		
		// Bind our TESR to our tile entity
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityForceFieldProjector.class, new TileEntityForceFieldProjectorRenderer());
	}
	
	@SuppressWarnings("deprecation")
	@SideOnly(Side.CLIENT)
	@Override
	public boolean shouldSideBeRendered(final IBlockState blockState, @Nonnull final IBlockAccess blockAccess, @Nonnull final BlockPos blockPos, final EnumFacing side) {
		return true;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean isBlockNormalCube(final IBlockState blockState) {
		return false;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean isOpaqueCube(final IBlockState state) {
		return false;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean isFullCube(final IBlockState state) {
		return false;
	}
	
	@Nonnull
	@SuppressWarnings("deprecation")
	@Override
	public AxisAlignedBB getBoundingBox(final IBlockState state, final IBlockAccess source, final BlockPos pos) {
		switch (state.getValue(BlockProperties.FACING)) {
			case DOWN : return AABB_DOWN ;
			case UP   : return AABB_UP   ;
			case NORTH: return AABB_NORTH;
			case SOUTH: return AABB_SOUTH;
			case WEST : return AABB_WEST ;
			case EAST : return AABB_EAST ;
			default   : return AABB_UP;
		}
	}
	
	@Nonnull
	@SuppressWarnings("deprecation")
	@Override
	public IBlockState onBlockPlaced(final World worldIn, final BlockPos pos, final EnumFacing facing, final float hitX, final float hitY, final float hitZ, final int metadata, final EntityLivingBase entityLiving) {
		final EnumFacing enumFacing = BlockAbstractBase.getFacingFromEntity(pos, entityLiving);
		return this.getDefaultState().withProperty(BlockProperties.FACING, enumFacing);
	}
	
	@Nullable
	@Override
	public ItemBlock createItemBlock() {
		return new ItemBlockForceFieldProjector(this);
	}
	
	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull final World world, final int metadata) {
		return new TileEntityForceFieldProjector();
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public void getSubBlocks(@Nonnull final Item item, final CreativeTabs creativeTab, final List<ItemStack> list) {
		for (int i = 0; i < 2; ++i) {
			list.add(new ItemStack(item, 1, i));
		}
	}
	
	@Override
	public int damageDropped(final IBlockState state) {
		return state.getValue(IS_DOUBLE_SIDED) ? 1 : 0;
	}
	
	@Override
	public void onBlockPlacedBy(final World world, final BlockPos blockPos, final IBlockState blockState,
	                            final EntityLivingBase entityLiving, final ItemStack itemStack) {
		super.onBlockPlacedBy(world, blockPos, blockState, entityLiving, itemStack);
		final TileEntityForceFieldProjector tileEntityForceFieldProjector = (TileEntityForceFieldProjector) world.getTileEntity(blockPos);
		if (!itemStack.hasTagCompound() && tileEntityForceFieldProjector != null) {
			tileEntityForceFieldProjector.isDoubleSided = (itemStack.getItemDamage() == 1);
		}
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
		if (!(tileEntity instanceof TileEntityForceFieldProjector)) {
			return false;
		}
		final TileEntityForceFieldProjector tileEntityForceFieldProjector = (TileEntityForceFieldProjector) tileEntity;
		
		EnumForceFieldUpgrade enumForceFieldUpgrade = EnumForceFieldUpgrade.NONE;
		if (itemStackHeld != null && itemStackHeld.getItem() instanceof ItemForceFieldUpgrade) {
			enumForceFieldUpgrade = EnumForceFieldUpgrade.get(itemStackHeld.getItemDamage());
		}
		
		// sneaking with an empty hand or an upgrade/shape item in hand to dismount current upgrade/shape
		if (entityPlayer.isSneaking()) {
			// using an upgrade item or no shape defined means dismount upgrade, otherwise dismount shape
			if ( (itemStackHeld != null && itemStackHeld.getItem() instanceof ItemForceFieldUpgrade)
			  || (tileEntityForceFieldProjector.getShape() == EnumForceFieldShape.NONE)
			  || (side != blockState.getValue(BlockProperties.FACING) && (!tileEntityForceFieldProjector.isDoubleSided || side.getOpposite() != blockState.getValue(BlockProperties.FACING))) ) {
				// find a valid upgrade to dismount
				if (!tileEntityForceFieldProjector.hasUpgrade(enumForceFieldUpgrade)) {
					enumForceFieldUpgrade = (EnumForceFieldUpgrade)tileEntityForceFieldProjector.getFirstUpgradeOfType(EnumForceFieldUpgrade.class, EnumForceFieldUpgrade.NONE);
				}
				
				if (enumForceFieldUpgrade == EnumForceFieldUpgrade.NONE) {
					// no more upgrades to dismount
					Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.no_upgrade_to_dismount"));
					return true;
				}
				
				if (!entityPlayer.capabilities.isCreativeMode) {
					// dismount the current upgrade item
					final ItemStack itemStackDrop = ItemForceFieldUpgrade.getItemStackNoCache(enumForceFieldUpgrade, 1);
					final EntityItem entityItem = new EntityItem(world, entityPlayer.posX, entityPlayer.posY + 0.5D, entityPlayer.posZ, itemStackDrop);
					entityItem.setNoPickupDelay();
					world.spawnEntityInWorld(entityItem);
				}
				
				tileEntityForceFieldProjector.dismountUpgrade(enumForceFieldUpgrade);
				// upgrade dismounted
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.dismounted", enumForceFieldUpgrade.name()));
				return false;
				
			} else {// default to dismount shape
				if (tileEntityForceFieldProjector.getShape() != EnumForceFieldShape.NONE) {
					if (side == blockState.getValue(BlockProperties.FACING) || (tileEntityForceFieldProjector.isDoubleSided && side.getOpposite() == blockState.getValue(BlockProperties.FACING))) {
						if (!entityPlayer.capabilities.isCreativeMode) {
							// dismount the shape item(s)
							final ItemStack itemStackDrop = ItemForceFieldShape.getItemStackNoCache(tileEntityForceFieldProjector.getShape(), tileEntityForceFieldProjector.isDoubleSided ? 2 : 1);
							final EntityItem entityItem = new EntityItem(world, entityPlayer.posX, entityPlayer.posY + 0.5D, entityPlayer.posZ, itemStackDrop);
							entityItem.setNoPickupDelay();
							world.spawnEntityInWorld(entityItem);
						}
						
						tileEntityForceFieldProjector.setShape(EnumForceFieldShape.NONE);
						// shape dismounted
						Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.shape_dismounted"));
					} else {
						// wrong side
						Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.wrong_shape_side"));
						return true;
					}
					
				} else {
					// no shape to dismount
					Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.no_shape_to_dismount"));
					return true;
				}
			}
			
		} else if (itemStackHeld == null) {// no sneaking and no item in hand => show status
			Commons.addChatMessage(entityPlayer, tileEntityForceFieldProjector.getStatus());
			return true;
			
		} else if (itemStackHeld.getItem() instanceof ItemForceFieldShape) {// no sneaking and shape in hand => mounting a shape
			if (side == blockState.getValue(BlockProperties.FACING) || (tileEntityForceFieldProjector.isDoubleSided && side.getOpposite() == blockState.getValue(BlockProperties.FACING))) {
				if (!entityPlayer.capabilities.isCreativeMode) {
					// validate quantity
					if (itemStackHeld.stackSize < (tileEntityForceFieldProjector.isDoubleSided ? 2 : 1)) {
						// not enough shape items
						Commons.addChatMessage(entityPlayer, new TextComponentTranslation(
							tileEntityForceFieldProjector.isDoubleSided ?
								"warpdrive.upgrade.result.not_enough_shapes.double" : "warpdrive.upgrade.result.not_enough_shapes.single"));
						return true;
					}
					
					// update player inventory
					itemStackHeld.stackSize -= tileEntityForceFieldProjector.isDoubleSided ? 2 : 1;
					
					// dismount the current shape item(s)
					if (tileEntityForceFieldProjector.getShape() != EnumForceFieldShape.NONE) {
						final ItemStack itemStackDrop = ItemForceFieldShape.getItemStackNoCache(tileEntityForceFieldProjector.getShape(), tileEntityForceFieldProjector.isDoubleSided ? 2 : 1);
						final EntityItem entityItem = new EntityItem(world, entityPlayer.posX, entityPlayer.posY + 0.5D, entityPlayer.posZ, itemStackDrop);
						entityItem.setNoPickupDelay();
						world.spawnEntityInWorld(entityItem);
					}
				}
				
				// mount the new shape item(s)
				tileEntityForceFieldProjector.setShape(EnumForceFieldShape.get(itemStackHeld.getItemDamage()));
				// shape mounted
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.shape_mounted"));
				
			} else {
				// wrong side
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.wrong_shape_side"));
				return true;
			}
			
		} else if (itemStackHeld.getItem() instanceof ItemForceFieldUpgrade) {// no sneaking and an upgrade in hand => mounting an upgrade
			// validate type
			if (tileEntityForceFieldProjector.getUpgradeMaxCount(enumForceFieldUpgrade) <= 0) {
				// invalid upgrade type
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.invalid_upgrade_for_projector"));
				return true;
			}
			if (!tileEntityForceFieldProjector.canUpgrade(enumForceFieldUpgrade)) {
				// too many upgrades
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.too_many_upgrades",
					tileEntityForceFieldProjector.getUpgradeMaxCount(enumForceFieldUpgrade)));
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
			tileEntityForceFieldProjector.mountUpgrade(enumForceFieldUpgrade);
			// upgrade mounted
			Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.mounted", enumForceFieldUpgrade));
		}
		
		return false;
	}
}
