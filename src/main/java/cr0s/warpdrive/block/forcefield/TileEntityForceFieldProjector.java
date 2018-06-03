package cr0s.warpdrive.block.forcefield;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBeamFrequency;
import cr0s.warpdrive.api.IForceFieldShape;
import cr0s.warpdrive.config.Dictionary;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.BlockProperties;
import cr0s.warpdrive.data.EnumForceFieldShape;
import cr0s.warpdrive.data.EnumForceFieldState;
import cr0s.warpdrive.data.EnumForceFieldUpgrade;
import cr0s.warpdrive.data.ForceFieldSetup;
import cr0s.warpdrive.data.SoundEvents;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.data.VectorI;
import cr0s.warpdrive.network.PacketHandler;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

import javax.annotation.Nonnull;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.Optional;

public class TileEntityForceFieldProjector extends TileEntityAbstractForceField {
	
	private static final int PROJECTOR_MAX_ENERGY_STORED = 30000;
	private static final int PROJECTOR_COOLDOWN_TICKS = 300;
	public static final int PROJECTOR_PROJECTION_UPDATE_TICKS = 8;
	private static final int PROJECTOR_SETUP_TICKS = 20;
	private static final int PROJECTOR_SOUND_UPDATE_TICKS = 60;
	private static final int PROJECTOR_GUIDE_UPDATE_TICKS = 300;
	
	// persistent properties
	public boolean isDoubleSided;
	private EnumForceFieldShape shape;
	// rotation provided by player, before applying block orientation
	private float rotationYaw;
	private float rotationPitch;
	private float rotationRoll;
	private Vector3 v3Min = new Vector3(-1.0D, -1.0D, -1.0D);
	private Vector3 v3Max = new Vector3( 1.0D,  1.0D,  1.0D);
	private Vector3 v3Translation = new Vector3( 0.0D,  0.0D,  0.0D);
	private boolean legacy_isOn = false;
	
	// computed properties
	private int maxEnergyStored;
	private int cooldownTicks;
	private int setupTicks;
	private int updateTicks;
	private int soundTicks;
	private int guideTicks;
	private double damagesEnergyCost = 0.0D;
	private final HashSet<UUID> setInteractedEntities = new HashSet<>();
	private boolean isPowered = true;
	private ForceFieldSetup cache_forceFieldSetup;
	private ForceFieldSetup legacy_forceFieldSetup;
	private double consumptionLeftOver = 0.0D;
	public EnumFacing enumFacing = EnumFacing.UP;
	public float rotation_deg = 0.0F;
	public float rotationSpeed_degPerTick = 2.0F;
	
	// carry over speed to next tick, useful for slow interactions
	private float carryScanSpeed;
	private float carryPlaceSpeed;
	
	// allow only one computation at a time
	private static final AtomicBoolean isGlobalThreadRunning = new AtomicBoolean(false);
	// computation is ongoing for this specific tile
	private final AtomicBoolean isThreadRunning = new AtomicBoolean(false);
	// parameters have changed, new computation is required
	private final AtomicBoolean isDirty = new AtomicBoolean(true);
	
	private Set<VectorI> calculated_interiorField = null;
	private Set<VectorI> calculated_forceField = null;
	private Iterator<VectorI> iteratorForcefield = null;
	
	// currently placed forcefield blocks
	private final Set<VectorI> vForceFields = new HashSet<>();
	
	// schedule removal/destruction in main thread
	final CopyOnWriteArraySet<VectorI> vForceFields_forRemoval = new CopyOnWriteArraySet<>();
	
	public TileEntityForceFieldProjector() {
		super();
		IC2_sinkTier = 4;
		IC2_sourceTier = 4;
		
		peripheralName = "warpdriveForceFieldProjector";
		addMethods(new String[] {
			"min",
			"max",
			"rotation",
			"state",
			"translation"
		});
		CC_scripts = Arrays.asList("enable", "disable");
		
		for (final EnumForceFieldUpgrade enumForceFieldUpgrade : EnumForceFieldUpgrade.values()) {
			if (enumForceFieldUpgrade.maxCountOnProjector > 0) {
				setUpgradeMaxCount(enumForceFieldUpgrade, enumForceFieldUpgrade.maxCountOnProjector);
			}
		}
	}
	
	@Override
	protected void onFirstUpdateTick() {
		super.onFirstUpdateTick();
		maxEnergyStored = PROJECTOR_MAX_ENERGY_STORED * (1 + 2 * tier);
		cooldownTicks = 0;
		setupTicks = worldObj.rand.nextInt(PROJECTOR_SETUP_TICKS);
		updateTicks = worldObj.rand.nextInt(PROJECTOR_PROJECTION_UPDATE_TICKS);
		guideTicks = PROJECTOR_GUIDE_UPDATE_TICKS;
		enumFacing = worldObj.getBlockState(pos).getValue(BlockProperties.FACING);
		rotation_deg = worldObj.rand.nextFloat() * 360.0F;
	}
	
