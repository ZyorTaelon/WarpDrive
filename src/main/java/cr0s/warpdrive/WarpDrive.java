package cr0s.warpdrive;

import cr0s.warpdrive.api.IBlockBase;
import cr0s.warpdrive.block.BlockChunkLoader;
import cr0s.warpdrive.block.BlockLaser;
import cr0s.warpdrive.block.BlockLaserMedium;
import cr0s.warpdrive.block.atomic.BlockAcceleratorControlPoint;
import cr0s.warpdrive.block.atomic.BlockAcceleratorController;
import cr0s.warpdrive.block.atomic.BlockChiller;
import cr0s.warpdrive.block.atomic.BlockElectromagnetGlass;
import cr0s.warpdrive.block.atomic.BlockElectromagnetPlain;
import cr0s.warpdrive.block.atomic.BlockParticlesCollider;
import cr0s.warpdrive.block.atomic.BlockParticlesInjector;
import cr0s.warpdrive.block.atomic.BlockVoidShellGlass;
import cr0s.warpdrive.block.atomic.BlockVoidShellPlain;
import cr0s.warpdrive.block.breathing.BlockAir;
import cr0s.warpdrive.block.breathing.BlockAirFlow;
import cr0s.warpdrive.block.breathing.BlockAirGenerator;
import cr0s.warpdrive.block.breathing.BlockAirGeneratorTiered;
import cr0s.warpdrive.block.breathing.BlockAirShield;
import cr0s.warpdrive.block.breathing.BlockAirSource;
import cr0s.warpdrive.block.building.BlockShipScanner;
import cr0s.warpdrive.block.collection.BlockLaserTreeFarm;
import cr0s.warpdrive.block.collection.BlockMiningLaser;
import cr0s.warpdrive.block.decoration.BlockBedrockGlass;
import cr0s.warpdrive.block.decoration.BlockDecorative;
import cr0s.warpdrive.block.decoration.BlockGas;
import cr0s.warpdrive.block.decoration.BlockLamp_bubble;
import cr0s.warpdrive.block.decoration.BlockLamp_flat;
import cr0s.warpdrive.block.decoration.BlockLamp_long;
import cr0s.warpdrive.block.detection.BlockCamera;
import cr0s.warpdrive.block.detection.BlockCloakingCoil;
import cr0s.warpdrive.block.detection.BlockCloakingCore;
import cr0s.warpdrive.block.detection.BlockMonitor;
import cr0s.warpdrive.block.detection.BlockRadar;
import cr0s.warpdrive.block.detection.BlockSiren;
import cr0s.warpdrive.block.detection.BlockWarpIsolation;
import cr0s.warpdrive.block.energy.BlockEnanReactorCore;
import cr0s.warpdrive.block.energy.BlockEnanReactorLaser;
import cr0s.warpdrive.block.energy.BlockEnergyBank;
import cr0s.warpdrive.block.energy.BlockIC2reactorLaserMonitor;
import cr0s.warpdrive.block.forcefield.BlockForceField;
import cr0s.warpdrive.block.forcefield.BlockForceFieldProjector;
import cr0s.warpdrive.block.forcefield.BlockForceFieldRelay;
import cr0s.warpdrive.block.hull.BlockHullGlass;
import cr0s.warpdrive.block.hull.BlockHullOmnipanel;
import cr0s.warpdrive.block.hull.BlockHullPlain;
import cr0s.warpdrive.block.hull.BlockHullSlab;
import cr0s.warpdrive.block.hull.BlockHullStairs;
import cr0s.warpdrive.block.movement.BlockLift;
import cr0s.warpdrive.block.movement.BlockShipController;
import cr0s.warpdrive.block.movement.BlockShipCore;
import cr0s.warpdrive.block.movement.BlockTransporterBeacon;
import cr0s.warpdrive.block.movement.BlockTransporterContainment;
import cr0s.warpdrive.block.movement.BlockTransporterCore;
import cr0s.warpdrive.block.movement.BlockTransporterScanner;
import cr0s.warpdrive.block.passive.BlockHighlyAdvancedMachine;
import cr0s.warpdrive.block.passive.BlockIridium;
import cr0s.warpdrive.block.weapon.BlockLaserCamera;
import cr0s.warpdrive.block.weapon.BlockWeaponController;
import cr0s.warpdrive.command.CommandDebug;
import cr0s.warpdrive.command.CommandDump;
import cr0s.warpdrive.command.CommandEntity;
import cr0s.warpdrive.command.CommandFind;
import cr0s.warpdrive.command.CommandGenerate;
import cr0s.warpdrive.command.CommandBed;
import cr0s.warpdrive.command.CommandInvisible;
import cr0s.warpdrive.command.CommandJumpgates;
import cr0s.warpdrive.command.CommandReload;
import cr0s.warpdrive.command.CommandSpace;
import cr0s.warpdrive.config.RecipeParticleShapedOre;
import cr0s.warpdrive.config.RecipeTuningDriver;
import cr0s.warpdrive.config.Recipes;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.damage.DamageAsphyxia;
import cr0s.warpdrive.damage.DamageCold;
import cr0s.warpdrive.damage.DamageIrradiation;
import cr0s.warpdrive.damage.DamageLaser;
import cr0s.warpdrive.damage.DamageShock;
import cr0s.warpdrive.damage.DamageTeleportation;
import cr0s.warpdrive.damage.DamageWarm;
import cr0s.warpdrive.data.CamerasRegistry;
import cr0s.warpdrive.data.CelestialObjectManager;
import cr0s.warpdrive.data.CloakManager;
import cr0s.warpdrive.data.EnumHullPlainType;
import cr0s.warpdrive.data.JumpgatesRegistry;
import cr0s.warpdrive.data.StarMapRegistry;
import cr0s.warpdrive.event.ChunkHandler;
import cr0s.warpdrive.event.ChunkLoadingHandler;
import cr0s.warpdrive.event.ClientHandler;
import cr0s.warpdrive.event.CommonWorldGenerator;
import cr0s.warpdrive.event.ItemHandler;
import cr0s.warpdrive.event.LivingHandler;
import cr0s.warpdrive.event.ModelBakeEventHandler;
import cr0s.warpdrive.event.WorldHandler;
import cr0s.warpdrive.item.ItemAirTank;
import cr0s.warpdrive.item.ItemComponent;
import cr0s.warpdrive.item.ItemElectromagneticCell;
import cr0s.warpdrive.item.ItemForceFieldShape;
import cr0s.warpdrive.item.ItemForceFieldUpgrade;
import cr0s.warpdrive.item.ItemIC2reactorLaserFocus;
import cr0s.warpdrive.item.ItemShipToken;
import cr0s.warpdrive.item.ItemTuningDriver;
import cr0s.warpdrive.item.ItemTuningFork;
import cr0s.warpdrive.item.ItemWarpArmor;
import cr0s.warpdrive.network.PacketHandler;
import cr0s.warpdrive.render.ClientCameraHandler;
import cr0s.warpdrive.render.RenderOverlayAir;
import cr0s.warpdrive.render.RenderOverlayCamera;
import cr0s.warpdrive.render.RenderOverlayLocation;
import cr0s.warpdrive.world.BiomeSpace;
import cr0s.warpdrive.world.HyperSpaceWorldProvider;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraft.item.ItemBlock;
import net.minecraft.world.DimensionType;
import net.minecraft.world.biome.Biome;

