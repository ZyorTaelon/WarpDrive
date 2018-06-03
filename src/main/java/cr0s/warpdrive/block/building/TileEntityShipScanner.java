package cr0s.warpdrive.block.building;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.ISequencerCallbacks;
import cr0s.warpdrive.block.TileEntityAbstractInterfaced;
import cr0s.warpdrive.block.movement.BlockShipCore;
import cr0s.warpdrive.block.movement.TileEntityShipCore;
import cr0s.warpdrive.config.Dictionary;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.EnumShipScannerState;
import cr0s.warpdrive.data.JumpBlock;
import cr0s.warpdrive.data.JumpShip;
import cr0s.warpdrive.data.SoundEvents;
import cr0s.warpdrive.data.Transformation;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.event.DeploySequencer;
import cr0s.warpdrive.item.ItemShipToken;
import cr0s.warpdrive.network.PacketHandler;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;

public class TileEntityShipScanner extends TileEntityAbstractInterfaced implements ISequencerCallbacks {
	
	// persistent properties
	private String schematicFileName = "";
	private int targetX, targetY, targetZ;
	private byte rotationSteps;
	public Block blockCamouflage;
	public int metadataCamouflage;
	protected int colorMultiplierCamouflage;
	protected int lightCamouflage;
	
	// computed properties
	private boolean isShipToken;
	private EnumShipScannerState enumShipScannerState = EnumShipScannerState.IDLE;
	private TileEntityShipCore shipCore = null;
	
	private int laserTicks = 0;
	private int scanTicks = 0;
	private int deployTicks = 0;
	
	private int searchTicks = 0;
	
	private String playerName = "";
	
	private JumpShip jumpShip;
	private int blocksToDeployCount;
	
	public TileEntityShipScanner() {
		super();
		
		peripheralName = "warpdriveShipScanner";
		addMethods(new String[] {
				"scan",
				"fileName",
				"deploy",
				"state"
		});
	}
	
