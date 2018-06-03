package cr0s.warpdrive.block.detection;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.block.TileEntityAbstractEnergy;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.BlockProperties;
import cr0s.warpdrive.data.CloakedArea;
import cr0s.warpdrive.data.SoundEvents;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.network.PacketHandler;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

import javax.annotation.Nonnull;
import java.util.Arrays;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.fml.common.Optional;

public class TileEntityCloakingCore extends TileEntityAbstractEnergy {
	
	private static final int CLOAKING_CORE_SOUND_UPDATE_TICKS = 40;
	private static final int DISTANCE_INNER_COILS_BLOCKS = 2;
	private static final int LASER_REFRESH_TICKS = 100;
	private static final int LASER_DURATION_TICKS = 110;
	
	public boolean isEnabled = false;
	public byte tier = 1; // cloaking field tier, 1 or 2
	
	// inner coils color map
	private final float[] innerCoilColor_r = { 1.00f, 1.00f, 1.00f, 1.00f, 0.75f, 0.25f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f, 1.00f };
	private final float[] innerCoilColor_g = { 0.00f, 0.25f, 0.75f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 0.50f, 0.25f, 0.00f, 0.00f };
	private final float[] innerCoilColor_b = { 0.25f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f, 1.00f, 1.00f, 1.00f, 1.00f, 0.75f };
	
	// Spatial cloaking field parameters
	private final boolean[] isValidInnerCoils = { false, false, false, false, false, false };
	private final int[] distanceOuterCoils_blocks = { 0, 0, 0, 0, 0, 0 };   // 0 means invalid
	private int minX = 0;
	private int minY = 0;
	private int minZ = 0;
	private int maxX = 0;
	private int maxY = 0;
	private int maxZ = 0;
	
	private boolean isValid = false;
	private String messageValidityIssues = "";
	private boolean isCloaking = false;
	private int volume = 0;
	private int energyRequired = 0;
	private int updateTicks = 0;
	private int laserDrawingTicks = 0;
	
	private boolean soundPlayed = false;
	private int soundTicks = 0;
	
	public TileEntityCloakingCore() {
		super();
		peripheralName = "warpdriveCloakingCore";
		addMethods(new String[] {
			"tier",				// set field tier to 1 or 2, return field tier
			"isAssemblyValid",	// returns true or false
			"enable"			// set field enable state (true or false), return true if enabled
		});
		CC_scripts = Arrays.asList("cloak1", "cloak2", "uncloak");
	}
	