import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLMissingMappingsEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.RecipeSorter;

import java.lang.reflect.Field;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.mojang.authlib.GameProfile;

import javax.annotation.Nullable;

@Mod(modid = WarpDrive.MODID,
     name = "WarpDrive",
     version = WarpDrive.VERSION,
     dependencies = "after:IC2;"
                  + "after:cofhcore;"
                  + "after:ComputerCraft;"
                  + "after:OpenComputer;"
                  + "after:CCTurtle;"
                  + "after:gregtech;"
                  + "after:AppliedEnergistics;"
                  + "after:EnderIO;"
                  + "after:DefenseTech;"
                  + "after:icbmclassic;"
)
public class WarpDrive {
	public static final String MODID = "warpdrive";
	public static final String VERSION = "@version@";
	public static final String PREFIX = MODID + ":";
	public static final boolean isDev = VERSION.equals("@" + "version" + "@") || VERSION.contains("-dev");
	public static GameProfile gameProfile = new GameProfile(UUID.nameUUIDFromBytes("[WarpDrive]".getBytes()), "[WarpDrive]");
	
	public static Block blockShipCore;
	public static Block blockShipController;
	public static Block blockRadar;
	public static Block blockWarpIsolation;
	public static Block blockAirGenerator;
	public static Block[] blockAirGeneratorTiered;
	public static Block blockLaser;
	public static Block blockLaserCamera;
	public static Block blockWeaponController;
	public static Block blockCamera;
	public static Block blockMonitor;
	public static Block blockLaserMedium;
	public static Block blockMiningLaser;
	public static Block blockLaserTreeFarm;
	public static Block blockLift;
	public static Block blockShipScanner;
	public static Block blockCloakingCore;
	public static Block blockCloakingCoil;
	public static Block blockTransporterBeacon;
	public static Block blockTransporterCore;
	public static Block blockTransporterContainment;
	public static Block blockTransporterScanner;
	public static Block blockIC2reactorLaserMonitor;
	public static Block blockEnanReactorCore;
	public static Block blockEnanReactorLaser;
	public static Block blockEnergyBank;
	public static Block blockAir;
	public static Block blockAirFlow;
	public static Block blockAirSource;
	public static Block blockAirShield;
	public static Block blockBedrockGlass;
	public static Block blockGas;
	public static Block blockIridium;
	public static Block blockLamp_bubble;
	public static Block blockLamp_flat;
	public static Block blockLamp_long;
	public static Block blockHighlyAdvancedMachine;
	public static Block blockChunkLoader;
	public static Block[] blockForceFields;
	public static Block[] blockForceFieldProjectors;
	public static Block[] blockForceFieldRelays;
	public static Block blockAcceleratorController;
	public static Block blockAcceleratorControlPoint;
	public static Block blockParticlesCollider;
	public static Block blockParticlesInjector;
	public static Block blockVoidShellPlain;
	public static Block blockVoidShellGlass;
	public static Block[] blockElectromagnetPlain;
	public static Block[] blockElectromagnetGlass;
	public static Block[] blockChillers;
	public static Block blockDecorative;
	public static Block[][] blockHulls_plain;
	public static Block[] blockHulls_glass;
	public static Block[] blockHulls_omnipanel;
	public static Block[][] blockHulls_stairs;
	public static Block[][] blockHulls_slab;
	public static Block blockSiren;
	