	@Override
	public void update() {
		super.update();
		
		if (worldObj.isRemote) {
			rotationSpeed_degPerTick = 0.98F * rotationSpeed_degPerTick
			                         + 0.02F * getState().getRotationSpeed_degPerTick();
			rotation_deg += rotationSpeed_degPerTick;
			return;
		}
		
		// Frequency is not set
		if (!isConnected) {
			return;
		}
		
		// clear setup cache periodically
		setupTicks--;
		if (setupTicks <= 0) {
			setupTicks = PROJECTOR_SETUP_TICKS;
			if (cache_forceFieldSetup != null) {
				legacy_forceFieldSetup = cache_forceFieldSetup;
				cache_forceFieldSetup = null;
			}
		}
		
		// update counters
		if (cooldownTicks > 0) {
			cooldownTicks--;
		}
		if (guideTicks > 0) {
			guideTicks--;
		}
		
		// remove from main thread
		doScheduledForceFieldRemoval();
		
		// Powered ?
		final ForceFieldSetup forceFieldSetup = getForceFieldSetup();
		final int energyRequired;
		if (!legacy_isOn) {
			energyRequired = (int) Math.round(forceFieldSetup.startupEnergyCost + forceFieldSetup.placeEnergyCost * forceFieldSetup.placeSpeed * PROJECTOR_PROJECTION_UPDATE_TICKS / 20.0F);
		} else {
			energyRequired = (int) Math.round(                                    forceFieldSetup.scanEnergyCost * forceFieldSetup.scanSpeed * PROJECTOR_PROJECTION_UPDATE_TICKS / 20.0F);
		}
		if (energyRequired > energy_getMaxStorage()) {
			WarpDrive.logger.error("Force field projector requires " + energyRequired + " to get started but can only store " + energy_getMaxStorage());
		}
		final boolean new_isPowered = energy_getEnergyStored() >= energyRequired;
		if (isPowered != new_isPowered) {
			isPowered = new_isPowered;
			markDirty();
		}
		
		final boolean isEnabledAndValid = isEnabled && isValid();
		final boolean isOn = isEnabledAndValid && cooldownTicks <= 0 && isPowered;
		if (isOn) {
			if (!legacy_isOn) {
				consumeEnergy(forceFieldSetup.startupEnergyCost, false);
				if (WarpDriveConfig.LOGGING_FORCEFIELD) {
					WarpDrive.logger.info(this + " starting up...");
				}
				legacy_isOn = true;
				markDirty();
			}
			cooldownTicks = 0;
			
			final int countEntityInteractions = setInteractedEntities.size();
			if (countEntityInteractions > 0) {
				setInteractedEntities.clear();
				consumeEnergy(forceFieldSetup.getEntityEnergyCost(countEntityInteractions), false);
			}
			
			if (damagesEnergyCost > 0.0D) {
				if (WarpDriveConfig.LOGGING_FORCEFIELD) {
					WarpDrive.logger.info(String.format("%s damages received, energy lost: %.6f", toString(), damagesEnergyCost));
				}
				consumeEnergy(damagesEnergyCost, false);
				damagesEnergyCost = 0.0D;
			}
			
			updateTicks--;
			if (updateTicks <= 0) {
				updateTicks = PROJECTOR_PROJECTION_UPDATE_TICKS;
				if (!isCalculated()) {
					calculateForceField();
				} else {
					projectForceField();
				}
			}
			
			soundTicks--;
			if (soundTicks < 0) {
				soundTicks = PROJECTOR_SOUND_UPDATE_TICKS;
				if (!hasUpgrade(EnumForceFieldUpgrade.SILENCER)) {
					worldObj.playSound(null, pos, SoundEvents.PROJECTING, SoundCategory.BLOCKS, 1.0F, 0.85F + 0.15F * worldObj.rand.nextFloat());
				}
			}
			
		} else {
			if (legacy_isOn) {
				if (WarpDriveConfig.LOGGING_FORCEFIELD) {
					WarpDrive.logger.info(this + " shutting down...");
				}
				legacy_isOn = false;
				markDirty();
				cooldownTicks = PROJECTOR_COOLDOWN_TICKS;
				guideTicks = 0;
			}
			destroyForceField();
			
			if (isEnabledAndValid) {
				if (guideTicks <= 0) {
					guideTicks = PROJECTOR_GUIDE_UPDATE_TICKS;
					
					final ITextComponent msg = Commons.getChatPrefix(getBlockType())
					    .appendSibling(new TextComponentTranslation("warpdrive.forcefield.guide.low_power"));
					
					final AxisAlignedBB axisalignedbb = new AxisAlignedBB(pos.getX() - 10, pos.getY() - 10, pos.getZ() - 10, pos.getX() + 10, pos.getY() + 10, pos.getZ() + 10);
					final List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(null, axisalignedbb);
					
					for (final Entity entity : list) {
						if ( (!(entity instanceof EntityPlayer))
						  || entity instanceof FakePlayer ) {
							continue;
						}
						
						Commons.addChatMessage(entity, msg);
					}
				}
			}
		}
	}
	
