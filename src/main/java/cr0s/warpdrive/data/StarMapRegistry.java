package cr0s.warpdrive.data;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.LocalProfiler;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IStarMapRegistryTileEntity;
import cr0s.warpdrive.block.movement.TileEntityShipCore;
import cr0s.warpdrive.config.WarpDriveConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

/**
 * Registry of all known ships, jumpgates, etc. in the world
 * 
 * @author LemADEC
 */
public class StarMapRegistry {
	
	private final HashMap<Integer, CopyOnWriteArraySet<StarMapRegistryItem>> registry;
	private int countAdd = 0;
	private int countRemove = 0;
	private int countRead = 0;
	
	public StarMapRegistry() {
		registry = new HashMap<>();
	}
	
	public void updateInRegistry(final IStarMapRegistryTileEntity tileEntity) {
		// validate context
		assert(tileEntity instanceof TileEntity);
		if (!Commons.isSafeThread()) {
			WarpDrive.logger.error(String.format("Non-threadsafe call to StarMapRegistry:updateInRegistry outside main thread, for %s",
			                                     tileEntity));
		}
		
		// update statistics
		countRead++;
		if (WarpDriveConfig.LOGGING_STARMAP) {
			if (countRead % 1000 == 0) {
				WarpDrive.logger.info("Starmap registry stats: read " + countRead + " add " + countAdd + " remove " + countRemove + " => " + ((float) countRead) / (countRemove + countRead + countAdd) + "% read");
			}
		}
		
		// get dimension
		CopyOnWriteArraySet<StarMapRegistryItem> setRegistryItems = registry.get(((TileEntity) tileEntity).getWorld().provider.getDimension());
		if (setRegistryItems == null) {
			setRegistryItems = new CopyOnWriteArraySet<>();
		}
		
		// get entry
		final ArrayList<StarMapRegistryItem> listToRemove = new ArrayList<>(3);
		final UUID uuidTileEntity = tileEntity.getUUID();
		for (final StarMapRegistryItem registryItem : setRegistryItems) {
			if (registryItem.uuid == null) {
				WarpDrive.logger.error(String.format("Removing invalid StarMapRegistryItem %s",
				                                     registryItem));
				listToRemove.add(registryItem);
				continue;
			}
			
			if ( registryItem.type.equals(tileEntity.getStarMapType())
			  && registryItem.uuid.equals(uuidTileEntity) ) {// already registered
				registryItem.update(tileEntity);    // in-place update only works as long as hashcode remains unchanged
				return;
			} else if (registryItem.sameCoordinates(tileEntity)) {
				listToRemove.add(registryItem);
			}
		}
		setRegistryItems.removeAll(listToRemove);
		
		// not found => add
		countAdd++;
		setRegistryItems.add(new StarMapRegistryItem(tileEntity));
		registry.put(((TileEntity) tileEntity).getWorld().provider.getDimension(), setRegistryItems);
		if (WarpDriveConfig.LOGGING_STARMAP) {
			printRegistry("added");
		}
	}
	
	public void removeFromRegistry(final IStarMapRegistryTileEntity tileEntity) {
		assert(tileEntity instanceof TileEntity);
		
		countRead++;
		final Set<StarMapRegistryItem> setRegistryItems = registry.get(((TileEntity) tileEntity).getWorld().provider.getDimension());
		if (setRegistryItems == null) {
			// noting to remove
			return;
		}
		
		for (final StarMapRegistryItem registryItem : setRegistryItems) {
			if (registryItem.sameCoordinates(tileEntity)) {
				// found it, remove and exit
				countRemove++;
				setRegistryItems.remove(registryItem);
				return;
			}
		}
		// not found => ignore it
	}
	
	public StarMapRegistryItem getByUUID(final EnumStarMapEntryType enumStarMapEntryType, final UUID uuid) {
		for (final Integer dimensionId : registry.keySet()) {
			final CopyOnWriteArraySet<StarMapRegistryItem> setStarMapRegistryItems = registry.get(dimensionId);
			if (setStarMapRegistryItems == null) {
				continue;
			}
			
			for (final StarMapRegistryItem starMapRegistryItem : setStarMapRegistryItems) {
				if ( enumStarMapEntryType == null
				  || starMapRegistryItem.type == enumStarMapEntryType ) {
					if (starMapRegistryItem.uuid.equals(uuid)) {
						return starMapRegistryItem;
					}
				}
			}
		}
		return null;
	}
	
