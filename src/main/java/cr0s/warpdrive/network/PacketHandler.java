package cr0s.warpdrive.network;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.CelestialObject;
import cr0s.warpdrive.data.CloakedArea;
import cr0s.warpdrive.data.GlobalPosition;
import cr0s.warpdrive.data.MovingEntity;
import cr0s.warpdrive.data.Vector3;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;

public class PacketHandler {
	
	private static final SimpleNetworkWrapper simpleNetworkManager = NetworkRegistry.INSTANCE.newSimpleChannel(WarpDrive.MODID);
	private static Method EntityTrackerEntry_getPacketForThisEntity;
	
	public static void init() {
		// Forge packets
		simpleNetworkManager.registerMessage(MessageBeamEffect.class          , MessageBeamEffect.class          , 0, Side.CLIENT);
		simpleNetworkManager.registerMessage(MessageClientSync.class          , MessageClientSync.class          , 2, Side.CLIENT);
		simpleNetworkManager.registerMessage(MessageCloak.class               , MessageCloak.class               , 3, Side.CLIENT);
		simpleNetworkManager.registerMessage(MessageSpawnParticle.class       , MessageSpawnParticle.class       , 4, Side.CLIENT);
		simpleNetworkManager.registerMessage(MessageVideoChannel.class        , MessageVideoChannel.class        , 5, Side.CLIENT);
		simpleNetworkManager.registerMessage(MessageTransporterEffect.class   , MessageTransporterEffect.class   , 6, Side.CLIENT);
		
		simpleNetworkManager.registerMessage(MessageTargeting.class           , MessageTargeting.class           , 100, Side.SERVER);
		simpleNetworkManager.registerMessage(MessageClientValidation.class    , MessageClientValidation.class    , 101, Side.SERVER);
		
		// Entity packets for 'uncloaking' entities
		try {
			EntityTrackerEntry_getPacketForThisEntity = ReflectionHelper.findMethod(
				EntityTrackerEntry.class, null, new String[] { "createSpawnPacket", "func_151260_c" } );
		} catch (final Exception exception) {
			throw new RuntimeException(exception);
		}
	}
	
	// Beam effect sent to client side
	public static void sendBeamPacket(final World world, final Vector3 v3Source, final Vector3 v3Target,
	                                  final float red, final float green, final float blue,
	                                  final int age, final int energy, final int radius) {
		assert(!world.isRemote);
		
		final MessageBeamEffect messageBeamEffect = new MessageBeamEffect(v3Source, v3Target, red, green, blue, age);
		
		// small beam are sent relative to beam center
		if (v3Source.distanceTo_square(v3Target) < 3600 /* 60 * 60 */) {
			simpleNetworkManager.sendToAllAround(messageBeamEffect, new TargetPoint(
					world.provider.getDimension(), (v3Source.x + v3Target.x) / 2, (v3Source.y + v3Target.y) / 2, (v3Source.z + v3Target.z) / 2, radius));
		} else {// large beam are sent from both ends
			final List<EntityPlayerMP> playerEntityList = world.getMinecraftServer().getPlayerList().getPlayerList();
			final int dimensionId = world.provider.getDimension();
			final int radius_square = radius * radius;
			for (int index = 0; index < playerEntityList.size(); index++) {
				final EntityPlayerMP entityPlayerMP = playerEntityList.get(index);
				
				if (entityPlayerMP.dimension == dimensionId) {
					if ( v3Source.distanceTo_square(entityPlayerMP) < radius_square
					  || v3Target.distanceTo_square(entityPlayerMP) < radius_square ) {
						simpleNetworkManager.sendTo(messageBeamEffect, entityPlayerMP);
					}
				}
			}
		}
	}
	
	public static void sendBeamPacketToPlayersInArea(final World world, final Vector3 source, final Vector3 target,
	                                                 final float red, final float green, final float blue,
	                                                 final int age, final AxisAlignedBB aabb) {
		assert(!world.isRemote);
		
		final MessageBeamEffect messageBeamEffect = new MessageBeamEffect(source, target, red, green, blue, age);
		// Send packet to all players within cloaked area
		final List<Entity> list = world.getEntitiesWithinAABB(EntityPlayerMP.class, aabb);
		for (final Entity entity : list) {
			if (entity instanceof EntityPlayerMP) {
				PacketHandler.simpleNetworkManager.sendTo(messageBeamEffect, (EntityPlayerMP) entity);
			}
		}
	}
	
