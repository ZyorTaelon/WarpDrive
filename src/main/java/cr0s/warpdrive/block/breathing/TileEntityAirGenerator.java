package cr0s.warpdrive.block.breathing;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.block.TileEntityAbstractEnergy;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.CelestialObjectManager;
import cr0s.warpdrive.data.BlockProperties;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

import javax.annotation.Nonnull;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fml.common.Optional;

public class TileEntityAirGenerator extends TileEntityAbstractEnergy {
	
	private int cooldownTicks = 0;
	private boolean isEnabled = true;
	private static final int START_CONCENTRATION_VALUE = 15;
	
	public TileEntityAirGenerator() {
		super();
		
		peripheralName = "warpdriveAirGenerator";
		addMethods(new String[] {
			"enable"
		});
	}
	
	@Override
	public void update() {
		super.update();
		
		if (worldObj.isRemote) {
			return;
		}
		
		if (isInvalid()) {
			return;
		}
		
		// Air generator works only in space & hyperspace
		if (CelestialObjectManager.hasAtmosphere(worldObj, pos.getX(), pos.getZ())) {
			final IBlockState blockState = worldObj.getBlockState(pos);
			if (blockState.getValue(BlockProperties.ACTIVE)) {
				worldObj.setBlockState(pos, blockState.withProperty(BlockProperties.ACTIVE, false)); // set disabled texture
			}
			return;
		}
		
		cooldownTicks++;
		if (cooldownTicks > WarpDriveConfig.BREATHING_AIR_GENERATION_TICKS) {
			IBlockState blockState = worldObj.getBlockState(pos);
			if (isEnabled && energy_consume(WarpDriveConfig.BREATHING_ENERGY_PER_NEW_AIR_BLOCK[0], true)) {
				if (!blockState.getValue(BlockProperties.ACTIVE)) {
					worldObj.setBlockState(pos, blockState.withProperty(BlockProperties.ACTIVE, true)); // set enabled texture
				}
			} else {
				if (blockState.getValue(BlockProperties.ACTIVE)) {
					worldObj.setBlockState(pos, blockState.withProperty(BlockProperties.ACTIVE, false)); // set disabled texture
				}
			}
			releaseAir(pos.north());
			releaseAir(pos.south());
			releaseAir(pos.east());
			releaseAir(pos.west());
			releaseAir(pos.up());
			releaseAir(pos.down());
			
			
			cooldownTicks = 0;
		}
	}
	
	private void releaseAir(final BlockPos blockPos) {
		final IBlockState blockState = worldObj.getBlockState(blockPos);
		if (blockState.getBlock().isAir(blockState, worldObj, blockPos)) {// can be air
			final int energy_cost = (!blockState.getBlock().isAssociatedBlock(WarpDrive.blockAir)) ? WarpDriveConfig.BREATHING_ENERGY_PER_NEW_AIR_BLOCK[0] : WarpDriveConfig.BREATHING_ENERGY_PER_EXISTING_AIR_BLOCK[0];
			if (isEnabled && energy_consume(energy_cost, true)) {// enough energy and enabled
				if (worldObj.setBlockState(blockPos, WarpDrive.blockAir.getStateFromMeta(START_CONCENTRATION_VALUE), 2)) {
					// (needs to renew air or was not maxed out)
					energy_consume(WarpDriveConfig.BREATHING_ENERGY_PER_NEW_AIR_BLOCK[0], false);
				} else {
					energy_consume(WarpDriveConfig.BREATHING_ENERGY_PER_EXISTING_AIR_BLOCK[0], false);
				}
			} else {// low energy => remove air block
				if (blockState.getBlock().isAssociatedBlock(WarpDrive.blockAir)) {
					final int metadata = blockState.getBlock().getMetaFromState(blockState);
					if (metadata > 4) {
						worldObj.setBlockState(blockPos, WarpDrive.blockAir.getStateFromMeta(metadata - 4), 2);
					} else if (metadata > 1) {
						worldObj.setBlockState(blockPos, WarpDrive.blockAir.getStateFromMeta(1), 2);
					// } else {
						// worldObj.setBlockState(blockPos, Blocks.AIR.getDefaultState(), 2);
					}
				}
			}
		}
	}
	
	@Override
	public void readFromNBT(final NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		isEnabled = !tagCompound.hasKey("isEnabled") || tagCompound.getBoolean("isEnabled");
	}
	
	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound.setBoolean("isEnabled", isEnabled);
		return super.writeToNBT(tagCompound);
	}
	
	@Override
	public int energy_getMaxStorage() {
		return WarpDriveConfig.BREATHING_MAX_ENERGY_STORED[0];
	}
	
	@Override
	public boolean energy_canInput(final EnumFacing from) {
		return true;
	}
	
	@Override
	public String toString() {
		return String.format("%s @ %s (%d %d %d)",
			getClass().getSimpleName(),
			worldObj == null ? "~NULL~" : worldObj.provider.getSaveFolder(),
			pos.getX(), pos.getY(), pos.getZ());
	}
	
	// Common OC/CC methods
	public Object[] enable(final Object[] arguments) {
		if (arguments.length == 1 && arguments[0] != null) {
			isEnabled = Commons.toBool(arguments[0]);
		}
		return new Object[]{isEnabled};
	}
	
	// OpenComputer callback methods
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
		case "enable": 
			return enable(arguments);		
		}
		
		return super.callMethod(computer, context, method, arguments);
	}
}