	public String find(final String nameShip) {
		final int MAX_LENGTH = 2000;
		final StringBuilder resultMatch = new StringBuilder();
		final StringBuilder resultCaseInsensitive = new StringBuilder();
		final StringBuilder resultContains = new StringBuilder();
		for (final Integer dimensionId : registry.keySet()) {
			final CopyOnWriteArraySet<StarMapRegistryItem> setStarMapRegistryItems = registry.get(dimensionId);
			if (setStarMapRegistryItems == null) {
				continue;
			}
			
			for (final StarMapRegistryItem starMapRegistryItem : setStarMapRegistryItems) {
				if (starMapRegistryItem.type == EnumStarMapEntryType.SHIP) {
					if (starMapRegistryItem.name.equals(nameShip)) {
						if (resultMatch.length() < MAX_LENGTH) {
							if (resultMatch.length() > 0) {
								resultMatch.append("\n");
							}
							resultContains.append(String.format("Ship '%s' found in %s",
							                                    starMapRegistryItem.name,
							                                    starMapRegistryItem.getFormattedLocation()));
						} else {
							resultMatch.append(".");
						}
					} else if (starMapRegistryItem.name.equalsIgnoreCase(nameShip)) {
						if (resultCaseInsensitive.length() < MAX_LENGTH) {
							if (resultCaseInsensitive.length() > 0) {
								resultCaseInsensitive.append("\n");
							}
							resultContains.append(String.format("Ship '%s' found in %s",
							                                    starMapRegistryItem.name,
							                                    starMapRegistryItem.getFormattedLocation()));
						} else {
							resultCaseInsensitive.append(".");
						}
					} else if (starMapRegistryItem.name.contains(nameShip)) {
						if (resultContains.length() < MAX_LENGTH) {
							if (resultContains.length() > 0) {
								resultContains.append("\n");
							}
							resultContains.append(String.format("Ship '%s' found in %s",
							                                    starMapRegistryItem.name,
							                                    starMapRegistryItem.getFormattedLocation()));
						} else {
							resultContains.append(".");
						}
					}
				}
			}
		}
		
		if (resultMatch.length() > 0) {
			return resultMatch.toString();
		}
		if (resultCaseInsensitive.length() > 0) {
			return resultCaseInsensitive.toString();
		}
		if (resultContains.length() > 0) {
			return resultContains.toString();
		}
		return String.format("No ship found with name '%s'", nameShip);
	}
	
	public void onBlockUpdated(final World world, final BlockPos blockPos, final IBlockState blockState) {
		final CopyOnWriteArraySet<StarMapRegistryItem> setStarMapRegistryItems = registry.get(world.provider.getDimension());
		if (setStarMapRegistryItems == null) {
			return;
		}
		for (final StarMapRegistryItem registryItem : setStarMapRegistryItems) {
			if (registryItem.contains(blockPos)) {
				final TileEntity tileEntity = world.getTileEntity(new BlockPos(registryItem.x, registryItem.y, registryItem.z));
				if (tileEntity instanceof IStarMapRegistryTileEntity) {
					((IStarMapRegistryTileEntity) tileEntity).onBlockUpdatedInArea(new VectorI(blockPos), blockState);
				}
			}
		}
	}
	
	public static double getGravity(final Entity entity) {
		final CelestialObject celestialObject = CelestialObjectManager.get(entity.worldObj, (int) entity.posX, (int) entity.posZ);
		return celestialObject == null ? 1.0D : celestialObject.getGravity();
	}
	
	public static int getSpaceDimensionId(final World world, final int x, final int z) {
		CelestialObject celestialObject = CelestialObjectManager.get(world, x, z);
		if (celestialObject == null) {
			return world.provider.getDimension();
		}
		// already in space?
		if (celestialObject.isSpace()) {
			return celestialObject.dimensionId;
		}
		// coming from hyperspace?
		if (celestialObject.isHyperspace()) {
			celestialObject = CelestialObjectManager.getClosestChild(world, x, z);
			return celestialObject == null ? 0 : celestialObject.dimensionId;
		}
		// coming from a planet?
		while (celestialObject != null && !celestialObject.isSpace()) {
			celestialObject = celestialObject.parent;
		}
		return celestialObject == null ? 0 : celestialObject.dimensionId;
	}
	