	// Forced particle effect sent to client side
	public static void sendSpawnParticlePacket(final World world, final String type, final byte quantity,
	                                           final Vector3 origin, final Vector3 direction,
	                                           final float baseRed, final float baseGreen, final float baseBlue,
	                                           final float fadeRed, final float fadeGreen, final float fadeBlue,
	                                           final int radius) {
		assert(!world.isRemote);
		
		final MessageSpawnParticle messageSpawnParticle = new MessageSpawnParticle(
			type, quantity, origin, direction, baseRed, baseGreen, baseBlue, fadeRed, fadeGreen, fadeBlue);
		
		// small beam are sent relative to beam center
		simpleNetworkManager.sendToAllAround(messageSpawnParticle, new TargetPoint(
				world.provider.getDimension(), origin.x, origin.y, origin.z, radius));
		
		if (WarpDriveConfig.LOGGING_EFFECTS) {
			WarpDrive.logger.info(String.format("Sent particle effect '%s' x %d from %s toward %s as RGB %.2f %.2f %.2f fading to %.2f %.2f %.2f",
				type, quantity, origin, direction, baseRed, baseGreen, baseBlue, fadeRed, fadeGreen, fadeBlue));
		}
	}
	
	// Transporter effect sent to client side
	public static void sendTransporterEffectPacket(final World world, final GlobalPosition globalPositionLocal, final GlobalPosition globalPositionRemote, final double lockStrength,
	                                               final Collection<MovingEntity> movingEntitiesLocal, final Collection<MovingEntity> movingEntitiesRemote,
	                                               final int tickEnergizing, final int tickCooldown, final int radius) {
		assert(!world.isRemote);
		
		final MessageTransporterEffect messageTransporterEffectLocal = new MessageTransporterEffect(
				true, globalPositionLocal, movingEntitiesLocal,
				lockStrength, tickEnergizing, tickCooldown);
		final MessageTransporterEffect messageTransporterEffectRemote = new MessageTransporterEffect(
				false, globalPositionRemote, movingEntitiesRemote,
				lockStrength, tickEnergizing, tickCooldown);
		
		// check both ends to send packet
		final List<EntityPlayerMP> playerEntityList = world.getMinecraftServer().getPlayerList().getPlayerList();
		final int radius_square = radius * radius;
		for (int index = 0; index < playerEntityList.size(); index++) {
			final EntityPlayerMP entityPlayerMP = playerEntityList.get(index);
			
			if ( globalPositionLocal != null
			  && globalPositionLocal.distance2To(entityPlayerMP) < radius_square ) {
				simpleNetworkManager.sendTo(messageTransporterEffectLocal, entityPlayerMP);
			}
			if ( globalPositionRemote != null
			  && globalPositionRemote.distance2To(entityPlayerMP) < radius_square ) {
				simpleNetworkManager.sendTo(messageTransporterEffectRemote, entityPlayerMP);
			}
		}
	}
	
	// Monitor/Laser/Camera updating its video channel to client side
	public static void sendVideoChannelPacket(final int dimensionId, final BlockPos blockPos, final int videoChannel) {
		final MessageVideoChannel messageVideoChannel = new MessageVideoChannel(blockPos, videoChannel);
		simpleNetworkManager.sendToAllAround(messageVideoChannel, new TargetPoint(dimensionId, blockPos.getX(),blockPos.getY(), blockPos.getZ(), 100));
		if (WarpDriveConfig.LOGGING_VIDEO_CHANNEL) {
			WarpDrive.logger.info("Sent video channel packet (" + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ() + ") video channel " + videoChannel);
		}
	}
	
	// LaserCamera shooting at target (client -> server)
	public static void sendLaserTargetingPacket(final int x, final int y, final int z, final float yaw, final float pitch) {
		final MessageTargeting messageTargeting = new MessageTargeting(x, y, z, yaw, pitch);
		simpleNetworkManager.sendToServer(messageTargeting);
		if (WarpDriveConfig.LOGGING_TARGETING) {
			WarpDrive.logger.info("Sent targeting packet (" + x + " " + y + " " + z + ") yaw " + yaw + " pitch " + pitch);
		}
	}
	
