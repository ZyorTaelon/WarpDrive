package cr0s.warpdrive.event;

import cr0s.warpdrive.CommonProxy;
import cr0s.warpdrive.Commons;
import cr0s.warpdrive.LocalProfiler;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBlockTransformer;
import cr0s.warpdrive.api.ITransformation;
import cr0s.warpdrive.block.movement.TileEntityShipCore;
import cr0s.warpdrive.data.CelestialObjectManager;
import cr0s.warpdrive.config.Dictionary;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.CelestialObject;
import cr0s.warpdrive.data.EnumJumpSequencerState;
import cr0s.warpdrive.data.EnumShipMovementType;
import cr0s.warpdrive.data.JumpBlock;
import cr0s.warpdrive.data.JumpShip;
import cr0s.warpdrive.data.MovingEntity;
import cr0s.warpdrive.data.SoundEvents;
import cr0s.warpdrive.data.Transformation;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.data.VectorI;
import cr0s.warpdrive.network.PacketHandler;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentBase;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

public class JumpSequencer extends AbstractSequencer {
	
	// Jump vector
	protected Transformation transformation;
	
	private final EnumShipMovementType shipMovementType;
	private int moveX, moveY, moveZ;
	private final byte rotationSteps;
	private final String nameTarget;
	private Vector3 v3Source;
	private int blocksPerTick = WarpDriveConfig.G_BLOCKS_PER_TICK;
	private static final boolean enforceEntitiesPosition = false;
	
	protected final World sourceWorld;
	protected World targetWorld;
	private Ticket sourceWorldTicket;
	private Ticket targetWorldTicket;
	
	private boolean collisionDetected = false;
	private ArrayList<Vector3> collisionAtSource;
	private ArrayList<Vector3> collisionAtTarget;
	private float collisionStrength = 0;
	
	protected boolean isEnabled = false;
	private EnumJumpSequencerState enumJumpSequencerState = EnumJumpSequencerState.IDLE;
	private int actualIndexInShip = 0;
	
	protected final JumpShip ship;
	private boolean betweenWorlds;
	private boolean isPluginCheckDone = false;
	private String firstAdjustmentReason = "";
	
	protected final int destX;
	protected final int destY;
	protected final int destZ;
	
	private long msCounter = 0;
	private int ticks = 0;
	