	public static int getHyperspaceDimensionId(final World world, final int x, final int z) {
		CelestialObject celestialObject = CelestialObjectManager.get(world, x, z);
		if (celestialObject == null) {
			return world.provider.getDimension();
		}
		// already in hyperspace?
		if (celestialObject.isHyperspace()) {
			return celestialObject.dimensionId;
		}
		// coming from space?
		if (celestialObject.isSpace()) {
			return celestialObject.parent.dimensionId;
		}
		// coming from a planet?
		while (celestialObject != null && !celestialObject.isSpace()) {
			celestialObject = celestialObject.parent;
		}
		return celestialObject == null || celestialObject.parent == null ? 0 : celestialObject.parent.dimensionId;
	}
	
	public static int getDimensionId(final String stringDimension, final Entity entity) {
		switch (stringDimension.toLowerCase()) {
		case "world":
		case "overworld":
		case "0":
			return 0;
		case "nether":
		case "thenether":
		case "-1":
			return -1;
		case "s":
		case "space":
			return getSpaceDimensionId(entity.worldObj, (int) entity.posX, (int) entity.posZ);
		case "h":
		case "hyper":
		case "hyperspace":
			return getHyperspaceDimensionId(entity.worldObj, (int) entity.posX, (int) entity.posZ);
		default:
			try {
				return Integer.parseInt(stringDimension);
			} catch (final Exception exception) {
				// exception.printStackTrace();
				WarpDrive.logger.info("Invalid dimension '" + stringDimension + "', expecting integer or overworld/nether/end/theend/space/hyper/hyperspace");
			}
		}
		return 0;
	}
	
	public ArrayList<RadarEcho> getRadarEchos(final TileEntity tileEntity, final int radius) {
		final ArrayList<RadarEcho> arrayListRadarEchos = new ArrayList<>(registry.size());
		cleanup();
		
		final CelestialObject celestialObject = CelestialObjectManager.get(tileEntity.getWorld(), tileEntity.getPos().getX(), tileEntity.getPos().getZ());
		final Vector3 vectorRadar = getUniversalCoordinates(
			celestialObject,
			tileEntity.getPos().getX(), tileEntity.getPos().getY(), tileEntity.getPos().getZ());
		// printRegistry();
		final int radius2 = radius * radius;
		for (final Map.Entry<Integer, CopyOnWriteArraySet<StarMapRegistryItem>> entryDimension : registry.entrySet()) {
			for (final StarMapRegistryItem starMapRegistryItem : entryDimension.getValue()) {
				if (!starMapRegistryItem.type.hasRadarEcho()) {
					continue;
				}
				final Vector3 vectorItem = starMapRegistryItem.getUniversalCoordinates(tileEntity.getWorld().isRemote);
				if (vectorItem == null) {
					continue;
				}
				final double dX = vectorItem.x - vectorRadar.x;
				final double dY = vectorItem.y - vectorRadar.y;
				final double dZ = vectorItem.z - vectorRadar.z;
				final double distance2 = dX * dX + dY * dY + dZ * dZ;
				if (distance2 > radius2) {
					continue;
				}
				if ( starMapRegistryItem.isolationRate != 0.0D
				  && tileEntity.getWorld().rand.nextDouble() < starMapRegistryItem.isolationRate) {
					continue;
				}
				
				arrayListRadarEchos.add( new RadarEcho(starMapRegistryItem.type.getName(),
				                                       vectorItem,
				                                       starMapRegistryItem.mass,
				                                       starMapRegistryItem.name) );
			}
		}
		
		return arrayListRadarEchos;
	}
	
	public static Vector3 getUniversalCoordinates(final CelestialObject celestialObject, final double x, final double y, final double z) {
		if (celestialObject == null) {
			// not a registered area
			return null;
		}
		final Vector3 vec3Result = new Vector3(x, y + 512.0D, z);
		CelestialObject celestialObjectNode = celestialObject;
		boolean hasHyperspace = celestialObjectNode.isHyperspace();
		while (celestialObjectNode.parent != null) {
			final VectorI vEntry = celestialObjectNode.getEntryOffset();
			vec3Result.x -= vEntry.x;
			vec3Result.y -= 256.0D;
			vec3Result.z -= vEntry.z;
			celestialObjectNode = celestialObjectNode.parent;
			hasHyperspace |= celestialObjectNode.isHyperspace();
		}
		return hasHyperspace ? vec3Result : null;
	}
	