	public static Item itemIC2reactorLaserFocus;
	public static ItemComponent itemComponent;
	public static ItemShipToken itemShipToken;
	public static ItemTuningFork itemTuningFork;
	public static ItemTuningDriver itemTuningDriver;
	public static ItemForceFieldShape itemForceFieldShape;
	public static ItemForceFieldUpgrade itemForceFieldUpgrade;
	public static ItemElectromagneticCell itemElectromagneticCell;
	
	public static final ArmorMaterial armorMaterial = EnumHelper.addArmorMaterial("WARP", "warp", 18, new int[] { 2, 6, 5, 2 }, 9, SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.0F);
	public static ItemArmor[] itemWarpArmor;
	public static ItemAirTank[] itemAirTanks;
	
	public static DamageAsphyxia damageAsphyxia;
	public static DamageCold damageCold;
	public static DamageIrradiation damageIrradiation;
	public static DamageLaser damageLaser;
	public static DamageShock damageShock;
	public static DamageTeleportation damageTeleportation;
	public static DamageWarm damageWarm;
	
	public static Biome spaceBiome;
	public static DimensionType dimensionTypeSpace;
	public static DimensionType dimensionTypeHyperSpace;
	@SuppressWarnings("FieldCanBeLocal")
	private CommonWorldGenerator commonWorldGenerator;
	
	public static Field fieldBlockHardness = null;
	
	// Client settings
	public static final CreativeTabs creativeTabWarpDrive = new CreativeTabWarpDrive(MODID.toLowerCase());
	
	@Instance(WarpDrive.MODID)
	public static WarpDrive instance;
	@SidedProxy(clientSide = "cr0s.warpdrive.client.ClientProxy", serverSide = "cr0s.warpdrive.CommonProxy")
	public static CommonProxy proxy;
	
	public static StarMapRegistry starMap;
	public static JumpgatesRegistry jumpgates;
	public static CloakManager cloaks;
	public static CamerasRegistry cameras;
	
	@SuppressWarnings("FieldCanBeLocal")
	private static WarpDrivePeripheralHandler peripheralHandler = null;
	
	public static Logger logger;
	