	@Override
	public void update() {
		super.update();
		
		if (worldObj.isRemote) {
			return;
		}
		
		searchTicks++;
		if (searchTicks > WarpDriveConfig.SS_SEARCH_INTERVAL_TICKS) {
			searchTicks = 0;
			shipCore = searchShipCore();
		}
		
		// Trigger deployment by player, provided setup is done
		final boolean isSetupDone = targetX != 0 || targetY != 0 || targetZ != 0;
		if (isSetupDone) {
			if (enumShipScannerState == EnumShipScannerState.IDLE) {
				checkPlayerForShipToken();
			}
			if (enumShipScannerState != EnumShipScannerState.DEPLOYING) {
				setState(EnumShipScannerState.IDLE); // disable scanner
				return;
			}
			
		} else if (enumShipScannerState != EnumShipScannerState.DEPLOYING && shipCore == null) {// Ship core is not found
			laserTicks++;
			if (laserTicks > 20) {
				PacketHandler.sendBeamPacket(worldObj,
				                             new Vector3(this).translate(0.5D),
				                             new Vector3(pos.getX(), pos.getY() + 5, pos.getZ()).translate(0.5D), 
				                             1.0F, 0.2F, 0.0F, 40, 0, 100);
				laserTicks = 0;
			}
			return;
		}
		
		switch (enumShipScannerState) {
		case IDLE:// inactive
			if (shipCore != null) {// and ship core found
				laserTicks++;
				if (laserTicks > 20) {
					PacketHandler.sendBeamPacket(worldObj,
					                             new Vector3(this).translate(0.5D),
					                             new Vector3(shipCore).translate(0.5D),
					                             0.0F, 1.0F, 0.2F, 40, 0, 100);
					laserTicks = 0;
				}
			}
			break;
			
		case SCANNING:// active and scanning
			laserTicks++;
			if (laserTicks > 5) {
				laserTicks = 0;
				
				for (int index = 0; index < 10; index++) {
					final int randomX = shipCore.minX + worldObj.rand.nextInt(shipCore.maxX - shipCore.minX + 1);
					final int randomY = shipCore.minY + worldObj.rand.nextInt(shipCore.maxY - shipCore.minY + 1);
					final int randomZ = shipCore.minZ + worldObj.rand.nextInt(shipCore.maxZ - shipCore.minZ + 1);
					
					worldObj.playSound(null, pos, SoundEvents.LASER_LOW, SoundCategory.HOSTILE, 4F, 1F);
					final float r = worldObj.rand.nextFloat() - worldObj.rand.nextFloat();
					final float g = worldObj.rand.nextFloat() - worldObj.rand.nextFloat();
					final float b = worldObj.rand.nextFloat() - worldObj.rand.nextFloat();
					
					PacketHandler.sendBeamPacket(worldObj,
							new Vector3(this).translate(0.5D),
							new Vector3(randomX, randomY, randomZ).translate(0.5D),
							r, g, b, 15, 0, 100);
				}
			}
			
			scanTicks++;
			if (scanTicks > 20 * (1 + shipCore.shipMass / WarpDriveConfig.SS_SCAN_BLOCKS_PER_SECOND)) {
				setState(EnumShipScannerState.IDLE); // disable scanner
			}
			break;
			
		case DEPLOYING:// active and deploying
			if (deployTicks == 0) {
				final DeploySequencer sequencer = new DeploySequencer(jumpShip, getWorld(), isShipToken, targetX, targetY, targetZ, rotationSteps);
				
				// deploy at most (jump speed / 4), at least (deploy speed), optimally in 10 seconds 
				final int optimumSpeed = Math.round(blocksToDeployCount * WarpDriveConfig.SS_DEPLOY_INTERVAL_TICKS / (20.0F * 10.0F));
				final int blockToDeployPerTick = Math.max(WarpDriveConfig.SS_DEPLOY_BLOCKS_PER_INTERVAL,
				                                          Math.min(WarpDriveConfig.G_BLOCKS_PER_TICK / 4, optimumSpeed));
				if (WarpDrive.isDev && WarpDriveConfig.LOGGING_BUILDING) {
					WarpDrive.logger.info("optimumSpeed " + optimumSpeed + " blockToDeployPerTick " + blockToDeployPerTick);
				}
				sequencer.setBlocksPerTick(blockToDeployPerTick);
				sequencer.setCaptain(playerName);
				sequencer.setEffectSource(new Vector3(this).translate(0.5D));
				sequencer.setCallback(this);
				sequencer.enable();
			}
			
			deployTicks++;
			if (deployTicks > 20.0F * 60.0F) {
				// timeout in sequencer?
				WarpDrive.logger.info(this + " Deployment timeout?");
				deployTicks = 0;
				setState(EnumShipScannerState.IDLE); // disable scanner
				shipToken_nextUpdate_ticks = SHIP_TOKEN_UPDATE_PERIOD_TICKS * 3;
			}
			break;
			
		default:
			WarpDrive.logger.error("Invalid ship scanner state, forcing to IDLE...");
			setState(EnumShipScannerState.IDLE);
			break;
		}
	}
	
	private void setState(final EnumShipScannerState newState) {
		if (enumShipScannerState == newState) {
			return;
		}
		enumShipScannerState = newState;
		if (blockCamouflage == null) {
			if (getBlockMetadata() == newState.getMetadata()) {
				updateMetadata(newState.getMetadata());
			}
		} else {
			if (getBlockMetadata() != metadataCamouflage) {
				updateMetadata(metadataCamouflage);
			}
		}
	}
	
	@Override
	public void sequencer_finished() {
		switch (enumShipScannerState) {
//		case IDLE:// inactive
//			break;
		
//		case SCANNING:// active and scanning
//			break;
		
		case DEPLOYING:// active and deploying
			setState(EnumShipScannerState.IDLE); // disable scanner
			if (WarpDriveConfig.LOGGING_BUILDING) {
				WarpDrive.logger.info(this + " Deployment done");
			}
			shipToken_nextUpdate_ticks = SHIP_TOKEN_UPDATE_PERIOD_TICKS * 3;
			break;
		
		default:
			WarpDrive.logger.error(this + " Invalid ship scanner state, forcing to IDLE...");
			setState(EnumShipScannerState.IDLE);
			break;
		}
	}
	