	public void printRegistry(final String trigger) {
		WarpDrive.logger.info("Starmap registry (" + registry.size() + " entries after " + trigger + "):");
		
		for (final Map.Entry<Integer, CopyOnWriteArraySet<StarMapRegistryItem>> entryDimension : registry.entrySet()) {
			final StringBuilder message = new StringBuilder();
			for (final StarMapRegistryItem registryItem : entryDimension.getValue()) {
				message.append(String.format("\n- %s '%s' @ DIM%d (%d %d %d) with %.3f isolation rate",
				                             registryItem.type, registryItem.name,
				                             registryItem.dimensionId, registryItem.x, registryItem.y, registryItem.z,
				                             registryItem.isolationRate));
			}
			WarpDrive.logger.info(String.format("- %d entries in dimension %d: %s",
			                                    entryDimension.getValue().size(), entryDimension.getKey(), message.toString()));
		}
	}
	
	public boolean isWarpCoreIntersectsWithOthers(final TileEntityShipCore core) {
		final StringBuilder reason = new StringBuilder();
		cleanup();
		
		core.validateShipSpatialParameters(reason);
		final AxisAlignedBB aabb1 = new AxisAlignedBB(core.minX, core.minY, core.minZ, core.maxX, core.maxY, core.maxZ);
		
		final CopyOnWriteArraySet<StarMapRegistryItem> setRegistryItems = registry.get(core.getWorld().provider.getDimension());
		if (setRegistryItems == null) {
			return false;
		}
		for (final StarMapRegistryItem registryItem : setRegistryItems) {
			assert(registryItem.dimensionId == core.getWorld().provider.getDimension());
			
			// only check cores
			if (registryItem.type != EnumStarMapEntryType.SHIP) {
				continue;
			}
			
			// Skip self
			if (registryItem.x == core.getPos().getX() && registryItem.y == core.getPos().getY() && registryItem.z == core.getPos().getZ()) {
				continue;
			}
			
			// Compare areas for intersection
			final AxisAlignedBB aabb2 = new AxisAlignedBB(registryItem.minX, registryItem.minY, registryItem.minZ,
			                                              registryItem.maxX, registryItem.maxY, registryItem.maxZ);
			if (!aabb1.intersectsWith(aabb2)) {
				continue;
			}
			
			// Skip missing ship cores
			final TileEntity tileEntity = core.getWorld().getTileEntity(new BlockPos(registryItem.x, registryItem.y, registryItem.z));
			if (!(tileEntity instanceof TileEntityShipCore)) {
				continue;
			}
			final TileEntityShipCore shipCore = (TileEntityShipCore) tileEntity;
			
			// Skip offline ship cores
			if (shipCore.isOffline()) {
				continue;
			}
			
			// Skip invalid ships
			if (!shipCore.validateShipSpatialParameters(reason)) {
				continue;
			}
			
			// ship is intersecting, online and valid
			return true;
		}
		
		return false;
	}
	
