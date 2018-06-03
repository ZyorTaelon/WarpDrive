package cr0s.warpdrive.event;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.config.Dictionary;
import cr0s.warpdrive.config.WarpDriveConfig;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemArmor.ArmorMaterial;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;

public class ClientHandler {
	
	private boolean isSneaking;
	private boolean isCreativeMode;
	
	@SuppressWarnings("ConstantConditions") // getBlockFromItem() might return null, by design
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onTooltipEvent(final ItemTooltipEvent event) {
		if (event.getEntityPlayer() == null) {
			return;
		}
		if (Dictionary.ITEMS_BREATHING_HELMET.contains(event.getItemStack().getItem())) {
			Commons.addTooltip(event.getToolTip(), new TextComponentTranslation("warpdrive.tooltip.item_tag.breathing_helmet").getFormattedText());
		}
		if (Dictionary.ITEMS_FLYINSPACE.contains(event.getItemStack().getItem())) {
			Commons.addTooltip(event.getToolTip(), new TextComponentTranslation("warpdrive.tooltip.item_tag.flyInSpace").getFormattedText());
		}
		if (Dictionary.ITEMS_NOFALLDAMAGE.contains(event.getItemStack().getItem())) {
			Commons.addTooltip(event.getToolTip(), new TextComponentTranslation("warpdrive.tooltip.item_tag.noFallDamage").getFormattedText());
		}
		
		isSneaking = event.getEntityPlayer().isSneaking();
		isCreativeMode = event.getEntityPlayer().capabilities.isCreativeMode;
		
		// add block/items details
		final Block block = Block.getBlockFromItem(event.getItemStack().getItem());
		if (block != null) {
			addBlockDetails(event, block);
		} else {
			addItemDetails(event, event.getItemStack().getItem());
		}
		
		// add burn time details (vanilla only register server side?)
		if (WarpDriveConfig.CLIENT_TOOLTIP_BURN_TIME.isEnabled(isSneaking, isCreativeMode)) {
			final int fuelValue = GameRegistry.getFuelValue(event.getItemStack());
			if (fuelValue > 0) {
				Commons.addTooltip(event.getToolTip(), String.format("Burn time is %d (%.1f ores)", fuelValue, fuelValue / 200.0F));
			}
		}
		
		// add ore dictionary names
		if (WarpDriveConfig.CLIENT_TOOLTIP_ORE_DICTIONARY_NAME.isEnabled(isSneaking, isCreativeMode)) {
			final int[] idOres = OreDictionary.getOreIDs(event.getItemStack());
			if (idOres.length != 0) {
				Commons.addTooltip(event.getToolTip(), "Ore dictionary names:");
				for (final int idOre : idOres) {
					final String nameOre = OreDictionary.getOreName(idOre);
					Commons.addTooltip(event.getToolTip(), "- " + nameOre);
				}
			}
		}
	}
	
