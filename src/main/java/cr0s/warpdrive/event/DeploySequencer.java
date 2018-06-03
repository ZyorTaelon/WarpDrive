package cr0s.warpdrive.event;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.ISequencerCallbacks;
import cr0s.warpdrive.block.movement.TileEntityShipCore;
import cr0s.warpdrive.data.EnumShipMovementType;
import cr0s.warpdrive.data.JumpShip;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public class DeploySequencer extends JumpSequencer {
	
	private String playerName;
	private ISequencerCallbacks callback;
	
	/*
	public DeploySequencer(final TileEntityShipCore shipCore, final EnumShipMovementType shipMovementType, final String nameTarget,
	                       final int moveX, final int moveY, final int moveZ, final byte rotationSteps,
	                       final int destX, final int destY, final int destZ) {
		super(shipCore, shipMovementType, nameTarget, moveX, moveY, moveZ, rotationSteps, destX, destY, destZ);
	}
	/**/
	
	public DeploySequencer(final JumpShip jumpShip, final World world, final boolean isInstantiated,
	                       final int destX, final int destY, final int destZ, final byte rotationSteps) {
		super(jumpShip, world, isInstantiated ? EnumShipMovementType.INSTANTIATE : EnumShipMovementType.RESTORE, destX, destY, destZ, rotationSteps);
	}
	
	public void setCaptain(final String playerName) {
		this.playerName = playerName;
		ship.setCaptain(playerName);
	}
	
	public void setCallback(final ISequencerCallbacks object) {
		this.callback = object;
	}
	
	@Override
	public void disableAndMessage(final String reason) {
		super.disableAndMessage(reason);
		callback.sequencer_finished();
	}
	
	@Override
	protected void state_chunkReleasing() {
		super.state_chunkReleasing();
		
		if (playerName != null) {
			// Warn owner if deployment done but wait next tick for teleportation
			final EntityPlayerMP entityPlayerMP = Commons.getOnlinePlayerByName(playerName);
			if (entityPlayerMP != null) {
				Commons.addChatMessage(entityPlayerMP, new TextComponentString("Ship deployed. Teleporting captain to the main deck"));
			}
		}
	}
	
	@Override
	protected void state_finishing() {
		if (playerName != null && !playerName.isEmpty()) {
			final EntityPlayerMP entityPlayerMP = Commons.getOnlinePlayerByName(playerName);
			if (entityPlayerMP != null) {
				final TileEntity tileEntity = targetWorld.getTileEntity(new BlockPos(destX, destY, destZ));
				if (tileEntity instanceof TileEntityShipCore) {
					final boolean isSuccess = ((TileEntityShipCore) tileEntity).summonOwnerOnDeploy(entityPlayerMP);
					if (isSuccess) {
						Commons.addChatMessage(entityPlayerMP, new TextComponentString("§6" + "Welcome aboard captain. Use the computer to get moving..."));
					} else {
						WarpDrive.logger.warn(String.format("Failed to assign new captain %s",
						                                    playerName));
					}
				} else {
					WarpDrive.logger.warn(String.format("Unable to detect ship core after deployment, found %s",
					                                    tileEntity));
				}
			}
		}
		
		super.state_finishing();
	}
}
