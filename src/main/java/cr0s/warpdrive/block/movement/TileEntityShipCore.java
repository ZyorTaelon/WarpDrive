package cr0s.warpdrive.block.movement;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IStarMapRegistryTileEntity;
import cr0s.warpdrive.block.TileEntityAbstractEnergy;
import cr0s.warpdrive.config.Dictionary;
import cr0s.warpdrive.config.ShipMovementCosts;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.BlockProperties;
import cr0s.warpdrive.data.CelestialObjectManager;
import cr0s.warpdrive.data.EnumShipControllerCommand;
import cr0s.warpdrive.data.EnumShipCoreState;
import cr0s.warpdrive.data.EnumShipMovementType;
import cr0s.warpdrive.data.Jumpgate;
import cr0s.warpdrive.data.EnumStarMapEntryType;
import cr0s.warpdrive.data.SoundEvents;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.data.VectorI;
import cr0s.warpdrive.event.JumpSequencer;
import cr0s.warpdrive.render.EntityFXBoundingBox;

import javax.annotation.Nonnull;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileEntityShipCore extends TileEntityAbstractEnergy implements IStarMapRegistryTileEntity {
	
	private static final int LOG_INTERVAL_TICKS = 20 * 180;
	private static final int BOUNDING_BOX_INTERVAL_TICKS = 60;
	
	// persistent properties
	public EnumFacing facing;
	public UUID uuid = null;
	public String shipName = "default";
	private double isolationRate = 0.0D;
	private int cooldownTime_ticks = 0;
	private int warmupTime_ticks = 0;
	protected int jumpCount = 0;
	
	// computed properties
	public int maxX, maxY, maxZ;
	public int minX, minY, minZ;
	protected boolean showBoundingBox = false;
	private int countBoundingBoxUpdate = 0;
	
	private EnumShipCoreState stateCurrent = EnumShipCoreState.IDLE;
	private EnumShipControllerCommand commandCurrent = EnumShipControllerCommand.IDLE;
	
	private long timeLastShipScanDone = -1;
	public int shipMass;
	public int shipVolume;
	private EnumShipMovementType shipMovementType;
	private ShipMovementCosts shipMovementCosts;
	
	private long distanceSquared = 0;
	private boolean isCooldownReported = false;
	private boolean isMotionSicknessApplied = false;
	private boolean isSoundPlayed = false;
	private boolean isWarmupReported = false;
	protected int randomWarmupAddition_ticks = 0;
	
	private int registryUpdateTicks = 0;
	private int bootTicks = 20;
	private int logTicks = 120;
	
	private int isolationBlocksCount = 0;
	private int isolationUpdateTicks = 0;
	
	
	private WeakReference<TileEntityShipController> tileEntityShipControllerWeakReference;
	
	
	public TileEntityShipCore() {
		super();
		peripheralName = "warpdriveShipCore";
		// methodsArray = Arrays.asList("", "");
	}
	
	@SideOnly(Side.CLIENT)
	private void doShowBoundingBox() {
		countBoundingBoxUpdate--;
		if (countBoundingBoxUpdate > 0) {
			return;
		}
		countBoundingBoxUpdate = BOUNDING_BOX_INTERVAL_TICKS;
		
		final Vector3 vector3 = new Vector3(this);
		vector3.translate(0.5D);
		
		FMLClientHandler.instance().getClient().effectRenderer.addEffect(
				new EntityFXBoundingBox(worldObj, vector3,
				                        new Vector3(minX - 0.0D, minY - 0.0D, minZ - 0.0D),
				                        new Vector3(maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D),
				                        1.0F, 0.8F, 0.3F, BOUNDING_BOX_INTERVAL_TICKS + 1) );
	}
	
	@Override
	public void update() {
		super.update();
		
		if (worldObj.isRemote) {
			if (showBoundingBox) {
				doShowBoundingBox();
			}
			return;
		}
		
		TileEntityShipController tileEntityShipController = tileEntityShipControllerWeakReference == null ? null : tileEntityShipControllerWeakReference.get();
		if (tileEntityShipController != null
		    && tileEntityShipController.isInvalid()) {
			tileEntityShipControllerWeakReference = null;
			tileEntityShipController = null;
		}
		
		// Always cooldown
		if (cooldownTime_ticks > 0) {
			cooldownTime_ticks--;
			if (cooldownTime_ticks == 0 && tileEntityShipController != null) {
				tileEntityShipController.cooldownDone();
			}
		}
		
		// Enforce priority states
		if (cooldownTime_ticks > 0) {
			if (stateCurrent != EnumShipCoreState.COOLING_DOWN) {
				stateCurrent = EnumShipCoreState.COOLING_DOWN;
				isCooldownReported = false;
			}
		} else if (tileEntityShipController == null) {
			stateCurrent = EnumShipCoreState.DISCONNECTED;
		} else {
			if (stateCurrent == EnumShipCoreState.DISCONNECTED) {
				stateCurrent = EnumShipCoreState.IDLE;
			}
			if (!tileEntityShipController.isEnabled) {
				stateCurrent = EnumShipCoreState.IDLE;
			}
			if (timeLastShipScanDone <= 0L) {
				stateCurrent = EnumShipCoreState.SCANNING;
			}
		}
		
		// accelerate update ticks during boot
		if (bootTicks > 0) {
			bootTicks--;
			if (tileEntityShipController == null) {
				registryUpdateTicks = 1;
			}
		}
		
		// periodically update starmap registry
		registryUpdateTicks--;
		if (registryUpdateTicks <= 0) {
			registryUpdateTicks = 20 * WarpDriveConfig.STARMAP_REGISTRY_UPDATE_INTERVAL_SECONDS;
			if (uuid == null || (uuid.getMostSignificantBits() == 0 && uuid.getLeastSignificantBits() == 0)) {
				uuid = UUID.randomUUID();
			}
			// recover registration, shouldn't be needed, in theory...
			WarpDrive.starMap.updateInRegistry(this);
			
			final TileEntityShipController tileEntityShipControllerNew = findControllerBlock();
			if (tileEntityShipControllerNew == null) {
				tileEntityShipControllerWeakReference = null;
			}
			if (tileEntityShipControllerNew != tileEntityShipController) {
				tileEntityShipController = tileEntityShipControllerNew;
				tileEntityShipControllerWeakReference = new WeakReference<>(tileEntityShipController);
			}
		}
		
		// periodically log the ship state
		logTicks--;
		if (logTicks <= 0) {
			logTicks = LOG_INTERVAL_TICKS;
			if (WarpDriveConfig.LOGGING_JUMP) {
				WarpDrive.logger.info(String.format("%s %s, jumpFlag %s from %s, warmup %d, cooldown %d",
				                                    this,
				                                    stateCurrent,
				                                    tileEntityShipController == null ? "NA" : tileEntityShipController.isEnabled,
				                                    tileEntityShipController,
				                                    warmupTime_ticks,
				                                    cooldownTime_ticks));
			}
		}
		
		// periodically check isolation blocks
		isolationUpdateTicks--;
		if (isolationUpdateTicks <= 0) {
			isolationUpdateTicks = WarpDriveConfig.SHIP_CORE_ISOLATION_UPDATE_INTERVAL_SECONDS * 20;
			updateIsolationState();
		}
		
		// Refresh rendering
		final boolean isActive = stateCurrent != EnumShipCoreState.DISCONNECTED
		                      && tileEntityShipController != null
		                      && commandCurrent != EnumShipControllerCommand.OFFLINE;
		updateBlockState(null, BlockProperties.ACTIVE, isActive);
		
		if (tileEntityShipController == null) {
			return;
		}
		
		final StringBuilder reason = new StringBuilder();
				
		final EnumShipControllerCommand commandController = tileEntityShipController.getCommand();
		
		switch (stateCurrent) {
		case DISCONNECTED:
			// empty state, will move directly to IDLE upon next tick
			break;
			
		case IDLE:
			if ( tileEntityShipController.isEnabled
			  && commandController != EnumShipControllerCommand.IDLE
			  && commandController != EnumShipControllerCommand.MAINTENANCE ) {
				commandCurrent = commandController;
				stateCurrent = EnumShipCoreState.ONLINE;
			}
			break;
			
		case COOLING_DOWN:
			// Report cooldown time when command is requested
			if ( tileEntityShipController.isEnabled
			  && commandController != EnumShipControllerCommand.IDLE
			  && commandController != EnumShipControllerCommand.MAINTENANCE ) {
				if (cooldownTime_ticks % 20 == 0) {
					final int seconds = cooldownTime_ticks / 20;
					if (!isCooldownReported || (seconds < 5) || ((seconds < 30) && (seconds % 5 == 0)) || (seconds % 10 == 0)) {
						isCooldownReported = true;
						messageToAllPlayersOnShip(String.format("Warp core is cooling down... %ds to go...", seconds));
					}
				}
			}
			if (cooldownTime_ticks <= 0) {
				stateCurrent = EnumShipCoreState.IDLE;
				isCooldownReported = false;
			}
			break;
		
		case SCANNING:
			stateCurrent = EnumShipCoreState.IDLE;
			timeLastShipScanDone = worldObj.getTotalWorldTime();
			if (!validateShipSpatialParameters(tileEntityShipController, reason)) {// @TODO progressive scan
				if (tileEntityShipController.isEnabled) {
					tileEntityShipController.commandDone(false, reason.toString());
				}
			}
			break;
		
		case ONLINE:
			// (disabling will switch back to IDLE and clear variables)
			
			switch (commandCurrent) {
			case SUMMON:
				final String targetName = tileEntityShipController.getTargetName();
				if ( targetName == null
				  || targetName.isEmpty()) {
					summonPlayers(tileEntityShipController);
				} else {
					summonSinglePlayer(tileEntityShipController, targetName);
				}
				tileEntityShipController.commandDone(true, "Teleportation done");
				break;
			
			case MANUAL:
			case HYPERDRIVE:
			case GATE:
				// initiating jump
				
				// compute distance
				distanceSquared = tileEntityShipController.getMovement().getMagnitudeSquared();
				// rescan ship mass/volume if it's too old
				if (timeLastShipScanDone + WarpDriveConfig.SHIP_VOLUME_SCAN_AGE_TOLERANCE_SECONDS < worldObj.getTotalWorldTime()) {
					stateCurrent = EnumShipCoreState.SCANNING;
					break;
				}
				
				messageToAllPlayersOnShip("Running pre-jump checklist...");
				
				// update ship spatial parameters
				if (!validateShipSpatialParameters(tileEntityShipController, reason)) {
					tileEntityShipController.commandDone(false, reason.toString());
					return;
				}
				
				// update movement parameters
				if (!validateShipMovementParameters(tileEntityShipController, reason)) {
					tileEntityShipController.commandDone(false, reason.toString());
					return;
				}
				
				// compute random ticks to warmup so it's harder to 'dup' items
				randomWarmupAddition_ticks = worldObj.rand.nextInt(WarpDriveConfig.SHIP_WARMUP_RANDOM_TICKS);
				
				stateCurrent = EnumShipCoreState.WARMING_UP;
				warmupTime_ticks = shipMovementCosts.warmup_seconds * 20 + randomWarmupAddition_ticks;
				isMotionSicknessApplied = false;
				isSoundPlayed = false;
				isWarmupReported = false;
				break;
			
			default:
				WarpDrive.logger.error(String.format("%s Invalid controller command %s for current state %s", this, commandController, stateCurrent));
				break;
			}
			break;
			
		case WARMING_UP:
			// Apply motion sickness as applicable
			if (shipMovementCosts.sickness_seconds > 0) {
				final int motionSicknessThreshold_ticks = shipMovementCosts.sickness_seconds * 20 - randomWarmupAddition_ticks / 4; 
				if ( !isMotionSicknessApplied
				   && motionSicknessThreshold_ticks >= warmupTime_ticks ) {
					if (WarpDriveConfig.LOGGING_JUMP) {
						WarpDrive.logger.info(this + " Giving warp sickness to on-board players");
					}
					makePlayersOnShipDrunk(shipMovementCosts.sickness_seconds * 20 + WarpDriveConfig.SHIP_WARMUP_RANDOM_TICKS);
					isMotionSicknessApplied = true;
				}
			}
			
			// Select best sound file and adjust offset
			final int soundThreshold;
			final SoundEvent soundEvent;
			if (shipMovementCosts.warmup_seconds < 10) {
				soundThreshold =  4 * 20 - randomWarmupAddition_ticks;
				soundEvent = SoundEvents.WARP_4_SECONDS;
			} else if (shipMovementCosts.warmup_seconds > 29) {
				soundThreshold = 30 * 20 - randomWarmupAddition_ticks;
				soundEvent = SoundEvents.WARP_30_SECONDS;
			} else {
				soundThreshold = 10 * 20 - randomWarmupAddition_ticks;
				soundEvent = SoundEvents.WARP_10_SECONDS;
			}
			
			if ( !isSoundPlayed
			  && soundThreshold >= warmupTime_ticks ) {
				if (WarpDriveConfig.LOGGING_JUMP) {
					WarpDrive.logger.info(this + " Playing sound effect '" + soundEvent + "' soundThreshold " + soundThreshold + " warmupTime " + warmupTime_ticks);
				}
				worldObj.playSound(null, pos, soundEvent, SoundCategory.BLOCKS, 4.0F, 1.0F);
				isSoundPlayed = true;
			}
			
			if (warmupTime_ticks % 20 == 0) {
				final int seconds = warmupTime_ticks / 20;
				if ( !isWarmupReported
				  || (seconds >= 60 && (seconds % 15 == 0))
				  || (seconds <  60 && seconds > 30 && (seconds % 10 == 0)) ) {
					isWarmupReported = true;
					messageToAllPlayersOnShip(String.format("Warp core is warming up... %ds to go...", seconds));
				}
			}
			
			// Awaiting warm-up time
			if (warmupTime_ticks > 0) {
				warmupTime_ticks--;
				break;
			}
			
			warmupTime_ticks = 0;
			isMotionSicknessApplied = false;
			isSoundPlayed = false;
			isWarmupReported = false;
			
			if (!validateShipSpatialParameters(tileEntityShipController, reason)) {
				tileEntityShipController.commandDone(false, reason.toString());
				return;
			}
			
			if (WarpDrive.starMap.isWarpCoreIntersectsWithOthers(this)) {
				tileEntityShipController.commandDone(false, "Warp field intersects with other ship's field. Disable the other core to jump.");
				return;
			}
			
			if (WarpDrive.cloaks.isCloaked(worldObj.provider.getDimension(), pos)) {
				tileEntityShipController.commandDone(false, "Core is inside a cloaking field. Aborting. Disable cloaking field to jump!");
				return;
			}
			
			doJump(tileEntityShipController);
			cooldownTime_ticks = Math.max(1, shipMovementCosts.cooldown_seconds * 20);
			tileEntityShipController.commandDone(true, "Ok");
			jumpCount++;
			stateCurrent = EnumShipCoreState.COOLING_DOWN;
			isCooldownReported = false;
			break;
			
		default:
			break;
		}
	}
	
	public boolean isOffline() {
		if (tileEntityShipControllerWeakReference == null) {
			return false;
		}
		final TileEntityShipController tileEntityShipController = tileEntityShipControllerWeakReference.get();
		return tileEntityShipController != null
		    && !tileEntityShipController.isInvalid()
		    && tileEntityShipController.getCommand() == EnumShipControllerCommand.OFFLINE;
	}
	
	protected boolean isAttached(final TileEntityShipController tileEntityShipControllerExpected) {
		return tileEntityShipControllerWeakReference != null
		    && tileEntityShipControllerExpected == tileEntityShipControllerWeakReference.get();
	}
	
	@Deprecated
	private void messageToAllPlayersOnShip(final String string) {
		messageToAllPlayersOnShip(new TextComponentString(string));
	}
	public void messageToAllPlayersOnShip(final ITextComponent textComponent) {
		final AxisAlignedBB axisalignedbb = new AxisAlignedBB(minX, minY, minZ, maxX + 0.99D, maxY + 0.99D, maxZ + 0.99D);
		final List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(null, axisalignedbb);
		final ITextComponent messageFormatted = new TextComponentString("[" + (!shipName.isEmpty() ? shipName : "ShipCore") + "] ")
		                                             .appendSibling(textComponent);
		
		WarpDrive.logger.info(this + " messageToAllPlayersOnShip: " + textComponent.getFormattedText());
		for (final Entity entity : list) {
			if (!(entity instanceof EntityPlayer)) {
				continue;
			}
			
			Commons.addChatMessage(entity, messageFormatted);
		}
	}
	
	public String getAllPlayersOnShip() {
		final AxisAlignedBB axisalignedbb = new AxisAlignedBB(minX, minY, minZ, maxX + 0.99D, maxY + 0.99D, maxZ + 0.99D);
		final List list = worldObj.getEntitiesWithinAABBExcludingEntity(null, axisalignedbb);
		final StringBuilder stringBuilderResult = new StringBuilder();
		
		boolean isFirst = true;
		for (final Object object : list) {
			if (!(object instanceof EntityPlayer)) {
				continue;
			}
			if (isFirst) {
				isFirst = false;
			} else {
				stringBuilderResult.append(", ");
			}
			stringBuilderResult.append(((EntityPlayer) object).getName());
		}
		return stringBuilderResult.toString();
	}
	
	public boolean isBooting() {
		if (bootTicks > 0) {
			return true;
		}
		
		if (tileEntityShipControllerWeakReference == null) {// not attached
			return false;
		}
		
		final TileEntityShipController tileEntityShipController = tileEntityShipControllerWeakReference.get();
		if ( tileEntityShipController == null
		  || tileEntityShipController.isInvalid() ) {// we're desync
			// force a refresh
			registryUpdateTicks = 0;
			return true;
		}
		
		return false;
	}
	
	public String getFirstOnlineCrew() {
		if (tileEntityShipControllerWeakReference == null) {// not attached
			return null;
		}
		
		final TileEntityShipController tileEntityShipController = tileEntityShipControllerWeakReference.get();
		if ( tileEntityShipController == null
		  || tileEntityShipController.isInvalid() ) {// we're desync
			// force a refresh
			registryUpdateTicks = 0;
			return "-busy-";
		}
		
		if (tileEntityShipController.players == null || tileEntityShipController.players.isEmpty()) {// no crew defined
			return null;
		}
		
		for (final String namePlayer : tileEntityShipController.players) {
			final EntityPlayer entityPlayer = Commons.getOnlinePlayerByName(namePlayer);
			if (entityPlayer != null) {// crew member is online
				return namePlayer;
			}
		}
		
		// all cleared
		return null;
	}
	
	private void updateIsolationState() {
		// Search block in cube around core
		final int xMin = pos.getX() - WarpDriveConfig.RADAR_MAX_ISOLATION_RANGE;
		final int xMax = pos.getX() + WarpDriveConfig.RADAR_MAX_ISOLATION_RANGE;
		
		final int zMin = pos.getZ() - WarpDriveConfig.RADAR_MAX_ISOLATION_RANGE;
		final int zMax = pos.getZ() + WarpDriveConfig.RADAR_MAX_ISOLATION_RANGE;
		
		// scan 1 block higher to encourage putting isolation block on both
		// ground and ceiling
		final int yMin = Math.max(  0, pos.getY() - WarpDriveConfig.RADAR_MAX_ISOLATION_RANGE + 1);
		final int yMax = Math.min(255, pos.getY() + WarpDriveConfig.RADAR_MAX_ISOLATION_RANGE + 1);
		
		int newCount = 0;
		
		// Search for warp isolation blocks
		for (int y = yMin; y <= yMax; y++) {
			for (int x = xMin; x <= xMax; x++) {
				for (int z = zMin; z <= zMax; z++) {
					if (worldObj.getBlockState(new BlockPos(x, y, z)).getBlock() == WarpDrive.blockWarpIsolation) {
						newCount++;
					}
				}
			}
		}
		isolationBlocksCount = newCount;
		final double legacy_isolationRate = isolationRate;
		if (isolationBlocksCount >= WarpDriveConfig.RADAR_MIN_ISOLATION_BLOCKS) {
			isolationRate = Math.min(1.0, WarpDriveConfig.RADAR_MIN_ISOLATION_EFFECT
					+ (isolationBlocksCount - WarpDriveConfig.RADAR_MIN_ISOLATION_BLOCKS) // bonus blocks
					* (WarpDriveConfig.RADAR_MAX_ISOLATION_EFFECT - WarpDriveConfig.RADAR_MIN_ISOLATION_EFFECT)
					/ (WarpDriveConfig.RADAR_MAX_ISOLATION_BLOCKS - WarpDriveConfig.RADAR_MIN_ISOLATION_BLOCKS));
		} else {
			isolationRate = 0.0D;
		}
		if (WarpDriveConfig.LOGGING_RADAR && (WarpDrive.isDev || legacy_isolationRate != isolationRate)) {
			WarpDrive.logger.info(String.format("%s Isolation updated to %d (%.1f%%)",
			                                    this, isolationBlocksCount , isolationRate * 100.0));
		}
	}
	
	private void makePlayersOnShipDrunk(final int tickDuration) {
		final AxisAlignedBB axisalignedbb = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
		final List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(null, axisalignedbb);
		
		for (final Entity entity : list) {
			if (!(entity instanceof EntityPlayer)) {
				continue;
			}
			
			// Set "drunk" effect
			((EntityPlayer) entity).addPotionEffect(
					new PotionEffect(MobEffects.NAUSEA, tickDuration, 0, true, true));
		}
	}
	
	private void summonPlayers(final TileEntityShipController tileEntityShipController) {
		final AxisAlignedBB aabb = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
		
		for (int i = 0; i < tileEntityShipController.players.size(); i++) {
			final String playerName = tileEntityShipController.players.get(i);
			final EntityPlayerMP entityPlayerMP = Commons.getOnlinePlayerByName(playerName);

			if ( entityPlayerMP != null
			  && isOutsideBB(aabb, MathHelper.floor_double(entityPlayerMP.posX), MathHelper.floor_double(entityPlayerMP.posY), MathHelper.floor_double(entityPlayerMP.posZ)) ) {
				summonPlayer(entityPlayerMP);
			}
		}
	}
	
	private void summonSinglePlayer(final TileEntityShipController tileEntityShipController, final String nickname) {
		final AxisAlignedBB aabb = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
		final MinecraftServer server = worldObj.getMinecraftServer();
		assert(server != null);
		
		for (int i = 0; i < tileEntityShipController.players.size(); i++) {
			final String playerName = tileEntityShipController.players.get(i);
			final EntityPlayerMP entityPlayerMP = Commons.getOnlinePlayerByName(playerName);
			
			if ( entityPlayerMP != null && playerName.equals(nickname)
			  && isOutsideBB(aabb, MathHelper.floor_double(entityPlayerMP.posX), MathHelper.floor_double(entityPlayerMP.posY), MathHelper.floor_double(entityPlayerMP.posZ)) ) {
				summonPlayer(entityPlayerMP);
				return;
			}
		}
	}
	
	public boolean summonOwnerOnDeploy(final EntityPlayerMP entityPlayerMP) {
		if (entityPlayerMP == null) {
			WarpDrive.logger.warn(this + " No player given to summonOwnerOnDeploy()");
			return false;
		}
		final StringBuilder reason = new StringBuilder();
		final TileEntityShipController tileEntityShipController = findControllerBlock();
		if (!validateShipSpatialParameters(tileEntityShipController, reason)) {
			Commons.addChatMessage(entityPlayerMP, new TextComponentString("[" + (!shipName.isEmpty() ? shipName : "ShipCore") + "] §c" + reason.toString()));
			return false;
		}
		
		if (tileEntityShipController != null) {
			tileEntityShipController.players.clear();
			tileEntityShipController.players.add(entityPlayerMP.getName());
		} else {
			WarpDrive.logger.warn(this + " Failed to find controller block");
			return false;
		}
		
		final AxisAlignedBB aabb = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
		if (isOutsideBB(aabb, MathHelper.floor_double(entityPlayerMP.posX), MathHelper.floor_double(entityPlayerMP.posY), MathHelper.floor_double(entityPlayerMP.posZ))) {
			summonPlayer(entityPlayerMP);
		}
		return true;
	}
	
	private static final VectorI[] SUMMON_OFFSETS = { new VectorI(2, 0, 0), new VectorI(-1, 0, 0),
		new VectorI(2, 0, 1), new VectorI(2, 0, -1), new VectorI(-1, 0, 1), new VectorI(-1, 0, -1),
		new VectorI(1, 0, 1), new VectorI(1, 0, -1), new VectorI( 0, 0, 1), new VectorI( 0, 0, -1) };
	private void summonPlayer(final EntityPlayerMP entityPlayer) {
		// validate distance
		double distance = Math.sqrt(new VectorI(entityPlayer).distance2To(this));
		if (entityPlayer.worldObj != this.worldObj) {
			distance += 256;
			if (!WarpDriveConfig.SHIP_SUMMON_ACROSS_DIMENSIONS) {
				messageToAllPlayersOnShip("§c" + String.format("%1$s is in a different dimension, too far away to be summoned", entityPlayer.getDisplayName()));
				return;
			}
		}
		if (WarpDriveConfig.SHIP_SUMMON_MAX_RANGE >= 0 && distance > WarpDriveConfig.SHIP_SUMMON_MAX_RANGE) {
			messageToAllPlayersOnShip("§c" + String.format("%1$s is too far away to be summoned (max. is %2$d m)", entityPlayer.getDisplayName(), WarpDriveConfig.SHIP_SUMMON_MAX_RANGE));
			Commons.addChatMessage(entityPlayer, new TextComponentString("§c" + String.format("You are to far away to be summoned aboard '%1$s' (max. is %2$d m)", shipName, WarpDriveConfig.SHIP_SUMMON_MAX_RANGE)));
			return;
		}
		
		// find a free spot
		final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(pos);
		for (final VectorI vOffset : SUMMON_OFFSETS) {
			mutableBlockPos.setPos(
				pos.getX() + facing.getFrontOffsetX() * vOffset.x + facing.getFrontOffsetZ() * vOffset.z,
			    pos.getY(),
				pos.getZ() + facing.getFrontOffsetZ() * vOffset.x + facing.getFrontOffsetX() * vOffset.z);
			if (worldObj.isAirBlock(mutableBlockPos)) {
				if (worldObj.isAirBlock(mutableBlockPos.add(0, 1, 0))) {
					summonPlayer(entityPlayer, mutableBlockPos);
					return;
				}
				mutableBlockPos.move(EnumFacing.DOWN);
				if (worldObj.isAirBlock(mutableBlockPos)) {
					summonPlayer(entityPlayer, mutableBlockPos);
					return;
				}
			} else if ( worldObj.isAirBlock(mutableBlockPos.add(0, -1, 0))
			         && worldObj.isAirBlock(mutableBlockPos.add(0, -2, 0))
			         && !worldObj.isAirBlock(mutableBlockPos.add(0, -3, 0)) ) {
				summonPlayer(entityPlayer, mutableBlockPos.add(0, -2, 0));
				return;
			} else if ( worldObj.isAirBlock(mutableBlockPos.add(0, 1, 0))
			         && worldObj.isAirBlock(mutableBlockPos.add(0, 2, 0))
			         && !worldObj.isAirBlock(mutableBlockPos) ) {
				summonPlayer(entityPlayer, mutableBlockPos.add(0, 1, 0));
				return;
			}
		}
		final String message = "§c" + String.format("No safe spot found to summon player %1$s", entityPlayer.getDisplayName());
		messageToAllPlayersOnShip(message);
		Commons.addChatMessage(entityPlayer, new TextComponentString(message));
	}
	
	private void summonPlayer(final EntityPlayerMP player, final BlockPos blockPos) {
		if (energy_consume(WarpDriveConfig.SHIP_TELEPORT_ENERGY_PER_ENTITY, false)) {
			Commons.moveEntity(player, worldObj, new Vector3(blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D));
		}
	}
	
	public boolean validateShipSpatialParameters(final StringBuilder reason) {
		final TileEntityShipController tileEntityShipController = findControllerBlock();
		return validateShipSpatialParameters(tileEntityShipController, reason);
	}
	
	protected boolean validateShipSpatialParameters(final TileEntityShipController tileEntityShipController, final StringBuilder reason) {
		if (tileEntityShipController == null) {
			reason.append("TileEntityShipCore.validateShipSpatialParameters: no controller detected!");
			return false;
		}
		final int shipFront = tileEntityShipController.getFront();
		final int shipRight = tileEntityShipController.getRight();
		final int shipUp = tileEntityShipController.getUp();
		final int shipBack = tileEntityShipController.getBack();
		final int shipLeft = tileEntityShipController.getLeft();
		final int shipDown = tileEntityShipController.getDown();
		
		int x1 = 0, x2 = 0, z1 = 0, z2 = 0;
		
		if (facing.getFrontOffsetX() == 1) {
			x1 = pos.getX() - shipBack;
			x2 = pos.getX() + shipFront;
			z1 = pos.getZ() - shipLeft;
			z2 = pos.getZ() + shipRight;
		} else if (facing.getFrontOffsetX() == -1) {
			x1 = pos.getX() - shipFront;
			x2 = pos.getX() + shipBack;
			z1 = pos.getZ() - shipRight;
			z2 = pos.getZ() + shipLeft;
		} else if (facing.getFrontOffsetZ() == 1) {
			z1 = pos.getX() - shipBack;
			z2 = pos.getX() + shipFront;
			x1 = pos.getZ() - shipRight;
			x2 = pos.getZ() + shipLeft;
		} else if (facing.getFrontOffsetZ() == -1) {
			z1 = pos.getX() - shipFront;
			z2 = pos.getX() + shipBack;
			x1 = pos.getZ() - shipLeft;
			x2 = pos.getZ() + shipRight;
		}
		
		if (x1 < x2) {
			minX = x1;
			maxX = x2;
		} else {
			minX = x2;
			maxX = x1;
		}
		
		if (z1 < z2) {
			minZ = z1;
			maxZ = z2;
		} else {
			minZ = z2;
			maxZ = z1;
		}
		
		minY = pos.getY() - shipDown;
		maxY = pos.getY() + shipUp;
		
		// update dimensions to client
		markDirty();
		
		// Ship side is too big
		if ( (shipBack + shipFront) > WarpDriveConfig.SHIP_MAX_SIDE_SIZE
		  || (shipLeft + shipRight) > WarpDriveConfig.SHIP_MAX_SIDE_SIZE
		  || (shipDown + shipUp) > WarpDriveConfig.SHIP_MAX_SIDE_SIZE) {
			reason.append(String.format("Ship is too big (max is %d per side)",
				WarpDriveConfig.SHIP_MAX_SIDE_SIZE));
			return false;
		}
		
		boolean isUnlimited = false;
		final AxisAlignedBB axisalignedbb = new AxisAlignedBB(minX, minY, minZ, maxX + 0.99D, maxY + 0.99D, maxZ + 0.99D);
		final List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(null, axisalignedbb);
		for (final Entity entity : list) {
			if (!(entity instanceof EntityPlayer)) {
				continue;
			}
			
			final String playerName = entity.getName();
			for (final String nameUnlimited : WarpDriveConfig.SHIP_VOLUME_UNLIMITED_PLAYERNAMES) {
				isUnlimited = isUnlimited || nameUnlimited.equals(playerName);
			}
		}
		
		updateShipMassAndVolume();
		if ( !isUnlimited
		  && shipMass > WarpDriveConfig.SHIP_VOLUME_MAX_ON_PLANET_SURFACE
		  && CelestialObjectManager.isPlanet(worldObj, pos.getX(), pos.getZ()) ) {
			reason.append(String.format("Ship is too big for a planet (max is %d blocks)",
				WarpDriveConfig.SHIP_VOLUME_MAX_ON_PLANET_SURFACE));
			return false;
		}
		
		return true;
	}
	
	private boolean validateShipMovementParameters(final TileEntityShipController tileEntityShipController, final StringBuilder reason) {
		shipMovementType = EnumShipMovementType.compute(worldObj, pos.getX(), minY, maxY, pos.getZ(), commandCurrent, tileEntityShipController.getMovement().y, reason);
		if (shipMovementType == null) {
			return false;
		}
		
		// compute movement costs
		shipMovementCosts = new ShipMovementCosts(worldObj, pos, shipMass, shipMovementType, (int) Math.ceil(Math.sqrt(distanceSquared)));
		return true;
	}
	
	// Computer interface are running independently of updateTicks, hence doing local computations getMaxJumpDistance() and getEnergyRequired()
	protected int getMaxJumpDistance(final TileEntityShipController tileEntityShipController, final EnumShipControllerCommand command, final StringBuilder reason) {
		final EnumShipMovementType shipMovementType = EnumShipMovementType.compute(worldObj, pos.getX(), minY, maxY, pos.getZ(), command, tileEntityShipController.getMovement().y, reason);
		if (shipMovementType == null) {
			tileEntityShipController.commandDone(false, reason.toString());
			return -1;
		}
		
		// compute movement costs
		final ShipMovementCosts shipMovementCosts = new ShipMovementCosts(worldObj, pos, shipMass, shipMovementType, (int) Math.ceil(Math.sqrt(distanceSquared)));
		return shipMovementCosts.maximumDistance_blocks;
	}
	
	protected int getEnergyRequired(final TileEntityShipController tileEntityShipController, final EnumShipControllerCommand command, final StringBuilder reason) {
		final EnumShipMovementType shipMovementType = EnumShipMovementType.compute(worldObj, pos.getX(), minY, maxY, pos.getZ(), command, tileEntityShipController.getMovement().y, reason);
		if (shipMovementType == null) {
			tileEntityShipController.commandDone(false, reason.toString());
			return -1;
		}
		
		// compute movement costs
		final ShipMovementCosts shipMovementCosts = new ShipMovementCosts(worldObj, pos, shipMass, shipMovementType, (int) Math.ceil(Math.sqrt(distanceSquared)));
		return shipMovementCosts.energyRequired;
	}
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean isShipInJumpgate(final Jumpgate jumpgate, final StringBuilder reason) {
		final AxisAlignedBB aabb = jumpgate.getGateAABB();
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Jumpgate " + jumpgate.name + " AABB is " + aabb);
		}
		int countBlocksInside = 0;
		int countBlocksTotal = 0;
		
		if ( aabb.isVecInside(new Vec3d(minX, minY, minZ))
		  && aabb.isVecInside(new Vec3d(maxX, maxY, maxZ)) ) {
			// fully inside
			return true;
		}
		
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					final IBlockState blockState = worldObj.getBlockState(new BlockPos(x, y, z));
					
					// Skipping vanilla air & ignored blocks
					if (blockState.getBlock() == Blocks.AIR || Dictionary.BLOCKS_LEFTBEHIND.contains(blockState.getBlock())) {
						continue;
					}
					if (Dictionary.BLOCKS_NOMASS.contains(blockState.getBlock())) {
						continue;
					}
					
					if (aabb.minX <= x && aabb.maxX >= x && aabb.minY <= y && aabb.maxY >= y && aabb.minZ <= z && aabb.maxZ >= z) {
						countBlocksInside++;
					}
					countBlocksTotal++;
				}
			}
		}
		
		float percent = 0F;
		if (shipMass != 0) {
			percent = Math.round((((countBlocksInside * 1.0F) / shipMass) * 100.0F) * 10.0F) / 10.0F;
		}
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			if (shipMass != countBlocksTotal) {
				WarpDrive.logger.info(this + " Ship mass has changed from " + shipMass + " to " + countBlocksTotal + " blocks");
			}
			WarpDrive.logger.info(this + "Ship has " + countBlocksInside + " / " + shipMass + " blocks (" + percent + "%) in jumpgate '" + jumpgate.name + "'");
		}
		
		// At least 80% of ship must be inside jumpgate
		if (percent > 80F) {
			return true;
		} else if (percent <= 0.001) {
			reason.append(String.format("Ship is not inside a jumpgate. Jump rejected. Nearest jumpgate is %s",
				jumpgate.toNiceString()));
			return false;
		} else {
			reason.append(String.format("Ship is only %.1f%% inside a jumpgate. Sorry, we'll loose too much crew as is, jump rejected.",
				percent));
			return false;
		}
	}
	
	private boolean isFreePlaceForShip(final TileEntityShipController tileEntityShipController, final int destX, final int destY, final int destZ) {
		int newX, newZ;
		
		if ( tileEntityShipController == null
		  || destY + tileEntityShipController.getUp() > 255
		  || destY - tileEntityShipController.getDown() < 5 ) {
			return false;
		}
		
		final int moveX = destX - pos.getX();
		final int moveY = destY - pos.getY();
		final int moveZ = destZ - pos.getZ();
		
		for (int x = minX; x <= maxX; x++) {
			newX = moveX + x;
			for (int z = minZ; z <= maxZ; z++) {
				newZ = moveZ + z;
				for (int y = minY; y <= maxY; y++) {
					if (moveY + y < 0 || moveY + y > 255) {
						return false;
					}
					
					final Block blockSource = worldObj.getBlockState(new BlockPos(x, y, z)).getBlock();
					final Block blockTarget = worldObj.getBlockState(new BlockPos(newX, moveY + y, newZ)).getBlock();
					
					// not vanilla air nor ignored blocks at source
					// not vanilla air nor expandable blocks are target location
					if ( blockSource != Blocks.AIR
					  && !Dictionary.BLOCKS_EXPANDABLE.contains(blockSource)
					  && blockTarget != Blocks.AIR
					  && !Dictionary.BLOCKS_EXPANDABLE.contains(blockTarget)) {
						return false;
					}
				}
			}
		}
		
		return true;
	}
	
	private void doGateJump(final TileEntityShipController tileEntityShipController) {
		// Search nearest jump-gate
		final String targetName = tileEntityShipController.getTargetName();
		final Jumpgate targetGate = WarpDrive.jumpgates.findGateByName(targetName);
		
		if (targetGate == null) {
			tileEntityShipController.commandDone(false, String.format("Destination jumpgate '%s' is unknown. Check jumpgate name.", targetName));
			return;
		}
		
		// Now make jump to a beacon
		final int gateX = targetGate.xCoord;
		final int gateY = targetGate.yCoord;
		final int gateZ = targetGate.zCoord;
		int destX = gateX;
		int destY = gateY;
		int destZ = gateZ;
		final Jumpgate nearestGate = WarpDrive.jumpgates.findNearestGate(pos);
		
		final StringBuilder reason = new StringBuilder();
		if (!isShipInJumpgate(nearestGate, reason)) {
			tileEntityShipController.commandDone(false, reason.toString());
			return;
		}
		
		// If gate is blocked by obstacle
		if (!isFreePlaceForShip(tileEntityShipController, gateX, gateY, gateZ)) {
			// Randomize destination coordinates and check for collision with obstacles around jumpgate
			// Try to find good place for ship
			int numTries = 10; // num tries to check for collision
			boolean placeFound = false;
			
			for (; numTries > 0; numTries--) {
				// randomize destination coordinates around jumpgate
				destX = gateX + ((worldObj.rand.nextBoolean()) ? -1 : 1) * (20 + worldObj.rand.nextInt(100));
				destZ = gateZ + ((worldObj.rand.nextBoolean()) ? -1 : 1) * (20 + worldObj.rand.nextInt(100));
				destY = gateY + ((worldObj.rand.nextBoolean()) ? -1 : 1) * (20 + worldObj.rand.nextInt(50));
				
				// check for collision
				if (isFreePlaceForShip(tileEntityShipController, destX, destY, destZ)) {
					placeFound = true;
					break;
				}
			}
			
			if (!placeFound) {
				tileEntityShipController.commandDone(false, "Destination gate is blocked by obstacles. Aborting...");
				return;
			}
			
			WarpDrive.logger.info("[GATE] Place found over " + (10 - numTries) + " tries.");
		}
		
		// Consume energy
		if (energy_consume(shipMovementCosts.energyRequired, false)) {
			WarpDrive.logger.info(this + " Moving ship to a place around gate '" + targetGate.name + "' (" + destX + "; " + destY + "; " + destZ + ")");
			final JumpSequencer jump = new JumpSequencer(this, EnumShipMovementType.GATE_ACTIVATING, targetName, 0, 0, 0, (byte) 0, destX, destY, destZ);
			jump.enable();
		} else {
			messageToAllPlayersOnShip("Insufficient energy level");
		}
	}
	
	private void doJump(final TileEntityShipController tileEntityShipController) {
		
		final int requiredEnergy = shipMovementCosts.energyRequired;
		
		if (!energy_consume(requiredEnergy, true)) {
			tileEntityShipController.commandDone(false, String.format("Insufficient energy to jump! Core is currently charged with %d EU while jump requires %d EU",
			                                            energy_getEnergyStored(), requiredEnergy));
			return;
		}
		
		final String shipInfo = "" + shipVolume + " blocks inside (" + minX + ", " + minY + ", " + minZ + ") to (" + maxX + ", " + maxY + ", " + maxZ + ") with an actual mass of " + shipMass + " blocks";
		switch (commandCurrent) {
		case GATE:
			WarpDrive.logger.info(this + " Performing gate jump of " + shipInfo);
			doGateJump(tileEntityShipController);
			return;
			
		case HYPERDRIVE:
			WarpDrive.logger.info(this + " Performing hyperdrive jump of " + shipInfo);
			
			// Check ship size for hyper-space jump
			if (shipMass < WarpDriveConfig.SHIP_VOLUME_MIN_FOR_HYPERSPACE) {
				Jumpgate nearestGate = null;
				if (WarpDrive.jumpgates == null) {
					WarpDrive.logger.warn(this + " WarpDrive.jumpGates is NULL!");
				} else {
					nearestGate = WarpDrive.jumpgates.findNearestGate(pos);
				}
				
				final StringBuilder reason = new StringBuilder();
				if (nearestGate == null || !isShipInJumpgate(nearestGate, reason)) {
					tileEntityShipController.commandDone(false, new TextComponentString(String.format("Ship is too small (%d/%d).\nInsufficient ship mass to engage alcubierre drive.\nIncrease your mass or use a jumpgate to reach or exit hyperspace.",
					                                            shipMass, WarpDriveConfig.SHIP_VOLUME_MIN_FOR_HYPERSPACE)).getFormattedText());
					return;
				}
			}
			break;
			
		case MANUAL:
			WarpDrive.logger.info(String.format("%s Performing manual jump of %s, %s, movement %s, rotationSteps %d",
			                                    this, shipInfo, shipMovementType, tileEntityShipController.getMovement(), tileEntityShipController.getRotationSteps()));
			break;
			
		default:
			WarpDrive.logger.error(String.format("%s Aborting while trying to perform invalid jump command %s",
			                                     this, commandCurrent));
			tileEntityShipController.commandDone(false, "Internal error, check console for details");
			commandCurrent = EnumShipControllerCommand.IDLE;
			stateCurrent = EnumShipCoreState.DISCONNECTED;
			return;
		}
		
		if (!energy_consume(requiredEnergy, false)) {
			tileEntityShipController.commandDone(false, "Insufficient energy level");
			return;
		}
		
		int moveX = 0;
		int moveY = 0;
		int moveZ = 0;
		
		if (commandCurrent != EnumShipControllerCommand.HYPERDRIVE) {
			final VectorI movement = tileEntityShipController.getMovement();
			final VectorI shipSize = new VectorI(tileEntityShipController.getFront() + 1 + tileEntityShipController.getBack(),
			                                     tileEntityShipController.getUp()    + 1 + tileEntityShipController.getDown(),
			                                     tileEntityShipController.getRight() + 1 + tileEntityShipController.getLeft());
			final int maxDistance = shipMovementCosts.maximumDistance_blocks;
			if (Math.abs(movement.x) - shipSize.x > maxDistance) {
				movement.x = (int) Math.signum(movement.x) * (shipSize.x + maxDistance);
			}
			if (Math.abs(movement.y) - shipSize.y > maxDistance) {
				movement.y = (int) Math.signum(movement.y) * (shipSize.y + maxDistance);
			}
			if (Math.abs(movement.z) - shipSize.z > maxDistance) {
				movement.z = (int) Math.signum(movement.z) * (shipSize.z + maxDistance);
			}
			moveX = facing.getFrontOffsetX() * movement.x - facing.getFrontOffsetZ() * movement.z;
			moveY = movement.y;
			moveZ = facing.getFrontOffsetZ() * movement.x + facing.getFrontOffsetX() * movement.z;
		}
		
		if (WarpDriveConfig.LOGGING_JUMP) {
			WarpDrive.logger.info(this + " Movement adjusted to (" + moveX + " " + moveY + " " + moveZ + ") blocks.");
		}
		final JumpSequencer jump = new JumpSequencer(this, shipMovementType, null,
				moveX, moveY, moveZ, tileEntityShipController.getRotationSteps(),
				0, 0, 0);
		jump.enable();
	}
	
	private static boolean isOutsideBB(final AxisAlignedBB axisalignedbb, final int x, final int y, final int z) {
		return axisalignedbb.minX > x || axisalignedbb.maxX < x
		    || axisalignedbb.minY > y || axisalignedbb.maxY < y
		    || axisalignedbb.minZ > z || axisalignedbb.maxZ < z;
	}
	
	@Override
	public ITextComponent getStatus() {
		final String strIsolationRate = String.format("%.1f", isolationRate * 100.0D);
		return super.getStatus()
			.appendSibling((cooldownTime_ticks > 0) ? new TextComponentString("\n").appendSibling(new TextComponentTranslation("warpdrive.ship.status_line.cooling", cooldownTime_ticks / 20)) : new TextComponentString(""))
			.appendSibling((isolationBlocksCount > 0) ? new TextComponentString("\n").appendSibling(new TextComponentTranslation("warpdrive.ship.status_line.isolation", isolationBlocksCount, strIsolationRate)) : new TextComponentString(""));
	}
	
	public ITextComponent getBoundingBoxStatus() {
		return super.getStatusPrefix()
			.appendSibling(new TextComponentTranslation(showBoundingBox ? "tile.warpdrive.movement.ship_core.bounding_box.enabled" : "tile.warpdrive.movement.ship_core.bounding_box.disabled"));
	}
	
	private void updateShipMassAndVolume() {
		int newMass = 0;
		int newVolume = 0;
		
		try {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					for (int y = minY; y <= maxY; y++) {
						final Block block = worldObj.getBlockState(new BlockPos(x, y, z)).getBlock();
						
						// Skipping vanilla air & ignored blocks
						if (block == Blocks.AIR || Dictionary.BLOCKS_LEFTBEHIND.contains(block)) {
							continue;
						}
						newVolume++;
						
						if (Dictionary.BLOCKS_NOMASS.contains(block)) {
							continue;
						}
						newMass++;
					}
				}
			}
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
		shipMass = newMass;
		shipVolume = newVolume;
	}
	
	private TileEntityShipController findControllerBlock() {
		TileEntity tileEntity;
		tileEntity = worldObj.getTileEntity(new BlockPos(pos.getX() + 1, pos.getY(), pos.getZ()));
		
		if (tileEntity instanceof TileEntityShipController) {
			facing = EnumFacing.EAST;
			return (TileEntityShipController) tileEntity;
		}
		
		tileEntity = worldObj.getTileEntity(new BlockPos(pos.getX() - 1, pos.getY(), pos.getZ()));
		
		if (tileEntity instanceof TileEntityShipController) {
			facing = EnumFacing.WEST;
			return (TileEntityShipController) tileEntity;
		}
		
		tileEntity = worldObj.getTileEntity(new BlockPos(pos.getX(), pos.getY(), pos.getZ() + 1));
		
		if (tileEntity instanceof TileEntityShipController) {
			facing = EnumFacing.SOUTH;
			return (TileEntityShipController) tileEntity;
		}
		
		tileEntity = worldObj.getTileEntity(new BlockPos(pos.getX(), pos.getY(), pos.getZ() - 1));
		
		if (tileEntity instanceof TileEntityShipController) {
			facing = EnumFacing.NORTH;
			return (TileEntityShipController) tileEntity;
		}
		
		return null;
	}
	
	public int getCooldown() {
		return cooldownTime_ticks;
	}
	
	@Override
	public int energy_getMaxStorage() {
		return WarpDriveConfig.SHIP_MAX_ENERGY_STORED;
	}
	
	@Override
	public boolean energy_canInput(final EnumFacing from) {
		return true;
	}
	
	@Override
	public void readFromNBT(final NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		
		facing = EnumFacing.getFront(tagCompound.getByte("facing"));
		uuid = new UUID(tagCompound.getLong("uuidMost"), tagCompound.getLong("uuidLeast"));
		if (uuid.getMostSignificantBits() == 0 && uuid.getLeastSignificantBits() == 0) {
			uuid = UUID.randomUUID();
		}
		shipName = tagCompound.getString("corefrequency") + tagCompound.getString("shipName");	// coreFrequency is the legacy tag name
		isolationRate = tagCompound.getDouble("isolationRate");
		cooldownTime_ticks = tagCompound.getInteger("cooldownTime");
		warmupTime_ticks = tagCompound.getInteger("warmupTime");
		jumpCount = tagCompound.getInteger("jumpCount");
	}
	
	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeToNBT(tagCompound);
		if (facing != null) {
			tagCompound.setByte("facing", (byte) facing.ordinal());
		}
		if (uuid != null) {
			tagCompound.setLong("uuidMost", uuid.getMostSignificantBits());
			tagCompound.setLong("uuidLeast", uuid.getLeastSignificantBits());
		}
		tagCompound.setString("shipName", shipName);
		tagCompound.setDouble("isolationRate", isolationRate);
		tagCompound.setInteger("cooldownTime", cooldownTime_ticks);
		tagCompound.setInteger("warmupTime", warmupTime_ticks);
		tagCompound.setInteger("jumpCount", jumpCount);
		return tagCompound;
	}
	
	@Nonnull
	@Override
	public NBTTagCompound getUpdateTag() {
		final NBTTagCompound tagCompound = writeToNBT(super.getUpdateTag());
		tagCompound.setInteger("minX", minX);
		tagCompound.setInteger("maxX", maxX);
		tagCompound.setInteger("minY", minY);
		tagCompound.setInteger("maxY", maxY);
		tagCompound.setInteger("minZ", minZ);
		tagCompound.setInteger("maxZ", maxZ);
		return tagCompound;
	}
	
	@Override
	public void onDataPacket(NetworkManager networkManager, SPacketUpdateTileEntity packet) {
		final NBTTagCompound tagCompound = packet.getNbtCompound();
		readFromNBT(tagCompound);
		minX = tagCompound.getInteger("minX");
		maxX = tagCompound.getInteger("maxX");
		minY = tagCompound.getInteger("minY");
		maxY = tagCompound.getInteger("maxY");
		minZ = tagCompound.getInteger("minZ");
		maxZ = tagCompound.getInteger("maxZ");
	}
	
	@Override
	public void validate() {
		super.validate();
		
		if (worldObj.isRemote) {
			return;
		}
		
		WarpDrive.starMap.updateInRegistry(this);
	}
	
	@Override
	public void invalidate() {
		if (!worldObj.isRemote) {
			WarpDrive.starMap.removeFromRegistry(this);
		}
		super.invalidate();
	}
	
	// IStarMapRegistryTileEntity overrides
	@Override
	public EnumStarMapEntryType getStarMapType() {
		return EnumStarMapEntryType.SHIP;
	}
	
	@Override
	public UUID getUUID() {
		return uuid;
	}
	
	@Override
	public AxisAlignedBB getStarMapArea() {
		return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
	}
	
	@Override
	public int getMass() {
		return shipMass;
	}
	
	@Override
	public double getIsolationRate() {
		return isolationRate;
	}
	
	@Override
	public String getStarMapName() {
		return shipName;
	}
	
	@Override
	public void onBlockUpdatedInArea(final VectorI vector, final IBlockState blockState) {
		// no operation
	}
	
	@Override
	public String toString() {
		return String.format("%s \'%s\' @ %s (%d %d %d)",
		                     getClass().getSimpleName(),
		                     shipName,
		                     worldObj == null ? "~NULL~" : worldObj.provider.getSaveFolder(),
		                     pos.getX(), pos.getY(), pos.getZ());
	}
}
