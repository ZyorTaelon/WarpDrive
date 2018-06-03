package cr0s.warpdrive.block;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBlockBase;
import cr0s.warpdrive.client.ClientProxy;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import cr0s.warpdrive.data.BlockProperties;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public abstract class BlockAbstractBase extends Block implements IBlockBase {
	
	protected BlockAbstractBase(final String registryName, final Material material) {
		super(material);
		setHardness(5.0F);
		setResistance(6.0F * 5 / 3);
		setSoundType(SoundType.METAL);
		setCreativeTab(WarpDrive.creativeTabWarpDrive);
		if (registryName != null) {
			setRegistryName(registryName);
			WarpDrive.register(this);
		}
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
		
		// Force a single model through through a custom state mapper
		/*
		final StateMapperBase stateMapperBase = new StateMapperBase() {
			@Nonnull
			@Override
			protected ModelResourceLocation getModelResourceLocation(@Nonnull IBlockState blockState) {
				return modelResourceLocation;
			}
		};
		ModelLoader.setCustomStateMapper(this, stateMapperBase);
		/**/
		
		// Bind our TESR to our tile entity
		/*
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityXXXX.class, new TileEntityXXXRenderer());
		/**/
	}
	
	@Override
	public void onBlockPlacedBy(final World world, final BlockPos blockPos, final IBlockState blockState,
	                            final EntityLivingBase entityLiving, final ItemStack itemStack) {
		super.onBlockPlacedBy(world, blockPos, blockState, entityLiving, itemStack);
		final boolean isRotating = blockState.getProperties().containsKey(BlockProperties.FACING);
		if (isRotating) {
			EnumFacing enumFacing = BlockAbstractBase.getFacingFromEntity(blockPos, entityLiving);
			world.setBlockState(blockPos, blockState.withProperty(BlockProperties.FACING, enumFacing));
		}
	}
	
	@Override
	public boolean removedByPlayer(@Nonnull final IBlockState blockState, final World world, @Nonnull final BlockPos blockPos,
	                               @Nonnull final EntityPlayer player, final boolean willHarvest) {
		return willHarvest || super.removedByPlayer(blockState, world, blockPos, player, false);
	}
	
	@Override
	public boolean rotateBlock(final World world, @Nonnull final BlockPos blockPos, final EnumFacing axis) {
		// already handled by vanilla
		return super.rotateBlock(world, blockPos, axis);
	}
	
	@Override
	public byte getTier(final ItemStack itemStack) {
		return 1;
	}
	
	@Override
	public EnumRarity getRarity(final ItemStack itemStack, final EnumRarity rarity) {
		switch (getTier(itemStack)) {
		case 0:  return EnumRarity.EPIC;
		case 1:  return EnumRarity.COMMON;
		case 2:  return EnumRarity.UNCOMMON;
		case 3:  return EnumRarity.RARE;
		default: return rarity;
		}
	}
	
	public static EnumFacing getFacingFromEntity(final BlockPos clickedBlock, final EntityLivingBase entityLivingBase) {
		final EnumFacing facing = EnumFacing.getFacingFromVector(
				(float) (entityLivingBase.posX - clickedBlock.getX()),
				(float) (entityLivingBase.posY - clickedBlock.getY()),
				(float) (entityLivingBase.posZ - clickedBlock.getZ()) );
		if (entityLivingBase.isSneaking()) {
			return facing.getOpposite();
		}
		return facing;
	}
}