	@EventHandler
	public void onFMLPreInitialization(final FMLPreInitializationEvent event) {
		logger = event.getModLog();
		
		WarpDriveConfig.onFMLpreInitialization(event.getModConfigurationDirectory().getAbsolutePath());
		
		RecipeSorter.register("warpdrive:particleShaped", RecipeParticleShapedOre.class, RecipeSorter.Category.SHAPED, "before:minecraft:shaped");
		RecipeSorter.register("warpdrive:tuningDriver", RecipeTuningDriver.class, RecipeSorter.Category.SHAPELESS, "before:minecraft:shapeless");
		
		// open access to Block.blockHardness
		fieldBlockHardness = Commons.getField(Block.class, "blockHardness", "field_149782_v");
		
		// common blocks
		blockChunkLoader = new BlockChunkLoader("blockChunkLoader");
		blockLaser = new BlockLaser("blockLaser");
		blockLaserMedium = new BlockLaserMedium("blockLaserMedium");
		
		// atomic blocks
		if (WarpDriveConfig.ACCELERATOR_ENABLE) {
			blockAcceleratorController = new BlockAcceleratorController("blockAcceleratorController");
			blockAcceleratorControlPoint = new BlockAcceleratorControlPoint("blockAcceleratorControlPoint");
			blockParticlesCollider = new BlockParticlesCollider("blockParticlesCollider");
			blockParticlesInjector = new BlockParticlesInjector("blockParticlesInjector");
			blockVoidShellPlain = new BlockVoidShellPlain("blockVoidShellPlain");
			blockVoidShellGlass = new BlockVoidShellGlass("blockVoidShellGlass");
			
			blockElectromagnetPlain = new Block[3];
			blockElectromagnetGlass = new Block[3];
			blockChillers = new Block[3];
			for(byte tier = 1; tier <= 3; tier++) {
				final int index = tier - 1;
				blockElectromagnetPlain[index] = new BlockElectromagnetPlain("blockElectromagnetPlain" + tier, tier);
				blockElectromagnetGlass[index] = new BlockElectromagnetGlass("blockElectromagnetGlass" + tier, tier);
				blockChillers[index] = new BlockChiller("blockChiller" + tier, tier);
			}
			
			itemElectromagneticCell = new ItemElectromagneticCell("itemElectromagneticCell");
		}
		
		// building blocks
		blockShipScanner = new BlockShipScanner("blockShipScanner");
		
		// breathing blocks
		blockAir = new BlockAir("blockAir");
		blockAirFlow = new BlockAirFlow("blockAirFlow");
		blockAirSource = new BlockAirSource("blockAirSource");
		blockAirShield = new BlockAirShield("blockAirShield");
		blockAirGenerator = new BlockAirGenerator("blockAirGenerator");
		
		blockAirGeneratorTiered = new Block[3];
		for (byte tier = 1; tier <= 3; tier++) {
			final int index = tier - 1;
			blockAirGeneratorTiered[index] = new BlockAirGeneratorTiered("blockAirGenerator" + tier, tier);
		}
		
		// collection blocks
		blockMiningLaser = new BlockMiningLaser("blockMiningLaser");
		blockLaserTreeFarm = new BlockLaserTreeFarm("blockLaserTreeFarm");
		
		// decorative
		blockDecorative = new BlockDecorative("blockDecorative");
		blockGas = new BlockGas("blockGas");
		blockLamp_bubble = new BlockLamp_bubble("blockLamp_bubble");
		blockLamp_flat = new BlockLamp_flat("blockLamp_flat");
		blockLamp_long = new BlockLamp_long("blockLamp_long");
		
		// detection blocks
		blockCamera = new BlockCamera("blockCamera");
		blockCloakingCore = new BlockCloakingCore("blockCloakingCore");
		blockCloakingCoil = new BlockCloakingCoil("blockCloakingCoil");
		blockMonitor = new BlockMonitor("blockMonitor");
		blockRadar = new BlockRadar("blockRadar");
		blockSiren = new BlockSiren("blockSiren");
		blockWarpIsolation = new BlockWarpIsolation("blockWarpIsolation");
		
		// energy blocks and items
		blockEnanReactorCore = new BlockEnanReactorCore("blockEnanReactorCore");
		blockEnanReactorLaser = new BlockEnanReactorLaser("blockEnanReactorLaser");
		blockEnergyBank = new BlockEnergyBank("blockEnergyBank");
		
		if (WarpDriveConfig.isIndustrialCraft2Loaded) {
			blockIC2reactorLaserMonitor = new BlockIC2reactorLaserMonitor("blockIC2reactorLaserMonitor");
			itemIC2reactorLaserFocus = new ItemIC2reactorLaserFocus("itemIC2reactorLaserFocus");
		}
		
		// force field blocks and items
		blockForceFields = new Block[3];
		blockForceFieldProjectors = new Block[3];
		blockForceFieldRelays = new Block[3];
		for (byte tier = 1; tier <= 3; tier++) {
			int index = tier - 1;
			blockForceFields[index] = new BlockForceField("blockForceField" + tier, tier);
			blockForceFieldProjectors[index] = new BlockForceFieldProjector("blockProjector" + tier, tier);
			blockForceFieldRelays[index] = new BlockForceFieldRelay("blockForceFieldRelay" + tier, tier);
		}
		/* @TODO security station
		blockSecurityStation = new BlockSecurityStation("blockSecurityStation");
		*/
		itemForceFieldShape = new ItemForceFieldShape("itemForceFieldShape");
		itemForceFieldUpgrade = new ItemForceFieldUpgrade("itemForceFieldUpgrade");
		
		// hull blocks
		blockHulls_plain = new Block[3][EnumHullPlainType.length];
		blockHulls_glass = new Block[3];
		blockHulls_omnipanel = new Block[3];
		blockHulls_stairs = new Block[3][16];
		blockHulls_slab = new Block[3][16];
		
		for (byte tier = 1; tier <= 3; tier++) {
			final int index = tier - 1;
			for (final EnumHullPlainType hullPlainType : EnumHullPlainType.values()) {
				blockHulls_plain[index][hullPlainType.ordinal()] = new BlockHullPlain("blockHull" + tier + "_" + hullPlainType.getName(), tier, hullPlainType);
			}
			blockHulls_glass[index] = new BlockHullGlass("blockHull" + tier + "_glass", tier);
			blockHulls_omnipanel[index] = new BlockHullOmnipanel("blockHull" + tier + "_omnipanel", tier);
			for (final EnumDyeColor enumDyeColor : EnumDyeColor.values()) {
				blockHulls_stairs[index][enumDyeColor.getMetadata()] = new BlockHullStairs("blockHull" + tier + "_stairs_" + enumDyeColor.getUnlocalizedName(), blockHulls_plain[index][0].getStateFromMeta(enumDyeColor.getMetadata()), tier);
				blockHulls_slab[index][enumDyeColor.getMetadata()] = new BlockHullSlab("blockHull" + tier + "_slab_" + enumDyeColor.getUnlocalizedName(), blockHulls_plain[index][0].getStateFromMeta(enumDyeColor.getMetadata()), tier);
			}
		}
		
		// movement blocks
		blockLift = new BlockLift("blockLift");
		blockShipController = new BlockShipController("blockShipController");
		blockShipCore = new BlockShipCore("blockShipCore");
		blockTransporterBeacon = new BlockTransporterBeacon("blockTransporterBeacon");
		blockTransporterContainment = new BlockTransporterContainment("blockTransporterContainment");
		blockTransporterCore = new BlockTransporterCore("blockTransporterCore");
		blockTransporterScanner = new BlockTransporterScanner("blockTransporterScanner");
		
		// passive blocks
		blockBedrockGlass = new BlockBedrockGlass("blockBedrockGlass");
		blockHighlyAdvancedMachine = new BlockHighlyAdvancedMachine("blockHighlyAdvancedMachine");
		blockIridium = new BlockIridium("blockIridium");
		
		// weapon blocks
		blockLaserCamera = new BlockLaserCamera("blockLaserCamera");
		blockWeaponController = new BlockWeaponController("blockWeaponController");
		
		// component items
		itemComponent = new ItemComponent("itemComponent");
		itemShipToken = new ItemShipToken("itemShipToken");
		
		// warp armor
		itemWarpArmor = new ItemArmor[4];
		itemWarpArmor[EntityEquipmentSlot.HEAD.getIndex() ] = new ItemWarpArmor("itemWarpArmor_" + ItemWarpArmor.suffixes[EntityEquipmentSlot.HEAD.getIndex() ], armorMaterial, 3, EntityEquipmentSlot.HEAD );
		itemWarpArmor[EntityEquipmentSlot.CHEST.getIndex()] = new ItemWarpArmor("itemWarpArmor_" + ItemWarpArmor.suffixes[EntityEquipmentSlot.CHEST.getIndex()], armorMaterial, 3, EntityEquipmentSlot.CHEST);
		itemWarpArmor[EntityEquipmentSlot.LEGS.getIndex() ] = new ItemWarpArmor("itemWarpArmor_" + ItemWarpArmor.suffixes[EntityEquipmentSlot.LEGS.getIndex() ], armorMaterial, 3, EntityEquipmentSlot.LEGS );
		itemWarpArmor[EntityEquipmentSlot.FEET.getIndex() ] = new ItemWarpArmor("itemWarpArmor_" + ItemWarpArmor.suffixes[EntityEquipmentSlot.FEET.getIndex() ], armorMaterial, 3, EntityEquipmentSlot.FEET );
		
		itemAirTanks = new ItemAirTank[4];
		for (int index = 0; index < 4; index++) {
			itemAirTanks[index] = new ItemAirTank((byte) index, "itemAirTank" + index);
		}
		
		// tool items
		itemTuningFork = new ItemTuningFork("itemTuningFork");
		itemTuningDriver = new ItemTuningDriver("itemTuningDriver");
		
		// damage sources
		damageAsphyxia = new DamageAsphyxia();
		damageCold = new DamageCold();
		damageIrradiation = new DamageIrradiation();
		damageLaser = new DamageLaser();
		damageShock = new DamageShock();
		damageTeleportation = new DamageTeleportation();
		damageWarm = new DamageWarm();
		
		// entities
		proxy.registerEntities();
		proxy.registerRendering();
		
		// chunk loading
		ForgeChunkManager.setForcedChunkLoadingCallback(instance, ChunkLoadingHandler.INSTANCE);
		
		// world generation
		commonWorldGenerator = new CommonWorldGenerator();
		GameRegistry.registerWorldGenerator(commonWorldGenerator, 0);
		
		final Biome.BiomeProperties biomeProperties = new Biome.BiomeProperties("Space").setRainDisabled().setWaterColor(0);
		spaceBiome = new BiomeSpace(biomeProperties);
		BiomeDictionary.registerBiomeType(spaceBiome, BiomeDictionary.Type.DEAD, BiomeDictionary.Type.WASTELAND);
		dimensionTypeSpace = DimensionType.register("Space", "_space", WarpDriveConfig.G_SPACE_PROVIDER_ID, HyperSpaceWorldProvider.class, true);
		dimensionTypeHyperSpace = DimensionType.register("Hyperspace", "_hyperspace", WarpDriveConfig.G_HYPERSPACE_PROVIDER_ID, HyperSpaceWorldProvider.class, true);
		
		CelestialObjectManager.onFMLInitialization();
		
		if (getClass().desiredAssertionStatus()) {
			Recipes.patchOredictionary();
		}
		
		proxy.onForgePreInitialisation();
	}
	