	public void addBlockDetails(final ItemTooltipEvent event, final Block block) {
		// registry name
		if (WarpDriveConfig.CLIENT_TOOLTIP_REGISTRY_NAME.isEnabled(isSneaking, isCreativeMode)) {
			try {
				final ResourceLocation resourceLocation = Block.REGISTRY.getNameForObject(block);
				if (resourceLocation != null) {
					Commons.addTooltip(event.getToolTip(), "" + resourceLocation + "");
				}
			} catch (final Exception exception) {
				// no operation
			}
		}
		
		// tool related stats
		IBlockState blockState = null;
		try {
			blockState = block.getStateFromMeta(event.getItemStack().getItemDamage());
		} catch (AssertionError assertionError) {
			// assertionError.printStackTrace();
			WarpDrive.logger.error(String.format("Assertion error on item stack %s with state %s",
			                                     event.getItemStack(), (blockState != null) ? blockState : "-null-"));
		}
		if ( WarpDriveConfig.CLIENT_TOOLTIP_HARVESTING.isEnabled(isSneaking, isCreativeMode)
		  && blockState != null ) {
			try {
				final String harvestTool = block.getHarvestTool(blockState);
				if (harvestTool != null) {
					Commons.addTooltip(event.getToolTip(), String.format("Harvest with %s (%d)",
					                                                     harvestTool, 
					                                                     block.getHarvestLevel(blockState)));
				}
			} catch (final Exception exception) {
				// no operation
			}
		}
		
		// generic properties
		if ( WarpDriveConfig.CLIENT_TOOLTIP_OPACITY.isEnabled(isSneaking, isCreativeMode)
		  && blockState != null ) {
			try {
				Commons.addTooltip(event.getToolTip(), String.format("Light opacity is %d", block.getLightOpacity(blockState)));
			} catch (Exception exception) {
				// no operation
			}
		}
		
		if (WarpDriveConfig.CLIENT_TOOLTIP_HARDNESS.isEnabled(isSneaking, isCreativeMode)) {
			try {
				Commons.addTooltip(event.getToolTip(), String.format("Hardness is %.1f", (float) WarpDrive.fieldBlockHardness.get(block)));
			} catch (final Exception exception) {
				// no operation
			}
			Commons.addTooltip(event.getToolTip(), String.format("Explosion resistance is %.1f", + block.getExplosionResistance(null)));
		}
		
		// flammability
		if (WarpDriveConfig.CLIENT_TOOLTIP_FLAMMABILITY.isEnabled(isSneaking, isCreativeMode)) {
			try {
				final int flammability = Blocks.FIRE.getFlammability(block);
				final int fireSpread = Blocks.FIRE.getEncouragement(block);
				if (flammability > 0) {
					Commons.addTooltip(event.getToolTip(), String.format("Flammable: %d, spread %d", flammability, fireSpread));
				}
			} catch (final Exception exception) {
				// no operation
			}
		}
		
		// fluid stats
		if (WarpDriveConfig.CLIENT_TOOLTIP_FLUID.isEnabled(isSneaking, isCreativeMode)) {
			try {
				final Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
				if (fluid != null) {
					if (fluid.isGaseous()) {
						Commons.addTooltip(event.getToolTip(), String.format("Gaz viscosity is %d", fluid.getViscosity()));
						Commons.addTooltip(event.getToolTip(), String.format("Gaz density is %d", fluid.getDensity()));
					} else {
						Commons.addTooltip(event.getToolTip(), String.format("Liquid viscosity is %d", fluid.getViscosity()));
						Commons.addTooltip(event.getToolTip(), String.format("Liquid density is %d", fluid.getDensity()));
					}
					Commons.addTooltip(event.getToolTip(), String.format("Temperature is %d K", fluid.getTemperature()));
				}
			} catch (final Exception exception) {
				// no operation
			}
		}
	}
	
	public void addItemDetails(final ItemTooltipEvent event, final Item item) {
		// registry name
		if (WarpDriveConfig.CLIENT_TOOLTIP_REGISTRY_NAME.isEnabled(isSneaking, isCreativeMode)) {
			try {
				final ResourceLocation resourceLocation = Item.REGISTRY.getNameForObject(item);
				if (resourceLocation != null) {
					Commons.addTooltip(event.getToolTip(), "" + resourceLocation + "");
				}
			} catch (final Exception exception) {
				// no operation
			}
		}
		
		// (item duration can't be directly understood => out)
		
		// durability
		if (WarpDriveConfig.CLIENT_TOOLTIP_DURABILITY.isEnabled(isSneaking, isCreativeMode)) {
			try {
				if (event.getItemStack().isItemStackDamageable()) {
					Commons.addTooltip(event.getToolTip(), String.format("Durability: %d / %d",
					                                                event.getItemStack().getMaxDamage() - event.getItemStack().getItemDamage(),
					                                                event.getItemStack().getMaxDamage()));
				}
			} catch (final Exception exception) {
				// no operation
			}
		}
		
		// armor stats
		if (WarpDriveConfig.CLIENT_TOOLTIP_ARMOR.isEnabled(isSneaking, isCreativeMode)) {
			try {
				if (item instanceof ItemArmor) {
					Commons.addTooltip(event.getToolTip(), String.format("Armor points: %d",
					                                                ((ItemArmor) item).damageReduceAmount));
					final ArmorMaterial armorMaterial = ((ItemArmor) item).getArmorMaterial();
					Commons.addTooltip(event.getToolTip(), String.format("Enchantability: %d",
					                                                armorMaterial.getEnchantability()));
					
					if (WarpDriveConfig.CLIENT_TOOLTIP_REPAIR_WITH.isEnabled(isSneaking, isCreativeMode)) {
						final Item itemRepair = armorMaterial.getRepairItem();
						if (itemRepair != null) {
							Commons.addTooltip(event.getToolTip(), String.format("Repair with %s",
							                                                armorMaterial.getRepairItem().getUnlocalizedName()));
						}
					}
				}
			} catch (final Exception exception) {
				// no operation
			}
		}
	}
}