	@Override
	public void invalidate() {
		destroyForceField();
		try {
			doScheduledForceFieldRemoval();
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
		super.invalidate();
	}
	
	public boolean isValid() {
		return getShape() != EnumForceFieldShape.NONE;
	}
	
	public boolean isCalculated() {
		return !isDirty.get() && !isThreadRunning.get();
	}
	
	private void calculateForceField() {
		if ((!worldObj.isRemote) && isValid()) {
			if (!isGlobalThreadRunning.getAndSet(true)) {
				if (WarpDriveConfig.LOGGING_FORCEFIELD) {
					WarpDrive.logger.info("Calculation initiated for " + this);
				}
				isThreadRunning.set(true);
				isDirty.set(false);
				iteratorForcefield = null;
				calculated_interiorField = null;
				calculated_forceField = null;
				vForceFields.clear();
				
				new ThreadCalculation(this).start();
			}
		}
	}
	
	private void calculation_done(final Set<VectorI> interiorField, final Set<VectorI> forceField) {
		if (WarpDriveConfig.LOGGING_FORCEFIELD) {
			WarpDrive.logger.info("Calculation done for " + this);
		}
		if (interiorField == null || forceField == null) {
			calculated_interiorField = new HashSet<>(0);
			calculated_forceField = new HashSet<>(0);
		} else {
			calculated_interiorField = interiorField;
			calculated_forceField = forceField;
		}
		isThreadRunning.set(false);
		isGlobalThreadRunning.set(false);
	}
	
	boolean isOn() {
		return legacy_isOn;
	}
	
	boolean isPartOfForceField(final VectorI vector) {
		if (!isEnabled || !isValid()) {
			return false;
		}
		if (!isCalculated()) {
			return true;
		}
		// only consider the forcefield itself
		return calculated_forceField.contains(vector);
	}
	
	private boolean isPartOfInterior(final VectorI vector) {
		if (!isEnabled || !isValid()) {
			return false;
		}
		if (!isCalculated()) {
			return false;
		}
		// only consider the forcefield interior
		return calculated_interiorField.contains(vector);
	}
	
	public boolean onEntityInteracted(final UUID uniqueID) {
		return setInteractedEntities.add(uniqueID);
	}
	
	public void onEnergyDamage(final double energyCost) {
		damagesEnergyCost += energyCost;
	}
	
	private void projectForceField() {
		assert(!worldObj.isRemote && isCalculated());
		
		final ForceFieldSetup forceFieldSetup = getForceFieldSetup();
		
		// compute maximum number of blocks to scan
		int countScanned = 0;
		final float floatScanSpeed = Math.min(forceFieldSetup.scanSpeed * PROJECTOR_PROJECTION_UPDATE_TICKS / 20.0F + carryScanSpeed, calculated_forceField.size());
		final int countMaxScanned = (int)Math.floor(floatScanSpeed);
		carryScanSpeed = floatScanSpeed - countMaxScanned;
		
		// compute maximum number of blocks to place
		int countPlaced = 0;
		final float floatPlaceSpeed = Math.min(forceFieldSetup.placeSpeed * PROJECTOR_PROJECTION_UPDATE_TICKS / 20.0F + carryPlaceSpeed, calculated_forceField.size());
		final int countMaxPlaced = (int)Math.floor(floatPlaceSpeed);
		carryPlaceSpeed = floatPlaceSpeed - countMaxPlaced;
		
		// evaluate force field block metadata
		IBlockState blockStateForceField = WarpDrive.blockForceFields[tier - 1].getStateFromMeta(Math.min(15, (beamFrequency * 16) / IBeamFrequency.BEAM_FREQUENCY_MAX));
		if (forceFieldSetup.getCamouflageBlockState() != null) {
			blockStateForceField = WarpDrive.blockForceFields[tier - 1].getStateFromMeta(
					forceFieldSetup.getCamouflageBlockState().getBlock().getMetaFromState(forceFieldSetup.getCamouflageBlockState()) );
		}
		
		VectorI vector;
		IBlockState blockState;
		boolean doProjectThisBlock;
		
		while ( countScanned < countMaxScanned
		     && countPlaced < countMaxPlaced
			 && consumeEnergy(Math.max(forceFieldSetup.scanEnergyCost, forceFieldSetup.placeEnergyCost), true)) {
			if (iteratorForcefield == null || !iteratorForcefield.hasNext()) {
				iteratorForcefield = calculated_forceField.iterator();
			}
			countScanned++;
			
			vector = iteratorForcefield.next();
			
			// skip non loaded chunks
			if ( !worldObj.isBlockLoaded(vector.getBlockPos(), false)
			  || !worldObj.getChunkFromBlockCoords(vector.getBlockPos()).isLoaded() ) {
				continue;
			}

			blockState = vector.getBlockState(worldObj);
			doProjectThisBlock = true;
			
			// skip if fusion upgrade is present and it's inside another projector area
			if (forceFieldSetup.hasFusion) {
				for (final TileEntityForceFieldProjector projector : forceFieldSetup.projectors) {
					if (projector.isPartOfInterior(vector)) {
						doProjectThisBlock = false;
						break;
					}
				}
			}
			
			Fluid fluid = null;
			
			// skip if block properties prevents it
			if ( doProjectThisBlock
			  && (blockState.getBlock() != Blocks.TALLGRASS)
			  && (blockState.getBlock() != Blocks.DEADBUSH)
			  && !Dictionary.BLOCKS_EXPANDABLE.contains(blockState.getBlock()) ) {
				// MFR laser is unbreakable and replaceable
				// Liquid, vine and snow are replaceable
				final Block block = blockState.getBlock();
				if ( block instanceof BlockLiquid
				  || block instanceof IFluidBlock ) {
					// BlockLiquid remapped by CoFH
					// minecraft:flowing_water:0 = source
					// minecraft:flowing_water:1 2 = flow
					// minecraft:flowing_lava:1 2 4 6 = flow
					// BlockLiquid in Vanilla ?
					// minecraft:water:1 =
					// minecraft:lava:6 =
					
					fluid = block instanceof IFluidBlock ? ((IFluidBlock) block).getFluid() : Commons.fluid_getByBlock(block);
					if (WarpDriveConfig.LOGGING_FORCEFIELD) {
						WarpDrive.logger.info(String.format("Block %s %s Fluid %s with viscosity %d, projector max is %.1f: %s %s",
						                                    block.getUnlocalizedName(),
						                                    blockState,
						                                    fluid == null ? null : fluid.getName(),
						                                    fluid == null ? 0 : fluid.getViscosity(),
						                                    forceFieldSetup.pumping_maxViscosity,
						                                    block, fluid));
					}
					if (fluid == null) {
						if ((worldObj.getWorldTime() & 0xFF) == 0) {
							WarpDrive.logger.error(String.format("Block %s %s is not a valid fluid! %s",
							                                     block.getUnlocalizedName(),
							                                     blockState,
							                                     block));
						}
						doProjectThisBlock = false;
					} else {
						doProjectThisBlock = forceFieldSetup.pumping_maxViscosity >= fluid.getViscosity();
					}
					
				} else if (forceFieldSetup.breaking_maxHardness > 0) {
					final float blockHardness = blockState.getBlockHardness(worldObj, vector.getBlockPos());
					// stops on unbreakable or too hard
					if (blockHardness == -1.0F || blockHardness > forceFieldSetup.breaking_maxHardness || worldObj.isAirBlock(vector.getBlockPos())) {
						doProjectThisBlock = false;
					}
					
				} else {// doesn't have disintegration, not a liquid
					
					// recover force field blocks
					if (blockState.getBlock() instanceof BlockForceField) {
						final TileEntity tileEntity = vector.getTileEntity(worldObj);
						if (!(tileEntity instanceof TileEntityForceField)) {
							// missing a valid tile entity
							// => force a new placement
							worldObj.setBlockToAir(vector.getBlockPos());
							blockState = Blocks.AIR.getDefaultState();
							
						} else {
							final TileEntityForceField tileEntityForceField = ((TileEntityForceField)tileEntity);
							final TileEntityForceFieldProjector tileEntityForceFieldProjector = tileEntityForceField.getProjector();
							if (tileEntityForceFieldProjector == null) {
								// orphan force field, probably from an explosion
								// => recover it
								tileEntityForceField.setProjector(new VectorI(this));
								tileEntityForceField.cache_blockStateCamouflage = forceFieldSetup.getCamouflageBlockState();
								worldObj.setBlockState(vector.getBlockPos(), tileEntityForceField.cache_blockStateCamouflage, 2);
								
							} else if (tileEntityForceFieldProjector == this) {// this is ours
								if ( tileEntityForceField.cache_blockStateCamouflage.equals(forceFieldSetup.getCamouflageBlockState())
								  || blockState.equals(blockStateForceField) ) {
									// camouflage changed while chunk wasn't loaded or de-synchronisation
									// force field downgraded during explosion
									// => force a new placement
									worldObj.setBlockToAir(vector.getBlockPos());
									blockState = Blocks.AIR.getDefaultState();
								}
							}
						}
					}
					
					doProjectThisBlock = blockState.getBlock().isReplaceable(worldObj, vector.getBlockPos()) || (blockState.getBlock() == WarpDrive.blockForceFields[tier - 1]);
				}
			}
			
			// skip if area is protected
			if (doProjectThisBlock) {
				if (forceFieldSetup.breaking_maxHardness > 0) {
					doProjectThisBlock = ! isBlockBreakCanceled(null, worldObj, vector.getBlockPos());
				} else if (!(blockState.getBlock() instanceof BlockForceField)) {
					doProjectThisBlock = ! isBlockPlaceCanceled(null, worldObj, vector.getBlockPos(), blockStateForceField);
				}
			}
			
			if (doProjectThisBlock) {
				if ((blockState.getBlock() != WarpDrive.blockForceFields[tier - 1]) && (!vector.equals(this))) {
					boolean hasPlaced = false;
					if (fluid != null) {
						hasPlaced = true;
						doPumping(forceFieldSetup, blockStateForceField, vector, blockState, fluid);
						
					} else if (forceFieldSetup.breaking_maxHardness > 0) {
						hasPlaced = true;
						if (doBreaking(forceFieldSetup, vector, blockState)) {
							return;
						}
						
					} else if (forceFieldSetup.hasStabilize) {
						hasPlaced = true;
						if (doStabilize(forceFieldSetup, vector)) {
							return;
						}
						
					} else if (forceFieldSetup.isInverted && (forceFieldSetup.temperatureLevel < 295.0F || forceFieldSetup.temperatureLevel > 305.0F)) {
						doTerraforming(forceFieldSetup, vector, blockState);
						
					} else if (!forceFieldSetup.isInverted) {
						hasPlaced = true;
						worldObj.setBlockState(vector.getBlockPos(), blockStateForceField, 2);
						
						final TileEntity tileEntity = worldObj.getTileEntity(vector.getBlockPos());
						if (tileEntity instanceof TileEntityForceField) {
							((TileEntityForceField) tileEntity).setProjector(new VectorI(this));
						}
						
						vForceFields.add(vector);
					}
					if (hasPlaced) {
						countPlaced++;
						consumeEnergy(forceFieldSetup.placeEnergyCost, false);
					} else {
						consumeEnergy(forceFieldSetup.scanEnergyCost, false);
					}
					
				} else {
					// scanning a valid position
					consumeEnergy(forceFieldSetup.scanEnergyCost, false);
					
					// recover forcefield blocks from recalculation or chunk loading
					if (blockState.getBlock() == WarpDrive.blockForceFields[tier - 1] && !vForceFields.contains(vector)) {
						final TileEntity tileEntity = worldObj.getTileEntity(vector.getBlockPos());
						if (tileEntity instanceof TileEntityForceField && (((TileEntityForceField) tileEntity).getProjector() == this)) {
							vForceFields.add(vector);
						}
					}
				}
				
			} else {
				// scanning an invalid position
				consumeEnergy(forceFieldSetup.scanEnergyCost, false);
				
				// remove our own force field block
				if (blockState.getBlock() == WarpDrive.blockForceFields[tier - 1]) {
					assert(blockState.getBlock() instanceof BlockForceField);
					if (((BlockForceField) blockState.getBlock()).getProjector(worldObj, vector.getBlockPos()) == this) {
						worldObj.setBlockToAir(vector.getBlockPos());
						vForceFields.remove(vector);
					}
				}
			}
		}
	}
	
	private void doPumping(final ForceFieldSetup forceFieldSetup, final IBlockState blockStateForceField, final VectorI vector,
	                       final IBlockState blockState,
	                       final Fluid fluid) {
		final Block block = blockState.getBlock();
		final int metadata = block.getMetaFromState(blockState);
		final boolean isForceFluid = block instanceof IFluidBlock;
		final boolean isSourceBlock = ( block instanceof BlockLiquid
		                             && metadata == 0 )
		                           || ( isForceFluid
		                             && ((IFluidBlock) block).canDrain(worldObj, vector.getBlockPos()) );
		if (isSourceBlock) {
			final FluidStack fluidStack;
			if (isForceFluid) {
				fluidStack = ((IFluidBlock) block).drain(worldObj, vector.getBlockPos(), true);
			} else {
				fluidStack = new FluidStack(fluid, 1000);
			}
			if (WarpDriveConfig.LOGGING_FORCEFIELD) {
				WarpDrive.logger.info("Fluid source found! " + fluidStack.getUnlocalizedName() + " x " + fluidStack.amount + " mL");
			}
			// @TODO store fluid using IFluidHandler
			
		} else {
			if (WarpDriveConfig.LOGGING_FORCEFIELD) {
				WarpDrive.logger.info("Fluid flow found! " + fluid.getUnlocalizedName());
			}
		}
		
		if (forceFieldSetup.isInverted || forceFieldSetup.breaking_maxHardness > 0) {
			if (!(isForceFluid && isSourceBlock)) {
				worldObj.setBlockState(vector.getBlockPos(), Blocks.AIR.getDefaultState(), 2);
			}
		} else {
			worldObj.setBlockState(vector.getBlockPos(), blockStateForceField, 2);
			
			final TileEntity tileEntity = worldObj.getTileEntity(vector.getBlockPos());
			if (tileEntity instanceof TileEntityForceField) {
				((TileEntityForceField) tileEntity).setProjector(new VectorI(this));
			}
			
			vForceFields.add(vector);
		}
	}
	
	private boolean doStabilize(final ForceFieldSetup forceFieldSetup, final VectorI vector) {
		int slotIndex = 0;
		boolean found = false;
		int countItemBlocks = 0;
		ItemStack itemStack = null;
		Block blockToPlace = null;
		int metadataToPlace = -1;
		IInventory inventory = null;
		for (final IInventory inventoryLoop : forceFieldSetup.inventories) {
			if (!found) {
				slotIndex = 0;
			}
			while (slotIndex < inventoryLoop.getSizeInventory() && !found) {
				itemStack = inventoryLoop.getStackInSlot(slotIndex);
				if (itemStack == null || itemStack.stackSize <= 0) {
					slotIndex++;
					continue;
				}
				blockToPlace = Block.getBlockFromItem(itemStack.getItem());
				if (blockToPlace == Blocks.AIR) {
					slotIndex++;
					continue;
				}
				countItemBlocks++;
				metadataToPlace = itemStack.getItem().getMetadata(itemStack.getItemDamage());
				if (metadataToPlace == 0 && itemStack.getItemDamage() != 0) {
					metadataToPlace = itemStack.getItemDamage();
				}
				if (WarpDriveConfig.LOGGING_FORCEFIELD) {
					WarpDrive.logger.info("Slot " + slotIndex + " as " + itemStack + " known as block " + blockToPlace + ":" + metadataToPlace);
				}
				
				if (!blockToPlace.canPlaceBlockAt(worldObj, vector.getBlockPos())) {
					slotIndex++;
					continue;
				}
				// TODO place block using ItemBlock.place?
				
				found = true;
				inventory = inventoryLoop;
			}
		}
		
		// no ItemBlocks found at all
		if (countItemBlocks <= 0) {
			// skip the next scans...
			return true;
		}
		
		if (inventory == null) {
			if (WarpDriveConfig.LOGGING_FORCEFIELD) {
				WarpDrive.logger.debug("No item to place found");
			}
			// skip the next scans...
			return true;
		}
		//noinspection ConstantConditions
		assert(found);
		
		// check area protection
		if (isBlockPlaceCanceled(null, worldObj, vector.getBlockPos(), blockToPlace.getStateFromMeta(metadataToPlace))) {
			if (WarpDriveConfig.LOGGING_FORCEFIELD) {
				WarpDrive.logger.info(this + " Placing cancelled at (" + vector.x + " " + vector.y + " " + vector.z + ")");
			}
			// skip the next scans...
			return true;
		}
		
		itemStack.stackSize--;
		if (itemStack.stackSize <= 0) {
			itemStack = null;
		}
		inventory.setInventorySlotContents(slotIndex, itemStack);
		
		final int age = Math.max(10, Math.round((4 + worldObj.rand.nextFloat()) * WarpDriveConfig.MINING_LASER_MINE_DELAY_TICKS));
		PacketHandler.sendBeamPacket(worldObj, new Vector3(this).translate(0.5D), new Vector3(vector.x, vector.y, vector.z).translate(0.5D),
			0.2F, 0.7F, 0.4F, age, 0, 50);
		// worldObj.playSound(null, pos, SoundEvents.LASER_LOW, SoundCategory.BLOCKS, 4.0F, 1.0F);
		
		// standard place sound effect
		worldObj.playSound(null, vector.getBlockPos(), blockToPlace.getSoundType().getPlaceSound(), SoundCategory.BLOCKS,
				(blockToPlace.getSoundType().getVolume() + 1.0F) / 2.0F, blockToPlace.getSoundType().getPitch() * 0.8F);
		
		worldObj.setBlockState(vector.getBlockPos(), blockToPlace.getStateFromMeta(metadataToPlace), 3);
		return false;
	}
	
	private void doTerraforming(final ForceFieldSetup forceFieldSetup, final VectorI vector, final IBlockState blockState) {
		assert(vector != null);
		assert(blockState != null);
		if (forceFieldSetup.temperatureLevel > 300.0F) {
			
		} else {
			
		}
		// TODO glass <> sandstone <> sand <> gravel <> cobblestone <> stone <> obsidian
		// TODO ice <> snow <> water <> air > fire
		// TODO obsidian < lava
	}
	
	private boolean doBreaking(final ForceFieldSetup forceFieldSetup, final VectorI vector, final IBlockState blockState) {
		List<ItemStack> itemStacks;
		try {
			itemStacks = blockState.getBlock().getDrops(worldObj, vector.getBlockPos(), blockState, 0);
		} catch (final Exception exception) {// protect in case the mined block is corrupted
			exception.printStackTrace();
			itemStacks = null;
		}
		
		if (itemStacks != null) {
			if (forceFieldSetup.hasCollection) {
				if (addToInventories(itemStacks, forceFieldSetup.inventories)) {
					return true;
				}
			} else {
				for (final ItemStack itemStackDrop : itemStacks) {
					final ItemStack drop = itemStackDrop.copy();
					final EntityItem entityItem = new EntityItem(worldObj, vector.x + 0.5D, vector.y + 1.0D, vector.z + 0.5D, drop);
					worldObj.spawnEntityInWorld(entityItem);
				}
			}
		}
		final int age = Math.max(10, Math.round((4 + worldObj.rand.nextFloat()) * WarpDriveConfig.MINING_LASER_MINE_DELAY_TICKS));
		PacketHandler.sendBeamPacket(worldObj, new Vector3(vector.x, vector.y, vector.z).translate(0.5D), new Vector3(this).translate(0.5D),
			0.7F, 0.4F, 0.2F, age, 0, 50);
		// standard harvest block effect
		worldObj.playEvent(2001, vector.getBlockPos(), Block.getStateId(blockState));
		worldObj.setBlockToAir(vector.getBlockPos());
		return false;
	}
	
	private void destroyForceField() {
		if (worldObj == null || worldObj.isRemote) {
			return;
		}
		
		// invalidate() can be multi-threaded, so we're delaying the destruction
		if (!Commons.isSafeThread()) {
			vForceFields_forRemoval.addAll(vForceFields);
			vForceFields.clear();
			
			// add calculated blocks only once
			if ( isCalculated()
			  && vForceFields_forRemoval.size() < calculated_forceField.size() ) {
				vForceFields_forRemoval.addAll(calculated_forceField);
			}
			
			// force a reboot
			legacy_isOn = false;
			
			return;
		}
		
		if (!vForceFields.isEmpty()) {
			final VectorI[] vForceFields_cache = vForceFields.toArray(new VectorI[0]);
			vForceFields.clear();
			
			for (final VectorI vector : vForceFields_cache) {
				final IBlockState blockState = vector.getBlockState(worldObj);
				if (blockState.getBlock() == WarpDrive.blockForceFields[tier - 1]) {
					worldObj.setBlockToAir(vector.getBlockPos());
				}
			}
			
			if (isCalculated()) {
				for (final VectorI vector : calculated_forceField) {
					final Block block = vector.getBlock(worldObj);
					
					if (block == WarpDrive.blockForceFields[tier - 1]) {
						final TileEntity tileEntity = worldObj.getTileEntity(vector.getBlockPos());
						if ( tileEntity instanceof TileEntityForceField
						  && (((TileEntityForceField) tileEntity).getProjector() == this) ) {
							worldObj.setBlockToAir(vector.getBlockPos());
						}
					}
				}
			}
			
			if (isCalculated()) {
				for (final VectorI vector : calculated_forceField) {
					final Block block = vector.getBlock(worldObj);
					
					if (block == WarpDrive.blockForceFields[tier - 1]) {
						final BlockPos blockPos = vector.getBlockPos();
						final TileEntity tileEntity = worldObj.getTileEntity(blockPos);
						if ( tileEntity instanceof TileEntityForceField
						  && (((TileEntityForceField) tileEntity).getProjector() == this) ) {
							worldObj.setBlockToAir(blockPos);
						}
					}
				}
			}
		}
		
		// force a reboot
		legacy_isOn = false;
	}
	
	private void doScheduledForceFieldRemoval() {
		if (!Commons.isSafeThread()) {
			WarpDrive.logger.warn("Removing force field blocks outside main thread, bad things may happen...");
		}
		final VectorI[] vForceFields_cache = vForceFields_forRemoval.toArray(new VectorI[0]);
		vForceFields_forRemoval.clear();
		
		for (final VectorI vector : vForceFields_cache) {
			final IBlockState blockState = vector.getBlockState(worldObj);
			
			if (blockState.getBlock() == WarpDrive.blockForceFields[tier - 1]) {
				final TileEntity tileEntity = worldObj.getTileEntity(vector.getBlockPos());
				if ( tileEntity instanceof TileEntityForceField
				  && (((TileEntityForceField) tileEntity).getProjector() == this) ) {
					worldObj.setBlockToAir(vector.getBlockPos());
				}
			}
		}
	}
	
	public IForceFieldShape getShapeProvider() {
		return getShape();
	}
	
	@Override
	public int getBeamFrequency() {
		return beamFrequency;
	}
	
	@Override
	public void setBeamFrequency(final int beamFrequency) {
		super.setBeamFrequency(beamFrequency);
		cache_forceFieldSetup = null;
	}
	
	public Vector3 getMin() {
		return v3Min;
	}
	
	private void setMin(final float x, final float y, final float z) {
		v3Min = new Vector3(Commons.clamp(-1.0D, 0.0D, x), Commons.clamp(-1.0D, 0.0D, y), Commons.clamp(-1.0D, 0.0D, z));
	}
	
	public Vector3 getMax() {
		return v3Max;
	}
	
	private void setMax(final float x, final float y, final float z) {
		v3Max = new Vector3(Commons.clamp(0.0D, 1.0D, x), Commons.clamp(0.0D, 1.0D, y), Commons.clamp(0.0D, 1.0D, z));
	}
	
	public float getRotationYaw() {
		final int metadata = getBlockMetadata();
		float totalYaw;
		switch (EnumFacing.getFront(metadata & 7)) {
		case DOWN : totalYaw =   0.0F; break;
		case UP   : totalYaw =   0.0F; break;
		case NORTH: totalYaw =  90.0F; break;
		case SOUTH: totalYaw = 270.0F; break;
		case WEST : totalYaw =   0.0F; break;
		case EAST : totalYaw = 180.0F; break;
		default   : totalYaw =   0.0F; break;
		}
		if (hasUpgrade(EnumForceFieldUpgrade.ROTATION)) {
			totalYaw += rotationYaw;
		}
		return (totalYaw + 540.0F) % 360.0F - 180.0F; 
	}
	
	public float getRotationPitch() {
		final int metadata = getBlockMetadata();
		float totalPitch;
		switch (EnumFacing.getFront(metadata & 7)) {
		case DOWN : totalPitch =  180.0F; break;
		case UP   : totalPitch =    0.0F; break;
		case NORTH: totalPitch =  -90.0F; break;
		case SOUTH: totalPitch =  -90.0F; break;
		case WEST : totalPitch =  -90.0F; break;
		case EAST : totalPitch =  -90.0F; break;
		default   : totalPitch =    0.0F; break;
		}
		if (hasUpgrade(EnumForceFieldUpgrade.ROTATION)) {
			totalPitch += rotationPitch;
		}
		return (totalPitch + 540.0F) % 360.0F - 180.0F;
	}
	
	public float getRotationRoll() {
		if (hasUpgrade(EnumForceFieldUpgrade.ROTATION)) {
			return (rotationRoll + 540.0F) % 360.0F - 180.0F;
		} else {
			return 0.0F;
		}
	}
	
	private void setRotation(final float rotationYaw, final float rotationPitch, final float rotationRoll) {
		final float oldYaw = this.rotationYaw;
		final float oldPitch = this.rotationPitch;
		final float oldRoll = this.rotationRoll;
		this.rotationYaw = Commons.clamp( -45.0F, +45.0F, rotationYaw);
		this.rotationPitch = Commons.clamp( -45.0F, +45.0F, rotationPitch);
		this.rotationRoll = (rotationRoll + 720.0F) % 360.0F - 180.0F;
		if ( oldYaw != this.rotationYaw
		  || oldPitch != this.rotationPitch
		  || oldRoll != this.rotationRoll ) {
			cache_forceFieldSetup = null;
		}
	}
	
	public EnumForceFieldShape getShape() {
		if (shape == null) {
			return EnumForceFieldShape.NONE;
		}
		return shape;
	}
	
	void setShape(final EnumForceFieldShape shape) {
		if (this.shape != shape) {
			this.shape = shape;
			cache_forceFieldSetup = null;
			
			// refresh block rendering
			markDirty();
		}
	}
	
	public EnumForceFieldState getState() {
		EnumForceFieldState forceFieldState = EnumForceFieldState.NOT_CONNECTED;
		if (isConnected && isValid()) {
			if (isPowered) {
				if (isOn()) {
					forceFieldState = EnumForceFieldState.CONNECTED_POWERED;
				} else {
					forceFieldState = EnumForceFieldState.CONNECTED_OFFLINE;
				}
			} else {
				forceFieldState = EnumForceFieldState.CONNECTED_NOT_POWERED;
			}
		}
		return forceFieldState;
	}
	
	public Vector3 getTranslation() {
		if (hasUpgrade(EnumForceFieldUpgrade.TRANSLATION)) {
			return v3Translation;
		} else {
			return new Vector3(0.0D, 0.0D, 0.0D);
		}
	}
	
	private void setTranslation(final float x, final float y, final float z) {
		final Vector3 oldTranslation = this.v3Translation;
		v3Translation = new Vector3(Commons.clamp(-1.0D, 1.0D, x), Commons.clamp(-1.0D, 1.0D, y), Commons.clamp(-1.0D, 1.0D, z));
		if (!oldTranslation.equals(v3Translation)) {
			cache_forceFieldSetup = null;
		}
	}
	
	@Override
	public boolean mountUpgrade(final Object upgrade) {
		if  (super.mountUpgrade(upgrade)) {
			cache_forceFieldSetup = null;
			return true;
		}
		return false;
	}
	
	@Override
	public boolean dismountUpgrade(final Object upgrade) {
		if (super.dismountUpgrade(upgrade)) {
			cache_forceFieldSetup = null;
			return true;
		}
		return false;
	}
	
	private ITextComponent getShapeStatus() {
		final EnumForceFieldShape enumForceFieldShape = getShape();
		final ITextComponent displayName = new TextComponentTranslation("warpdrive.forcefield.shape.status_line." + enumForceFieldShape.getName());
		if (enumForceFieldShape == EnumForceFieldShape.NONE) {
			return new TextComponentTranslation("warpdrive.forcefield.shape.status_line.none", 
				displayName);
		} else if (isDoubleSided) {
			return new TextComponentTranslation("warpdrive.forcefield.shape.status_line.double",
				displayName);
		} else {
			return new TextComponentTranslation("warpdrive.forcefield.shape.status_line.single", 
				displayName);
		}
	}
	
	@Override
	public ITextComponent getStatus() {
		return super.getStatus()
			.appendSibling(new TextComponentString("\n")).appendSibling(getShapeStatus());
	}
	
	@Override
	public void readFromNBT(final NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		isDoubleSided = tagCompound.getBoolean("isDoubleSided");
		
		if (tagCompound.hasKey("minX")) {
			setMin(tagCompound.getFloat("minX"), tagCompound.getFloat("minY"), tagCompound.getFloat("minZ"));
		} else {
			setMin(-1.0F, -1.0F, -1.0F);
		}
		if (tagCompound.hasKey("maxX")) {
			setMax(tagCompound.getFloat("maxX"), tagCompound.getFloat("maxY"), tagCompound.getFloat("maxZ"));
		} else {
			setMax(1.0F, 1.0F, 1.0F);
		}
		
		setRotation(tagCompound.getFloat("rotationYaw"), tagCompound.getFloat("rotationPitch"), tagCompound.getFloat("rotationRoll"));
		
		setShape(EnumForceFieldShape.get(tagCompound.getByte("shape")));
		
		setTranslation(tagCompound.getFloat("translationX"), tagCompound.getFloat("translationY"), tagCompound.getFloat("translationZ"));
		
		legacy_isOn = tagCompound.getBoolean("isOn");
		
		isPowered = tagCompound.getBoolean("isPowered");
	}
	
	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeToNBT(tagCompound);
		tagCompound.setBoolean("isDoubleSided", isDoubleSided);
		
		if (v3Min.x != -1.0D || v3Min.y != -1.0D || v3Min.z != -1.0D) {
			tagCompound.setFloat("minX", (float)v3Min.x);
			tagCompound.setFloat("minY", (float)v3Min.y);
			tagCompound.setFloat("minZ", (float)v3Min.z);
		}
		if (v3Max.x !=  1.0D || v3Max.y !=  1.0D || v3Max.z !=  1.0D) {
			tagCompound.setFloat("maxX", (float)v3Max.x);
			tagCompound.setFloat("maxY", (float)v3Max.y);
			tagCompound.setFloat("maxZ", (float)v3Max.z);
		}
		
		if (rotationYaw != 0.0F) {
			tagCompound.setFloat("rotationYaw", rotationYaw);
		}
		if (rotationPitch != 0.0F) {
			tagCompound.setFloat("rotationPitch", rotationPitch);
		}
		if (rotationRoll != 0.0F) {
			tagCompound.setFloat("rotationRoll", rotationRoll);
		}
		
		tagCompound.setByte("shape", (byte) getShape().ordinal());
		
		if (v3Translation.x !=  0.0D || v3Translation.y !=  0.0D || v3Translation.z !=  0.0D) {
			tagCompound.setFloat("translationX", (float)v3Translation.x);
			tagCompound.setFloat("translationY", (float)v3Translation.y);
			tagCompound.setFloat("translationZ", (float)v3Translation.z);
		}
		
		tagCompound.setBoolean("isOn", legacy_isOn);
		
		tagCompound.setBoolean("isPowered", isPowered);
		return tagCompound;
	}
	