	@EventHandler
	public void onFMLInitialization(final FMLInitializationEvent event) {
		PacketHandler.init();
		
		WarpDriveConfig.onFMLInitialization();
	}
	
	@EventHandler
	public void onFMLPostInitialization(final FMLPostInitializationEvent event) {
		/* @TODO not sure why it would be needed, disabling for now
		// load all owned dimensions at boot
		for (final CelestialObject celestialObject : CelestialObjectManager.celestialObjects) {
			if (celestialObject.provider.equals(CelestialObject.PROVIDER_OTHER)) {
				DimensionManager.getWorld(celestialObject.dimensionId);
			}
		}
		/**/
		
		WarpDriveConfig.onFMLPostInitialization();
		
		Recipes.initDynamic();
		
		// Registers
		starMap = new StarMapRegistry();
		jumpgates = new JumpgatesRegistry();
		cloaks = new CloakManager();
		cameras = new CamerasRegistry();
		
		// Event handlers
		MinecraftForge.EVENT_BUS.register(new ClientHandler());
		MinecraftForge.EVENT_BUS.register(new ItemHandler());
		MinecraftForge.EVENT_BUS.register(new LivingHandler());
		MinecraftForge.EVENT_BUS.register(ModelBakeEventHandler.instance);
		
		if (WarpDriveConfig.isComputerCraftLoaded) {
			peripheralHandler = new WarpDrivePeripheralHandler();
			peripheralHandler.register();
		}
		
		final WorldHandler worldHandler = new WorldHandler();
		MinecraftForge.EVENT_BUS.register(worldHandler);
		
		final ChunkHandler chunkHandler = new ChunkHandler();
		MinecraftForge.EVENT_BUS.register(chunkHandler);
	}
	
