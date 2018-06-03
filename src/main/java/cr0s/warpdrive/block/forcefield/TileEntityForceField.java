package cr0s.warpdrive.block.forcefield;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBeamFrequency;
import cr0s.warpdrive.block.TileEntityAbstractBase;
import cr0s.warpdrive.config.Dictionary;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.ForceFieldSetup;
import cr0s.warpdrive.data.VectorI;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileEntityForceField extends TileEntityAbstractBase {
	
	private VectorI vProjector;
	
	// cache parameters used for rendering, force projector check for others
	private int cache_beamFrequency;
	public IBlockState cache_blockStateCamouflage;
	protected int cache_colorMultiplierCamouflage;
	protected int cache_lightCamouflage;
	
	// number of projectors check ignored before self-destruction
	private int gracePeriod_calls = 3;
	
	@Override
	public void readFromNBT(final NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		if (tagCompound.hasKey("projector")) {// are we server side and is it a valid force field block?
			vProjector = VectorI.createFromNBT(tagCompound.getCompoundTag("projector"));
			cache_beamFrequency = tagCompound.getInteger(IBeamFrequency.BEAM_FREQUENCY_TAG);
		} else {
			vProjector = null;
			cache_beamFrequency = -1;
		}
		if (tagCompound.hasKey("camouflage")) {
			final NBTTagCompound nbtCamouflage = tagCompound.getCompoundTag("camouflage");
			try {
				cache_blockStateCamouflage = Block.getBlockFromName(nbtCamouflage.getString("block")).getStateFromMeta(nbtCamouflage.getByte("meta"));
				cache_colorMultiplierCamouflage = nbtCamouflage.getInteger("color");
				cache_lightCamouflage = nbtCamouflage.getByte("light");
				if (Dictionary.BLOCKS_NOCAMOUFLAGE.contains(cache_blockStateCamouflage.getBlock())) {
					cache_blockStateCamouflage = null;
					cache_colorMultiplierCamouflage = 0;
					cache_lightCamouflage = 0;
				}
			} catch (final Exception exception) {
				exception.printStackTrace();
				cache_blockStateCamouflage = null;
				cache_colorMultiplierCamouflage = 0;
				cache_lightCamouflage = 0;
			}
		} else {
			cache_blockStateCamouflage = null;
			cache_colorMultiplierCamouflage = 0;
			cache_lightCamouflage = 0;
		}
	}
	
	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeToNBT(tagCompound);
		if (vProjector != null) {
			tagCompound.setTag("projector", vProjector.writeToNBT(new NBTTagCompound()));
			tagCompound.setInteger(IBeamFrequency.BEAM_FREQUENCY_TAG, cache_beamFrequency);
			if (cache_blockStateCamouflage != null) {
				final NBTTagCompound nbtCamouflage = new NBTTagCompound();
				nbtCamouflage.setString("block", cache_blockStateCamouflage.getBlock().getRegistryName().toString());
				nbtCamouflage.setByte("meta", (byte) cache_blockStateCamouflage.getBlock().getMetaFromState(cache_blockStateCamouflage));
				nbtCamouflage.setInteger("color", cache_colorMultiplierCamouflage);
				nbtCamouflage.setByte("light", (byte) cache_lightCamouflage);
				tagCompound.setTag("camouflage", nbtCamouflage);
			}
		}
		return tagCompound;
	}
	
	@Nonnull
	@Override
	public NBTTagCompound getUpdateTag() {
		final NBTTagCompound tagCompound = writeToNBT(super.getUpdateTag());
		
		tagCompound.removeTag("projector");
		tagCompound.removeTag(IBeamFrequency.BEAM_FREQUENCY_TAG);
		
		return tagCompound;
	}
	
	@Override
	public void onDataPacket(final NetworkManager networkManager, final SPacketUpdateTileEntity packet) {
		final NBTTagCompound tagCompound = packet.getNbtCompound();
		readFromNBT(tagCompound);
	}
	
	public void setProjector(final VectorI vectorI) {
		vProjector = vectorI;
		final ForceFieldSetup forceFieldSetup = getForceFieldSetup();
		if (forceFieldSetup != null) {
			cache_beamFrequency = forceFieldSetup.beamFrequency;
			if (getBlockMetadata() == forceFieldSetup.getCamouflageBlockState().getBlock().getMetaFromState(forceFieldSetup.getCamouflageBlockState())) {
				cache_blockStateCamouflage = forceFieldSetup.getCamouflageBlockState();
				cache_colorMultiplierCamouflage = forceFieldSetup.getCamouflageColorMultiplier();
				cache_lightCamouflage = forceFieldSetup.getCamouflageLight();
			}
		}
		IBlockState blockState = worldObj.getBlockState(pos);
		worldObj.notifyBlockUpdate(pos, blockState, blockState, 3);
	}
	
	public TileEntityForceFieldProjector getProjector() {
		if (vProjector != null) {
			final TileEntity tileEntity = vProjector.getTileEntity(worldObj);
			if (tileEntity instanceof TileEntityForceFieldProjector) {
				final TileEntityForceFieldProjector tileEntityForceFieldProjector = (TileEntityForceFieldProjector) tileEntity;
				if (worldObj.isRemote) {
					return tileEntityForceFieldProjector;
					
				} else if (tileEntityForceFieldProjector.isPartOfForceField(new VectorI(this))) {
					if (tileEntityForceFieldProjector.isOn()) {
						return tileEntityForceFieldProjector;
					} else {
						// projector is disabled or out of power
						worldObj.setBlockToAir(pos);
						if (WarpDriveConfig.LOGGING_FORCEFIELD) {
							WarpDrive.logger.info(String.format("Removed a force field from an offline projector @ %s (%d %d %d)", 
							                                    worldObj == null ? "~NULL~" : worldObj.provider.getSaveFolder(),
							                                    pos.getX(), pos.getY(), pos.getZ()));
						}
					}
				}
			}
		}
		
		if (!worldObj.isRemote) {
			gracePeriod_calls--;
			if (gracePeriod_calls < 0) {
				worldObj.setBlockToAir(pos);
				if (WarpDriveConfig.LOGGING_FORCEFIELD) {
					WarpDrive.logger.info(String.format("Removed a force field with no projector defined @ %s (%d %d %d)",
					                                    worldObj == null ? "~NULL~" : worldObj.provider.getSaveFolder(),
					                                    pos.getX(), pos.getY(), pos.getZ()));
				}
			}
		}
		
		return null;
	}
	
	public ForceFieldSetup getForceFieldSetup() {
		final TileEntityForceFieldProjector tileEntityForceFieldProjector = getProjector();
		if (tileEntityForceFieldProjector == null) {
			return null;
		}
		return tileEntityForceFieldProjector.getForceFieldSetup();
	}
}