	private TileEntityShipCore searchShipCore() {
		final StringBuilder reason = new StringBuilder();
		TileEntityShipCore tileEntityShipCore = null;
		
		// Search for ship cores above
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(pos);
		for (int newY = pos.getY() + 1; newY <= 255; newY++) {
			mutableBlockPos.setY(newY);
			if (worldObj.getBlockState(mutableBlockPos).getBlock().isAssociatedBlock(WarpDrive.blockShipCore)) { // found ship core above
				tileEntityShipCore = (TileEntityShipCore) worldObj.getTileEntity(mutableBlockPos);
				
				if (tileEntityShipCore != null) {
					if (!tileEntityShipCore.validateShipSpatialParameters(reason)) { // If we can't refresh ship's spatial parameters
						tileEntityShipCore = null;
					}
				}
				
				break;
			}
		}
		
		return tileEntityShipCore;
	}
	
	private boolean saveShipToSchematic(final String fileName, final StringBuilder reason) {
		if (!shipCore.validateShipSpatialParameters(reason)) {
			return false;
		}
		final short width = (short) (shipCore.maxX - shipCore.minX + 1);
		final short length = (short) (shipCore.maxZ - shipCore.minZ + 1);
		final short height = (short) (shipCore.maxY - shipCore.minY + 1);
		final int size = width * length * height;
		
		if (width <= 0 || length <= 0 || height <= 0) {
			reason.append("Invalid ship dimensions, nothing to scan");
			return false;
		}
		
		// Save header
		final NBTTagCompound schematic = new NBTTagCompound();
		
		schematic.setShort("Width", width);
		schematic.setShort("Length", length);
		schematic.setShort("Height", height);
		schematic.setInteger("shipMass", shipCore.shipMass);
		schematic.setString("shipName", shipCore.shipName);
		schematic.setInteger("shipVolume", shipCore.shipVolume);
		
		// Save new format
		final JumpShip ship = new JumpShip();
		ship.worldObj = shipCore.getWorld();
		ship.core = shipCore.getPos();
		ship.dx = shipCore.facing.getFrontOffsetX();
		ship.dz = shipCore.facing.getFrontOffsetZ();
		ship.minX = shipCore.minX;
		ship.maxX = shipCore.maxX;
		ship.minY = shipCore.minY;
		ship.maxY = shipCore.maxY;
		ship.minZ = shipCore.minZ;
		ship.maxZ = shipCore.maxZ;
		ship.shipCore = shipCore;
		if (!ship.save(reason)) {
			return false;
		}
		final NBTTagCompound tagCompoundShip = new NBTTagCompound();
		ship.writeToNBT(tagCompoundShip);
		schematic.setTag("ship", tagCompoundShip);
		
		// Storage collections
		final String stringBlockRegistryNames[] = new String[size];
		final byte byteMetadatas[] = new byte[size];
		final NBTTagList tileEntitiesList = new NBTTagList();
		
		// Scan the whole area
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < length; z++) {
					BlockPos blockPos = new BlockPos(shipCore.minX + x, shipCore.minY + y, shipCore.minZ + z);
					IBlockState blockState = worldObj.getBlockState(blockPos);
					
					// Skip leftBehind and anchor blocks
					if (Dictionary.BLOCKS_LEFTBEHIND.contains(blockState.getBlock()) || Dictionary.BLOCKS_ANCHOR.contains(blockState.getBlock())) {
						blockState = Blocks.AIR.getDefaultState();
					}
					
					final int index = x + (y * length + z) * width;
					stringBlockRegistryNames[index] = Block.REGISTRY.getNameForObject(blockState.getBlock()).toString();
					byteMetadatas[index] = (byte) blockState.getBlock().getMetaFromState(blockState);
					
					if (!blockState.getBlock().isAssociatedBlock(Blocks.AIR)) {
						final TileEntity tileEntity = worldObj.getTileEntity(blockPos);
						if (tileEntity != null) {
							try {
								final NBTTagCompound tagTileEntity = new NBTTagCompound();
								tileEntity.writeToNBT(tagTileEntity);
								
								JumpBlock.removeUniqueIDs(tagTileEntity);
								
								// Transform TE's coordinates from local axis to .schematic offset-axis
								// Warning: this is a cheap workaround for World Edit. Use the native format for proper transformation
								tagTileEntity.setInteger("x", tileEntity.getPos().getX() - shipCore.minX);
								tagTileEntity.setInteger("y", tileEntity.getPos().getY() - shipCore.minY);
								tagTileEntity.setInteger("z", tileEntity.getPos().getZ() - shipCore.minZ);
								
								tileEntitiesList.appendTag(tagTileEntity);
							} catch (final Exception exception) {
								exception.printStackTrace();
							}
						}
					}
				}
			}
		}
		
		schematic.setString("Materials", "Alpha");
		final NBTTagList tagListBlocks = new NBTTagList();
		for (final String stringRegistryName : stringBlockRegistryNames) {
			tagListBlocks.appendTag(new NBTTagString(stringRegistryName));
		}
		schematic.setTag("Blocks", tagListBlocks);
		schematic.setByteArray("Data", byteMetadatas);
		
		schematic.setTag("Entities", new NBTTagList()); // don't save entities
		schematic.setTag("TileEntities", tileEntitiesList);
		
		Commons.writeNBTToFile(fileName, schematic);
		
		return true;
	}
	
	// Begins ship scan
	private boolean scanShip(final StringBuilder reason) {
		// Enable scanner
		setState(EnumShipScannerState.SCANNING);
		final File file = new File(WarpDriveConfig.G_SCHEMALOCATION);
		if (!file.exists() || !file.isDirectory()) {
			if (!file.mkdirs()) {
				return false;
			}
		}
		
		// Generate unique file name
		final SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss's'SSS");
		final String shipName = shipCore.shipName.replaceAll("[^ -~]", "").replaceAll("[:/\\\\]", "");
		do {
			final Date now = new Date();
			schematicFileName = shipName + "_" + sdfDate.format(now);
		} while (new File(WarpDriveConfig.G_SCHEMALOCATION + "/" + schematicFileName + ".schematic").exists());
		
		if (!saveShipToSchematic(WarpDriveConfig.G_SCHEMALOCATION + "/" + schematicFileName + ".schematic", reason)) {
			return false;
		}
		reason.append(schematicFileName);
		return true;
	}
	
	// Returns error code and reason string
	private int deployShip(final String fileName, final int offsetX, final int offsetY, final int offsetZ,
	                       final byte rotationSteps, final boolean isForced, final StringBuilder reason) {
		targetX = pos.getX() + offsetX;
		targetY = pos.getY() + offsetY;
		targetZ = pos.getZ() + offsetZ;
		this.rotationSteps = rotationSteps;
		
		jumpShip = JumpShip.createFromFile(fileName, reason);
		if (jumpShip == null) {
			return -1;
		}
		
		blocksToDeployCount = jumpShip.jumpBlocks.length;
		if (WarpDriveConfig.LOGGING_BUILDING) {
			WarpDrive.logger.info(String.format("[ShipScanner] Loaded %d blocks to deploy", blocksToDeployCount));
		}
		
		// Validate context
		{
			// Check distance
			final double dX = pos.getX() - targetX;
			final double dY = pos.getY() - targetY;
			final double dZ = pos.getZ() - targetZ;
			final double distance = MathHelper.sqrt_double(dX * dX + dY * dY + dZ * dZ);
			
			if (distance > WarpDriveConfig.SS_MAX_DEPLOY_RADIUS_BLOCKS) {
				reason.append(String.format("§cCannot deploy ship more than %d blocks away from scanner.", WarpDriveConfig.SS_MAX_DEPLOY_RADIUS_BLOCKS));
				return 5;
			}
			
			// Compute target area
			final Transformation transformation = new Transformation(jumpShip, worldObj, targetX - jumpShip.core.getX(), targetY - jumpShip.core.getY(), targetZ - jumpShip.core.getZ(), rotationSteps);
			final BlockPos targetLocation1 = transformation.apply(jumpShip.minX, jumpShip.minY, jumpShip.minZ);
			final BlockPos targetLocation2 = transformation.apply(jumpShip.maxX, jumpShip.maxY, jumpShip.maxZ);
			final BlockPos targetLocationMin = new BlockPos(
			                Math.min(targetLocation1.getX(), targetLocation2.getX()) - 1,
			    Math.max(0, Math.min(targetLocation1.getY(), targetLocation2.getY()) - 1),
			                Math.min(targetLocation1.getZ(), targetLocation2.getZ()) - 1);
			final BlockPos targetLocationMax = new BlockPos(
			                  Math.max(targetLocation1.getX(), targetLocation2.getX()) + 1,
			    Math.min(255, Math.max(targetLocation1.getY(), targetLocation2.getY()) + 1),
			                  Math.max(targetLocation1.getZ(), targetLocation2.getZ()) + 1);
			
			if (isForced) {
				if (!isShipCoreClear(worldObj, new BlockPos(targetX, targetY, targetZ), playerName, reason)) {
					if (WarpDriveConfig.LOGGING_BUILDING) {
						WarpDrive.logger.info(String.format("Deployment collision detected at (%d %d %d)",
						                                    targetX, targetY, targetZ));
					}
					return 2;
				}
				
				// Clear specified area for any blocks to avoid corruption and ensure clean full ship
				for (int x = targetLocationMin.getX(); x <= targetLocationMax.getX(); x++) {
					for (int y = targetLocationMin.getY(); y <= targetLocationMax.getY(); y++) {
						for (int z = targetLocationMin.getZ(); z <= targetLocationMax.getZ(); z++) {
							worldObj.setBlockToAir(new BlockPos(x, y, z));
						}
					}
				}
				
			} else {
				
				// Check specified area for occupation by blocks
				// If specified area is occupied, break deployment with error message
				int occupiedBlockCount = 0;
				for (int x = targetLocationMin.getX(); x <= targetLocationMax.getX(); x++) {
					for (int y = targetLocationMin.getY(); y <= targetLocationMax.getY(); y++) {
						for (int z = targetLocationMin.getZ(); z <= targetLocationMax.getZ(); z++) {
							if (!worldObj.isAirBlock(new BlockPos(x, y, z))) {
								occupiedBlockCount++;
								if (occupiedBlockCount == 1 || (occupiedBlockCount <= 100 && worldObj.rand.nextInt(10) == 0)) {
									worldObj.newExplosion(null, x, y, z, 1, false, false);
								}
								if (WarpDriveConfig.LOGGING_BUILDING) {
									WarpDrive.logger.info("Deployment collision detected at " + x + " " + y + " " + z);
								}
							}
						}
					}
				}
				if (occupiedBlockCount > 0) {
					reason.append(String.format("§cDeployment area occupied with %d blocks. Can't deploy ship.", occupiedBlockCount));
					return 2;
				}
			}
		}
		
		// initiate deployment sequencer
		deployTicks = 0;
		
		isShipToken = isForced;
		setState(EnumShipScannerState.DEPLOYING);
		reason.append(String.format("Deploying ship '%s'...", fileName));
		return 3;
	}
	
	private static boolean isShipCoreClear(final World world, final BlockPos blockPos,
	                                       final String nameRequestingPlayer, final StringBuilder reason) {
		final IBlockState blockState = world.getBlockState(blockPos);
		if (blockState.getBlock().isAir(blockState, world, blockPos)) {
			return true;
		}
		
		if (!(blockState.getBlock() instanceof BlockShipCore)) {
			world.newExplosion(null, blockPos.getX(), blockPos.getY(), blockPos.getZ(),
			                   1, false, false);
			reason.append(String.format("§cDeployment area occupied by %s.\nCan't deploy new ship at (%d %d %d)",
			                            blockState.getBlock().getLocalizedName(),
			                            blockPos.getX(), blockPos.getY(), blockPos.getZ()));
			return false;
		}
		
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (!(tileEntity instanceof TileEntityShipCore)) {
			reason.append(String.format("§cDeployment area occupied with invalid tile entity %s for ship core.\nContact an admin for help at (%d %d %d)",
			                            tileEntity,
			                            blockPos.getX(), blockPos.getY(), blockPos.getZ()));
			WarpDrive.logger.error(reason.toString());
			world.newExplosion(null, blockPos.getX(), blockPos.getY(), blockPos.getZ(),
			                   1, false, false);
			return false;
		}
		
		final TileEntityShipCore tileEntityShipCore = (TileEntityShipCore) tileEntity;
		final String namePlayersAboard = tileEntityShipCore.getAllPlayersOnShip();
		if (!namePlayersAboard.isEmpty()) {
			reason.append(String.format("§cDeployment area occupied by active crew %s.\n§6Please wait or use another deployment spot",
			                            namePlayersAboard));
			return false;
		}
		
		if (tileEntityShipCore.isBooting()) {
			reason.append("§cDeployment area is busy.\n§6Please try again in a few seconds.");
			return false;
		}
		
		final String nameOnlineCrew = tileEntityShipCore.getFirstOnlineCrew();
		if (nameOnlineCrew == null || nameOnlineCrew.isEmpty()) {
			return true;
		}
		
		if (nameOnlineCrew.equals(nameRequestingPlayer)) {
			reason.append(String.format("§cDeployment area occupied by your ship, captain %s!\n§2Come back inside and use the computer to jump away!",
			                            nameOnlineCrew));
			return false;
		}
		
		reason.append(String.format("§cDeployment area occupied with ship owned by %s.\n§6Contact that player or use another deployment spot",
		                            nameOnlineCrew));
		return false;
		
	}
	
	@Override
	public void readFromNBT(final NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		schematicFileName = tagCompound.getString("schematic");
		targetX = tagCompound.getInteger("targetX");
		targetY = tagCompound.getInteger("targetY");
		targetZ = tagCompound.getInteger("targetZ");
		rotationSteps = tagCompound.getByte("rotationSteps");
		if (tagCompound.hasKey("camouflageBlock")) {
			try {
				blockCamouflage = Block.getBlockFromName(tagCompound.getString("camouflageBlock"));
				metadataCamouflage = tagCompound.getByte("camouflageMeta");
				colorMultiplierCamouflage = tagCompound.getInteger("camouflageColorMultiplier");
				lightCamouflage = tagCompound.getByte("camouflageLight");
				if (Dictionary.BLOCKS_NOCAMOUFLAGE.contains(blockCamouflage)) {
					blockCamouflage = null;
					metadataCamouflage = 0;
					colorMultiplierCamouflage = 0;
					lightCamouflage = 0;
				}
			} catch (final Exception exception) {
				exception.printStackTrace();
			}
		} else {
			blockCamouflage = null;
			metadataCamouflage = 0;
			colorMultiplierCamouflage = 0;
			lightCamouflage = 0;
		}
	}
	
	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeToNBT(tagCompound);
		tagCompound.setString("schematic", schematicFileName);
		tagCompound.setInteger("targetX", targetX);
		tagCompound.setInteger("targetY", targetY);
		tagCompound.setInteger("targetZ", targetZ);
		tagCompound.setByte("rotationSteps", rotationSteps);
		if (blockCamouflage != null) {
			tagCompound.setString("camouflageBlock", blockCamouflage.getRegistryName().toString());
			tagCompound.setByte("camouflageMeta", (byte) metadataCamouflage);
			tagCompound.setInteger("camouflageColorMultiplier",  colorMultiplierCamouflage);
			tagCompound.setByte("camouflageLight", (byte) lightCamouflage);
		}
		return tagCompound;
	}
	
	@Nonnull
	@Override
	public NBTTagCompound getUpdateTag() {
		return writeToNBT(super.getUpdateTag());
	}
	
	@Override
	public void onDataPacket(NetworkManager networkManager, SPacketUpdateTileEntity packet) {
		final NBTTagCompound tagCompound = packet.getNbtCompound();
		readFromNBT(tagCompound);
	}
	
	// OpenComputer callback methods
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] scan(final Context context, final Arguments arguments) {
		return scan();
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] filename(final Context context, final Arguments arguments) {
		return filename();
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] deploy(final Context context, final Arguments arguments) {
		return deploy(argumentsOCtoCC(arguments));
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] state(final Context context, final Arguments arguments) {
		return state();
	}
	
	private Object[] scan() {
		// Already scanning?
		if (enumShipScannerState != EnumShipScannerState.IDLE) {
			return new Object[] { false, 0, "Already active" };
		}
		
		if (shipCore == null) {
			return new Object[] { false, 1, "Ship-Core not found" };
		}
		final StringBuilder reason = new StringBuilder();
		final boolean success = scanShip(reason);
		return new Object[] { success, 3, reason.toString() };
	}
	
	private Object[] filename() {
		if (enumShipScannerState != EnumShipScannerState.IDLE && !schematicFileName.isEmpty()) {
			if (enumShipScannerState == EnumShipScannerState.DEPLOYING) {
				return new Object[] { false, "Deployment in progress. Please wait..." };
			} else {
				return new Object[] { false, "Scan in progress. Please wait..." };
			}
		}
		
		return new Object[] { true, schematicFileName };
	}
	
	private Object[] deploy(final Object[] arguments) {
		if (arguments.length != 5) {
			return new Object[] { 4, "Invalid arguments count, you need <.schematic file name>, <offsetX>, <offsetY>, <offsetZ>, <rotationSteps>!" };
		}
		
		final String fileName = (String) arguments[0];
		final int x = Commons.toInt(arguments[1]);
		final int y = Commons.toInt(arguments[2]);
		final int z = Commons.toInt(arguments[3]);
		final byte rotationSteps = (byte) Commons.toInt(arguments[4]);
		
		if (!new File(WarpDriveConfig.G_SCHEMALOCATION + "/" + fileName + ".schematic").exists()) {
			return new Object[] { 0, "Specified schematic file was not found!" };
		}
		
		final StringBuilder reason = new StringBuilder();
		final int result = deployShip(fileName, x, y, z, rotationSteps, false, reason);
		
		// don't force captain when deploying from LUA
		playerName = null;
		/*
		final EntityPlayer entityPlayer = worldObj.getClosestPlayer(xCoord, yCoord, zCoord, 8);
		if (entityPlayer != null) {
			playerName = entityPlayer.getCommandSenderName();
		} else {
			playerName = "";
		}
		/**/
		return new Object[] { result, reason.toString() };
	}
	
	private Object[] state() {
		switch (enumShipScannerState) {
		default:
		case IDLE:
			return new Object[] { false, "IDLE", 0, 0 };
		case SCANNING:
			return new Object[] { true, "Scanning", 0, 0 };
		case DEPLOYING:
			return new Object[] { true, "Deploying", 0, blocksToDeployCount };
		}
	}
	
	// ComputerCraft IPeripheral methods implementation
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public Object[] callMethod(final IComputerAccess computer, final ILuaContext context, final int method, final Object[] arguments) {
		final String methodName = getMethodName(method);
		
		switch (methodName) {
			case "scan":
				return scan();

			case "fileName":
				return filename();

			case "deploy": // deploy(schematicFileName, offsetX, offsetY, offsetZ)
				return deploy(arguments);

			case "state":
				return state();
		}
		
		return super.callMethod(computer, context, method, arguments);
	}
	
	private static final int SHIP_TOKEN_UPDATE_PERIOD_TICKS = 20;
	private static final int SHIP_TOKEN_UPDATE_DELAY_FAILED_PRECONDITION_TICKS = 3 * 20;
	private static final int SHIP_TOKEN_UPDATE_DELAY_FAILED_DEPLOY_TICKS = 5 * 20;
	private int shipToken_nextUpdate_ticks = 5;
	private static final int SHIP_TOKEN_PLAYER_WARMUP_PERIODS = 5;
	private UUID shipToken_idPlayer = null;
	private int shipToken_countWarmup = SHIP_TOKEN_PLAYER_WARMUP_PERIODS;
	private String shipToken_nameSchematic = "";
	private void checkPlayerForShipToken() {
		// cooldown to prevent player chat spam and server lag
		shipToken_nextUpdate_ticks--;
		if (shipToken_nextUpdate_ticks > 0) {
			return;
		}
		shipToken_nextUpdate_ticks = SHIP_TOKEN_UPDATE_PERIOD_TICKS;
		
		// find a unique player in range
		final AxisAlignedBB axisalignedbb = new AxisAlignedBB(pos.getX() - 1.0D, pos.getY() + 1.0D, pos.getZ() - 1.0D,
		                                                      pos.getX() + 1.99D, pos.getY() + 5.0D, pos.getZ() + 1.99D);
		final List list = worldObj.getEntitiesWithinAABBExcludingEntity(null, axisalignedbb);
		final List<EntityPlayer> entityPlayers = new ArrayList<>(10);
		for (final Object object : list) {
			if (object instanceof EntityPlayer) {
				entityPlayers.add((EntityPlayer) object);
			}
		}
		if (entityPlayers.isEmpty()) {
			shipToken_idPlayer = null;
			return;
		}
		if (entityPlayers.size() > 1) {
			for (final EntityPlayer entityPlayer : entityPlayers) {
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("Too many players detected: please stand in the beam one at a time.")
					.setStyle(new Style().setColor(TextFormatting.RED)));
				shipToken_nextUpdate_ticks = SHIP_TOKEN_UPDATE_DELAY_FAILED_PRECONDITION_TICKS;
			}
			shipToken_idPlayer = null;
			return;
		}
		final EntityPlayer entityPlayer = entityPlayers.get(0);
		
		// check inventory
		int slotIndex = 0;
		ItemStack itemStack = null;
		for (; slotIndex < entityPlayer.inventory.getSizeInventory(); slotIndex++) {
			itemStack = entityPlayer.inventory.getStackInSlot(slotIndex);
			if ( itemStack != null
			  && itemStack.getItem() == WarpDrive.itemShipToken
			  && itemStack.stackSize >= 1) {
				break;
			}
		}
		if ( itemStack == null
		  || slotIndex >= entityPlayer.inventory.getSizeInventory() ) {
			Commons.addChatMessage(entityPlayer, new TextComponentTranslation("Please come back once you've a Ship token."));
			shipToken_nextUpdate_ticks = SHIP_TOKEN_UPDATE_DELAY_FAILED_PRECONDITION_TICKS;
			shipToken_idPlayer = null;
			return;
		}
		
		// short warmup so payer can cancel eventually
		if ( entityPlayer.getUniqueID() != shipToken_idPlayer
		  || !shipToken_nameSchematic.equals(ItemShipToken.getSchematicName(itemStack)) ) {
			shipToken_idPlayer = entityPlayer.getUniqueID();
			shipToken_countWarmup = SHIP_TOKEN_PLAYER_WARMUP_PERIODS + 1;
			shipToken_nameSchematic = ItemShipToken.getSchematicName(itemStack);
			Commons.addChatMessage(entityPlayer, new TextComponentString(String.format("Ship token '%1$s' detected!", shipToken_nameSchematic))
			                                        .setStyle(new Style().setColor(TextFormatting.GOLD)));
		}
		shipToken_countWarmup--;
		if (shipToken_countWarmup > 0) {
			Commons.addChatMessage(entityPlayer, new TextComponentString(String.format("Stand by for ship materialization in %2$d...",
			                                                                           shipToken_nameSchematic, shipToken_countWarmup)));
			return;
		}
		// warmup done
		shipToken_idPlayer = null;
		playerName = entityPlayer.getName();
		
		// try deploying
		final StringBuilder reason = new StringBuilder();
		deployShip(ItemShipToken.getSchematicName(itemStack), targetX - pos.getX(), targetY - pos.getY(), targetZ - pos.getZ(), rotationSteps, true, reason);
		if (enumShipScannerState == EnumShipScannerState.IDLE) {
			// failed
			Commons.addChatMessage(entityPlayer, new TextComponentString(reason.toString()).setStyle(new Style().setColor(TextFormatting.RED)));
			shipToken_nextUpdate_ticks = SHIP_TOKEN_UPDATE_DELAY_FAILED_DEPLOY_TICKS;
			return;
		}
		Commons.addChatMessage(entityPlayer, new TextComponentString(reason.toString()).setStyle(new Style().setColor(TextFormatting.GOLD)));
		
		// success => remove token
		if (!entityPlayer.capabilities.isCreativeMode) {
			itemStack.stackSize--;
			if (itemStack.stackSize > 0) {
				entityPlayer.inventory.setInventorySlotContents(slotIndex, itemStack);
			} else {
				entityPlayer.inventory.setInventorySlotContents(slotIndex, null);
			}
			entityPlayer.inventory.markDirty();
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s @ %s (%d %d %d)",
		                     getClass().getSimpleName(),
		                     worldObj == null ? "~NULL~" : worldObj.provider.getSaveFolder(),
		                     pos.getX(), pos.getY(), pos.getZ());
	}
}