	// do not call during tileEntity construction (readFromNBT and validate)
	private static boolean isExceptionReported = false;
	private void cleanup() {
		LocalProfiler.start("Starmap registry cleanup");
		
		boolean isValid;
		for (final Map.Entry<Integer, CopyOnWriteArraySet<StarMapRegistryItem>> entryDimension : registry.entrySet()) {
			final WorldServer world = DimensionManager.getWorld(entryDimension.getKey());
			// skip unloaded worlds
			if (world == null) {
				continue;
			}
			for (final StarMapRegistryItem registryItem : entryDimension.getValue()) {
				isValid = false;
				if (registryItem != null) {
					
					boolean isLoaded;
					if (world.getChunkProvider() instanceof ChunkProviderServer) {
						final ChunkProviderServer chunkProviderServer = world.getChunkProvider();
						try {
							final Chunk chunk = chunkProviderServer.id2ChunkMap.get(ChunkPos.chunkXZ2Int(registryItem.x >> 4, registryItem.z >> 4));
							isLoaded = chunk != null && chunk.isLoaded();
						} catch (final NoSuchFieldError exception) {
							if (!isExceptionReported) {
								WarpDrive.logger.info(String.format("Unable to check non-loaded chunks for star map entry %s",
								                                    registryItem));
								exception.printStackTrace();
								isExceptionReported = true;
							}
							isLoaded = chunkProviderServer.chunkExists(registryItem.x >> 4, registryItem.z >> 4);
						}
					} else {
						isLoaded = world.getChunkProvider().chunkExists(registryItem.x >> 4, registryItem.z >> 4);
					}
					// skip unloaded chunks
					if (!isLoaded) {
						if (WarpDrive.isDev) {
							WarpDrive.logger.info(String.format("Skipping non-loaded star map entry %s",
							                                    registryItem));
						}
						continue;
					}
					
					// get block and tile entity
					final Block block = world.getBlockState(new BlockPos(registryItem.x, registryItem.y, registryItem.z)).getBlock();
					
					final TileEntity tileEntity = world.getTileEntity(new BlockPos(registryItem.x, registryItem.y, registryItem.z));
					isValid = true;
					switch (registryItem.type) {
					case UNDEFINED:
						break;
					case SHIP:
						isValid = block == WarpDrive.blockShipCore && tileEntity != null && !tileEntity.isInvalid();
						break;
					case JUMPGATE:
						break;
					case PLANET:
						break;
					case STAR:
						break;
					case STRUCTURE:
						break;
					case WARP_ECHO:
						break;
					case ACCELERATOR:
						isValid = block == WarpDrive.blockAcceleratorController && tileEntity != null && !tileEntity.isInvalid();
						break;
					case TRANSPORTER:
						isValid = block == WarpDrive.blockTransporterCore && tileEntity != null && !tileEntity.isInvalid();
						break;
					default:
						break;
					}
				}
				
				if (!isValid) {
					// if (WarpDriveConfig.LOGGING_STARMAP) {
						if (registryItem == null) {
							WarpDrive.logger.info("Cleaning up starmap object ~null~");
						} else {
							WarpDrive.logger.info("Cleaning up starmap object " + registryItem.type + " at "
							                      + registryItem.dimensionId + " " + registryItem.x + " " + registryItem.y + " " + registryItem.z);
						}
					// }
					countRemove++;
					entryDimension.getValue().remove(registryItem);
				}
			}
		}
		
		LocalProfiler.stop();
	}
	
	public void readFromNBT(final NBTTagCompound tagCompound) {
		if (tagCompound == null || !tagCompound.hasKey("starMapRegistryItems")) {
			registry.clear();
			return;
		}
		
		// read all entries in a flat structure
		final NBTTagList tagList = tagCompound.getTagList("starMapRegistryItems", Constants.NBT.TAG_COMPOUND);
		final StarMapRegistryItem[] registryFlat = new StarMapRegistryItem[tagList.tagCount()];
		final HashMap<Integer, Integer> sizeDimensions = new HashMap<>();
		for (int index = 0; index < tagList.tagCount(); index++) {
			final StarMapRegistryItem starMapRegistryItem = new StarMapRegistryItem(tagList.getCompoundTagAt(index));
			registryFlat[index] = starMapRegistryItem;
			
			// update stats
			Integer count = sizeDimensions.computeIfAbsent(starMapRegistryItem.dimensionId, k -> 0);
			count++;
			sizeDimensions.put(starMapRegistryItem.dimensionId, count);
		}
		
		// pre-build the local collections using known stats to avoid re-allocations
		final HashMap<Integer, ArrayList<StarMapRegistryItem>> registryLocal = new HashMap<>();
		for (final Entry<Integer, Integer> entryDimension : sizeDimensions.entrySet()) {
			registryLocal.put(entryDimension.getKey(), new ArrayList<>(entryDimension.getValue()));
		}
		
		// fill the local collections
		for (final StarMapRegistryItem starMapRegistryItem : registryFlat) {
			registryLocal.get(starMapRegistryItem.dimensionId).add(starMapRegistryItem);
		}
		
		// transfer to main one
		registry.clear();
		for (final Entry<Integer, ArrayList<StarMapRegistryItem>> entry : registryLocal.entrySet()) {
			registry.put(entry.getKey(), new CopyOnWriteArraySet<>(entry.getValue()));
		}
	}
	
	public void writeToNBT(final NBTTagCompound tagCompound) {
		final NBTTagList tagList = new NBTTagList();
		for (final CopyOnWriteArraySet<StarMapRegistryItem> starMapRegistryItems : registry.values()) {
			for (final StarMapRegistryItem starMapRegistryItem : starMapRegistryItems) {
				final NBTTagCompound tagCompoundItem = new NBTTagCompound();
				starMapRegistryItem.writeToNBT(tagCompoundItem);
				tagList.appendTag(tagCompoundItem);
			}
		}
		tagCompound.setTag("starMapRegistryItems", tagList);
	}
}
