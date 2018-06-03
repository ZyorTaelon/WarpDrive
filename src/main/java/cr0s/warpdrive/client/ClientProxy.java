package cr0s.warpdrive.client;

import cr0s.warpdrive.CommonProxy;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBlockBase;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.api.IItemBase;
import cr0s.warpdrive.data.EnumHullPlainType;
import cr0s.warpdrive.render.*;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {
	
	@Override
	public void onForgePreInitialisation() {
		super.onForgePreInitialisation();
		
		ModelLoaderRegistry.registerLoader(MyCustomModelLoader.INSTANCE);
		
		// blocks
		IModelInitialisation(WarpDrive.blockShipCore);
		IModelInitialisation(WarpDrive.blockShipController);
		IModelInitialisation(WarpDrive.blockRadar);
		IModelInitialisation(WarpDrive.blockWarpIsolation);
		
		IModelInitialisation(WarpDrive.blockAir);
		IModelInitialisation(WarpDrive.blockAirSource);
		IModelInitialisation(WarpDrive.blockAirFlow);
		IModelInitialisation(WarpDrive.blockAirShield);
		IModelInitialisation(WarpDrive.blockAirGenerator);
		for(int index = 0; index < 3; index++) {
			IModelInitialisation(WarpDrive.blockAirGeneratorTiered[index]);
		}
		
		IModelInitialisation(WarpDrive.blockLaser);
		IModelInitialisation(WarpDrive.blockLaserCamera);
		IModelInitialisation(WarpDrive.blockWeaponController);
		
		IModelInitialisation(WarpDrive.blockCamera);
		IModelInitialisation(WarpDrive.blockCloakingCore);
		IModelInitialisation(WarpDrive.blockCloakingCoil);
		IModelInitialisation(WarpDrive.blockMonitor);
		IModelInitialisation(WarpDrive.blockRadar);
		IModelInitialisation(WarpDrive.blockSiren);
		IModelInitialisation(WarpDrive.blockWarpIsolation);
		
		IModelInitialisation(WarpDrive.blockLaserMedium);
		IModelInitialisation(WarpDrive.blockMiningLaser);
		IModelInitialisation(WarpDrive.blockLaserTreeFarm);
		IModelInitialisation(WarpDrive.blockLift);
		IModelInitialisation(WarpDrive.blockShipScanner);
		IModelInitialisation(WarpDrive.blockTransporterBeacon);
		IModelInitialisation(WarpDrive.blockTransporterContainment);
		IModelInitialisation(WarpDrive.blockTransporterCore);
		IModelInitialisation(WarpDrive.blockTransporterScanner);
		IModelInitialisation(WarpDrive.blockBedrockGlass);
		if (WarpDriveConfig.isIndustrialCraft2Loaded) {
			IModelInitialisation(WarpDrive.blockIC2reactorLaserMonitor);
		}
		IModelInitialisation(WarpDrive.blockEnanReactorCore);
		IModelInitialisation(WarpDrive.blockEnanReactorLaser);
		IModelInitialisation(WarpDrive.blockEnergyBank);
		IModelInitialisation(WarpDrive.blockGas);
		IModelInitialisation(WarpDrive.blockIridium);
		IModelInitialisation(WarpDrive.blockLamp_bubble);
		IModelInitialisation(WarpDrive.blockLamp_flat);
		IModelInitialisation(WarpDrive.blockLamp_long);
		IModelInitialisation(WarpDrive.blockHighlyAdvancedMachine);
		IModelInitialisation(WarpDrive.blockChunkLoader);
		for (int index = 0; index < 3; index++) {
			IModelInitialisation(WarpDrive.blockForceFields[index]);
			IModelInitialisation(WarpDrive.blockForceFieldProjectors[index]);
			IModelInitialisation(WarpDrive.blockForceFieldRelays[index]);
		}
		
		IModelInitialisation(WarpDrive.blockAcceleratorController);
		IModelInitialisation(WarpDrive.blockAcceleratorControlPoint);
		IModelInitialisation(WarpDrive.blockParticlesCollider);
		IModelInitialisation(WarpDrive.blockParticlesInjector);
		IModelInitialisation(WarpDrive.blockVoidShellPlain);
		IModelInitialisation(WarpDrive.blockVoidShellGlass);
		for(byte tier = 1; tier <= 3; tier++) {
			int index = tier - 1;
			IModelInitialisation(WarpDrive.blockElectromagnetPlain[index]);
			IModelInitialisation(WarpDrive.blockElectromagnetGlass[index]);
			IModelInitialisation(WarpDrive.blockChillers[index]);
		}
		
		IModelInitialisation(WarpDrive.blockDecorative);
		
		for (int index = 0; index < 3; index++) {
			for (EnumHullPlainType enumHullPlainType : EnumHullPlainType.values()) {
				IModelInitialisation(WarpDrive.blockHulls_plain[index][enumHullPlainType.ordinal()]);
			}
			IModelInitialisation(WarpDrive.blockHulls_glass[index]);
			for (final EnumDyeColor enumDyeColor : EnumDyeColor.values()) {
				IModelInitialisation(WarpDrive.blockHulls_stairs[index][enumDyeColor.getMetadata()]);
				IModelInitialisation(WarpDrive.blockHulls_slab[index][enumDyeColor.getMetadata()]);
				// IModelInitialisation(WarpDrive.blockHulls_omnipanel[index][enumDyeColor.getMetadata()]);
			}
		}
		
		IModelInitialisation(WarpDrive.blockSiren);
		
		// items
		if (WarpDriveConfig.isIndustrialCraft2Loaded) {
			IModelInitialisation(WarpDrive.itemIC2reactorLaserFocus);
		}
		
		IModelInitialisation(WarpDrive.itemComponent);
		IModelInitialisation(WarpDrive.itemShipToken);
		
		IModelInitialisation(WarpDrive.itemWarpArmor[0]);
		IModelInitialisation(WarpDrive.itemWarpArmor[1]);
		IModelInitialisation(WarpDrive.itemWarpArmor[2]);
		IModelInitialisation(WarpDrive.itemWarpArmor[3]);
		
		IModelInitialisation(WarpDrive.itemAirTanks[0]);
		IModelInitialisation(WarpDrive.itemAirTanks[1]);
		IModelInitialisation(WarpDrive.itemAirTanks[2]);
		IModelInitialisation(WarpDrive.itemAirTanks[3]);
		
		IModelInitialisation(WarpDrive.itemTuningFork);
		IModelInitialisation(WarpDrive.itemTuningDriver);
		
		IModelInitialisation(WarpDrive.itemForceFieldShape);
		IModelInitialisation(WarpDrive.itemForceFieldUpgrade);
		
		IModelInitialisation(WarpDrive.itemElectromagneticCell);
		
		// creative tab
		WarpDrive.creativeTabWarpDrive.setBackgroundImageName("items.png");
		
		// generic rendering
		// MinecraftForge.EVENT_BUS.register(new WarpDriveKeyBindings());
		MinecraftForge.EVENT_BUS.register(new RenderOverlayAir());
		MinecraftForge.EVENT_BUS.register(new RenderOverlayCamera());
		MinecraftForge.EVENT_BUS.register(new RenderOverlayLocation());
		
		MinecraftForge.EVENT_BUS.register(new ClientCameraHandler());
		
		// entity rendering
		// RenderingRegistry.registerEntityRenderingHandler(EntityXXX.class, RenderXXX::new);
		// RenderingRegistry.registerEntityRenderingHandler(EntityParticleBunch.class, new RenderEntityParticleBunch());
		// @TODO MC1.10 force field rendering
		/*
		RenderBlockStandard.renderId = RenderingRegistry.getNextAvailableRenderId();
		RenderingRegistry.registerBlockHandler(RenderBlockStandard.instance);
		
		RenderBlockForceField.renderId = RenderingRegistry.getNextAvailableRenderId();
		RenderingRegistry.registerBlockHandler(RenderBlockForceField.instance);
		
		RenderBlockOmnipanel.renderId = RenderingRegistry.getNextAvailableRenderId();
		RenderingRegistry.registerBlockHandler(RenderBlockOmnipanel.instance);
		
		RenderBlockShipScanner.renderId = RenderingRegistry.getNextAvailableRenderId();
		RenderingRegistry.registerBlockHandler(RenderBlockShipScanner.instance);
		
		RenderBlockTransporterBeacon.renderId = RenderingRegistry.getNextAvailableRenderId();
		RenderingRegistry.registerBlockHandler(RenderBlockTransporterBeacon.instance);
		/**/
	}
	
	private static void IModelInitialisation(Object object) {
		if (object instanceof IBlockBase) {
			((IBlockBase) object).modelInitialisation();
		} else if (object instanceof Block) {
			Item item = Item.getItemFromBlock((Block) object);
			modelInitialisation(item);
		} else if (object instanceof Item) {
			modelInitialisation((Item) object);
		} else if (object == null) {
			WarpDrive.logger.info("Ignoring null object ModelInitialisation...");
		} else {
			throw new RuntimeException("Invalid object " + object);
		}
	}
	
	@Nonnull
	public static ModelResourceLocation getModelResourceLocation(final ItemStack itemStack) {
		final Item item = itemStack.getItem();
		ResourceLocation resourceLocation = item.getRegistryName();
		if (item.getHasSubtypes()) {
			resourceLocation = new ResourceLocation(resourceLocation.getResourceDomain(), resourceLocation.getResourcePath() + "-" + itemStack.getItemDamage());
		}
		return new ModelResourceLocation(resourceLocation, "inventory");
	}
	
	public static void modelInitialisation(Item item) {
		if (item == null) {
			throw new RuntimeException("Unable to PreInitialize a null item");
		} else if (!item.getHasSubtypes()) {
			ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
		} else {
			List<ItemStack> listItemStacks = new ArrayList<>(16);
			item.getSubItems(item, item.getCreativeTab(), listItemStacks);
			for (ItemStack itemStack : listItemStacks) {
				ModelResourceLocation modelResourceLocation; 
				if (item instanceof IItemBase) {
					modelResourceLocation = ((IItemBase) item).getModelResourceLocation(itemStack);
				} else {
					modelResourceLocation = getModelResourceLocation(itemStack);
				}
				ModelLoader.setCustomModelResourceLocation(item, itemStack.getMetadata(), modelResourceLocation);
			}
		}
	}
}