	// Sending cloaking area definition (server -> client)
	public static void sendCloakPacket(final EntityPlayerMP entityPlayerMP, final CloakedArea area, final boolean decloak) {
		final MessageCloak messageCloak = new MessageCloak(area, decloak);
		simpleNetworkManager.sendTo(messageCloak, entityPlayerMP);
		if (WarpDriveConfig.LOGGING_CLOAKING) {
			WarpDrive.logger.info("Sent cloak packet (area " + area + " decloak " + decloak + ")");
		}
	}
	
	public static void sendClientSync(final EntityPlayerMP entityPlayerMP, final CelestialObject celestialObject) {
		if (WarpDriveConfig.LOGGING_CLIENT_SYNCHRONIZATION) {
			WarpDrive.logger.info(String.format("PacketHandler.sendClientSync %s", entityPlayerMP));
		}
		final MessageClientSync messageClientSync = new MessageClientSync(entityPlayerMP, celestialObject);
		simpleNetworkManager.sendTo(messageClientSync, entityPlayerMP);
	}
	
	public static Packet getPacketForThisEntity(final Entity entity) {
		final EntityTrackerEntry entry = new EntityTrackerEntry(entity, 0, 0, 0, false);
		try {
			return (Packet) EntityTrackerEntry_getPacketForThisEntity.invoke(entry);
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
		WarpDrive.logger.error(String.format("Unable to get packet for entity %s",
		                                     entity));
		return null;
	}
	
	public static void revealEntityToPlayer(final Entity entity, final EntityPlayerMP entityPlayerMP) {
		try {
			final Packet packet = getPacketForThisEntity(entity);
			if (packet == null) {
				WarpDrive.logger.error(String.format("Unable to reveal entity %s to player %s: null packet",
					entity, entityPlayerMP));
				return;
			}
			if (WarpDriveConfig.LOGGING_CLOAKING) {
				WarpDrive.logger.info("Revealing entity " + entity + " with packet " + packet);
			}
			entityPlayerMP.connection.sendPacket(packet);
			
			if (!entity.getDataManager().isEmpty()) {
				entityPlayerMP.connection.sendPacket(new SPacketEntityMetadata(entity.getEntityId(), entity.getDataManager(), true));
			}
			
			if (entity instanceof EntityLivingBase) {
				final AttributeMap attributemap = (AttributeMap) ((EntityLivingBase) entity).getAttributeMap();
				final Collection<IAttributeInstance> collection = attributemap.getWatchedAttributes();
				
				if (!collection.isEmpty()) {
					entityPlayerMP.connection.sendPacket(new SPacketEntityProperties(entity.getEntityId(), collection));
				}
				
				// if (((EntityLivingBase)this.trackedEntity).isElytraFlying()) ... (we always send velocity information)
			}
			
			if (!(packet instanceof SPacketSpawnMob)) {
				entityPlayerMP.connection.sendPacket(new SPacketEntityVelocity(entity.getEntityId(), entity.motionX, entity.motionY, entity.motionZ));
			}
			
			if (entity instanceof EntityLivingBase) {
				for (EntityEquipmentSlot entityequipmentslot : EntityEquipmentSlot.values()) {
					final ItemStack itemstack = ((EntityLivingBase) entity).getItemStackFromSlot(entityequipmentslot);
					
					if (itemstack != null) {
						entityPlayerMP.connection.sendPacket(new SPacketEntityEquipment(entity.getEntityId(), entityequipmentslot, itemstack));
					}
				}
			}
			
			if (entity instanceof EntityPlayer) {
				final EntityPlayer entityplayer = (EntityPlayer) entity;
				
				if (entityplayer.isPlayerSleeping()) {
					entityPlayerMP.connection.sendPacket(new SPacketUseBed(entityplayer, new BlockPos(entity)));
				}
			}
			
			if (entity instanceof EntityLivingBase) {
				final EntityLivingBase entitylivingbase = (EntityLivingBase) entity;
				
				for (final PotionEffect potioneffect : entitylivingbase.getActivePotionEffects()) {
					entityPlayerMP.connection.sendPacket(new SPacketEntityEffect(entity.getEntityId(), potioneffect));
				}
			}
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
	}
}