	@Override
	public void update() {
		super.update();
		
		if (worldObj.isRemote) {
			return;
		}
		
		// Reset sound timer
		soundTicks--;
		if (soundTicks < 0) {
			soundTicks = CLOAKING_CORE_SOUND_UPDATE_TICKS;
			soundPlayed = false;
		}
		
		boolean isRefreshNeeded = false;
		
		updateTicks--;
		if (updateTicks <= 0) {
			updateTicks = ((tier == 1) ? 20 : (tier == 2) ? 10 : 20) * WarpDriveConfig.CLOAKING_FIELD_REFRESH_INTERVAL_SECONDS; // resetting timer
			
			isRefreshNeeded = validateAssembly();
			
			isCloaking = WarpDrive.cloaks.isAreaExists(worldObj, pos); 
			if (!isEnabled) {// disabled
				if (isCloaking) {// disabled, cloaking => stop cloaking
					if (WarpDriveConfig.LOGGING_CLOAKING) {
						WarpDrive.logger.info(this + " Disabled, cloak field going down...");
					}
					disableCloakingField();
					isRefreshNeeded = true;
				} else {// disabled, not cloaking
					// IDLE
					if (isRefreshNeeded) {
						setCoilsState(false);
					}
				}
				
			} else {// isEnabled
				updateVolumeAndEnergyRequired();
				final boolean hasEnoughPower = energy_consume(energyRequired, false);
				if (!isCloaking) {// enabled, not cloaking
					if (hasEnoughPower && isValid) {// enabled, can cloak and able to
						setCoilsState(true);
						isRefreshNeeded = true;
						
						// Register cloak
						WarpDrive.cloaks.updateCloakedArea(worldObj,
								worldObj.provider.getDimension(), pos, tier,
								minX, minY, minZ, maxX, maxY, maxZ);
						if (!soundPlayed) {
							soundPlayed = true;
							worldObj.playSound(null, pos, SoundEvents.CLOAK, SoundCategory.BLOCKS, 4F, 1F);
						}
						
						// Refresh the field
						final CloakedArea area = WarpDrive.cloaks.getCloakedArea(worldObj, pos);
						if (area != null) {
							area.sendCloakPacketToPlayersEx(false); // re-cloak field
						} else {
							WarpDrive.logger.error("getCloakedArea1 returned null for " + worldObj + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
						}
						
					} else {// enabled, not cloaking and not able to
						// IDLE
						setCoilsState(false);
					}
					
				} else {// enabled & cloaking
					if (!isValid) {// enabled, cloaking but invalid
						if (WarpDriveConfig.LOGGING_CLOAKING) {
							WarpDrive.logger.info(this + " Coil(s) lost, cloak field is collapsing...");
						}
						energy_consume(energy_getEnergyStored());
						disableCloakingField();
						isRefreshNeeded = true;
						
					} else {// enabled, cloaking and valid
						if (hasEnoughPower) {// enabled, cloaking and able to
							if (isRefreshNeeded) {
								WarpDrive.cloaks.updateCloakedArea(worldObj,
										worldObj.provider.getDimension(), pos, tier,
										minX, minY, minZ, maxX, maxY, maxZ);
							}
							
							// IDLE
							// Refresh the field (workaround to re-synchronize players since client may 'eat up' the packets)
							final CloakedArea area = WarpDrive.cloaks.getCloakedArea(worldObj, pos);
							if (area != null) {
								area.sendCloakPacketToPlayersEx(false); // re-cloak field
							} else {
								WarpDrive.logger.error("getCloakedArea2 returned null for " + worldObj + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
							}
							setCoilsState(true);
							
						} else {// loosing power
							if (WarpDriveConfig.LOGGING_CLOAKING) {
								WarpDrive.logger.info(this + " Low power, cloak field is collapsing...");
							}
							disableCloakingField();
							isRefreshNeeded = true;
						}
					}
				}
			}
		}
		
		laserDrawingTicks--;
		if (isRefreshNeeded || laserDrawingTicks < 0) {
			laserDrawingTicks = LASER_REFRESH_TICKS;
			
			if (isEnabled && isValid) {
				drawLasers();
			}
		}
	}
	
	private void setCoilsState(final boolean enabled) {
		updateBlockState(null, BlockProperties.ACTIVE, enabled);
		
		for (final EnumFacing direction : EnumFacing.VALUES) {
			if (isValidInnerCoils[direction.ordinal()]) {
				setCoilState(DISTANCE_INNER_COILS_BLOCKS, direction, enabled);
			}
			if (distanceOuterCoils_blocks[direction.ordinal()] > 0) {
				setCoilState(distanceOuterCoils_blocks[direction.ordinal()], direction, enabled);
			}
		}
	}
	
	private void setCoilState(final int distance, final EnumFacing direction, final boolean enabled) {
		final BlockPos blockPos = pos.offset(direction);
		final IBlockState blockState = worldObj.getBlockState(blockPos);
		if (blockState.getBlock().isAssociatedBlock(WarpDrive.blockCloakingCoil)) {
			if (distance == DISTANCE_INNER_COILS_BLOCKS) {
				BlockCloakingCoil.setBlockState(worldObj, blockPos, enabled, false, EnumFacing.UP);
			} else {
				BlockCloakingCoil.setBlockState(worldObj, blockPos, enabled, true, direction);
			}
		}
	}
	
	private void drawLasers() {
		float r = 0.0f;
		float g = 1.0f;
		float b = 0.0f;
		if (!isCloaking) {// out of energy
			r = 0.75f;
			g = 0.50f;
			b = 0.50f;
		} else if (tier == 1) {
			r = 0.00f;
			g = 1.00f;
			b = 0.25f;
		} else if (tier == 2) {
			r = 0.00f;
			g = 0.25f;
			b = 1.00f;
		}
		
		// Directions to check (all six directions: left, right, up, down, front, back)
		for (final EnumFacing direction : EnumFacing.values()) {
			if ( isValidInnerCoils[direction.ordinal()]
			  && distanceOuterCoils_blocks[direction.ordinal()] > 0) {
				PacketHandler.sendBeamPacketToPlayersInArea(worldObj,
				        new Vector3(
				                   pos.getX() + 0.5D + (DISTANCE_INNER_COILS_BLOCKS + 0.3D) * direction.getFrontOffsetX(),
				                   pos.getY() + 0.5D + (DISTANCE_INNER_COILS_BLOCKS + 0.3D) * direction.getFrontOffsetY(),
				                   pos.getZ() + 0.5D + (DISTANCE_INNER_COILS_BLOCKS + 0.3D) * direction.getFrontOffsetZ()),
				        new Vector3(
				                   pos.getX() + 0.5D + distanceOuterCoils_blocks[direction.ordinal()] * direction.getFrontOffsetX(),
				                   pos.getY() + 0.5D + distanceOuterCoils_blocks[direction.ordinal()] * direction.getFrontOffsetY(),
				                   pos.getZ() + 0.5D + distanceOuterCoils_blocks[direction.ordinal()] * direction.getFrontOffsetZ()),
				        r, g, b,
				        LASER_DURATION_TICKS,
				    		new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
			}
		}
		
		// draw connecting coils
		for (int i = 0; i < 5; i++) {
			final EnumFacing start = EnumFacing.VALUES[i];
			for (int j = i + 1; j < 6; j++) {
				final EnumFacing stop = EnumFacing.VALUES[j];
				// skip mirrored coils (removing the inner lines)
				if (start.getOpposite() == stop) {
					continue;
				}
				
				// draw a random colored beam
				final int mapIndex = worldObj.rand.nextInt(innerCoilColor_b.length);
				r = innerCoilColor_r[mapIndex];
				g = innerCoilColor_g[mapIndex];
				b = innerCoilColor_b[mapIndex];
				
				PacketHandler.sendBeamPacketToPlayersInArea(worldObj,
					new Vector3(
						pos.getX() + 0.5D + (DISTANCE_INNER_COILS_BLOCKS + 0.3D) * start.getFrontOffsetX() + 0.2D * stop .getFrontOffsetX(),
						pos.getY() + 0.5D + (DISTANCE_INNER_COILS_BLOCKS + 0.3D) * start.getFrontOffsetY() + 0.2D * stop .getFrontOffsetY(),
						pos.getZ() + 0.5D + (DISTANCE_INNER_COILS_BLOCKS + 0.3D) * start.getFrontOffsetZ() + 0.2D * stop .getFrontOffsetZ()),
					new Vector3(
						pos.getX() + 0.5D + (DISTANCE_INNER_COILS_BLOCKS + 0.3D) * stop .getFrontOffsetX() + 0.2D * start.getFrontOffsetX(),
						pos.getY() + 0.5D + (DISTANCE_INNER_COILS_BLOCKS + 0.3D) * stop .getFrontOffsetY() + 0.2D * start.getFrontOffsetY(),
						pos.getZ() + 0.5D + (DISTANCE_INNER_COILS_BLOCKS + 0.3D) * stop .getFrontOffsetZ() + 0.2D * start.getFrontOffsetZ()),
					r, g, b,
					LASER_DURATION_TICKS,
					new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
			}
		}
	}
	
	public void disableCloakingField() {
		setCoilsState(false);
		if (WarpDrive.cloaks.isAreaExists(worldObj, pos)) {
			WarpDrive.cloaks.removeCloakedArea(worldObj.provider.getDimension(), pos);
			
			if (!soundPlayed) {
				soundPlayed = true;
				worldObj.playSound(null, pos, SoundEvents.DECLOAK, SoundCategory.BLOCKS, 4F, 1F);
			}
		}
	}
	
	public void updateVolumeAndEnergyRequired() {
		int x, y, z;
		final int energyRequired_new;
		int volume_new = 0;
		if (tier == 1) {// tier1 = gaz and air blocks don't count
			for (y = minY; y <= maxY; y++) {
				for (x = minX; x <= maxX; x++) {
					for (z = minZ; z <= maxZ; z++) {
						if (!worldObj.isAirBlock(new BlockPos(x, y, z))) { // @TODO MC1.10 use mutableBlockPos
							volume_new++;
						} 
					}
				}
			}
			energyRequired_new = volume_new * WarpDriveConfig.CLOAKING_TIER1_ENERGY_PER_BLOCK;
		} else {// tier2 = everything counts
			for (y = minY; y <= maxY; y++) {
				for (x = minX; x <= maxX; x++) {
					for (z = minZ; z <= maxZ; z++) {
						if (worldObj.getBlockState(new BlockPos(x, y, z)).getBlock() != Blocks.AIR) { // @TODO MC1.10 use mutableBlockPos
							volume_new++;
						} 
					}
				}
			}
			energyRequired_new = volume_new * WarpDriveConfig.CLOAKING_TIER2_ENERGY_PER_BLOCK;
		}
		
		volume = volume_new;
		energyRequired = energyRequired_new;
		
		if (WarpDriveConfig.LOGGING_ENERGY) {
			WarpDrive.logger.info(String.format("%s Requiring %d EU for %d blocks",
			                                    this, energyRequired, volume));
		}
	}
	
	@Override
	public void readFromNBT(final NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		tier = tagCompound.getByte("tier");
		isEnabled = tagCompound.getBoolean("enabled");
	}
	
	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeToNBT(tagCompound);
		tagCompound.setByte("tier", tier);
		tagCompound.setBoolean("enabled", isEnabled);
		return tagCompound;
	}
	
	public boolean validateAssembly() {
		final int maxOuterCoilDistance = WarpDriveConfig.CLOAKING_MAX_FIELD_RADIUS - WarpDriveConfig.CLOAKING_COIL_CAPTURE_BLOCKS;
		boolean isRefreshNeeded = false;
		int countIntegrity = 1; // 1 for the core + 1 per coil
		final StringBuilder messageInnerCoils = new StringBuilder();
		final StringBuilder messageOuterCoils = new StringBuilder();
		
		// Directions to check (all six directions: left, right, up, down, front, back)
		for (final EnumFacing direction : EnumFacing.values()) {
			
			// check validity of inner coil
			BlockPos blockPos = new BlockPos(pos.offset(direction, DISTANCE_INNER_COILS_BLOCKS));
			final boolean isInnerValid = (worldObj.getBlockState(blockPos) == WarpDrive.blockCloakingCoil);
			
			// whenever a change is detected, force a laser redraw 
			if (isInnerValid != isValidInnerCoils[direction.ordinal()]) {
				isRefreshNeeded = true;
				isValidInnerCoils[direction.ordinal()] = isInnerValid;
			}
			
			// update validity results
			if (isValidInnerCoils[direction.ordinal()]) {
				countIntegrity++;
			} else {
				if (messageInnerCoils.length() != 0) {
					messageInnerCoils.append(", ");
				}
				messageInnerCoils.append(direction.name());
			}
			
			// find closest outer coil
			int newCoilDistance = 0;
			for (int distance = DISTANCE_INNER_COILS_BLOCKS + 1; distance < maxOuterCoilDistance; distance++) {
				blockPos = blockPos.offset(direction);
				
				if (worldObj.getBlockState(blockPos).getBlock() == WarpDrive.blockCloakingCoil) {
					BlockCloakingCoil.setBlockState(worldObj, blockPos, true, true, direction);
					newCoilDistance = distance;
					break;
				}
			}
			
			// whenever a change is detected, disable previous outer coil if it was valid and force a laser redraw
			final int oldCoilDistance = distanceOuterCoils_blocks[direction.ordinal()];
			if (newCoilDistance != oldCoilDistance) {
				if (oldCoilDistance > 0) {
					final BlockPos blockPosOld = pos.offset(direction, oldCoilDistance);
					if (worldObj.getBlockState(blockPosOld).getBlock() == WarpDrive.blockCloakingCoil) {
						BlockCloakingCoil.setBlockState(worldObj, blockPos, false, false, EnumFacing.UP);
					}
				}
				isRefreshNeeded = true;
				distanceOuterCoils_blocks[direction.ordinal()] = Math.max(0, newCoilDistance);
			}
			
			// update validity results
			if (newCoilDistance > 0) {
				countIntegrity++;
			} else {
				if (messageOuterCoils.length() != 0) {
					messageOuterCoils.append(", ");
				}
				messageOuterCoils.append(direction.name());
			}
		}
		
		// build status message
		final float integrity = countIntegrity / 13.0F; 
		if (messageInnerCoils.length() > 0 && messageOuterCoils.length() > 0) {
			messageValidityIssues = new TextComponentTranslation("warpdrive.cloaking_core.missing_channeling_and_projecting_coils",
					Math.round(100.0F * integrity), messageInnerCoils, messageOuterCoils).toString(); 
		} else if (messageInnerCoils.length() > 0) {
			messageValidityIssues = new TextComponentTranslation("warpdrive.cloaking_core.missing_channeling_coils",
			        Math.round(100.0F * integrity), messageInnerCoils).toString();
		} else if (messageOuterCoils.length() > 0) {
			messageValidityIssues = new TextComponentTranslation("warpdrive.cloaking_core.missing_projecting_coils",
					Math.round(100.0F * integrity), messageOuterCoils).toString();
		} else {
			messageValidityIssues = new TextComponentTranslation("warpdrive.cloaking_core.valid").toString();
		}
		
		// Update cloaking field parameters defined by coils
		isValid = countIntegrity >= 13;
		minX =               pos.getX() - distanceOuterCoils_blocks[4] - WarpDriveConfig.CLOAKING_COIL_CAPTURE_BLOCKS;
		maxX =               pos.getX() + distanceOuterCoils_blocks[5] + WarpDriveConfig.CLOAKING_COIL_CAPTURE_BLOCKS;
		minY = Math.max(  0, pos.getY() - distanceOuterCoils_blocks[0] - WarpDriveConfig.CLOAKING_COIL_CAPTURE_BLOCKS);
		maxY = Math.min(255, pos.getY() + distanceOuterCoils_blocks[1] + WarpDriveConfig.CLOAKING_COIL_CAPTURE_BLOCKS);
		minZ =               pos.getZ() - distanceOuterCoils_blocks[2] - WarpDriveConfig.CLOAKING_COIL_CAPTURE_BLOCKS;
		maxZ =               pos.getZ() + distanceOuterCoils_blocks[3] + WarpDriveConfig.CLOAKING_COIL_CAPTURE_BLOCKS;
		
		return isRefreshNeeded;
	}
	
	@Override
	public ITextComponent getStatusHeader() {
		if (worldObj == null) {
			return super.getStatusHeader();
		}
		
		final String unlocalizedStatus;
		if (!isValid) {
			unlocalizedStatus = messageValidityIssues;
		} else if (!isEnabled) {
			unlocalizedStatus = "warpdrive.cloaking_core.disabled";
		} else if (!isCloaking) {
			unlocalizedStatus = "warpdrive.cloaking_core.low_power";
		} else {
			unlocalizedStatus = "warpdrive.cloaking_core.cloaking";
		}
		return super.getStatusHeader()
		    .appendSibling(new TextComponentString("\n")).appendSibling(new TextComponentTranslation(unlocalizedStatus,
						tier,
						volume));
	}
	
	// Common OC/CC methods
	public Object[] tier(final Object[] arguments) {
		if (arguments.length == 1 && arguments[0] != null) {
			final int tier_new;
			try {
				tier_new = Commons.toInt(arguments[0]);
			} catch (final Exception exception) {
				return new Integer[] { (int) tier };
			}
			if (tier_new == 2) {
				tier = 2;
			} else {
				tier = 1;
			}
			markDirty();
		}
		return new Integer[] { (int) tier };
	}
	
	public Object[] isAssemblyValid() {
		return new Object[] { isValid, Commons.removeFormatting(messageValidityIssues) };
	}
	
	public Object[] enable(final Object[] arguments) {
		if (arguments.length == 1 && arguments[0] != null) {
			isEnabled = Commons.toBool(arguments[0]);
			markDirty();
		}
		return new Object[] { isEnabled };
	}
	
	// OpenComputer callback methods
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] tier(final Context context, final Arguments arguments) {
		return tier(argumentsOCtoCC(arguments));
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] isAssemblyValid(final Context context, final Arguments arguments) {
		return isAssemblyValid();
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] enable(final Context context, final Arguments arguments) {
		return enable(argumentsOCtoCC(arguments));
	}
	
	// ComputerCraft IPeripheral methods implementation
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public Object[] callMethod(final IComputerAccess computer, final ILuaContext context, final int method, final Object[] arguments) {
		final String methodName = getMethodName(method);
		
		switch (methodName) {
		case "tier":
			return tier(arguments);
			
		case "isAssemblyValid":
			return isAssemblyValid();
			
		case "enable":
			return enable(arguments);
		}
		
		return super.callMethod(computer, context, method, arguments);
	}
	
	@Override
	public int energy_getMaxStorage() {
		return WarpDriveConfig.CLOAKING_MAX_ENERGY_STORED;
	}
	
	@Override
	public boolean energy_canInput(final EnumFacing from) {
		return true;
	}
}
