package cr0s.warpdrive.block.energy;

import cr0s.warpdrive.block.TileEntityAbstractLaser;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.BlockProperties;
import cr0s.warpdrive.data.EnumValidPowered;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.item.ItemIC2reactorLaserFocus;
import cr0s.warpdrive.network.PacketHandler;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorChamber;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Optional;

import net.minecraftforge.fluids.BlockFluidBase;

public class TileEntityIC2reactorLaserMonitor extends TileEntityAbstractLaser {
	
	// persistent properties
	private int ticks = WarpDriveConfig.IC2_REACTOR_COOLING_INTERVAL_TICKS;
	
	// computed properties
	public EnumFacing facing = null;
	private boolean isValid = false;
	
	public TileEntityIC2reactorLaserMonitor() {
		super();
		laserMedium_maxCount = 1;
		peripheralName = "warpdriveIC2reactorLaserCooler";
	}
	
	// returns IReactor tile entities
	@Optional.Method(modid = "IC2")
	private IReactor findReactor() {
		for(final EnumFacing facing : EnumFacing.values()) {
			final TileEntity tileEntity = worldObj.getTileEntity(pos.offset(facing, 2));
			if (tileEntity == null) {
				continue;
			}
			
			IReactor output = null;
			if (tileEntity instanceof IReactor) {
				output = (IReactor) tileEntity;
				
			} else if (tileEntity instanceof IReactorChamber) {
				final IReactor reactor = ((IReactorChamber) tileEntity).getReactorInstance();
				if (reactor == null) {
					continue;
				}
				
				// ignore if we're right next to the reactor
				// ignore if we're not aligned with the reactor
				final BlockPos blockPos = reactor.getCoreTe().getPos();
				if ( blockPos.getX() != pos.getX() + 3 * facing.getFrontOffsetX()
				  || blockPos.getY() != pos.getY() + 3 * facing.getFrontOffsetY()
				  || blockPos.getZ() != pos.getZ() + 3 * facing.getFrontOffsetZ() ) {
					continue;
				}
				
				output = reactor;
			}
			
			// if reactor or chamber was found, check the space in between
			if (output != null) {
				final BlockPos blockPos = pos.offset(facing);
				final IBlockState blockState = worldObj.getBlockState(blockPos);
				final Block block = blockState.getBlock();
				final boolean isAir = block.isAir(blockState, worldObj, blockPos); 
				isValid = ( isAir
				         || block instanceof BlockFluidBase
				         || block instanceof IReactorChamber
				         || !blockState.getMaterial().isOpaque() );
				this.facing = facing; 
				return output;
			}
		}
		isValid = false;
		this.facing = null;
		return null;
	}
	
	@Optional.Method(modid = "IC2")
	private boolean coolReactor(final IReactor reactor) {
		for (int x = 0; x < 9; x++) {
			for (int y = 0; y < 6; y++) {
				final ItemStack itemStack = reactor.getItemAt(x, y);
				if ( itemStack != null
				  && itemStack.getItem() instanceof ItemIC2reactorLaserFocus ) {
					final int heatInLaserFocus = itemStack.getItemDamage();
					final int heatEnergyCap = (int) Math.floor(Math.min(laserMedium_getEnergyStored() / WarpDriveConfig.IC2_REACTOR_ENERGY_PER_HEAT, heatInLaserFocus));
					final int heatToTransfer = Math.min(heatEnergyCap, WarpDriveConfig.IC2_REACTOR_COOLING_PER_INTERVAL); 
					if (heatToTransfer > 0) {
						if (laserMedium_consumeExactly((int) Math.ceil(heatToTransfer * WarpDriveConfig.IC2_REACTOR_ENERGY_PER_HEAT), false)) {
							ItemIC2reactorLaserFocus.addHeat(itemStack, -heatToTransfer);
							return true;
						}
					}
					return false;
				}
			}
		}
		return false;
	}
	
	@Override
	@Optional.Method(modid = "IC2")
	public void update() {
		super.update();
		
		if (worldObj.isRemote) {
			return;
		}
		
		ticks--;
		if (ticks <= 0)  {
			ticks = WarpDriveConfig.IC2_REACTOR_COOLING_INTERVAL_TICKS;
			final IReactor reactor = findReactor();
			updateBlockState();
			if (reactor == null) {
				return;
			}
			
			if (coolReactor(reactor)) {
				final Vector3 vMonitor = new Vector3(this).translate(0.5);
				PacketHandler.sendBeamPacket(worldObj,
				                             vMonitor,
				                             new Vector3(reactor.getPosition()).translate(0.5D),
				                             0.0f, 0.8f, 1.0f, 20, 0, 20);
			}
		}
	}
	
	private void updateBlockState() {
		IBlockState blockStateNew = getBlockType().getDefaultState().withProperty(BlockProperties.FACING, facing != null ? facing : EnumFacing.NORTH);
		if (!isValid) {
			blockStateNew = blockStateNew.withProperty(BlockProperties.VALID_POWERED, EnumValidPowered.INVALID);
		} else if (cache_laserMedium_energyStored < WarpDriveConfig.IC2_REACTOR_ENERGY_PER_HEAT) {
			blockStateNew = blockStateNew.withProperty(BlockProperties.VALID_POWERED, EnumValidPowered.VALID);
		} else {
			blockStateNew = blockStateNew.withProperty(BlockProperties.VALID_POWERED, EnumValidPowered.POWERED);
		}
		updateBlockState(blockStateNew, null, null);
	}
	
	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeToNBT(tagCompound);
		tagCompound.setInteger("ticks", ticks);
		return tagCompound;
	}
	
	@Override
	public void readFromNBT(final NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		ticks = tagCompound.getInteger("ticks");
	}
	
	@Nonnull
	@Override
	public NBTTagCompound getUpdateTag() {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		writeToNBT(tagCompound);
		return tagCompound;
	}
	
	@Override
	public void onDataPacket(final NetworkManager networkManager, final SPacketUpdateTileEntity packet) {
		final NBTTagCompound tagCompound = packet.getNbtCompound();
		readFromNBT(tagCompound);
	}
	
	@Override
	@Optional.Method(modid = "IC2")
	public ITextComponent getStatus() {
		if (worldObj == null) {
			return super.getStatus();
		}
		
		if (facing != null) {
			return super.getStatus() 
					.appendSibling(new TextComponentTranslation("warpdrive.ic2_reactor_laser_monitor.reactor_found",
						facing.name().toLowerCase()));
		} else {
			return super.getStatus()
					.appendSibling(new TextComponentTranslation("warpdrive.ic2_reactor_laser_monitor.no_reactor"));
		}
	}
}