	public JumpSequencer(final TileEntityShipCore shipCore, final EnumShipMovementType shipMovementType, final String nameTarget,
	                     final int moveX, final int moveY, final int moveZ, final byte rotationSteps,
	                     final int destX, final int destY, final int destZ) {
		this.sourceWorld = shipCore.getWorld();
		this.ship = new JumpShip();
		this.ship.worldObj = sourceWorld;
		this.ship.core = shipCore.getPos();
		this.ship.dx = shipCore.facing.getFrontOffsetX();
		this.ship.dz = shipCore.facing.getFrontOffsetZ();
		this.ship.minX = shipCore.minX;
		this.ship.maxX = shipCore.maxX;
		this.ship.minY = shipCore.minY;
		this.ship.maxY = shipCore.maxY;
		this.ship.minZ = shipCore.minZ;
		this.ship.maxZ = shipCore.maxZ;
		this.ship.shipCore = shipCore;
		this.shipMovementType = shipMovementType;
		this.moveX = moveX;
		this.moveY = moveY;
		this.moveZ = moveZ;
		this.rotationSteps = rotationSteps;
		this.nameTarget = nameTarget;
		this.destX = destX;
		this.destY = destY;
		this.destZ = destZ;
		
		// no animation
		v3Source = null;
		
		// set when preparing jump
		targetWorld = null;
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(String.format("%s Sequencer created for shipCore %s with shipMovementType %s",
			                                    this, shipCore, shipMovementType));
		}
	}
	
	public JumpSequencer(final JumpShip jumpShip, final World world, final EnumShipMovementType enumShipMovementType,
	                     final int destX, final int destY, final int destZ, final byte rotationSteps) {
		this.sourceWorld = null;
		this.ship = jumpShip;
		this.shipMovementType = enumShipMovementType;
		this.rotationSteps = rotationSteps;
		this.nameTarget = null;
		this.destX = destX;
		this.destY = destY;
		this.destZ = destZ;
		
		targetWorld = world;
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(String.format("%s Sequencer created for ship %s with shipMovementType %s",
			                                    this, ship, shipMovementType));
		}
	}
	
	public void setBlocksPerTick(final int blocksPerTick) {
		this.blocksPerTick = Math.min(WarpDriveConfig.G_BLOCKS_PER_TICK, blocksPerTick);
	}
	
	public void setEffectSource(final Vector3 v3Source) {
		this.v3Source = v3Source;
	}
	
	public void enable() {
		isEnabled = true;
		register();
	}

	@Deprecated
	public void disableAndMessage(final String message) {
		disableAndMessage(new TextComponentString(message));
	}
	public void disableAndMessage(final ITextComponent textComponent) {
		disable(textComponent);
		ship.messageToAllPlayersOnShip(textComponent);
	}
	public void disable(final ITextComponent textComponent) {
		if (!isEnabled) {
			return;
		}
		
		isEnabled = false;
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			if (textComponent == null || textComponent.getFormattedText().isEmpty()) {
				WarpDrive.logger.info(this + " Killing jump sequencer...");
			} else {
				WarpDrive.logger.info(this + " Killing jump sequencer... (" + textComponent.getFormattedText() + ")");
			}
		}
		
		releaseChunks();
		unregister();
	}
	
	@Override
	public boolean onUpdate() {
		if ( (sourceWorld != null && sourceWorld.isRemote)
		  || (targetWorld != null && targetWorld.isRemote) ) {
			return false;
		}
		
		if (!isEnabled) {
			if (WarpDriveConfig.LOGGING_JUMP) {
				WarpDrive.logger.info(this + " Removing from onUpdate...");
			}
			return false;
		}
		
		if (ship.minY < 0 || ship.maxY > 255) {
			final TextComponentBase msg = new TextComponentString("Invalid Y coordinate(s), check ship dimensions...");
			ship.messageToAllPlayersOnShip(msg);
			disable(msg);
			return true;
		}
		
		ticks++;
		switch (enumJumpSequencerState) {
		case IDLE:
			// blank state in case we got desynchronized
			msCounter = System.currentTimeMillis();
			if (isEnabled) {
				if ( shipMovementType != EnumShipMovementType.INSTANTIATE
				  && shipMovementType != EnumShipMovementType.RESTORE ) {
					enumJumpSequencerState = EnumJumpSequencerState.LOAD_SOURCE_CHUNKS;
				} else {
					enumJumpSequencerState = EnumJumpSequencerState.GET_INITIAL_VECTOR;
				}
			}
			break;
			
		case LOAD_SOURCE_CHUNKS:
			state_chunkLoadingSource();
			if (isEnabled) {
				actualIndexInShip = 0;
				enumJumpSequencerState = EnumJumpSequencerState.SAVE_TO_MEMORY;
			}
			break;
			
		case SAVE_TO_MEMORY:
			state_saveToMemory();
			if (isEnabled) {
				actualIndexInShip = 0;
				enumJumpSequencerState = EnumJumpSequencerState.CHECK_BORDERS;
			}
			break;
			
		case CHECK_BORDERS:
			state_checkBorders();
			if (isEnabled) {
				enumJumpSequencerState = EnumJumpSequencerState.SAVE_TO_DISK;
			}
			break;
			
		case SAVE_TO_DISK:
			state_saveToDisk();
			if (isEnabled) {
				enumJumpSequencerState = EnumJumpSequencerState.GET_INITIAL_VECTOR;
			}
			break;
			
		case GET_INITIAL_VECTOR:
			state_getInitialVector();
			if (isEnabled) {
				enumJumpSequencerState = EnumJumpSequencerState.ADJUST_JUMP_VECTOR;
			}
			break;
			
		case ADJUST_JUMP_VECTOR:
			state_adjustJumpVector();
			if (isEnabled) {
				enumJumpSequencerState = EnumJumpSequencerState.LOAD_TARGET_CHUNKS;
			}
			break;
			
		case LOAD_TARGET_CHUNKS:
			state_loadTargetChunks();
			if (isEnabled) {
				enumJumpSequencerState = EnumJumpSequencerState.SAVE_ENTITIES;
			}
			break;
			
		case SAVE_ENTITIES:
			state_saveEntitiesAndInformPlayers();
			if (isEnabled) {
				actualIndexInShip = 0;
				enumJumpSequencerState = EnumJumpSequencerState.MOVE_BLOCKS;
			}
			break;
			
		case MOVE_BLOCKS:
			state_moveBlocks();
			if (actualIndexInShip >= ship.jumpBlocks.length - 1) {
				actualIndexInShip = 0;
				enumJumpSequencerState = EnumJumpSequencerState.MOVE_EXTERNALS;
			}
			break;
			
		case MOVE_EXTERNALS:
			state_moveExternals();
			if (actualIndexInShip >= ship.jumpBlocks.length - 1) {
				enumJumpSequencerState = EnumJumpSequencerState.MOVE_ENTITIES;
			}
			break;
			
		case MOVE_ENTITIES:
			state_moveEntities();
			actualIndexInShip = 0;
			enumJumpSequencerState = EnumJumpSequencerState.REMOVING;
			break;
			
		case REMOVING:
			if (enforceEntitiesPosition) {
				restoreEntitiesPosition();
			}
			state_removeBlocks();
			
			if (actualIndexInShip >= ship.jumpBlocks.length - 1) {
				enumJumpSequencerState = EnumJumpSequencerState.CHUNK_UNLOADING;
			}
			break;
			
		case CHUNK_UNLOADING:
			state_chunkReleasing();
			enumJumpSequencerState = EnumJumpSequencerState.FINISHING;
			break;
			
		case FINISHING:
			state_finishing();
			enumJumpSequencerState = EnumJumpSequencerState.IDLE;
			break;
			
		default:
			final TextComponentBase msg = new TextComponentString("Invalid state, aborting jump...");
			ship.messageToAllPlayersOnShip(msg);
			disable(msg);
			return true;
		}
		return true;
	}
	
	private boolean forceSourceChunks(final StringBuilder reason) {
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Forcing source chunks in " + sourceWorld.provider.getDimensionType().getName());
		}
		sourceWorldTicket = ForgeChunkManager.requestTicket(WarpDrive.instance, sourceWorld, Type.NORMAL);
		if (sourceWorldTicket == null) {
			reason.append(String.format("Chunkloading rejected in source world %s. Aborting.",
			                            sourceWorld.provider.getSaveFolder()));
			return false;
		}
		
		final int minX = ship.minX >> 4;
		final int maxX = ship.maxX >> 4;
		final int minZ = ship.minZ >> 4;
		final int maxZ = ship.maxZ >> 4;
		int chunkCount = 0;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				chunkCount++;
				if (chunkCount > sourceWorldTicket.getMaxChunkListDepth()) {
					reason.append(String.format("Ship is extending over %d chunks in source world. Max is currently set to %d in config/forgeChunkLoading.cfg. Aborting.",
					                            (maxX - minX + 1) * (maxZ - minZ + 1),
					                            sourceWorldTicket.getMaxChunkListDepth()));
					return false;
				}
				ForgeChunkManager.forceChunk(sourceWorldTicket, new ChunkPos(x, z));
			}
		}
		return true;
	}
	
	private boolean forceTargetChunks(final StringBuilder reason) {
		LocalProfiler.start("Jump.forceTargetChunks");
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Forcing target chunks in " + targetWorld.provider.getDimensionType().getName());
		}
		targetWorldTicket = ForgeChunkManager.requestTicket(WarpDrive.instance, targetWorld, Type.NORMAL);
		if (targetWorldTicket == null) {
			reason.append(String.format("Chunkloading rejected in target world %s. Aborting.",
			                            targetWorld.provider.getSaveFolder()));
			return false;
		}
		
		final BlockPos targetMin = transformation.apply(ship.minX, ship.minY, ship.minZ);
		final BlockPos targetMax = transformation.apply(ship.maxX, ship.maxY, ship.maxZ);
		final int minX = Math.min(targetMin.getX(), targetMax.getX()) >> 4;
		final int maxX = Math.max(targetMin.getX(), targetMax.getX()) >> 4;
		final int minZ = Math.min(targetMin.getZ(), targetMax.getZ()) >> 4;
		final int maxZ = Math.max(targetMin.getZ(), targetMax.getZ()) >> 4;
		int chunkCount = 0;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				chunkCount++;
				if (chunkCount > targetWorldTicket.getMaxChunkListDepth()) {
					reason.append(String.format("Ship is extending over %d chunks in target world. Max is currently set to %d in config/forgeChunkLoading.cfg. Aborting.",
					                            (maxX - minX + 1) * (maxZ - minZ + 1),
					                            targetWorldTicket.getMaxChunkListDepth()));
					return false;
				}
				ForgeChunkManager.forceChunk(targetWorldTicket, new ChunkPos(x, z));
			}
		}
		LocalProfiler.stop();
		return true;
	}
	
	private void releaseChunks() {
		if ( sourceWorldTicket == null
		  && targetWorldTicket == null ) {
			return;
		}
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Releasing chunks");
		}
		
		int minX, maxX, minZ, maxZ;
		if (sourceWorldTicket != null) {
			minX = ship.minX >> 4;
			maxX = ship.maxX >> 4;
			minZ = ship.minZ >> 4;
			maxZ = ship.maxZ >> 4;
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					sourceWorld.getChunkFromChunkCoords(x, z).generateSkylightMap();
					ForgeChunkManager.unforceChunk(sourceWorldTicket, new ChunkPos(x, z));
				}
			}
			ForgeChunkManager.releaseTicket(sourceWorldTicket);
			sourceWorldTicket = null;
		}
		
		if (targetWorldTicket != null) {
			final BlockPos targetMin = transformation.apply(ship.minX, ship.minY, ship.minZ);
			final BlockPos targetMax = transformation.apply(ship.maxX, ship.maxY, ship.maxZ);
			minX = Math.min(targetMin.getX(), targetMax.getX()) >> 4;
			maxX = Math.max(targetMin.getX(), targetMax.getX()) >> 4;
			minZ = Math.min(targetMin.getZ(), targetMax.getZ()) >> 4;
			maxZ = Math.max(targetMin.getZ(), targetMax.getZ()) >> 4;
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					targetWorld.getChunkFromChunkCoords(x, z).generateSkylightMap();
					ForgeChunkManager.unforceChunk(targetWorldTicket, new ChunkPos(x, z));
				}
			}
			ForgeChunkManager.releaseTicket(targetWorldTicket);
			targetWorldTicket = null;
		}
	}
	
	protected void state_chunkLoadingSource() {
		LocalProfiler.start("Jump.chunkLoadingSource");
		
		final StringBuilder reason = new StringBuilder();
		
		if (!forceSourceChunks(reason)) {
			final TextComponentBase msg = new TextComponentString(reason.toString());
			disable(msg);
			ship.messageToAllPlayersOnShip(msg);
			LocalProfiler.stop();
			return;
		}
		
		LocalProfiler.stop();
	}
	
	protected void state_saveToMemory() {
		LocalProfiler.start("Jump.saveToMemory");
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Saving ship...");
		}
		
		final StringBuilder reason = new StringBuilder();
		
		if (!ship.save(reason)) {
			final ITextComponent msg = new TextComponentString(reason.toString());
			disable(msg);
			ship.messageToAllPlayersOnShip(msg);
			LocalProfiler.stop();
			return;
		}
		
		LocalProfiler.stop();
	}
	
	protected void state_checkBorders() {
		LocalProfiler.start("Jump.checkBorders");
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Checking ship borders...");
		}
		
		final StringBuilder reason = new StringBuilder();
		
		if (!ship.checkBorders(reason)) {
			final ITextComponent msg = new TextComponentString(reason.toString());
			disable(msg);
			ship.messageToAllPlayersOnShip(msg);
			LocalProfiler.stop();
			return;
		}
		LocalProfiler.stop();
	}
	
	protected void state_saveToDisk() {
		LocalProfiler.start("Jump.saveToDisk");
		
		final File file = new File(WarpDriveConfig.G_SCHEMALOCATION + "/auto");
		if (!file.exists() || !file.isDirectory()) {
			if (!file.mkdirs()) {
				WarpDrive.logger.warn("Unable to create auto-backup folder, skipping...");
				LocalProfiler.stop();
				return;
			}
		}
		
		try {
			// Generate unique file name
			final SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss's'SSS");
			final String shipName = ship.shipCore.shipName.replaceAll("[^ -~]", "").replaceAll("[:/\\\\]", "");
			String schematicFileName;
			do {
				final Date now = new Date();
				schematicFileName = WarpDriveConfig.G_SCHEMALOCATION + "/auto/" + shipName + "_" + sdfDate.format(now) + ".schematic";
			} while (new File(schematicFileName).exists());
			
			// Save header
			final NBTTagCompound schematic = new NBTTagCompound();
			
			final short width = (short) (ship.shipCore.maxX - ship.shipCore.minX + 1);
			final short length = (short) (ship.shipCore.maxZ - ship.shipCore.minZ + 1);
			final short height = (short) (ship.shipCore.maxY - ship.shipCore.minY + 1);
			schematic.setShort("Width", width);
			schematic.setShort("Length", length);
			schematic.setShort("Height", height);
			schematic.setInteger("shipMass", ship.shipCore.shipMass);
			schematic.setString("shipName", ship.shipCore.shipName);
			schematic.setInteger("shipVolume", ship.shipCore.shipVolume);
			final NBTTagCompound tagCompoundShip = new NBTTagCompound();
			ship.writeToNBT(tagCompoundShip);
			schematic.setTag("ship", tagCompoundShip);
			WarpDrive.logger.info(this + " Saving ship state prior to jump in " + schematicFileName);
			Commons.writeNBTToFile(schematicFileName, schematic);
			if (WarpDriveConfig.LOGGING_JUMP && WarpDrive.isDev) {
				WarpDrive.logger.info(this + " Ship saved as " + schematicFileName);
			}
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
		
		msCounter = System.currentTimeMillis();
		LocalProfiler.stop();
	}
	
	protected void state_getInitialVector() {
		LocalProfiler.start("Jump.getInitialVector");
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Getting initial target vector...");
		}
		
		final StringBuilder reason = new StringBuilder();
		
		betweenWorlds = shipMovementType == EnumShipMovementType.PLANET_TAKEOFF
		             || shipMovementType == EnumShipMovementType.PLANET_LANDING
		             || shipMovementType == EnumShipMovementType.HYPERSPACE_EXITING
		             || shipMovementType == EnumShipMovementType.HYPERSPACE_ENTERING;
		// note: when deploying from scanner shipMovementType is CREATIVE, so betweenWorlds is false
		
		{// compute targetWorld and movement vector (moveX, moveY, moveZ)
			final CelestialObject celestialObjectSource = CelestialObjectManager.get(sourceWorld, ship.core.getX(), ship.core.getZ());
			final boolean isTargetWorldFound = computeTargetWorld(celestialObjectSource, shipMovementType, reason);
			if (!isTargetWorldFound) {
				LocalProfiler.stop();
				ship.messageToAllPlayersOnShip(new TextComponentString(reason.toString()));
				disable(new TextComponentString(reason.toString()));
				return;
			}
		}
		
		// Check mass constrains
		if ( ( sourceWorld != null
		    && CelestialObjectManager.isPlanet(sourceWorld, ship.core.getX(), ship.core.getZ()) )
		  || CelestialObjectManager.isPlanet(targetWorld, ship.core.getX() + moveX, ship.core.getZ() + moveZ) ) {
			if (!ship.isUnlimited() && ship.actualMass > WarpDriveConfig.SHIP_VOLUME_MAX_ON_PLANET_SURFACE) {
				LocalProfiler.stop();
				final ITextComponent message = new TextComponentString(String.format("Ship is too big for a planet (max is %d blocks while ship is %d blocks)",
				                                 WarpDriveConfig.SHIP_VOLUME_MAX_ON_PLANET_SURFACE, ship.actualMass));
				ship.messageToAllPlayersOnShip(message);
				disable(message);
				return;
			}
		}
		
		if (betweenWorlds && WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " From world " + sourceWorld.provider.getDimensionType().getName() + " to " + targetWorld.provider.getDimensionType().getName());
		}
		
		// Calculate jump vector
		isPluginCheckDone = false;
		firstAdjustmentReason = "";
		switch (shipMovementType) {
		case GATE_ACTIVATING:
			moveX = destX - ship.core.getX();
			moveY = destY - ship.core.getY();
			moveZ = destZ - ship.core.getZ();
			break;
			
		case INSTANTIATE:
		case RESTORE:
			moveX = destX - ship.core.getX();
			moveY = destY - ship.core.getY();
			moveZ = destZ - ship.core.getZ();
			isPluginCheckDone = true;
			break;
			
		case PLANET_TAKEOFF:
			// enter space at current altitude
			moveY = 0;
			break;
			
		case PLANET_LANDING:
			// re-enter atmosphere at max altitude
			moveY = 245 - ship.maxY;
			break;
			
		case PLANET_MOVING:
		case SPACE_MOVING:
		case HYPERSPACE_MOVING:
			if ((ship.maxY + moveY) > 255) {
				moveY = 255 - ship.maxY;
			}
			
			if ((ship.minY + moveY) < 5) {
				moveY = 5 - ship.minY;
			}
			
			// Do not check in long jumps
			final int rangeX = Math.abs(moveX) - (ship.maxX - ship.minX);
			final int rangeZ = Math.abs(moveZ) - (ship.maxZ - ship.minZ);
			if (Math.max(rangeX, rangeZ) < 256) {
				firstAdjustmentReason = getPossibleJumpDistance();
				isPluginCheckDone = true;
			}
			break;
			
		case HYPERSPACE_ENTERING:
		case HYPERSPACE_EXITING:
			break;
			
		default:
			WarpDrive.logger.error(String.format("Invalid movement type %s in JumpSequence.", shipMovementType));
			break;
		}
		transformation = new Transformation(ship, targetWorld, moveX, moveY, moveZ, rotationSteps);
		
		LocalProfiler.stop();
	}
	
	protected void state_adjustJumpVector() {
		LocalProfiler.start("Jump.adjustJumpVector");
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Adjusting jump vector...");
		}
		
		// final StringBuilder reason = new StringBuilder();
		
		{
			final BlockPos target1 = transformation.apply(ship.minX, ship.minY, ship.minZ);
			final BlockPos target2 = transformation.apply(ship.maxX, ship.maxY, ship.maxZ);
			final AxisAlignedBB aabbSource = new AxisAlignedBB(ship.minX, ship.minY, ship.minZ, ship.maxX, ship.maxY, ship.maxZ);
			aabbSource.expand(1.0D, 1.0D, 1.0D);
			final AxisAlignedBB aabbTarget = new AxisAlignedBB(
					Math.min(target1.getX(), target2.getX()), Math.min(target1.getY(), target2.getY()), Math.min(target1.getZ(), target2.getZ()),
					Math.max(target1.getX(), target2.getX()), Math.max(target1.getY(), target2.getY()), Math.max(target1.getZ(), target2.getZ()));
			// Validate positions aren't overlapping
			if ( shipMovementType != EnumShipMovementType.INSTANTIATE
			  && shipMovementType != EnumShipMovementType.RESTORE
			  && !betweenWorlds
			  && aabbSource.intersectsWith(aabbTarget) ) {
				// render fake explosions
				doCollisionDamage(false);
				
				// cancel jump
				final TextComponentBase msg;
				if (firstAdjustmentReason == null || firstAdjustmentReason.isEmpty()) {
					msg = new TextComponentString("Source and target areas are overlapping, jump aborted! Try increasing jump distance...");
				} else {
					msg = new TextComponentString(firstAdjustmentReason + "\nNot enough space after adjustment, jump aborted!");
				}
				disableAndMessage(msg);
				LocalProfiler.stop();
				return;
			}
			
			// Check world border
			final CelestialObject celestialObjectTarget = CelestialObjectManager.get(targetWorld, (int) aabbTarget.minX, (int) aabbTarget.minZ);
			if (celestialObjectTarget == null) {
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.error(String.format("There's no world border defined for dimension %s (%d)",
						targetWorld.provider.getSaveFolder(), targetWorld.provider.getDimension()));
				}
				
			} else {
				// are we in range?
				if (!celestialObjectTarget.isInsideBorder(aabbTarget)) {
					final AxisAlignedBB axisAlignedBB = celestialObjectTarget.getWorldBorderArea();
					ITextComponent message = new TextComponentTranslation(
						  "Target ship position is outside planet border, unable to jump!\n"
						+ "World borders are (%d %d %d) to (%d %d %d).",
						(int) axisAlignedBB.minX, (int) axisAlignedBB.minY, (int) axisAlignedBB.minZ,
						(int) axisAlignedBB.maxX, (int) axisAlignedBB.maxY, (int) axisAlignedBB.maxZ );
					LocalProfiler.stop();
					ship.messageToAllPlayersOnShip(message);
					disable(message);
					return;
				}
			}
		}
		if (!isPluginCheckDone) {
			final CheckMovementResult checkMovementResult = checkCollisionAndProtection(transformation, true, "target");
			if (checkMovementResult != null) {
				final TextComponentBase msg = new TextComponentString(checkMovementResult.reason + "\nJump aborted!");
				disable(msg);
				ship.messageToAllPlayersOnShip(msg);
				LocalProfiler.stop();
				return;
			}
		}
		
		LocalProfiler.stop();
	}
	
	protected void state_loadTargetChunks() {
		LocalProfiler.start("Jump.loadTargetChunks");
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Loading chunks at target...");
		}
		
		final StringBuilder reason = new StringBuilder();
		
		if (!forceTargetChunks(reason)) {
			final ITextComponent msg = new TextComponentString(reason.toString());
			disable(msg);
			ship.messageToAllPlayersOnShip(msg);
			LocalProfiler.stop();
			return;
		}
		
		LocalProfiler.stop();
	}
	
	protected void state_saveEntitiesAndInformPlayers() {
		LocalProfiler.start("Jump.saveEntitiesAndInformPlayers");
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Saving entities...");
		}
		
		final StringBuilder reason = new StringBuilder();
		
		{
			if ( shipMovementType != EnumShipMovementType.INSTANTIATE
			  && shipMovementType != EnumShipMovementType.RESTORE ) {
				if (!ship.saveEntities(reason)) {
					final ITextComponent msg = new TextComponentString(reason.toString());
					disable(msg);
					ship.messageToAllPlayersOnShip(msg);
					LocalProfiler.stop();
					return;
				}
			}
			if (WarpDriveConfig.LOGGING_JUMP) {
				WarpDrive.logger.info(this + " Saved " + ship.entitiesOnShip.size() + " entities from ship");
			}
		}
		
		switch (shipMovementType) {
		case HYPERSPACE_ENTERING:
			ship.messageToAllPlayersOnShip(new TextComponentString("Entering hyperspace..."));
			break;
			
		case HYPERSPACE_EXITING:
			ship.messageToAllPlayersOnShip(new TextComponentString("Leaving hyperspace.."));
			break;
			
		case GATE_ACTIVATING:
			ship.messageToAllPlayersOnShip(new TextComponentString(String.format("Engaging jumpgate towards %s!",
			                                             nameTarget)));
			break;
			
		// case GATE_ACTIVATING:
		// 	ship.messageToAllPlayersOnShip(new TextComponentString(String.format("Jumping to coordinates (%d %d %d)!",
		// 	                                             destX, destY, destZ)));
		// 	break;
		
		case INSTANTIATE:
		case RESTORE:
			// no messages in creative
			break;
			
		default:
			ship.messageToAllPlayersOnShip(new TextComponentString(String.format("Jumping of %d blocks (XYZ %d %d %d)", 
			                                             (int) Math.ceil(Math.sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ)),
			                                             moveX, moveY, moveZ)));
			break;
		}
		
		if ( shipMovementType != EnumShipMovementType.INSTANTIATE
		  && shipMovementType != EnumShipMovementType.RESTORE ) {
			switch (rotationSteps) {
			case 1:
				ship.messageToAllPlayersOnShip(new TextComponentString("Turning to the right"));
				break;
			case 2:
				ship.messageToAllPlayersOnShip(new TextComponentString("Turning back"));
				break;
			case 3:
				ship.messageToAllPlayersOnShip(new TextComponentString("Turning to the left"));
				break;
			default:
				break;
			}
		}
		
		LocalProfiler.stop();
		if (WarpDrive.isDev && WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(String.format("Removing TE duplicates: tileEntities in target world before jump: %d",
			                                    targetWorld.loadedTileEntityList.size()));
		}
	}
	
	protected boolean computeTargetWorld(final CelestialObject celestialObjectSource, final EnumShipMovementType shipMovementType, final StringBuilder reason) {
		switch (shipMovementType) {
		case INSTANTIATE:
		case RESTORE:
			// already defined, nothing to do
			break;
			
		case HYPERSPACE_EXITING: {
			final CelestialObject celestialObject = CelestialObjectManager.getClosestChild(sourceWorld, ship.core.getX(), ship.core.getZ());
			// anything defined?
			if (celestialObject == null) {
				reason.append(String.format("Unable to reach space from this location!\nThere's no celestial object defined for current dimension %s (%d).",
				                            sourceWorld.provider.getSaveFolder(), sourceWorld.provider.getDimension()));
				return false;
			}
			
			// are we clear for transit?
			final double distanceSquared = celestialObject.getSquareDistanceInParent(sourceWorld.provider.getDimension(), ship.core.getX(), ship.core.getZ());
			if (distanceSquared > 0.0D) {
				final AxisAlignedBB axisAlignedBB = celestialObject.getAreaInParent();
				reason.append(String.format(
						"Ship is outside any solar system, unable to reach space!\n"
						+ "Closest transition area is ~%d m away (%d %d %d) to (%d %d %d).",
						(int) Math.sqrt(distanceSquared),
						(int) axisAlignedBB.minX, (int) axisAlignedBB.minY, (int) axisAlignedBB.minZ,
						(int) axisAlignedBB.maxX, (int) axisAlignedBB.maxY, (int) axisAlignedBB.maxZ));
				return false;
			}
			
			// is world available?
			final int dimensionIdSpace = celestialObject.dimensionId;
			final MinecraftServer server = sourceWorld.getMinecraftServer();
			assert(server != null);
			try {
				targetWorld = server.worldServerForDimension(dimensionIdSpace);
			} catch (Exception exception) {
				exception.printStackTrace();
				reason.append(String.format("Unable to load Space dimension %d, aborting jump.",
				                            dimensionIdSpace));
				return false;
			}
			
			// update movement vector
			final VectorI vEntry = celestialObject.getEntryOffset();
			moveX = vEntry.x;
			moveZ = vEntry.z;
		}
		break;
		
		case HYPERSPACE_ENTERING: {
			// anything defined?
			if (celestialObjectSource.parent == null) {
				reason.append(String.format("Unable to reach hyperspace!\nThere's no parent defined for current dimension %s (%d).",
				                            sourceWorld.provider.getSaveFolder(), sourceWorld.provider.getDimension()));
				return false;
			}
			// (target world border is checked systematically after movement checks)
			
			// is world available?
			final int dimensionIdHyperspace = celestialObjectSource.parent.dimensionId;
			final MinecraftServer server = sourceWorld.getMinecraftServer();
			assert(server != null);
			try {
				targetWorld = server.worldServerForDimension(dimensionIdHyperspace);
			} catch (Exception exception) {
				exception.printStackTrace();
				reason.append(String.format("Unable to load Hyperspace dimension %d, aborting jump.",
				                            dimensionIdHyperspace));
				return false;
			}
			
			// update movement vector
			final VectorI vEntry = celestialObjectSource.getEntryOffset();
			moveX = -vEntry.x;
			moveZ = -vEntry.z;
		}
		break;
		
		case PLANET_TAKEOFF: {
			// anything defined?
			if (celestialObjectSource.parent == null) {
				reason.append(String.format("Unable to take off!\nThere's no parent defined for current dimension %s (%d).",
				                            sourceWorld.provider.getSaveFolder(), sourceWorld.provider.getDimension()));
				return false;
			}
			
			// are we clear for transit?
			final double distanceSquared = celestialObjectSource.getSquareDistanceOutsideBorder(ship.core.getX(), ship.core.getZ());
			if (distanceSquared > 0) {
				final AxisAlignedBB axisAlignedBB = celestialObjectSource.getAreaToReachParent();
				reason.append(String.format(
						"Ship is outside planet border, unable to reach space!\n"
						+ "Closest transition area is ~%d m away (%d %d %d) to (%d %d %d).",
						(int) Math.sqrt(distanceSquared),
						(int) axisAlignedBB.minX, (int) axisAlignedBB.minY, (int) axisAlignedBB.minZ,
						(int) axisAlignedBB.maxX, (int) axisAlignedBB.maxY, (int) axisAlignedBB.maxZ));
				return false;
			}
			
			// is world available?
			final int dimensionIdSpace = celestialObjectSource.parent.dimensionId;
			final MinecraftServer server = sourceWorld.getMinecraftServer();
			assert(server != null);
			try {
				targetWorld = server.worldServerForDimension(dimensionIdSpace);
			} catch (Exception exception) {
				exception.printStackTrace();
				reason.append(String.format("Unable to load Space dimension %d, aborting jump.",
					dimensionIdSpace));
				return false;
			}
			
			// update movement vector
			final VectorI vEntry = celestialObjectSource.getEntryOffset();
			moveX = -vEntry.x;
			moveZ = -vEntry.z;
		}
		break;
		
		case PLANET_LANDING: {
			final CelestialObject celestialObject = CelestialObjectManager.getClosestChild(sourceWorld, ship.core.getX(), ship.core.getZ());
			// anything defined?
			if (celestialObject == null) {
				reason.append("No planet exists in this dimension, there's nowhere to land!");
				return false;
			}
			
			// are we in orbit?
			final double distanceSquared = celestialObject.getSquareDistanceInParent(sourceWorld.provider.getDimension(), ship.core.getX(), ship.core.getZ());
			if (distanceSquared > 0.0D) {
				final AxisAlignedBB axisAlignedBB = celestialObject.getAreaInParent();
				reason.append(String.format(
						"No planet in range, unable to enter atmosphere!\n"
						+ "Closest planet is %d m away (%d %d %d) to (%d %d %d).",
						(int) Math.sqrt(distanceSquared),
						(int) axisAlignedBB.minX, (int) axisAlignedBB.minY, (int) axisAlignedBB.minZ,
						(int) axisAlignedBB.maxX, (int) axisAlignedBB.maxY, (int) axisAlignedBB.maxZ));
				return false;
			}
			
			// is it defined?
			if (celestialObject.isVirtual()) {
				reason.append(String.format("Sorry, we can't go to %s. This is a virtual celestial object. It's either a decorative planet or a server misconfiguration",
				                            celestialObject.getDisplayName()));
				return false;
			}
			
			// validate world availability
			final MinecraftServer server = sourceWorld.getMinecraftServer();
			assert(server != null);
			try {
				targetWorld = server.worldServerForDimension(celestialObject.dimensionId);
			} catch (Exception exception) {
				exception.printStackTrace();
				reason.append(String.format("Sorry, we can't land here. Dimension %d isn't defined. It might be a decorative planet or a server misconfiguration",
				                            celestialObject.dimensionId));
				return false;
			}
			
			// update movement vector
			final VectorI vEntry = celestialObject.getEntryOffset();
			moveX = vEntry.x;
			moveZ = vEntry.z;
		}
		break;
			
		case SPACE_MOVING:
		case HYPERSPACE_MOVING:
		case PLANET_MOVING:
			targetWorld = sourceWorld;
			break;
			
		case GATE_ACTIVATING:
			// @TODO Jumpgate reimplementation
		default:
			reason.append(String.format("Invalid movement type %s", shipMovementType));
			return false;
		}
		
		return true;
	}
	
	protected void state_moveBlocks() {
		LocalProfiler.start("Jump.moveBlocks");
		final int blocksToMove = Math.min(blocksPerTick, ship.jumpBlocks.length - actualIndexInShip);
		final int periodEffect = Math.max(1, blocksToMove / 10);
		if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
			WarpDrive.logger.info(this + " Moving ship blocks " + actualIndexInShip + " to " + (actualIndexInShip + blocksToMove - 1) + " / " + (ship.jumpBlocks.length - 1));
		}
		
		int indexEffect = targetWorld.rand.nextInt(periodEffect);
		for (int index = 0; index < blocksToMove; index++) {
			if (actualIndexInShip >= ship.jumpBlocks.length) {
				break;
			}
			
			final JumpBlock jumpBlock = ship.jumpBlocks[actualIndexInShip];
			if (jumpBlock != null) {
				if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
					WarpDrive.logger.info("Deploying from " + jumpBlock.x + " " + jumpBlock.y + " " + jumpBlock.z + " of " + jumpBlock.block + "@" + jumpBlock.blockMeta);
				}
				if (shipMovementType == EnumShipMovementType.INSTANTIATE) {
					jumpBlock.removeUniqueIDs();
					jumpBlock.fillEnergyStorage();
				}
				
				final BlockPos target = jumpBlock.deploy(targetWorld, transformation);
				
				if ( shipMovementType != EnumShipMovementType.INSTANTIATE
				  && shipMovementType != EnumShipMovementType.RESTORE ) {
					sourceWorld.removeTileEntity(new BlockPos(jumpBlock.x, jumpBlock.y, jumpBlock.z));
				}
				
				indexEffect--;
				if (indexEffect <= 0) {
					indexEffect = periodEffect;
					doBlockEffect(jumpBlock, target);
				}
			}
			actualIndexInShip++;
		}
		
		LocalProfiler.stop();
	}
	
	protected void doBlockEffect(final JumpBlock jumpBlock, final BlockPos target) {
		switch (shipMovementType) {
		case HYPERSPACE_ENTERING:
		case PLANET_TAKEOFF:
			PacketHandler.sendBeamPacket(sourceWorld,
			                             new Vector3(jumpBlock.x + 0.5D, jumpBlock.y + 0.5D, jumpBlock.z + 0.5D),
			                             new Vector3(target.getX() + 0.5D, target.getY() + 32.5D + targetWorld.rand.nextInt(5), target.getZ() + 0.5D),
			                             0.5F, 0.7F, 0.2F, 30, 0, 100);
			PacketHandler.sendBeamPacket(targetWorld,
			                             new Vector3(target.getX() + 0.5D, target.getY() - 31.5D - targetWorld.rand.nextInt(5), target.getZ() + 0.5D),
			                             new Vector3(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D),
			                             0.5F, 0.7F, 0.2F, 30, 0, 100);
			break;
			
		case HYPERSPACE_EXITING:
		case PLANET_LANDING:
			PacketHandler.sendBeamPacket(sourceWorld,
			                             new Vector3(jumpBlock.x + 0.5D, jumpBlock.y + 0.5D, jumpBlock.z + 0.5D),
			                             new Vector3(target.getX() + 0.5D, target.getY() - 31.5D - targetWorld.rand.nextInt(5), target.getZ() + 0.5D),
			                             0.7F, 0.1F, 0.6F, 30, 0, 100);
			PacketHandler.sendBeamPacket(targetWorld,
			                             new Vector3(target.getX() + 0.5D, target.getY() + 32.5D + targetWorld.rand.nextInt(5), target.getZ() + 0.5D),
			                             new Vector3(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D),
			                             0.7F, 0.1F, 0.6F, 30, 0, 100);
			break;
			
		case HYPERSPACE_MOVING:
		case PLANET_MOVING:
		case SPACE_MOVING:
			PacketHandler.sendBeamPacket(targetWorld,
			                             new Vector3(jumpBlock.x + 0.5D, jumpBlock.y + 0.5D, jumpBlock.z + 0.5D),
			                             new Vector3(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D),
			                             0.6F, 0.1F, 0.7F, 30, 0, 100);
			break;
			
		case GATE_ACTIVATING:
			break;
			
		case INSTANTIATE:
		case RESTORE:
			if (v3Source != null) {
				// play the builder effect
				targetWorld.playSound(null, target, SoundEvents.LASER_LOW, SoundCategory.BLOCKS, 0.5F, 1.0F);
				
				PacketHandler.sendBeamPacket(targetWorld,
				                             v3Source,
				                             new Vector3(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D),
				                             0.0F, 1.0F, 0.0F, 15, 0, 100);
			}
			
			// play the placement sound effect
			final SoundType soundtype = jumpBlock.block.getSoundType(targetWorld.getBlockState(target), targetWorld, target, null);
			targetWorld.playSound(null, target, soundtype.getPlaceSound(), SoundCategory.BLOCKS,
			                      (soundtype.getVolume() + 1.0F) / 2.0F,
			                      soundtype.getPitch() * 0.8F);
			break;
			
		case NONE:
			break;
		}
	}
	
	protected void state_moveExternals() {
		LocalProfiler.start("Jump.moveExternals");
		final int blocksToMove = Math.min(blocksPerTick, ship.jumpBlocks.length - actualIndexInShip);
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Moving ship externals from " + actualIndexInShip + " / " + (ship.jumpBlocks.length - 1));
		}
		int index = 0;
		while (index < blocksToMove && actualIndexInShip < ship.jumpBlocks.length) {
			final JumpBlock jumpBlock = ship.jumpBlocks[ship.jumpBlocks.length - actualIndexInShip - 1];
			if (jumpBlock == null) {
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info(this + " Moving ship externals: unexpected null found at ship[" + actualIndexInShip + "]");
				}
				actualIndexInShip++;
				continue;
			}
			
			if (jumpBlock.externals != null) {
				if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
					WarpDrive.logger.info("Moving externals for block " + jumpBlock.block + "@" + jumpBlock.blockMeta + " at " + jumpBlock.x + " " + jumpBlock.y + " " + jumpBlock.z);
				}
				final TileEntity tileEntitySource = jumpBlock.getTileEntity(sourceWorld);
				for (final Entry<String, NBTBase> external : jumpBlock.externals.entrySet()) {
					final IBlockTransformer blockTransformer = WarpDriveConfig.blockTransformers.get(external.getKey());
					if (blockTransformer != null) {
						if ( shipMovementType != EnumShipMovementType.INSTANTIATE
						  && shipMovementType != EnumShipMovementType.RESTORE ) {
							blockTransformer.removeExternals(sourceWorld, jumpBlock.x, jumpBlock.y, jumpBlock.z,
							                                 jumpBlock.block, jumpBlock.blockMeta, tileEntitySource);
						}
						
						final BlockPos target = transformation.apply(jumpBlock.x, jumpBlock.y, jumpBlock.z);
						final TileEntity newTileEntity = jumpBlock.weakTileEntity == null ? null : targetWorld.getTileEntity(target);;
						blockTransformer.restoreExternals(targetWorld, target.getX(), target.getY(), target.getZ(),
						                                  jumpBlock.block, jumpBlock.blockMeta, newTileEntity, transformation, external.getValue());
					}
				}
				index++;
			}
			actualIndexInShip++;
		}
		LocalProfiler.stop();
	}
	
	protected void state_moveEntities() {
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Moving entities");
		}
		LocalProfiler.start("Jump.moveEntities");
		
		if ( shipMovementType != EnumShipMovementType.INSTANTIATE
		  && shipMovementType != EnumShipMovementType.RESTORE ) {
			for (final MovingEntity movingEntity : ship.entitiesOnShip) {
				final Entity entity = movingEntity.getEntity();
				if (entity == null) {
					continue;
				}
				
				final double oldEntityX = movingEntity.v3OriginalPosition.x;
				final double oldEntityY = movingEntity.v3OriginalPosition.y;
				final double oldEntityZ = movingEntity.v3OriginalPosition.z;
				Vec3d target = transformation.apply(oldEntityX, oldEntityY, oldEntityZ);
				final double newEntityX = target.xCoord;
				final double newEntityY = target.yCoord;
				final double newEntityZ = target.zCoord;
				
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info(String.format("Entity moving: (%.2f %.2f %.2f) -> (%.2f %.2f %.2f) entity %s",
							movingEntity.v3OriginalPosition.x, movingEntity.v3OriginalPosition.y, movingEntity.v3OriginalPosition.z,
							newEntityX, newEntityY, newEntityZ, entity.toString()));
				}
				
				transformation.rotate(entity);
				Commons.moveEntity(entity, targetWorld, new Vector3(newEntityX, newEntityY, newEntityZ));
				
				// Update bed position
				if (entity instanceof EntityPlayerMP) {
					final EntityPlayerMP player = (EntityPlayerMP) entity;
					
					BlockPos bedLocation = player.getBedLocation(player.worldObj.provider.getDimension());
					
					if ( ship.minX <= bedLocation.getX() && ship.maxX >= bedLocation.getX()
					  && ship.minY <= bedLocation.getY() && ship.maxY >= bedLocation.getY()
					  && ship.minZ <= bedLocation.getZ() && ship.maxZ >= bedLocation.getZ()) {
						bedLocation = transformation.apply(bedLocation);
						player.setSpawnChunk(bedLocation, false, targetWorld.provider.getDimension());
					}
				}
			}
		}
		
		LocalProfiler.stop();
	}
	
	protected void state_removeBlocks() {
		LocalProfiler.start("Jump.removeBlocks");
		final int blocksToMove = Math.min(blocksPerTick, ship.jumpBlocks.length - actualIndexInShip);
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Removing ship blocks " + actualIndexInShip + " to " + (actualIndexInShip + blocksToMove - 1) + " / " + (ship.jumpBlocks.length - 1));
		}
		for (int index = 0; index < blocksToMove; index++) {
			if (actualIndexInShip >= ship.jumpBlocks.length) {
				break;
			}
			final JumpBlock jumpBlock = ship.jumpBlocks[ship.jumpBlocks.length - actualIndexInShip - 1];
			if (jumpBlock == null) {
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info(this + " Removing ship part: unexpected null found at ship[" + actualIndexInShip + "]");
				}
				actualIndexInShip++;
				continue;
			}
			if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
				WarpDrive.logger.info("Removing block " + jumpBlock.block + "@" + jumpBlock.blockMeta + " at " + jumpBlock.x + " " + jumpBlock.y + " " + jumpBlock.z);
			}
			
			if (sourceWorld != null) {
				if (jumpBlock.weakTileEntity != null) {
					if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
						WarpDrive.logger.info("Removing tile entity at " + jumpBlock.x + " " + jumpBlock.y + " " + jumpBlock.z);
					}
					sourceWorld.removeTileEntity(new BlockPos(jumpBlock.x, jumpBlock.y, jumpBlock.z));
				}
				try {
					JumpBlock.setBlockNoLight(sourceWorld, new BlockPos(jumpBlock.x, jumpBlock.y, jumpBlock.z), Blocks.AIR.getDefaultState(), 2);
				} catch (final Exception exception) {
					WarpDrive.logger.info("Exception while removing " + jumpBlock.block + "@" + jumpBlock.blockMeta + " at " + jumpBlock.x + " " + jumpBlock.y + " " + jumpBlock.z);
					if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
						exception.printStackTrace();
					}
				}
			}
			
			final BlockPos target = transformation.apply(jumpBlock.x, jumpBlock.y, jumpBlock.z); 
			JumpBlock.refreshBlockStateOnClient(targetWorld, target);
			
			actualIndexInShip++;
		}
		LocalProfiler.stop();
	}
	
	protected void state_chunkReleasing() {
		LocalProfiler.start("Jump.chunkReleasing");
		
		releaseChunks();
		
		LocalProfiler.stop();
	}
	
	/**
	 * Finishing jump: cleanup, collision effects and delete self
	 **/
	@SuppressWarnings("unchecked")
	protected void state_finishing() {
		LocalProfiler.start("Jump.finishing()");
		// FIXME TileEntity duplication workaround
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Jump done in " + ((System.currentTimeMillis() - msCounter) / 1000F) + " seconds and " + ticks + " ticks");
		}
		final int countBefore = targetWorld.loadedTileEntityList.size();
		
		try {
			// @TODO MC1.10 still leaking tile entities?
			// targetWorld.loadedTileEntityList = removeDuplicates(targetWorld.loadedTileEntityList);
		} catch (final Exception exception) {
			if (WarpDriveConfig.LOGGING_JUMP) {
				WarpDrive.logger.info("TE Duplicates removing exception: " + exception.getMessage());
				exception.printStackTrace();
			}
		}
		
		
		
		doCollisionDamage(true);
		
		disable(new TextComponentString("Jump done"));
		final int countAfter = targetWorld.loadedTileEntityList.size();
		if (WarpDriveConfig.LOGGING_JUMP && countBefore != countAfter) {
			WarpDrive.logger.info(String.format("Removing TE duplicates: tileEntities in target world after jump, cleanup %d -> %d",
			                      countBefore, countAfter));
		}
		LocalProfiler.stop();
	}
	
	private String getPossibleJumpDistance() {
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Calculating possible jump distance...");
		}
		final int originalRange = Math.max(Math.abs(moveX), Math.max(Math.abs(moveY), Math.abs(moveZ)));
		int testRange = originalRange;
		int blowPoints = 0;
		collisionDetected = false;
		
		CheckMovementResult result;
		String firstAdjustmentReason = "";
		while (testRange >= 0) {
			// Is there enough space in destination point?
			result = checkMovement(testRange / (double)originalRange, false);
			if (result == null) {
				break;
			}
			if (firstAdjustmentReason.isEmpty()) {
				firstAdjustmentReason = result.reason;
			}
			
			if (result.isCollision) {
				blowPoints++;
			}
			testRange--;
		}
		final VectorI finalMovement = getMovementVector(testRange / (double)originalRange);
		
		if (originalRange != testRange && WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Jump range adjusted from " + originalRange + " to " + testRange + " after " + blowPoints + " collisions");
		}
		
		// Register explosion(s) at collision point
		if (blowPoints > WarpDriveConfig.SHIP_COLLISION_TOLERANCE_BLOCKS) {
			result = checkMovement(Math.min(1.0D, Math.max(0.0D, (testRange + 1) / (double)originalRange)), true);
			if (result != null) {
				/*
				 * Strength scaling:
				 * Wither skull = 1
				 * Creeper = 3 or 6
				 * TNT = 4
				 * TNT cart = 4 to 11.5
				 * Wither boom = 5
				 * Endercrystal = 6
				 */
				final float massCorrection = 0.5F
						+ (float) Math.sqrt(Math.min(1.0D, Math.max(0.0D, ship.shipCore.shipMass - WarpDriveConfig.SHIP_VOLUME_MAX_ON_PLANET_SURFACE)
								/ WarpDriveConfig.SHIP_VOLUME_MIN_FOR_HYPERSPACE));
				collisionDetected = true;
				collisionStrength = (4.0F + blowPoints - WarpDriveConfig.SHIP_COLLISION_TOLERANCE_BLOCKS) * massCorrection;
				collisionAtSource = result.atSource;
				collisionAtTarget = result.atTarget;
				WarpDrive.logger.info(this + " Reporting " + collisionAtTarget.size() + " collisions points after " + blowPoints
							+ " blowPoints with " + String.format("%.2f", massCorrection) + " ship mass correction => "
							+ String.format("%.2f", collisionStrength) + " explosion strength");
			} else {
				WarpDrive.logger.error("WarpDrive error: unable to compute collision points, ignoring...");
			}
		}
		
		// Update movement after computing collision points 
		moveX = finalMovement.x;
		moveY = finalMovement.y;
		moveZ = finalMovement.z;
		return firstAdjustmentReason;
	}
	
	private void doCollisionDamage(final boolean atTarget) {
		if (!collisionDetected) {
			if (WarpDriveConfig.LOGGING_JUMP) {
				WarpDrive.logger.info(this + " doCollisionDamage No collision detected...");
			}
			return;
		}
		final ArrayList<Vector3> collisionPoints = atTarget ? collisionAtTarget : collisionAtSource;
		final Vector3 min = collisionPoints.get(0).clone();
		final Vector3 max = collisionPoints.get(0).clone();
		for (final Vector3 v : collisionPoints) {
			if (min.x > v.x) {
				min.x = v.x;
			} else if (max.x < v.x) {
				max.x = v.x;
			}
			if (min.y > v.y) {
				min.y = v.y;
			} else if (max.y < v.y) {
				max.y = v.y;
			}
			if (min.z > v.z) {
				min.z = v.z;
			} else if (max.z < v.z) {
				max.z = v.z;
			}
		}
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Ship collision from " + min + " to " + max);
		}
		
		// inform players on board

		final double rx = Math.round(min.x + sourceWorld.rand.nextInt(Math.max(1, (int) (max.x - min.x))));
		final double ry = Math.round(min.y + sourceWorld.rand.nextInt(Math.max(1, (int) (max.y - min.y))));
		final double rz = Math.round(min.z + sourceWorld.rand.nextInt(Math.max(1, (int) (max.z - min.z))));
		ship.messageToAllPlayersOnShip(new TextComponentString("Ship collision detected around " + (int) rx + ", " + (int) ry + ", " + (int) rz + ". Damage report pending..."));
		// randomize if too many collision points
		final int nbExplosions = Math.min(5, collisionPoints.size());
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info("doCollisionDamage nbExplosions " + nbExplosions + "/" + collisionPoints.size());
		}
		for (int i = 0; i < nbExplosions; i++) {
			// get location
			final Vector3 current;
			if (nbExplosions < collisionPoints.size()) {
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info("doCollisionDamage random #" + i);
				}
				current = collisionPoints.get(sourceWorld.rand.nextInt(collisionPoints.size()));
			} else {
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info("doCollisionDamage get " + i);
				}
				current = collisionPoints.get(i);
			}
			
			// compute explosion strength with a jitter, at least 1 TNT
			final float strength = Math.max(4.0F, collisionStrength / nbExplosions - 2.0F + 2.0F * sourceWorld.rand.nextFloat());
			
			(atTarget ? targetWorld : sourceWorld).newExplosion(null, current.x, current.y, current.z, strength, atTarget, atTarget);
			WarpDrive.logger.info("Ship collision caused explosion at " + current.x + " " + current.y + " " + current.z + " with strength " + strength);
		}
	}
	
	private void restoreEntitiesPosition() {
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Restoring entities position");
		}
		LocalProfiler.start("Jump.restoreEntitiesPosition");
		
		if ( shipMovementType != EnumShipMovementType.INSTANTIATE
		  && shipMovementType != EnumShipMovementType.RESTORE ) {
			for (final MovingEntity movingEntity : ship.entitiesOnShip) {
				final Entity entity = movingEntity.getEntity();
				if (entity == null) {
					continue;
				}
				
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info(String.format("Entity restoring position at (%f %f %f)",
					                                    movingEntity.v3OriginalPosition.x, movingEntity.v3OriginalPosition.y, movingEntity.v3OriginalPosition.z));
				}
				
				// Update position
				if (entity instanceof EntityPlayerMP) {
					final EntityPlayerMP player = (EntityPlayerMP) entity;
					
					player.setPositionAndUpdate(movingEntity.v3OriginalPosition.x, movingEntity.v3OriginalPosition.y, movingEntity.v3OriginalPosition.z);
				} else {
					entity.setPosition(movingEntity.v3OriginalPosition.x, movingEntity.v3OriginalPosition.y, movingEntity.v3OriginalPosition.z);
				}
			}
		}
		
		LocalProfiler.stop();
	}
	
	private class CheckMovementResult {
		final ArrayList<Vector3> atSource;
		final ArrayList<Vector3> atTarget;
		boolean isCollision;
		public String reason;
		
		CheckMovementResult() {
			atSource = new ArrayList<>(1);
			atTarget = new ArrayList<>(1);
			isCollision = false;
			reason = "Unknown reason";
		}
		
		public void add(final double sx, final double sy, final double sz,
		                final double tx, final double ty, final double tz,
		                final boolean pisCollision, final String preason) {
			atSource.add(new Vector3(sx, sy, sz));
			atTarget.add(new Vector3(tx, ty, tz));
			isCollision = isCollision || pisCollision;
			reason = preason;
			if (WarpDriveConfig.LOGGING_JUMPBLOCKS) {
				WarpDrive.logger.info("CheckMovementResult " + sx + ", " + sy + ", " + sz + " -> " + tx + ", " + ty + ", " + tz + " " + isCollision + " '" + reason + "'");
			}
		}
	}
	
	private CheckMovementResult checkCollisionAndProtection(final ITransformation transformation, final boolean fullCollisionDetails, final String context) {
		final CheckMovementResult result = new CheckMovementResult();
		final VectorI offset = new VectorI((int) Math.signum(moveX), (int) Math.signum(moveY), (int) Math.signum(moveZ));
		
		int x, y, z;
		BlockPos blockPosTarget;
		final BlockPos blockPosCoreAtTarget = transformation.apply(ship.core.getX(), ship.core.getY(), ship.core.getZ());
		IBlockState blockStateSource;
		IBlockState blockStateTarget;
		for (y = ship.minY; y <= ship.maxY; y++) {
			for (x = ship.minX; x <= ship.maxX; x++) {
				for (z = ship.minZ; z <= ship.maxZ; z++) {
					blockPosTarget = transformation.apply(x, y, z);
					blockStateSource = sourceWorld.getBlockState(new BlockPos(x, y, z));
					blockStateTarget = targetWorld.getBlockState(blockPosTarget);
					if (Dictionary.BLOCKS_ANCHOR.contains(blockStateTarget.getBlock())) {
						result.add(x, y, z,
						           blockPosTarget.getX() + 0.5D - offset.x,
						           blockPosTarget.getY() + 0.5D - offset.y,
						           blockPosTarget.getZ() + 0.5D - offset.z,
						           true,
						           String.format("Impassable %s detected at destination (%d %d %d)",
							           blockStateTarget, blockPosTarget.getX(), blockPosTarget.getY(), blockPosTarget.getZ()) );
						if (!fullCollisionDetails) {
							return result;
						} else if (WarpDriveConfig.LOGGING_JUMP) {
							WarpDrive.logger.info("Anchor collision at " + context);
						}
					}
					
					if ( blockStateSource != Blocks.AIR
					  && !Dictionary.BLOCKS_EXPANDABLE.contains(blockStateSource.getBlock())
					  && blockStateTarget != Blocks.AIR
					  && !Dictionary.BLOCKS_EXPANDABLE.contains(blockStateTarget.getBlock())) {
						result.add(x, y, z,
						           blockPosTarget.getX() + 0.5D + offset.x * 0.1D,
						           blockPosTarget.getY() + 0.5D + offset.y * 0.1D,
						           blockPosTarget.getZ() + 0.5D + offset.z * 0.1D,
						           true,
						           String.format("Obstacle %s detected at (%d %d %d)",
							                     blockStateTarget, blockPosTarget.getX(), blockPosTarget.getY(), blockPosTarget.getZ()) );
						if (!fullCollisionDetails) {
							return result;
						} else if (WarpDriveConfig.LOGGING_JUMP) {
							WarpDrive.logger.info("Hard collision at " + context);
						}
					}
					
					if ( blockStateSource != Blocks.AIR
					  && CommonProxy.isBlockPlaceCanceled(null, blockPosCoreAtTarget, targetWorld, blockPosTarget, blockStateSource)) {
						result.add(x, y, z,
						           blockPosTarget.getX(),
						           blockPosTarget.getY(),
						           blockPosTarget.getZ(),
						           false,
							       String.format("Ship is entering a protected area at (%d %d %d)",
						                         blockPosTarget.getX(), blockPosTarget.getZ(), blockPosTarget.getZ()) );
						return result;
					}
				}
			}
		}
		
		if (fullCollisionDetails && result.isCollision) {
			return result;
		} else {
			return null;
		}
	}
	
	private CheckMovementResult checkMovement(final double ratio, final boolean fullCollisionDetails) {
		final CheckMovementResult result = new CheckMovementResult();
		final VectorI testMovement = getMovementVector(ratio);
		if ((moveY > 0 && ship.maxY + testMovement.y > 255) && !betweenWorlds) {
			result.add(ship.core.getX(), ship.maxY + testMovement.y,
				ship.core.getZ(), ship.core.getX() + 0.5D,
				ship.maxY + testMovement.y + 1.0D,
				ship.core.getZ() + 0.5D,
				false, "Ship core is moving too high");
			return result;
		}
		
		if ((moveY < 0 && ship.minY + testMovement.y <= 8) && !betweenWorlds) {
			result.add(ship.core.getX(), ship.minY + testMovement.y, ship.core.getZ(),
				ship.core.getX() + 0.5D,
				ship.maxY + testMovement.y,
				ship.core.getZ() + 0.5D,
				false, "Ship core is moving too low");
			return result;
		}
		
		final ITransformation testTransformation = new Transformation(ship, targetWorld, testMovement.x, testMovement.y, testMovement.z, rotationSteps);
		return checkCollisionAndProtection(testTransformation, fullCollisionDetails, "ratio " + ratio + " testMovement " + testMovement);
	}
	
	private VectorI getMovementVector(final double ratio) {
		return new VectorI((int)Math.round(moveX * ratio), (int)Math.round(moveY * ratio), (int)Math.round(moveZ * ratio));
	}
	
	private static List<TileEntity> removeDuplicates(final List<TileEntity> l) {
		@SuppressWarnings("Convert2Lambda")
		final Set<TileEntity> s = new TreeSet<>(new Comparator<TileEntity>() {
			@Override
			public int compare(final TileEntity o1, final TileEntity o2) {
				if (o1.getPos().getX() == o2.getPos().getX() && o1.getPos().getY() == o2.getPos().getY() && o1.getPos().getZ() == o2.getPos().getZ()) {
					if (WarpDriveConfig.LOGGING_JUMP) {
						WarpDrive.logger.warn(String.format("Removing TE duplicates: detected duplicate in %s @ %d %d %d: %s vs %s",
						                                    o1.getWorld().provider.getDimensionType().getName(),
						                                    o1.getPos().getX(), o1.getPos().getY(), o1.getPos().getZ(),
						                                    o1, o2));
						final NBTTagCompound nbtTagCompound1 = new NBTTagCompound();
						o1.writeToNBT(nbtTagCompound1);
						final NBTTagCompound nbtTagCompound2 = new NBTTagCompound();
						o2.writeToNBT(nbtTagCompound2);
						WarpDrive.logger.warn(String.format("First  NBT is %s", nbtTagCompound1));
						WarpDrive.logger.warn(String.format("Second NBT is %s", nbtTagCompound2));
					}
					return 0;
				} else {
					return 1;
				}
			}
		});
		s.addAll(l);
		List<TileEntity> listTileEntities = new ArrayList<>();
		listTileEntities.addAll(s);
		return listTileEntities;
	}
	
	@Override
	protected void readFromNBT(final NBTTagCompound tagCompound) {
		WarpDrive.logger.error(this + " readFromNBT()");
	}
	
	@Override
	protected NBTTagCompound writeToNBT(final NBTTagCompound tagCompound) {
		WarpDrive.logger.error(this + " writeToNBT()");
		return tagCompound;
	}
	
	@Override
	public String toString() {
		return String.format("%s/%d \'%s\' @ %s (%d %d %d) #%d",
			getClass().getSimpleName(), hashCode(),
			(ship == null || ship.shipCore == null) ? "~NULL~" : (ship.shipCore.uuid + ":" + ship.shipCore.shipName),
			sourceWorld == null ? "~NULL~" : sourceWorld.provider.getSaveFolder(),
			ship == null ? -1 : ship.core.getX(), ship == null ? -1 : ship.core.getY(), ship == null ? -1 : ship.core.getZ(),
			ticks);
	}
}
