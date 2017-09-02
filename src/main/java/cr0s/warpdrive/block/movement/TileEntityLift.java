package cr0s.warpdrive.block.movement;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.api.computer.ILift;
import cr0s.warpdrive.block.TileEntityAbstractEnergy;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.EnumLiftMode;
import cr0s.warpdrive.data.SoundEvents;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.network.PacketHandler;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fml.common.Optional;

public class TileEntityLift extends TileEntityAbstractEnergy implements ILift {
	
	final double LIFT_GRAB_RADIUS = 0.4;
	
	// persistent properties
	private EnumLiftMode mode = EnumLiftMode.INACTIVE;
	private boolean isEnabled = true;
	private EnumLiftMode computerMode = EnumLiftMode.REDSTONE;
	
	// computed properties
	private int updateTicks = 0;
	private boolean isActive = false;
	private boolean isValid = false;
	private int firstUncoveredY;
	
	public TileEntityLift() {
		super();
		IC2_sinkTier = 2;
		IC2_sourceTier = 2;
		peripheralName = "warpdriveLift";
		addMethods(new String[] {
				"enable",
				"mode",
				"state"
		});
	}
	
	@Override
	public void update() {
		super.update();
		
		if (worldObj.isRemote) {
			return;
		}
		
		updateTicks--;
		if (updateTicks < 0) {
			updateTicks = WarpDriveConfig.LIFT_UPDATE_INTERVAL_TICKS;
			
			// Switching mode
			if (  computerMode == EnumLiftMode.DOWN
			  || (computerMode == EnumLiftMode.REDSTONE && worldObj.isBlockIndirectlyGettingPowered(pos) > 0)) {
				mode = EnumLiftMode.DOWN;
			} else {
				mode = EnumLiftMode.UP;
			}
			
			isValid = isPassableBlock(pos.getY() + 1)
			       && isPassableBlock(pos.getY() + 2)
			       && isPassableBlock(pos.getY() - 1)
			       && isPassableBlock(pos.getY() - 2);
			isActive = isEnabled && isValid;
			
			IBlockState blockState = worldObj.getBlockState(pos);
			if (energy_getEnergyStored() < WarpDriveConfig.LIFT_ENERGY_PER_ENTITY || !isActive) {
				mode = EnumLiftMode.INACTIVE;
				if (blockState.getValue(BlockLift.MODE) != EnumLiftMode.INACTIVE) {
					worldObj.setBlockState(pos, blockState.withProperty(BlockLift.MODE, EnumLiftMode.INACTIVE));
				}
				return;
			}
			
			if (blockState.getValue(BlockLift.MODE) != mode) {
				worldObj.setBlockState(pos, blockState.withProperty(BlockLift.MODE, mode));
			}
			
			// Launch a beam: search non-air blocks under lift
			for (int ny = pos.getY() - 2; ny > 0; ny--) {
				if (!isPassableBlock(ny)) {
					firstUncoveredY = ny + 1;
					break;
				}
			}
			
			if (pos.getY() - firstUncoveredY >= 2) {
				if (mode == EnumLiftMode.UP) {
					PacketHandler.sendBeamPacket(worldObj,
							new Vector3(pos.getX() + 0.5D, firstUncoveredY, pos.getZ() + 0.5D),
							new Vector3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D),
							0f, 1f, 0f, 40, 0, 100);
				} else if (mode == EnumLiftMode.DOWN) {
					PacketHandler.sendBeamPacket(worldObj,
							new Vector3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D),
							new Vector3(pos.getX() + 0.5D, firstUncoveredY, pos.getZ() + 0.5D), 0f,
							0f, 1f, 40, 0, 100);
				}
				
				if (liftEntity()) {
					updateTicks = WarpDriveConfig.LIFT_ENTITY_COOLDOWN_TICKS;
				}
			}
		}
	}
	
	private boolean isPassableBlock(int yPosition) {
		BlockPos blockPos = new BlockPos(pos.getX(), yPosition, pos.getZ());
		IBlockState blockState = worldObj.getBlockState(blockPos);
		return blockState.getBlock() == Blocks.AIR
			|| worldObj.isAirBlock(blockPos)
			|| blockState.getCollisionBoundingBox(worldObj, blockPos) == null;
	}
	
	private boolean liftEntity() {
		final double xMin = pos.getX() + 0.5 - LIFT_GRAB_RADIUS;
		final double xMax = pos.getX() + 0.5 + LIFT_GRAB_RADIUS;
		final double zMin = pos.getZ() + 0.5 - LIFT_GRAB_RADIUS;
		final double zMax = pos.getZ() + 0.5 + LIFT_GRAB_RADIUS;
		boolean isTransferDone = false; 
		
		// Lift up
		if (mode == EnumLiftMode.UP) {
			final AxisAlignedBB aabb = new AxisAlignedBB(
					xMin, firstUncoveredY, zMin,
					xMax, pos.getY(), zMax);
			final List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(null, aabb);
			for (Entity entity : list) {
				if ( entity != null
				  && entity instanceof EntityLivingBase
				  && energy_consume(WarpDriveConfig.LIFT_ENERGY_PER_ENTITY, true)) {
					entity.setPositionAndUpdate(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
					PacketHandler.sendBeamPacket(worldObj,
							new Vector3(pos.getX() + 0.5D, firstUncoveredY, pos.getZ() + 0.5D),
							new Vector3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D),
							1F, 1F, 0F, 40, 0, 100);
					worldObj.playSound(null, pos, SoundEvents.LASER_HIGH, SoundCategory.AMBIENT, 4.0F, 1.0F);
					energy_consume(WarpDriveConfig.LIFT_ENERGY_PER_ENTITY, false);
					isTransferDone = true;
				}
			}
			
		} else if (mode == EnumLiftMode.DOWN) {
			final AxisAlignedBB aabb = new AxisAlignedBB(
					xMin, Math.min(firstUncoveredY + 4.0D, pos.getY()), zMin,
					xMax, pos.getY() + 2.0D, zMax);
			final List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(null, aabb);
			for (Entity entity : list) {
				if ( entity != null
				  && entity instanceof EntityLivingBase
				  && energy_consume(WarpDriveConfig.LIFT_ENERGY_PER_ENTITY, true)) {
				entity.setPositionAndUpdate(pos.getX() + 0.5D, firstUncoveredY, pos.getZ() + 0.5D);
					PacketHandler.sendBeamPacket(worldObj,
							new Vector3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D),
							new Vector3(pos.getX() + 0.5D, firstUncoveredY, pos.getZ() + 0.5D), 1F, 1F, 0F, 40, 0, 100);
					worldObj.playSound(null, pos, SoundEvents.LASER_HIGH, SoundCategory.AMBIENT, 4.0F, 1.0F);
					energy_consume(WarpDriveConfig.LIFT_ENERGY_PER_ENTITY, false);
					isTransferDone = true;
				}
			}
		}
		
		return isTransferDone;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		if (tagCompound.hasKey("mode")) {
			final byte byteValue = tagCompound.getByte("mode");
			mode = EnumLiftMode.get(Commons.clamp(0, 3, byteValue == -1 ? 3 : byteValue));
		}
		if (tagCompound.hasKey("computerEnabled")) {
			isEnabled = tagCompound.getBoolean("computerEnabled");  // up to 1.3.30 included
		} else if (tagCompound.hasKey("isEnabled")) {
			isEnabled = tagCompound.getBoolean("isEnabled");
		}
		if (tagCompound.hasKey("computerMode")) {
			final byte byteValue = tagCompound.getByte("computerMode");
			computerMode = EnumLiftMode.get(Commons.clamp(0, 3, byteValue == -1 ? 3 : byteValue));
		}
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeToNBT(tagCompound);
		tagCompound.setByte("mode", (byte) mode.ordinal());
		tagCompound.setBoolean("isEnabled", isEnabled);
		tagCompound.setByte("computerMode", (byte) computerMode.ordinal());
		return tagCompound;
	}
	
	@Override
	public NBTTagCompound writeItemDropNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeItemDropNBT(tagCompound);
		tagCompound.removeTag("mode");
		return tagCompound;
	}
	
	@Override
	public int energy_getMaxStorage() {
		return WarpDriveConfig.LIFT_MAX_ENERGY_STORED;
	}
	
	@Override
	public boolean energy_canInput(EnumFacing from) {
		return true;
	}
	
	// Common OC/CC methods
	@Override
	public Object[] enable(Object[] arguments) {
		if (arguments.length == 1) {
			isEnabled = Commons.toBool(arguments[0]);
			markDirty();
		}
		return new Object[] { isEnabled };
	}
	
	@Override
	public Object[] mode(Object[] arguments) {
		if (arguments.length == 1 && arguments[0] instanceof String) {
			final String stringValue = (String) arguments[0];
			if (stringValue.equalsIgnoreCase("up")) {
				computerMode = EnumLiftMode.UP;
			} else if (stringValue.equalsIgnoreCase("down")) {
				computerMode = EnumLiftMode.DOWN;
			} else {
				computerMode = EnumLiftMode.REDSTONE;
			}
			markDirty();
		}
		
		return new Object[] { computerMode.getName() };
	}
	
	@Override
	public Object[] state() {
		final int energy = energy_getEnergyStored();
		final String status = getStatusHeaderInPureText();
		return new Object[] { status, isActive, energy, isValid, isEnabled, computerMode.getName() };
	}
	
	// OpenComputer callback methods
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] enable(Context context, Arguments arguments) {
		return enable(argumentsOCtoCC(arguments));
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] mode(Context context, Arguments arguments) {
		return mode(
			new Object[] {
				arguments.checkString(0)
			}
		);
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] state(Context context, Arguments arguments) {
		return state();
	}
	
	// ComputerCraft IPeripheral methods implementation
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) {
		final String methodName = getMethodName(method);
		
		switch (methodName) {
		case "enable":
			return enable(arguments);
			
		case "mode":
			return mode(arguments);
		
		case "state":
			return state();
		}
		
		return super.callMethod(computer, context, method, arguments);
	}
}
