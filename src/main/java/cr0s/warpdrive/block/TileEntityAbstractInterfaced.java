package cr0s.warpdrive.block;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.VectorI;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import li.cil.oc.api.FileSystem;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;

import javax.annotation.Nonnull;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Optional;

// OpenComputer API: https://github.com/MightyPirates/OpenComputers/tree/master-MC1.7.10/src/main/java/li/cil/oc/api

@Optional.InterfaceList({
	@Optional.Interface(iface = "li.cil.oc.api.network.Environment", modid = "OpenComputers"),
	@Optional.Interface(iface = "dan200.computercraft.api.peripheral.IPeripheral", modid = "ComputerCraft")
})
public abstract class TileEntityAbstractInterfaced extends TileEntityAbstractBase implements IPeripheral, Environment, cr0s.warpdrive.api.computer.IInterfaced {
	
	// Common computer properties
	protected String peripheralName = null;
	private String[] methodsArray = {};
	
	// String returned to LUA script in case of error
	public static final String COMPUTER_ERROR_TAG = "!ERROR!";
	
	// pre-loaded scripts support
	private volatile ManagedEnvironment OC_fileSystem = null;
	private volatile boolean CC_hasResource = false;
	private volatile boolean OC_hasResource = false;
	protected volatile List<String> CC_scripts = null;
	
	// OpenComputer specific properties
	protected boolean 	OC_enable = true;
	protected Node		OC_node = null;
	protected boolean	OC_addedToNetwork = false;
	
	// ComputerCraft specific properties
	protected final HashMap<Integer, IComputerAccess> connectedComputers = new HashMap<>();
	
	public TileEntityAbstractInterfaced() {
		super();
		addMethods(new String[] {
				"interfaced",
				"position",
				"version"
		});
	}
	
	// WarpDrive abstraction layer
	protected void addMethods(final String[] methodsToAdd) {
		if (methodsArray == null) {
			methodsArray = methodsToAdd;
		} else {
			int currentLength = methodsArray.length;
			methodsArray = Arrays.copyOf(methodsArray, methodsArray.length + methodsToAdd.length);
			for (final String method : methodsToAdd) {
				methodsArray[currentLength] = method;
				currentLength++;
			}
		}
	}
	
	protected String getMethodName(final int methodIndex) {
		return methodsArray[methodIndex];
	}
	
	protected String getMethodName(final int methodIndex, final Object[] arguments) {
		final String methodName = methodsArray[methodIndex];
		
		if (WarpDrive.isDev && WarpDriveConfig.LOGGING_LUA) {
			WarpDrive.logger.info(String.format("LUA call to %s.%s(%s)", peripheralName, methodName, Commons.format(arguments)));
		}
		
		return methodName;
	}
	
	private boolean assetExist(final String resourcePath) {
		final URL url = getClass().getResource(resourcePath);
		return (url != null);
	}
	
	@Override
 	public void update() {
		super.update();
		
		if (WarpDriveConfig.isOpenComputersLoaded) {
			if (!OC_addedToNetwork && OC_enable) {
				OC_addedToNetwork = true;
				Network.joinOrCreateNetwork(this);
			}
		}
	}
	
	@Override
	public void validate() {
		if (WarpDriveConfig.isComputerCraftLoaded) {
			final String CC_path = "/assets/" + WarpDrive.MODID.toLowerCase() + "/lua.ComputerCraft/" + peripheralName;
			CC_hasResource = assetExist(CC_path);
		}
		
		// deferred constructor so the derived class can finish it's initialization first
		if (WarpDriveConfig.isOpenComputersLoaded && OC_node == null) {
			OC_constructor();
		}
		super.validate();
	}
	
	@Override
	public void invalidate() {
		if (WarpDriveConfig.isOpenComputersLoaded) {
			if (OC_node != null) {
				OC_node.remove();
				OC_node = null;
			}
		}
		super.invalidate();
	}
	
	@Override
	public void onChunkUnload() {
		if (WarpDriveConfig.isOpenComputersLoaded) {
			if (OC_node != null) {
				OC_node.remove();
				OC_node = null;
			}
		}
		super.onChunkUnload();
	}
	
