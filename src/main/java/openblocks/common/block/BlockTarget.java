package openblocks.common.block;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import openblocks.OpenBlocks;
import openblocks.common.tileentity.TileEntityTarget;
import openmods.Log;
import openmods.block.OpenBlock;
import openmods.fakeplayer.FakePlayerPool;
import openmods.fakeplayer.FakePlayerPool.PlayerUserReturning;
import openmods.fakeplayer.OpenModsFakePlayer;
import openmods.geometry.BlockSpaceTransform;
import openmods.geometry.Orientation;
import openmods.utils.BlockNotifyFlags;
import openmods.utils.BlockUtils;

public class BlockTarget extends OpenBlock.FourDirections {

	private static final AxisAlignedBB FOLDED_AABB = new AxisAlignedBB(0, 0, 0, 1.0f, 0.1f, 1.0f);
	private static final AxisAlignedBB DEPLOYED_AABB = new AxisAlignedBB(0.0, 0.0, 0.9, 1.0, 1.0, 1.0);
	private int lastEntityHit = 0;

	public static final IProperty<Boolean> POWERED = PropertyBool.create("powered");

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, new IProperty[] { getPropertyOrientation(), POWERED });
	}

	private static final int MASK_POWERED = 0x8;

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return super.getStateFromMeta(meta)
				.withProperty(POWERED, (meta & MASK_POWERED) != 0);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return super.getMetaFromState(state) | (state.getValue(POWERED)? MASK_POWERED : 0);
	}

	public BlockTarget() {
		super(Material.ROCK);
		setLightLevel(0.3f);
		setRequiresInitialization(true);
	}

	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	@Override
	public void onEntityCollidedWithBlock(World world, BlockPos pos, IBlockState state, Entity entity) {
		if (!world.isRemote && entity != null && entity instanceof EntityArrow) {
			/**
			 * onEntityCollidedWithBlock is called twice when the arrow is hit
			 * The first is from the raytracing, which is predictive and
			 * inaccurate The second is from the bounding box collision. We only
			 * care about the second one
			 */
			if (lastEntityHit != entity.getEntityId()) {
				lastEntityHit = entity.getEntityId();
				return;
			}
			lastEntityHit = entity.getEntityId();
			onTargetHit(world, pos, state, new Vec3d(entity.posX, entity.posY, entity.posZ));
		}
	}

	public void onTargetHit(World world, BlockPos pos, IBlockState state, Vec3d entityPosition) {
		if (world.isRemote) return;

		if (!state.getValue(POWERED)) return;

		EnumFacing opposite = getFront(state).getOpposite();

		double centerX = pos.getX() + 0.5 + (opposite.getFrontOffsetX() * 0.5);
		double centerY = pos.getY() + 0.55 + (opposite.getFrontOffsetY() * 0.45);
		double centerZ = pos.getZ() + 0.5 + (opposite.getFrontOffsetZ() * 0.5);

		final Vec3d bullseye = new Vec3d(centerX, centerY, centerZ);

		double distance = entityPosition.distanceTo(bullseye);

		final TileEntityTarget target = getTileEntity(world, pos, TileEntityTarget.class);
		target.setRedstoneStrength(15 - Math.min(15, Math.max(0, (int)(distance * 32))));

	}

	@Override
	public int getWeakPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		final TileEntityTarget tile = getTileEntity(blockAccess, pos, TileEntityTarget.class);
		return tile != null? tile.getRedstoneStrength() : 0;
	}

	@Override
	public int getStrongPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		// TODO Side-aware?
		return getWeakPower(blockState, blockAccess, pos, side);
	}

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		if (state.getValue(POWERED)) {
			final Orientation orientation = state.getValue(propertyOrientation);
			return BlockSpaceTransform.instance.mapBlockToWorld(orientation, DEPLOYED_AABB);
		} else {
			return FOLDED_AABB;
		}
	}

	@Override
	public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
		return isOnTopOfSolidBlock(world, pos, side);
	}

	@Override
	public void neighborChanged(IBlockState state, World world, BlockPos blockPos, Block neighbour) {
		updateRedstone(world, blockPos, state);
		super.neighborChanged(state, world, blockPos, neighbour);
	}

	@Override
	protected boolean onBlockAddedNextTick(World world, BlockPos blockPos, IBlockState state) {
		updateRedstone(world, blockPos, state);
		return super.onBlockAddedNextTick(world, blockPos, state);
	}

	private static void updateRedstone(World world, BlockPos blockPos, IBlockState state) {
		if (!(world instanceof WorldServer)) return;

		boolean isPowered = world.isBlockIndirectlyGettingPowered(blockPos) > 0;

		IBlockState newState = state.withProperty(POWERED, isPowered);
		if (state != newState) {
			dropArrowsAsItems((WorldServer)world, blockPos);
			BlockUtils.playSoundAtPos(world, blockPos, isPowered? OpenBlocks.Sounds.BLOCK_TARGET_OPEN : OpenBlocks.Sounds.BLOCK_TARGET_CLOSE, SoundCategory.BLOCKS, 0.5f, 1.0f);
			world.setBlockState(blockPos, newState, BlockNotifyFlags.ALL);
		}
	}

	private static void dropArrowsAsItems(WorldServer world, BlockPos pos) {
		final AxisAlignedBB aabb = BlockUtils.aabbOffset(pos, -0.2, -0.2, -0.2, +1.2, +1.2, +1.2);

		final List<EntityArrow> arrows = world.getEntitiesWithinAABB(EntityArrow.class, aabb);

		final List<ItemStack> drops = Lists.newArrayList();

		int failed = FakePlayerPool.instance.executeOnPlayer(world, new PlayerUserReturning<Integer>() {

			@Override
			public Integer usePlayer(OpenModsFakePlayer fakePlayer) {
				int failed = 0;

				for (EntityArrow arrow : arrows) {
					if (arrow.pickupStatus == EntityArrow.PickupStatus.CREATIVE_ONLY) {
						arrow.setDead();
					} else {
						try {
							arrow.onCollideWithPlayer(fakePlayer);
						} catch (Throwable t) {
							Log.warn(t, "Failed to collide arrow %s with fake player, returing vanilla one", arrow);
							failed++;
						}
					}
				}

				IInventory inventory = fakePlayer.inventory;
				for (int i = 0; i < inventory.getSizeInventory(); i++) {
					ItemStack stack = inventory.getStackInSlot(i);
					if (stack != null) {
						drops.add(stack);
						inventory.setInventorySlotContents(i, null);
					}
				}

				return failed;

			}
		});

		for (ItemStack drop : drops)
			BlockUtils.dropItemStackInWorld(world, pos, drop);

		if (failed > 0) BlockUtils.dropItemStackInWorld(world, pos, new ItemStack(Items.ARROW, failed));
	}
}
