package cr0s.warpdrive.block;

import cr0s.warpdrive.api.computer.IAbstractLaser;
import cr0s.warpdrive.config.WarpDriveConfig;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

import java.util.LinkedList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.fml.common.Optional;

// Abstract class to manage laser mediums
public abstract class TileEntityAbstractLaser extends TileEntityAbstractInterfaced implements IAbstractLaser {
	
	// configuration overridden by derived classes
	protected EnumFacing[] laserMedium_directionsValid = EnumFacing.values();
	protected int laserMedium_maxCount = 0;
	
	// computed properties
	protected EnumFacing laserMedium_direction = null;
	protected int cache_laserMedium_count = 0;
	protected int cache_laserMedium_energyStored = 0;
	protected int cache_laserMedium_maxStorage = 0;
	
	private final int updateInterval_slow_ticks = 20 * WarpDriveConfig.SHIP_CONTROLLER_UPDATE_INTERVAL_SECONDS;
	protected int updateInterval_ticks = updateInterval_slow_ticks;
	private int updateTicks = updateInterval_ticks;
	private int bootTicks = 20;
	
	public TileEntityAbstractLaser() {
		super();
		
		addMethods(new String[] {
				"energy",
				"laserMediumDirection",
				"laserMediumCount"
		});
	}
	
	@Override
	protected void onFirstUpdateTick() {
		super.onFirstUpdateTick();
		updateLaserMediumDirection();
	}
	
	@Override
	public void update() {
		super.update();
		
		if (worldObj.isRemote) {
			return;
		}
		
		// accelerate update ticks during boot
		if (bootTicks > 0) {
			bootTicks--;
			if (laserMedium_direction == null) {
				updateTicks = 1;
			}
		}
		updateTicks--;
		if (updateTicks <= 0) {
			updateTicks = updateInterval_ticks;
			
			updateLaserMediumDirection();
		}
	}
	
	private void updateLaserMediumDirection() {
		assert(laserMedium_maxCount != 0);
		
		for (final EnumFacing facing : laserMedium_directionsValid) {
			TileEntity tileEntity = worldObj.getTileEntity(pos.offset(facing));
			
			if (tileEntity instanceof TileEntityLaserMedium) {
				// at least one found
				int energyStored = 0;
				int maxStorage = 0;
				int count = 0;
				while ( (tileEntity instanceof TileEntityLaserMedium)
				     && count <= laserMedium_maxCount) {
					// add current one
					energyStored += ((TileEntityLaserMedium) tileEntity).energy_getEnergyStored();
					maxStorage += ((TileEntityLaserMedium) tileEntity).energy_getMaxStorage();
					count++;
					
					// check next one
					tileEntity = worldObj.getTileEntity(pos.offset(facing, count + 1));
				}
				
				// save results
				laserMedium_direction = facing;
				cache_laserMedium_count = count;
				cache_laserMedium_energyStored = energyStored;
				cache_laserMedium_maxStorage = maxStorage;
				return;
			}
		}
		
		// nothing found
		laserMedium_direction = null;
		cache_laserMedium_count = 0;
		cache_laserMedium_energyStored = 0;
		cache_laserMedium_maxStorage = 0;
	}
	
	protected int laserMedium_getEnergyStored() {
		return laserMedium_consumeUpTo(Integer.MAX_VALUE, true);
	}
	
	protected boolean laserMedium_consumeExactly(final int amountRequested, final boolean simulate) {
		final int amountSimulated = laserMedium_consumeUpTo(amountRequested, true);
		if (simulate) {
			return amountRequested <= amountSimulated;
		}
		if (amountRequested > amountSimulated) {
			return false;
		}
		return amountRequested <= laserMedium_consumeUpTo(amountRequested, false);
	}
	
	protected int laserMedium_consumeUpTo(final int amount, final boolean simulate) {
		if (laserMedium_direction == null) {
			return 0;
		}
		
		// Primary scan of all laser mediums
		int totalEnergy = 0;
		int count = 1;
		final List<TileEntityLaserMedium> laserMediums = new LinkedList<>();
		for (; count <= laserMedium_maxCount; count++) {
			final TileEntity tileEntity = worldObj.getTileEntity(pos.offset(laserMedium_direction, count));
			if (!(tileEntity instanceof TileEntityLaserMedium)) {
				break;
			}
			laserMediums.add((TileEntityLaserMedium) tileEntity);
			totalEnergy += ((TileEntityLaserMedium) tileEntity).energy_getEnergyStored();
		}
		count--;
		if (count == 0) {
			return 0;
		}
		if (simulate) {
			return totalEnergy;
		}
		
		// Compute average energy to get per laser medium, capped at its capacity
		int energyAverage = amount / count;
		int energyLeftOver = amount - energyAverage * count;
		if (energyAverage >= WarpDriveConfig.LASER_MEDIUM_MAX_ENERGY_STORED) {
			energyAverage = WarpDriveConfig.LASER_MEDIUM_MAX_ENERGY_STORED;
			energyLeftOver = 0;
		}
		
		// Secondary scan for laser medium below the required average
		for (final TileEntityLaserMedium laserMedium : laserMediums) {
			final int energyStored = laserMedium.energy_getEnergyStored();
			if (energyStored < energyAverage) {
				energyLeftOver += energyAverage - energyStored;
			}
		}
		
		// Third and final pass for energy consumption
		int energyTotalConsumed = 0;
		for (final TileEntityLaserMedium laserMedium : laserMediums) {
			final int energyStored = laserMedium.energy_getEnergyStored();
			final int energyToConsume = Math.min(energyStored, energyAverage + energyLeftOver);
			energyLeftOver -= Math.max(0, energyToConsume - energyAverage);
			laserMedium.energy_consume(energyToConsume, false); // simulate is always false here
			energyTotalConsumed += energyToConsume;
		}
		return energyTotalConsumed;
	}
	
	// IAbstractLaser overrides
	@Override
	public Object[] energy() {
		return new Object[] { cache_laserMedium_energyStored, cache_laserMedium_maxStorage };
	}
	
	@Override
	public Object[] laserMediumDirection() {
		return new Object[] {
			laserMedium_direction.name(),
			laserMedium_direction.getFrontOffsetX(),
			laserMedium_direction.getFrontOffsetY(),
			laserMedium_direction.getFrontOffsetZ() };
	}
	
	@Override
	public Object[] laserMediumCount() {
		return new Object[] { cache_laserMedium_count };
	}
	
	// OpenComputers callback methods
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] energy(final Context context, final Arguments arguments) {
		return energy();
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] laserMediumDirection(final Context context, final Arguments arguments) {
		return laserMediumDirection();
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] laserMediumCount(final Context context, final Arguments arguments) {
		return laserMediumCount();
	}
	
	// ComputerCraft IPeripheral methods
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public Object[] callMethod(final IComputerAccess computer, final ILuaContext context, final int method, final Object[] arguments) {
		final String methodName = getMethodName(method);
		
		switch (methodName) {
		case "energy":
			return energy();
			
		case "laserMediumDirection":
			return laserMediumDirection();
			
		case "laserMediumCount":
			return laserMediumCount();
		}
		
		return super.callMethod(computer, context, method, arguments);
	}
}