	@Nonnull
	@Override
	public NBTTagCompound getUpdateTag() {
		final NBTTagCompound tagCompound = super.getUpdateTag();
		return writeToNBT(tagCompound);
	}
	
	@Override
	public void onDataPacket(final NetworkManager networkManager, final SPacketUpdateTileEntity packet) {
		final NBTTagCompound tagCompound = packet.getNbtCompound();
		readFromNBT(tagCompound);
	}
	
	public ForceFieldSetup getForceFieldSetup() {
		if (cache_forceFieldSetup == null) {
			// don't try until projector has initialised (see: entities colliding with the world during chunk loading)
			if (isFirstTick()) {
				return null;
			}
			
			cache_forceFieldSetup = new ForceFieldSetup(worldObj.provider.getDimension(), pos, tier, beamFrequency);
			setupTicks = Math.max(setupTicks, 10);
			
			// reset field in case of major changes
			if (legacy_forceFieldSetup != null) {
				final int energyRequired = (int) Math.max(0, Math.round(cache_forceFieldSetup.startupEnergyCost - legacy_forceFieldSetup.startupEnergyCost));
				if ( !legacy_forceFieldSetup.getCamouflageBlockState().equals(cache_forceFieldSetup.getCamouflageBlockState())
				  || legacy_forceFieldSetup.beamFrequency != cache_forceFieldSetup.beamFrequency
				  || !energy_consume(energyRequired, false)) {
					if (WarpDriveConfig.LOGGING_FORCEFIELD) {
						WarpDrive.logger.info(this + " rebooting with new rendering...");
					}
					destroyForceField();
					
				} else if ( legacy_forceFieldSetup.isInverted != cache_forceFieldSetup.isInverted
				         || legacy_forceFieldSetup.shapeProvider != cache_forceFieldSetup.shapeProvider
				         || legacy_forceFieldSetup.thickness != cache_forceFieldSetup.thickness
				         || !legacy_forceFieldSetup.vMin.equals(cache_forceFieldSetup.vMin)
				         || !legacy_forceFieldSetup.vMax.equals(cache_forceFieldSetup.vMax)
				         || !legacy_forceFieldSetup.vTranslation.equals(cache_forceFieldSetup.vTranslation)
				         || legacy_forceFieldSetup.rotationYaw != cache_forceFieldSetup.rotationYaw
				         || legacy_forceFieldSetup.rotationPitch != cache_forceFieldSetup.rotationPitch
				         || legacy_forceFieldSetup.rotationRoll != cache_forceFieldSetup.rotationRoll
					     || (legacy_forceFieldSetup.breaking_maxHardness <= 0 && cache_forceFieldSetup.breaking_maxHardness > 0) ) {
					if (WarpDriveConfig.LOGGING_FORCEFIELD) {
						WarpDrive.logger.info(this + " rebooting with new shape...");
					}
					destroyForceField();
					isDirty.set(true);
				}
			}
		}
		return cache_forceFieldSetup;
	}
	