	@Override
	public void readFromNBT(final NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		if ( WarpDriveConfig.isOpenComputersLoaded
		  && FMLCommonHandler.instance().getEffectiveSide().isServer() ) {
			if (OC_node == null) {
				OC_constructor();
			}
			if (OC_node != null && OC_node.host() == this) {
				OC_node.load(tagCompound.getCompoundTag("oc:node"));
			} else if (tagCompound.hasKey("oc:node")) {
				WarpDrive.logger.error(this + " OC node failed to construct or wrong host, ignoring NBT node data read...");
			}
			if (OC_fileSystem != null && OC_fileSystem.node() != null) {
				OC_fileSystem.node().load(tagCompound.getCompoundTag("oc:fs"));
			} else if (OC_hasResource) {
				WarpDrive.logger.error(this + " OC filesystem failed to construct or wrong node, ignoring NBT filesystem data read...");
			}
		}
	}
	
	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeToNBT(tagCompound);
		if (WarpDriveConfig.isOpenComputersLoaded) {
			if (OC_node != null && OC_node.host() == this) {
				final NBTTagCompound nbtNode = new NBTTagCompound();
				OC_node.save(nbtNode);
				tagCompound.setTag("oc:node", nbtNode);
			}
			if (OC_fileSystem != null && OC_fileSystem.node() != null) {
				final NBTTagCompound nbtFileSystem = new NBTTagCompound();
				OC_fileSystem.node().save(nbtFileSystem);
				tagCompound.setTag("oc:fs", nbtFileSystem);
			}
		}
		return tagCompound;
	}
	
	@Override
	public NBTTagCompound writeItemDropNBT(NBTTagCompound tagCompound) {
		tagCompound = super.writeItemDropNBT(tagCompound);
		tagCompound.removeTag("oc:node");
		tagCompound.removeTag("oc:fs");
		return tagCompound;
	}
	
	@Override
	public int hashCode() {
		return (((((super.hashCode() + (worldObj == null ? 0 : worldObj.provider.getDimension()) << 4) + pos.getX()) << 4) + pos.getY()) << 4) + pos.getZ();
	}
	
	// Dirty cheap conversion methods
	@Optional.Method(modid = "OpenComputers")
	protected Object[] argumentsOCtoCC(final Arguments args) {
		final Object[] arguments = new Object[args.count()];
		int index = 0;
		for (final Object arg : args) {
			if (args.isString(index)) {
				arguments[index] = args.checkString(index);
			} else {
				arguments[index] = arg;
			}
			index++;
		}
		return arguments;
	}
	
	// Declare type
	@Override
	public Object[] interfaced() {
		return new String[] { "I'm a WarpDrive computer interfaced tile entity." };
	}
	
	// Return block coordinates
	@Override
	public Object[] position() {
		return new Object[] { pos.getX(), pos.getY(), pos.getZ(), "?", pos.getX(), pos.getY(), pos.getZ() };
	}
	
	// Return version
	@Override
	public Object[] version() {
		if (WarpDriveConfig.LOGGING_LUA) {
			WarpDrive.logger.info("Version is " + WarpDrive.VERSION + " isDev " + WarpDrive.isDev);
		}
		String[] strings = WarpDrive.VERSION.split("-");
		if (WarpDrive.isDev) {
			strings = strings[strings.length - 2].split("\\.");
		} else {
			strings = strings[strings.length - 1].split("\\.");
		}
		final ArrayList<Integer> integers = new ArrayList<>(strings.length);
		for (final String string : strings) {
			integers.add(Integer.parseInt(string));
		}
		return integers.toArray();
	}
	
	// ComputerCraft IPeripheral methods
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public String getType() {
		return peripheralName;
	}
	
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public String[] getMethodNames() {
		return methodsArray;
	}
	
	protected VectorI computer_getVectorI(final VectorI vDefault, final Object[] arguments) {
		try {
			if (arguments.length == 3) {
				final int x = Commons.toInt(arguments[0]);
				final int y = Commons.toInt(arguments[1]);
				final int z = Commons.toInt(arguments[2]);
				return new VectorI(x, y, z);
			}
		} catch (final NumberFormatException exception) {
			// ignore
		}
		return vDefault;
	}
	
	protected UUID computer_getUUID(final UUID uuidDefault, final Object[] arguments) {
		try {
			if (arguments.length == 1 && arguments[0] != null) {
				if (arguments[0] instanceof UUID) {
					return (UUID) arguments[0];
				}
				if (arguments[0] instanceof String) {
					return UUID.fromString((String) arguments[0]);
				}
			}
		} catch (final IllegalArgumentException exception) {
			// ignore
		}
		return uuidDefault;
	}
	
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public Object[] callMethod(final IComputerAccess computer, final ILuaContext context, final int method, final Object[] arguments) {
		final String methodName = getMethodName(method);
		
		switch (methodName) {
		case "interfaced":
			return interfaced();
			
		case "position":
			return position();
			
		case "version":
			return version();
		}
		
		return null;
	}
	
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public void attach(final IComputerAccess computer) {
		final int id = computer.getID();
		connectedComputers.put(id, computer);
		if (CC_hasResource && WarpDriveConfig.G_LUA_SCRIPTS != WarpDriveConfig.LUA_SCRIPTS_NONE) {
			try {
				final String modid = WarpDrive.MODID.toLowerCase();
				final String folderPeripheral = peripheralName.replace(modid, modid + "/");
				computer.mount("/" + modid           , ComputerCraftAPI.createResourceMount(WarpDrive.class, modid, "lua.ComputerCraft/common"));
				computer.mount("/" + folderPeripheral, ComputerCraftAPI.createResourceMount(WarpDrive.class, modid, "lua.ComputerCraft/" + peripheralName));
				computer.mount("/warpupdater"        , ComputerCraftAPI.createResourceMount(WarpDrive.class, modid, "lua.ComputerCraft/common/updater"));
				if (WarpDriveConfig.G_LUA_SCRIPTS == WarpDriveConfig.LUA_SCRIPTS_ALL) {
					for (final String script : CC_scripts) {
						computer.mount("/" + script, ComputerCraftAPI.createResourceMount(WarpDrive.class, modid, "lua.ComputerCraft/" + peripheralName + "/" + script));
					}
				}
			} catch (final Exception exception) {
				exception.printStackTrace();
				WarpDrive.logger.error(String.format("Failed to mount ComputerCraft scripts for %s @ %s (%d %d %d), isFirstTick %s",
				                                     peripheralName,
				                                     worldObj == null ? "~NULL~" : worldObj.provider.getSaveFolder(),
				                                     pos.getX(), pos.getY(), pos.getZ(),
				                                     isFirstTick()));
			}
		}
	}
	
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public void detach(final IComputerAccess computer) {
		final int id = computer.getID();
		connectedComputers.remove(id);
	}
	
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public boolean equals(final IPeripheral other) {
		return other.hashCode() == hashCode();
	}
	
	// Computer abstraction methods
	protected void sendEvent(final String eventName, final Object... arguments) {
		if (WarpDriveConfig.LOGGING_LUA) {
			WarpDrive.logger.info(this + " Sending event '" + eventName + "'");
		}
		if (WarpDriveConfig.isComputerCraftLoaded) {
			for (final Map.Entry<Integer, IComputerAccess> integerIComputerAccessEntry : connectedComputers.entrySet()) {
				final IComputerAccess comp = integerIComputerAccessEntry.getValue();
				comp.queueEvent(eventName, arguments);
			}
		}
		if (WarpDriveConfig.isOpenComputersLoaded) {
			if (OC_node != null && OC_node.network() != null) {
				if (arguments == null || arguments.length == 0) {
					OC_node.sendToReachable("computer.signal", eventName);
				} else {
					final Object[] eventWithArguments = new Object[arguments.length + 1];
					eventWithArguments[0] = eventName;
					int index = 1;
					for (final Object object : arguments) {
						eventWithArguments[index] = object;
						index++;
					}
					OC_node.sendToReachable("computer.signal", eventWithArguments);
				}
			}
		}
	}
	
	// OpenComputers methods
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] position(final Context context, final Arguments arguments) {
		return position();
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] version(final Context context, final Arguments arguments) {
		return version();
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] interfaced(final Context context, final Arguments arguments) {
		return interfaced();
	}
	
	@Optional.Method(modid = "OpenComputers")
	private void OC_constructor() {
		assert(OC_node == null);
		final String OC_path = "/assets/" + WarpDrive.MODID.toLowerCase() + "/lua.OpenComputers/" + peripheralName;
		OC_hasResource = assetExist(OC_path);
		OC_node = Network.newNode(this, Visibility.Network).withComponent(peripheralName).create();
		if (OC_node != null && OC_hasResource && WarpDriveConfig.G_LUA_SCRIPTS != WarpDriveConfig.LUA_SCRIPTS_NONE) {
			OC_fileSystem = FileSystem.asManagedEnvironment(FileSystem.fromClass(getClass(), WarpDrive.MODID.toLowerCase(), "lua.OpenComputers/" + peripheralName), peripheralName);
			((Component) OC_fileSystem.node()).setVisibility(Visibility.Network);
		}
	}
	
	@Override
	@Optional.Method(modid = "OpenComputers")
	public Node node() {
		return OC_node;
	}
	
	@Override
	@Optional.Method(modid = "OpenComputers")
	public void onConnect(final Node node) {
		if (node.host() instanceof Context) {
			// Attach our file system to new computers we get connected to.
			// Note that this is also called for all already present computers
			// when we're added to an already existing network, so we don't
			// have to loop through the existing nodes manually.
			if (OC_fileSystem != null) {
				node.connect(OC_fileSystem.node());
			}
		}
	}
	
	@Override
	@Optional.Method(modid = "OpenComputers")
	public void onDisconnect(final Node node) {
		if (OC_fileSystem != null) {
			if (node.host() instanceof Context) {
				// Disconnecting from a single computer
				node.disconnect(OC_fileSystem.node());
			} else if (node == OC_node) {
				// Disconnecting from the network
				OC_fileSystem.node().remove();
			}
		}
	}
	
	@Override
	@Optional.Method(modid = "OpenComputers")
	public void onMessage(final Message message) {
		// nothing special
	}
}