	@EventHandler
	public void onFMLServerStarting(final FMLServerStartingEvent event) {
		event.registerServerCommand(new CommandDebug());
		event.registerServerCommand(new CommandDump());
		event.registerServerCommand(new CommandEntity());
		event.registerServerCommand(new CommandFind());
		event.registerServerCommand(new CommandGenerate());
		event.registerServerCommand(new CommandBed());
		event.registerServerCommand(new CommandInvisible());
		event.registerServerCommand(new CommandJumpgates());
		event.registerServerCommand(new CommandReload());
		event.registerServerCommand(new CommandSpace());
	}
	
	@SuppressWarnings("ConstantConditions")
	@Mod.EventHandler
	public void onFMLMissingMappings(final FMLMissingMappingsEvent event) {
		for (final FMLMissingMappingsEvent.MissingMapping mapping: event.get()) {
			if (mapping.type == GameRegistry.Type.ITEM) {
				switch (mapping.name) {
					case "WarpDrive:airBlock":
						mapping.remap(Item.getItemFromBlock(blockAir));
						break;
					case "WarpDrive:airCanisterFull":
					case "WarpDrive:itemAirCanisterFull":
					case "WarpDrive:itemAirTank":
						mapping.remap(itemAirTanks[0]);
						break;
					case "WarpDrive:airgenBlock":
						mapping.remap(Item.getItemFromBlock(blockAirGenerator));
						break;
					case "WarpDrive:blockHAMachine":
						mapping.remap(Item.getItemFromBlock(blockHighlyAdvancedMachine));
						break;
					case "WarpDrive:boosterBlock":
						mapping.remap(Item.getItemFromBlock(blockLaserMedium));
						break;
					case "WarpDrive:cameraBlock":
						mapping.remap(Item.getItemFromBlock(blockCamera));
						break;
					case "WarpDrive:chunkLoader":
						mapping.remap(Item.getItemFromBlock(blockChunkLoader));
						break;
					case "WarpDrive:cloakBlock":
						mapping.remap(Item.getItemFromBlock(blockCloakingCore));
						break;
					case "WarpDrive:cloakCoilBlock":
						mapping.remap(Item.getItemFromBlock(blockCloakingCoil));
						break;
					case "WarpDrive:component":
						mapping.remap(itemComponent);
						break;
					case "WarpDrive:decorative":
						mapping.remap(Item.getItemFromBlock(blockDecorative));
						break;
					case "WarpDrive:gasBlock":
						mapping.remap(Item.getItemFromBlock(blockGas));
						break;
					case "WarpDrive:helmet":
					case "WarpDrive:itemHelmet":
						mapping.remap(itemWarpArmor[3]);
						break;
					case "WarpDrive:iridiumBlock":
						mapping.remap(Item.getItemFromBlock(blockIridium));
						break;
					case "WarpDrive:isolationBlock":
						mapping.remap(Item.getItemFromBlock(blockWarpIsolation));
						break;
					case "WarpDrive:laserBlock":
						mapping.remap(Item.getItemFromBlock(blockLaser));
						break;
					case "WarpDrive:laserCamBlock":
						mapping.remap(Item.getItemFromBlock(blockLaserCamera));
						break;
					case "WarpDrive:laserTreeFarmBlock":
						mapping.remap(Item.getItemFromBlock(blockLaserTreeFarm));
						break;
					case "WarpDrive:liftBlock":
						mapping.remap(Item.getItemFromBlock(blockLift));
						break;
					case "WarpDrive:miningLaserBlock":
						mapping.remap(Item.getItemFromBlock(blockMiningLaser));
						break;
					case "WarpDrive:monitorBlock":
						mapping.remap(Item.getItemFromBlock(blockMonitor));
						break;
					case "WarpDrive:powerLaser":
						mapping.remap(Item.getItemFromBlock(blockEnanReactorLaser));
						break;
					case "WarpDrive:powerReactor":
						mapping.remap(Item.getItemFromBlock(blockEnanReactorCore));
						break;
					case "WarpDrive:powerStore":
						mapping.remap(Item.getItemFromBlock(blockEnergyBank));
						break;
					case "WarpDrive:protocolBlock":
						mapping.remap(Item.getItemFromBlock(blockShipController));
						break;
					case "WarpDrive:radarBlock":
						mapping.remap(Item.getItemFromBlock(blockRadar));
						break;
					case "WarpDrive:reactorLaserFocus":
						mapping.remap(itemIC2reactorLaserFocus);
						break;
					case "WarpDrive:reactorMonitor":
						mapping.remap(Item.getItemFromBlock(blockIC2reactorLaserMonitor));
						break;
					case "WarpDrive:scannerBlock":
						mapping.remap(Item.getItemFromBlock(blockShipScanner));
						break;
					case "WarpDrive:transportBeacon":
					case "WarpDrive:blockTransportBeacon":
						mapping.remap(Item.getItemFromBlock(blockTransporterBeacon));
						break;
					case "WarpDrive:transporter":
					case "WarpDrive:blockTransporter":
						mapping.remap(Item.getItemFromBlock(blockTransporterCore));
						break;
					case "WarpDrive:warpCore":
						mapping.remap(Item.getItemFromBlock(blockShipCore));
						break;
					case "WarpDrive:itemTuningRod":
						mapping.remap(itemTuningFork);
						break;
					case "WarpDrive:itemCrystalToken":
						mapping.remap(itemShipToken);
						break;
				}
				
			} else if (mapping.type == GameRegistry.Type.BLOCK) {
				switch (mapping.name) {
					case "WarpDrive:airBlock":
						mapping.remap(blockAir);
						break;
					case "WarpDrive:airgenBlock":
						mapping.remap(blockAirGenerator);
						break;
					case "WarpDrive:blockHAMachine":
						mapping.remap(blockHighlyAdvancedMachine);
						break;
					case "WarpDrive:boosterBlock":
						mapping.remap(blockLaserMedium);
						break;
					case "WarpDrive:cameraBlock":
						mapping.remap(blockCamera);
						break;
					case "WarpDrive:chunkLoader":
						mapping.remap(blockChunkLoader);
						break;
					case "WarpDrive:cloakBlock":
						mapping.remap(blockCloakingCore);
						break;
					case "WarpDrive:cloakCoilBlock":
						mapping.remap(blockCloakingCoil);
						break;
					case "WarpDrive:decorative":
						mapping.remap(blockDecorative);
						break;
					case "WarpDrive:gasBlock":
						mapping.remap(blockGas);
						break;
					case "WarpDrive:iridiumBlock":
						mapping.remap(blockIridium);
						break;
					case "WarpDrive:isolationBlock":
						mapping.remap(blockWarpIsolation);
						break;
					case "WarpDrive:laserBlock":
						mapping.remap(blockLaser);
						break;
					case "WarpDrive:laserCamBlock":
						mapping.remap(blockLaserCamera);
						break;
					case "WarpDrive:laserTreeFarmBlock":
						mapping.remap(blockLaserTreeFarm);
						break;
					case "WarpDrive:liftBlock":
						mapping.remap(blockLift);
						break;
					case "WarpDrive:miningLaserBlock":
						mapping.remap(blockMiningLaser);
						break;
					case "WarpDrive:monitorBlock":
						mapping.remap(blockMonitor);
						break;
					case "WarpDrive:powerLaser":
						mapping.remap(blockEnanReactorLaser);
						break;
					case "WarpDrive:powerReactor":
						mapping.remap(blockEnanReactorCore);
						break;
					case "WarpDrive:powerStore":
						mapping.remap(blockEnergyBank);
						break;
					case "WarpDrive:protocolBlock":
						mapping.remap(blockShipController);
						break;
					case "WarpDrive:radarBlock":
						mapping.remap(blockRadar);
						break;
					case "WarpDrive:reactorMonitor":
						mapping.remap(blockIC2reactorLaserMonitor);
						break;
					case "WarpDrive:scannerBlock":
						mapping.remap(blockShipScanner);
						break;
					case "WarpDrive:transportBeacon":
					case "WarpDrive:blockTransportBeacon":
						mapping.remap(blockTransporterBeacon);
						break;
					case "WarpDrive:transporter":
					case "WarpDrive:blockTransporter":
						mapping.remap(blockTransporterCore);
						break;
					case "WarpDrive:warpCore":
						mapping.remap(blockShipCore);
						break;
				}
			}
		}
	}
	
	/**
	 * Register a Block with the default ItemBlock class.
	 */
	public static <BLOCK extends Block> BLOCK register(final BLOCK block) {
		if (block instanceof IBlockBase) {
			return register(block, ((IBlockBase) block).createItemBlock());
		} else {
			return register(block, new ItemBlock(block));
		}
	}
	
	/**
	 * Register a Block with a custom ItemBlock class.
	 */
	public static <BLOCK extends Block> BLOCK register(final BLOCK block, @Nullable final ItemBlock itemBlock) {
		GameRegistry.register(block);
		
		if (itemBlock != null) {
			GameRegistry.register(itemBlock.setRegistryName(block.getRegistryName()));
		}
		
		// blocks.add(block);
		return block;
	}
}
