package cr0s.warpdrive.block.forcefield;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IDamageReceiver;
import cr0s.warpdrive.block.hull.BlockHullGlass;
import cr0s.warpdrive.client.ClientProxy;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.BlockProperties;
import cr0s.warpdrive.data.EnumPermissionNode;
import cr0s.warpdrive.data.ForceFieldSetup;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.data.VectorI;
import cr0s.warpdrive.event.ModelBakeEventHandler;
import cr0s.warpdrive.render.BakedModelCamouflage;

import java.util.List;
import java.util.Random;

import net.minecraft.block.BlockGlass;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BlockForceField extends BlockAbstractForceField implements IDamageReceiver {
	
	private static final float BOUNDING_TOLERANCE = 0.05F;
	private static final AxisAlignedBB AABB_FORCEFIELD = new AxisAlignedBB(
			BOUNDING_TOLERANCE, BOUNDING_TOLERANCE, BOUNDING_TOLERANCE,
		    1 - BOUNDING_TOLERANCE, 1 - BOUNDING_TOLERANCE, 1 - BOUNDING_TOLERANCE);
	
	public static final PropertyInteger FREQUENCY = PropertyInteger.create("frequency", 0, 15);
	
	public BlockForceField(final String registryName, final byte tier) {
		super(registryName, tier, Material.GLASS);
		setSoundType(SoundType.CLOTH);
		setUnlocalizedName("warpdrive.forcefield.block" + tier);
		setBlockUnbreakable();
		
		setDefaultState(getDefaultState()
		                .withProperty(FREQUENCY, 0)
		);
		GameRegistry.registerTileEntity(TileEntityForceField.class, WarpDrive.PREFIX + registryName);
	}
	
	@Nonnull
	@Override
	protected BlockStateContainer createBlockState() {
		return new ExtendedBlockState(this,
		                              new IProperty[] { FREQUENCY },
		                              new IUnlistedProperty[] { BlockProperties.CAMOUFLAGE });
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public MapColor getMapColor(final IBlockState state) {
		// @TODO: color from force field frequency
		return super.getMapColor(state);
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState getStateFromMeta(final int metadata) {
		return this.getDefaultState().withProperty(FREQUENCY, metadata);
	}
	
	@Override
	public int getMetaFromState(final IBlockState state) {
		return state.getValue(FREQUENCY);
	}
	
	@Nonnull
	@Override
	public IBlockState getExtendedState(@Nonnull final IBlockState blockState, final IBlockAccess blockAccess, final BlockPos blockPos) {
		if (!(blockState instanceof IExtendedBlockState)) {
			return blockState;
		}
		final TileEntity tileEntity = blockAccess.getTileEntity(blockPos);
		if (!(tileEntity instanceof TileEntityForceField)) {
			return blockState;
		}
		final TileEntityForceField tileEntityForceField = (TileEntityForceField) tileEntity;
		IBlockState blockStateCamouflage = tileEntityForceField.cache_blockStateCamouflage;
		if (!Commons.isValidCamouflage(blockStateCamouflage)) {
			blockStateCamouflage = Blocks.AIR.getDefaultState();
		}
		return ((IExtendedBlockState) blockState)
		       .withProperty(BlockProperties.CAMOUFLAGE, blockStateCamouflage);
	}
	
	@Nullable
	@Override
	public ItemBlock createItemBlock() {
		return new ItemBlockForceField(this);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void getSubBlocks(@Nonnull final Item item, final CreativeTabs creativeTab, final List<ItemStack> list) {
		// hide in NEI
		for (int i = 0; i < 16; i++) {
			Commons.hideItemStack(new ItemStack(item, 1, i));
		}
	}
	
	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull final World world, final int metadata) {
		return new TileEntityForceField();
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public void modelInitialisation() {
		final Item item = Item.getItemFromBlock(this);
		ClientProxy.modelInitialisation(item);
		
		// register camouflage
		for (final Integer integer : FREQUENCY.getAllowedValues()) {
			final String variant = String.format("%s=%d", FREQUENCY.getName(), integer);
			ModelBakeEventHandler.registerBakedModel(new ModelResourceLocation(getRegistryName(), variant), BakedModelCamouflage.class);
		}
	}
	
	@Override
	public boolean isVisuallyOpaque() {
		return false;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean isOpaqueCube(final IBlockState state) {
		return false;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean isFullyOpaque(final IBlockState state) {
		return false;
	}
	
	@Nonnull
	@Override
	public ItemStack getPickBlock(@Nonnull final IBlockState blockState, final RayTraceResult target, @Nonnull final World world, @Nonnull final BlockPos blockPos, final EntityPlayer entityPlayer) {
		return new ItemStack(Blocks.AIR);
	}
	
	@Override
	public int quantityDropped(final Random random) {
		return 0;
	}
	
	@Nonnull
	@Override
	public EnumBlockRenderType getRenderType(final IBlockState state) {
		return EnumBlockRenderType.MODEL;
	}
	
	@SuppressWarnings("deprecation")
	@SideOnly(Side.CLIENT)
	@Override
	public boolean shouldSideBeRendered(final IBlockState blockState, @Nonnull final IBlockAccess blockAccess, @Nonnull final BlockPos blockPos, final EnumFacing facing) {
		final BlockPos blockPosSide = blockPos.offset(facing);
		if (blockAccess.isAirBlock(blockPosSide)) {
			return true;
		}
		final EnumFacing opposite = facing.getOpposite();
		final IBlockState blockStateSide = blockAccess.getBlockState(blockPosSide);
		if ( blockStateSide.getBlock() instanceof BlockGlass 
		  || blockStateSide.getBlock() instanceof BlockHullGlass
		  || blockStateSide.getBlock() instanceof BlockForceField ) {
			return blockState.getBlock().getMetaFromState(blockState)
				!= blockStateSide.getBlock().getMetaFromState(blockStateSide);
		}
		return !blockAccess.isSideSolid(blockPosSide, opposite, false);
	}
	
	protected TileEntityForceFieldProjector getProjector(final World world, @Nonnull final BlockPos blockPos) {
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (tileEntity instanceof TileEntityForceField) {
			return ((TileEntityForceField) tileEntity).getProjector();
		}
		return null;
	}
	
	private ForceFieldSetup getForceFieldSetup(final World world, @Nonnull final BlockPos blockPos) {
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (tileEntity instanceof TileEntityForceField) {
			return ((TileEntityForceField) tileEntity).getForceFieldSetup();
		}
		return null;
	}
	
	@Override
	public void onBlockClicked(final World world, final BlockPos blockPos, final EntityPlayer entityPlayer) {
		if (world.isRemote) {
			return;
		}
		final ForceFieldSetup forceFieldSetup = getForceFieldSetup(world, blockPos);
		if (forceFieldSetup != null) {
			forceFieldSetup.onEntityEffect(world, blockPos, entityPlayer);
		}
	}
	
	@SuppressWarnings("deprecation")
	@Nullable
	@Override
	public AxisAlignedBB getCollisionBoundingBox(final IBlockState blockState, @Nonnull final World world, @Nonnull final BlockPos blockPos) {
		final ForceFieldSetup forceFieldSetup = getForceFieldSetup(world, blockPos);
		if (forceFieldSetup != null) {
			final List<EntityPlayer> entities = world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(
				blockPos.getX(), blockPos.getY(), blockPos.getZ(),
				blockPos.getX() + 1.0D, blockPos.getY() + 1.0D, blockPos.getZ() + 1.0D));
			
			for (final EntityPlayer entityPlayer : entities) {
				if (entityPlayer != null && entityPlayer.isSneaking()) {
					if ( entityPlayer.capabilities.isCreativeMode 
					  || forceFieldSetup.isAccessGranted(entityPlayer, EnumPermissionNode.SNEAK_THROUGH)) {
							return null;
					}
				}
			}
		}
		
		return AABB_FORCEFIELD;
	}
	
	@Override
	public void onEntityCollidedWithBlock(final World world, final BlockPos blockPos, final IBlockState blockState, final Entity entity) {
		if (world.isRemote) {
			return;
		}
		
		final ForceFieldSetup forceFieldSetup = getForceFieldSetup(world, blockPos);
		if (forceFieldSetup != null) {
			forceFieldSetup.onEntityEffect(world, blockPos, entity);
			final double distance2 = new Vector3(blockPos).translate(0.5F).distanceTo_square(entity);
			if (entity instanceof EntityLiving && distance2 < 0.26D) {
				boolean hasPermission = false;
				
				final List<EntityPlayer> entities = world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(
					blockPos.getX(), blockPos.getY(), blockPos.getZ(),
					blockPos.getX() + 1.0D, blockPos.getY() + 0.9D, blockPos.getZ() + 1.0D));
				for (final EntityPlayer entityPlayer : entities) {
					if (entityPlayer != null && entityPlayer.isSneaking()) {
						if ( entityPlayer.capabilities.isCreativeMode
						  || forceFieldSetup.isAccessGranted(entityPlayer, EnumPermissionNode.SNEAK_THROUGH) ) {
							hasPermission = true;
							break;
						}
					}
				}
				
				// always slowdown
				((EntityLiving) entity).addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 20, 1));
				
				if (!hasPermission) {
					((EntityLiving) entity).addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 80, 3));
					if (distance2 < 0.24D) {
						entity.attackEntityFrom(WarpDrive.damageShock, 5);
					}
				}
			}
		}
	}
	
	/* @TODO MC1.10 camouflage color multiplier
	@Override
	public int colorMultiplier(final IBlockAccess blockAccess, final BlockPos blockPos) {
		final TileEntity tileEntity = blockAccess.getTileEntity(blockPos);
		if (tileEntity instanceof TileEntityForceField && ((TileEntityForceField)tileEntity).cache_blockStateCamouflage != null) {
			return ((TileEntityForceField)tileEntity).cache_colorMultiplierCamouflage;
		}
		
		return super.colorMultiplier(blockAccess, blockPos);
	}
	/**/
	
	@Override
	public int getLightValue(@Nonnull final IBlockState blockState, final IBlockAccess blockAccess, @Nonnull final BlockPos blockPos) {
		final TileEntity tileEntity = blockAccess.getTileEntity(blockPos);
		if (tileEntity instanceof TileEntityForceField) {
			return ((TileEntityForceField)tileEntity).cache_lightCamouflage;
		}
		
		return 0;
	}
	
	private void downgrade(final World world, final BlockPos blockPos) {
		if (tier > 1) {
			final TileEntityForceFieldProjector tileEntityForceFieldProjector = getProjector(world, blockPos);
			final IBlockState blockState = world.getBlockState(blockPos);
			final int frequency = blockState.getValue(FREQUENCY);
			world.setBlockState(blockPos, WarpDrive.blockForceFields[tier - 2].getDefaultState().withProperty(FREQUENCY, (frequency + 1) % 16), 2);
			if (tileEntityForceFieldProjector != null) {
				final TileEntity tileEntity = world.getTileEntity(blockPos);
				if (tileEntity instanceof TileEntityForceField) {
					((TileEntityForceField) tileEntity).setProjector(new VectorI(tileEntityForceFieldProjector));
				}
			}
			
		} else {
			world.setBlockToAir(blockPos);
		}
	}
	
	/* @TODO MC1.10 explosion effect redesign
	private double log_explosionX;
	private double log_explosionY = -1.0D;
	private double log_explosionZ;
	
	@Override
	public float getExplosionResistance(final Entity exploder) {
		return super.getExplosionResistance(exploder);
	}
	@Override
	public float getExplosionResistance(Entity entity, World world, BlockPos blockPos, double explosionX, double explosionY, double explosionZ) {
		boolean enableFirstHit = (log_explosionX != explosionX || log_explosionY != explosionY || log_explosionZ != explosionZ); 
		if (enableFirstHit) {
			log_explosionX = explosionX;
			log_explosionY = explosionY;
			log_explosionZ = explosionZ;
		}
		
		// find explosion strength, defaults to no effect
		double strength = 0.0D;
		if ( entity == null
		  && explosionX == Math.rint(explosionX)
		  && explosionY == Math.rint(explosionY)
		  && explosionZ == Math.rint(explosionZ) ) {
			// IC2 Reactor blowing up => block is already air
			final IBlockState blockState = world.getBlockState(new BlockPos((int)explosionX, (int)explosionY, (int)explosionZ));
			final TileEntity tileEntity = world.getTileEntity(new BlockPos((int)explosionX, (int)explosionY, (int)explosionZ));
			if (enableFirstHit && WarpDriveConfig.LOGGING_FORCEFIELD) {
				WarpDrive.logger.info("Block at location is " + blockState.getBlock() + " " + blockState.getBlock().getUnlocalizedName() + " with tileEntity " + tileEntity);
			}
			// explosion with no entity and block removed, hence we can't compute the energy impact => boosting explosion resistance
			return 2.0F * super.getExplosionResistance(entity, world, blockPos, explosionX, explosionY, explosionZ);
		}
		
		if (entity != null) {
			switch (entity.getClass().toString()) {
			// Vanilla explosive
			case "class net.minecraft.entity.item.EntityEnderCrystal": strength = 6.0D; break;
			case "class net.minecraft.entity.item.EntityMinecartTNT": strength = 4.0D; break;
			case "class net.minecraft.entity.item.EntityTNTPrimed": strength = 5.0D; break;
			case "class net.minecraft.entity.monster.EntityCreeper": strength = 3.0D; break;  // *2 for powered ones
			
			// Applied Energistics Tiny TNT
			case "class appeng.entity.EntityTinyTNTPrimed": strength = 0.2D; break;
			
			// Blood Arsenal Blood TNT
			case "class com.arc.bloodarsenal.common.entity.EntityBloodTNT": strength = 1.0D; break;
			
			// IC2 explosives
			case "class ic2.core.block.EntityItnt": strength = 5.5D; break; 
			case "class ic2.core.block.EntityNuke": strength = 0.02D; break;
			case "class ic2.core.block.EntityDynamite": strength = 1.0D; break;
			case "class ic2.core.block.EntityStickyDynamite": strength = 1.0D; break;
			
			// ICBM Classic & DefenseTech S-mine (initial explosion)
			case "class defense.common.entity.EntityExplosion": strength = 1.0D; break;
			case "class icbm.classic.content.entity.EntityExplosion": strength = 1.0D; break;
			
			// ICBM Classic & DefenseTech Condensed, Incendiary, Repulsive, Attractive, Fragmentation, Sonic, Breaching, Thermobaric, Nuclear,
			// Exothermic, Endothermic, Anti-gravitational, Hypersonic, (Antimatter?)
			case "class defense.common.entity.EntityExplosive": strength = 15.0D; break;
			case "class icbm.classic.content.entity.EntityExplosive": strength = 15.0D; break;
			
			// ICBM Classic & DefenseTechFragmentation, S-mine fragments
			case "class defense.common.entity.EntityFragments": strength = 0.02D; break;
			case "class icbm.classic.content.entity.EntityFragments": strength = 0.02D; break;
			
			// ICBM Classic & DefenseTech Conventional, Attractive, Repulsive, Sonic, Breaching, Thermobaric, Nuclear, 
			// Exothermic, Endothermic, Anti-Gravitational, Hypersonic missile, (Antimatter?), (Red matter?), (Homing?), (Anti-Ballistic?)
			case "class defense.common.entity.EntityMissile": strength = 15.0D; break;
			case "class icbm.classic.content.entity.EntityMissile": strength = 15.0D; break;
			
			// ICBM Classic & DefenseTech Conventional/Incendiary/Repulsive grenade
			case "class defense.common.entity.EntityGrenade": strength = 3.0D; break;
			case "class icbm.classic.content.entity.EntityGrenade": strength = 3.0D; break;
			
			// Tinker's Construct SDX explosives
			case "class tconstruct.mechworks.entity.item.ExplosivePrimed": strength = 5.0D; break;
			
			default:
				if (enableFirstHit) {
					WarpDrive.logger.error("Unknown explosion source " + entity.getClass().toString() + " " + entity);
				}
				break;
			}
		}
		
		// apply damages to force field by consuming energy
		final Explosion explosion = new Explosion(world, entity, explosionX, explosionY, explosionZ, 4.0F);
		final Vector3 vDirection = new Vector3(x + 0.5D - explosionX, y + 0.5D - explosionY, z + 0.5D - explosionZ);
		final double magnitude = Math.max(1.0D, vDirection.getMagnitude());
		if (magnitude != 0) {// normalize
			vDirection.scale(1 / magnitude);
		}
		final double damageLevel = strength / (magnitude * magnitude) * 1.0D;
		double damageLeft = 0;
		final ForceFieldSetup forceFieldSetup = getForceFieldSetup(world, blockPos);
		if (forceFieldSetup != null) {
			damageLeft = forceFieldSetup.applyDamage(world, DamageSource.setExplosionSource(explosion), damageLevel);
		}
		
		assert(damageLeft >= 0);
		if (enableFirstHit && WarpDriveConfig.LOGGING_FORCEFIELD) {
			WarpDrive.logger.info( "BlockForceField(" + tier + " at " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ() + ")"
				                 + " involved in explosion " + ((entity != null) ? " from " + entity : " at " + explosionX + " " + explosionY + " " + explosionZ)
								 + (WarpDrive.isDev ? (" damageLevel " + damageLevel + " damageLeft " + damageLeft) : ""));
		}
		return super.getExplosionResistance(entity, world, blockPos, explosionX, explosionY, explosionZ);
	}
	/**/
	
	@Override
	public boolean canDropFromExplosion(final Explosion explosion) {
		return false;
	}
	
	@Override
	public void onBlockExploded(final World world, @Nonnull final BlockPos blockPos, @Nonnull final Explosion explosion) {
		downgrade(world, blockPos);
		super.onBlockExploded(world, blockPos, explosion);
	}
	
	@Override
	public void onEMP(final World world, final BlockPos blockPos, final float efficiency) {
		if (efficiency * (1.0F - 0.20F * (tier - 1)) > world.rand.nextFloat()) {
			downgrade(world, blockPos);
		}
		// already handled => no ancestor call
	}
	
	@Override
	public void onBlockDestroyedByExplosion(final World world, final BlockPos blockPos, final Explosion explosion) {
		// (block is already set to air by caller, see IC2 iTNT for example)
		downgrade(world, blockPos);
		super.onBlockDestroyedByExplosion(world, blockPos, explosion);
	}
	
	@Override
	public float getBlockHardness(final IBlockState blockState, final World world, final BlockPos blockPos,
	                              final DamageSource damageSource, final int damageParameter, final Vector3 damageDirection, final int damageLevel) {
		return WarpDriveConfig.HULL_HARDNESS[tier - 1];
	}
	
	@Override
	public int applyDamage(final IBlockState blockState, final World world, final BlockPos blockPos, final DamageSource damageSource,
	                       final int damageParameter, final Vector3 damageDirection, final int damageLevel) {
		final ForceFieldSetup forceFieldSetup = getForceFieldSetup(world, blockPos);
		if (forceFieldSetup != null) {
			return (int) Math.round(forceFieldSetup.applyDamage(world, damageSource, damageLevel));
		}
		
		return damageLevel;
	}
}
