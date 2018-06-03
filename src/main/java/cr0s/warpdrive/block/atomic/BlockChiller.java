package cr0s.warpdrive.block.atomic;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.data.SoundEvents;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.network.PacketHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockChiller extends BlockAbstractAccelerator {
	
	private static final float BOUNDING_TOLERANCE = 0.05F;
	
	public BlockChiller(final String registryName, final byte tier) {
		super(registryName, tier);
		setUnlocalizedName("warpdrive.atomic.chiller" + tier);
	}
	
	@Override
	public int damageDropped(final IBlockState blockState) {
		return 0;
	}
	
	@SuppressWarnings("deprecation")
	@Nullable
	@Override
	public AxisAlignedBB getCollisionBoundingBox(final IBlockState blockState, @Nonnull final World world, @Nonnull final BlockPos blockPos) {
		return new AxisAlignedBB(
			blockPos.getX() + BOUNDING_TOLERANCE, blockPos.getY() + BOUNDING_TOLERANCE, blockPos.getZ() + BOUNDING_TOLERANCE,
			blockPos.getX() + 1 - BOUNDING_TOLERANCE, blockPos.getY() + 1 - BOUNDING_TOLERANCE, blockPos.getZ() + 1 - BOUNDING_TOLERANCE);
	}
	
	@Override
	public void onEntityCollidedWithBlock(final World world, final BlockPos blockPos, final IBlockState blockState, final Entity entity) {
		super.onEntityCollidedWithBlock(world, blockPos, blockState, entity);
		if (world.isRemote) {
			return;
		}
		
		onEntityEffect(world, blockPos, entity);
	}
	
	@Override
	public void onEntityWalk(World world, BlockPos blockPos, Entity entity) {
		super.onEntityWalk(world, blockPos, entity);
		if (world.isRemote) {
			return;
		}
		
		onEntityEffect(world, blockPos, entity);
	}
	
	@Override
	public void onBlockClicked(final World world, final BlockPos blockPos, final EntityPlayer entityPlayer) {
		super.onBlockClicked(world, blockPos, entityPlayer);
		if (world.isRemote) {
			return;
		}
		
		onEntityEffect(world, blockPos, entityPlayer);
	}
	
	private void onEntityEffect(final World world, final BlockPos blockPos, final Entity entity) {
		if (entity.isDead || !(entity instanceof EntityLivingBase)) {
			return;
		}
		if (world.getBlockState(blockPos) == null) { // @TODO: add proper state handling == 0) {
			return;
		}
		if (!entity.isImmuneToFire()) {
			entity.setFire(1);
		}
		entity.attackEntityFrom(WarpDrive.damageWarm, 1 + tier);
		
		final Vector3 v3Entity = new Vector3(entity);
		final Vector3 v3Chiller = new Vector3(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D);
		final Vector3 v3Direction = new Vector3(entity).subtract(v3Chiller).normalize();
		v3Chiller.translateFactor(v3Direction, 0.6D);
		v3Entity.translateFactor(v3Direction, -0.6D);
		
		// visual effect
		v3Direction.scale(0.20D);
		PacketHandler.sendSpawnParticlePacket(world, "snowshovel", (byte) 5, v3Entity, v3Direction,
			0.90F + 0.10F * world.rand.nextFloat(), 0.35F + 0.25F * world.rand.nextFloat(), 0.30F + 0.15F * world.rand.nextFloat(),
			0.0F, 0.0F, 0.0F, 32);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void randomDisplayTick(final IBlockState blockState, final World world, final BlockPos blockPos, final Random random) {
		final int metadata = this.getMetaFromState(world.getBlockState(blockPos)); // @TODO MC1.10
		if (metadata == 0) {
			return;
		}
		
		// sound effect
		final int countNearby = 17
		                - (world.getBlockState(blockPos.east()  ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.west()  ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.north() ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.south() ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.east(2) ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.west(2) ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.north(2)).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.south(2)).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.up(2).east()   ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.up(2).west()   ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.up(2).north()  ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.up(2).south()  ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.down(2).east() ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.down(2).west() ).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.down(2).north()).getBlock() == this ? 1 : 0)
		                - (world.getBlockState(blockPos.down(2).south()).getBlock() == this ? 1 : 0);
		if (world.rand.nextInt(17) < countNearby) {
			world.playSound(null, blockPos,
				SoundEvents.CHILLER, SoundCategory.AMBIENT, metadata == 1 ? 1.0F : 0.15F, 1.0F);
		}
		
		// particle effect, loosely based on redstone ore
		if (world.rand.nextInt(8) != 1) {
			final double dOffset = 0.0625D;
			
			for (int l = 0; l < 6; ++l) {
				double dX = (double) ((float) blockPos.getX() + random.nextFloat());
				double dY = (double) ((float) blockPos.getY() + random.nextFloat());
				double dZ = (double) ((float) blockPos.getZ() + random.nextFloat());
				boolean isValidSide = false;
				
				if (l == 0 && !world.getBlockState(blockPos.up()).isOpaqueCube()) {
					dY = blockPos.getY() + 1.0D + dOffset;
					isValidSide = true;
				}
				
				if (l == 1 && !world.getBlockState(blockPos.down()).isOpaqueCube()) {
					dY = blockPos.getY() - dOffset;
					isValidSide = true;
				}
				
				if (l == 2 && !world.getBlockState(blockPos.south()).isOpaqueCube()) {
					dZ = blockPos.getZ() + 1.0D + dOffset;
					isValidSide = true;
				}
				
				if (l == 3 && !world.getBlockState(blockPos.north()).isOpaqueCube()) {
					dZ = blockPos.getZ() - dOffset;
					isValidSide = true;
				}
				
				if (l == 4 && !world.getBlockState(blockPos.east()).isOpaqueCube()) {
					dX = blockPos.getX() + 1.0D + dOffset;
					isValidSide = true;
				}
				
				if (l == 5 && !world.getBlockState(blockPos.west()).isOpaqueCube()) {
					dX = blockPos.getX() - dOffset;
					isValidSide = true;
				}
				
				if (isValidSide) {
					world.spawnParticle(EnumParticleTypes.REDSTONE, dX, dY, dZ, 0.0D, 0.0D, 0.0D);
				}
			}
		}
	}
	
}