	@Override
	public int energy_getMaxStorage() {
		return maxEnergyStored;
	}
	
	@Override
	public boolean energy_canInput(final EnumFacing from) {
		return true;
	}
	
	public boolean consumeEnergy(final double amount_internal, final boolean simulate) {
		final int intAmount = (int)Math.floor(amount_internal + consumptionLeftOver);
		final boolean bResult = super.energy_consume(intAmount, simulate);
		if (!simulate) {
			consumptionLeftOver = amount_internal + consumptionLeftOver - intAmount;
		}
		return bResult;
	}
	
	// OpenComputer callback methods
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] state(final Context context, final Arguments arguments) {
		return state();
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] min(final Context context, final Arguments arguments) {
		if (arguments.count() == 1) {
			setMin((float)arguments.checkDouble(0), (float)arguments.checkDouble(0), (float)arguments.checkDouble(0));
		} else if (arguments.count() == 2) {
			setMin((float)arguments.checkDouble(0), (float)arguments.checkDouble(1), (float)arguments.checkDouble(0));
		} else if (arguments.count() == 3) {
			setMin((float)arguments.checkDouble(0), (float)arguments.checkDouble(1), (float)arguments.checkDouble(2));
		}
		return new Double[] { v3Min.x, v3Min.y, v3Min.z };
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] max(final Context context, final Arguments arguments) {
		if (arguments.count() == 1) {
			setMax((float)arguments.checkDouble(0), (float)arguments.checkDouble(0), (float)arguments.checkDouble(0));
		} else if (arguments.count() == 2) {
			setMax((float)arguments.checkDouble(0), (float)arguments.checkDouble(1), (float)arguments.checkDouble(0));
		} else if (arguments.count() == 3) {
			setMax((float)arguments.checkDouble(0), (float)arguments.checkDouble(1), (float)arguments.checkDouble(2));
		}
		return new Double[] { v3Max.x, v3Max.y, v3Max.z };
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] rotation(final Context context, final Arguments arguments) {
		if (arguments.count() == 1) {
			setRotation((float)arguments.checkDouble(0), rotationPitch, rotationRoll);
		} else if (arguments.count() == 2) {
			setRotation((float)arguments.checkDouble(0), (float)arguments.checkDouble(1), rotationRoll);
		} else if (arguments.count() == 3) {
			setRotation((float)arguments.checkDouble(0), (float)arguments.checkDouble(1), (float)arguments.checkDouble(2));
		}
		return new Float[] { rotationYaw, rotationPitch, rotationRoll };
	}
	
	// Common OC/CC methods
	private Object[] state() {    // isConnected, isPowered, shape
		final int energy = energy_getEnergyStored();
		final String status = getStatusHeaderInPureText();
		return new Object[] { status, isEnabled, isConnected, isPowered, getShape().name(), energy };
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] translation(final Context context, final Arguments arguments) {
		if (arguments.count() == 1) {
			setTranslation((float)arguments.checkDouble(0), (float)arguments.checkDouble(0), (float)arguments.checkDouble(0));
		} else if (arguments.count() == 2) {
			setTranslation((float)arguments.checkDouble(0), (float)arguments.checkDouble(1), (float)arguments.checkDouble(0));
		} else if (arguments.count() == 3) {
			setTranslation((float)arguments.checkDouble(0), (float)arguments.checkDouble(1), (float)arguments.checkDouble(2));
		}
		return new Double[] { v3Translation.x, v3Translation.y, v3Translation.z };
	}
	
	// ComputerCraft IPeripheral methods implementation
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public Object[] callMethod(final IComputerAccess computer, final ILuaContext context, final int method, final Object[] arguments) {
		final String methodName = getMethodName(method);
		
		switch (methodName) {
		case "min":
			if (arguments.length == 1 && arguments[0] != null) {
				setMin(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[0]), Commons.toFloat(arguments[0]));
			} else if (arguments.length == 2) {
				setMin(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[1]), Commons.toFloat(arguments[0]));
			} else if (arguments.length == 3) {
				setMin(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[1]), Commons.toFloat(arguments[2]));
			}
			return new Double[] { v3Min.x, v3Min.y, v3Min.z };
		
		case "max":
			if (arguments.length == 1 && arguments[0] != null) {
				setMax(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[0]), Commons.toFloat(arguments[0]));
			} else if (arguments.length == 2) {
				setMax(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[1]), Commons.toFloat(arguments[0]));
			} else if (arguments.length == 3) {
				setMax(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[1]), Commons.toFloat(arguments[2]));
			}
			return new Double[] { v3Max.x, v3Max.y, v3Max.z };
		
		case "rotation":
			if (arguments.length == 1 && arguments[0] != null) {
				setRotation(Commons.toFloat(arguments[0]), rotationPitch, rotationRoll);
			} else if (arguments.length == 2) {
				setRotation(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[1]), rotationRoll);
			} else if (arguments.length == 3) {
				setRotation(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[1]), Commons.toFloat(arguments[2]));
			}
			return new Float[] { rotationYaw, rotationPitch, rotationRoll };
		
		case "state":
			return state();
		
		case "translation":
			if (arguments.length == 1 && arguments[0] != null) {
				setTranslation(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[0]), Commons.toFloat(arguments[0]));
			} else if (arguments.length == 2) {
				setTranslation(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[1]), Commons.toFloat(arguments[0]));
			} else if (arguments.length == 3) {
				setTranslation(Commons.toFloat(arguments[0]), Commons.toFloat(arguments[1]), Commons.toFloat(arguments[2]));
			}
			return new Double[] { v3Translation.x, v3Translation.y, v3Translation.z };
		}
		
		return super.callMethod(computer, context, method, arguments);
	}
	
	private class ThreadCalculation extends Thread {
		
		private final WeakReference<TileEntityForceFieldProjector> weakProjector;
		private final String stringProjector;
		
		ThreadCalculation(final TileEntityForceFieldProjector projector) {
			this.weakProjector = new WeakReference<>(projector);
			stringProjector = projector.toString();
		}
		
		@Override
		public void run() {
			TileEntityForceFieldProjector projector;
			Set<VectorI> vPerimeterBlocks = null;
			Set<VectorI> vInteriorBlocks = null;
			
			// calculation start is done synchronously, by caller
			try {
				projector = weakProjector.get();
				if ( projector != null
				  && projector.isValid() ) {
					// collect what we need, then release the object
					final ForceFieldSetup forceFieldSetup = projector.getForceFieldSetup();
					final int heightWorld = projector.worldObj.getHeight();
					projector = null;
					
					if (WarpDriveConfig.LOGGING_FORCEFIELD) {
						WarpDrive.logger.debug(this + " Calculation started for " + stringProjector);
					}
					
					// create HashSets
					final VectorI vScale = forceFieldSetup.vMax.clone().subtract(forceFieldSetup.vMin);
					vInteriorBlocks = new HashSet<>(vScale.x * vScale.y * vScale.z);
					vPerimeterBlocks = new HashSet<>(2 * vScale.x * vScale.y + 2 * vScale.x * vScale.z + 2 * vScale.y * vScale.z);
					
					// compute interior fields to remove overlapping parts
					final Map<VectorI, Boolean> vertexes = forceFieldSetup.shapeProvider.getVertexes(forceFieldSetup);
					if (vertexes.isEmpty()) {
						WarpDrive.logger.error(this + " No vertexes for " + forceFieldSetup + " at " + stringProjector);
					}
					for (final Map.Entry<VectorI, Boolean> entry : vertexes.entrySet()) {
						final VectorI vPosition = entry.getKey();
						if (forceFieldSetup.isDoubleSided || vPosition.y >= 0) {
							if ( (forceFieldSetup.rotationYaw != 0.0F)
							  || (forceFieldSetup.rotationPitch != 0.0F)
							  || (forceFieldSetup.rotationRoll != 0.0F) ) {
								vPosition.rotateByAngle(forceFieldSetup.rotationYaw, forceFieldSetup.rotationPitch, forceFieldSetup.rotationRoll);
							}
							
							vPosition.translate(forceFieldSetup.vTranslation);
							
							if (vPosition.y > 0 && vPosition.y <= heightWorld) {
								if (entry.getValue()) {
									vPerimeterBlocks.add(vPosition);
								} else {
									vInteriorBlocks.add(vPosition);
								}
							}
						}
					}
					
					// compute forcefield itself
					if (forceFieldSetup.isInverted) {
						// inverted mode => same as interior before fusion => need to be fully cloned
						vPerimeterBlocks = new HashSet<>(vInteriorBlocks);
					}
					
					if (WarpDriveConfig.LOGGING_FORCEFIELD) {
						WarpDrive.logger.debug(this + " Calculation done: "
							+ vInteriorBlocks.size() + " blocks inside, including " + vPerimeterBlocks.size() + " blocks to place");
					}
				} else {
					if (WarpDriveConfig.LOGGING_FORCEFIELD) {
						WarpDrive.logger.error(this + " Calculation aborted");
					}
				}
			} catch (final Exception exception) {
				vInteriorBlocks = null;
				vPerimeterBlocks = null;
				exception.printStackTrace();
				WarpDrive.logger.error(this + " Calculation failed for " + stringProjector);
			}
			
			projector = weakProjector.get();
			if (projector != null) {
				projector.calculation_done(vInteriorBlocks, vPerimeterBlocks);
			}
		}
	}
}
