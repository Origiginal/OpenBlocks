package openblocks.common.tileentity;

import com.google.common.collect.ImmutableMap;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.model.animation.Animation;
import net.minecraftforge.common.animation.ITimeValue;
import net.minecraftforge.common.animation.TimeValues.VariableValue;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.model.animation.CapabilityAnimation;
import net.minecraftforge.common.model.animation.IAnimationStateMachine;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import openblocks.OpenBlocks;
import openblocks.client.gui.GuiProjector;
import openblocks.common.HeightMapData;
import openblocks.common.MapDataManager;
import openblocks.common.block.BlockProjector;
import openblocks.common.container.ContainerProjector;
import openblocks.common.item.ItemEmptyMap;
import openblocks.common.item.ItemHeightMap;
import openblocks.rpc.IRotatable;
import openmods.OpenMods;
import openmods.api.IHasGui;
import openmods.include.IncludeInterface;
import openmods.inventory.GenericInventory;
import openmods.inventory.IInventoryProvider;
import openmods.inventory.TileEntityInventory;
import openmods.sync.ISyncListener;
import openmods.sync.ISyncableObject;
import openmods.sync.SyncMap;
import openmods.sync.SyncableByte;
import openmods.sync.SyncableInt;
import openmods.tileentity.SyncedTileEntity;
import openmods.utils.BlockNotifyFlags;
import openmods.utils.BlockUtils;

public class TileEntityProjector extends SyncedTileEntity implements IHasGui, IInventoryProvider, ISyncListener, IRotatable {

	private final GenericInventory inventory = new TileEntityInventory(this, "openblocks.projector", false, 1) {
		@Override
		public boolean isItemValidForSlot(int i, ItemStack stack) {
			if (stack == null) return false;
			Item item = stack.getItem();
			return item instanceof ItemHeightMap || item instanceof ItemEmptyMap;
		}

		@Override
		public int getInventoryStackLimit() {
			return 1;
		}

		@Override
		public void onInventoryChanged(int slotNumber) {
			super.onInventoryChanged(slotNumber);

			if (!isInvalid()) {
				if (!worldObj.isRemote) {
					ItemStack stack = getStackInSlot(slotNumber);
					if (stack != null && stack.stackSize == 1) {
						Item item = stack.getItem();
						if (item instanceof ItemHeightMap) {
							int mapId = stack.getItemDamage();
							TileEntityProjector.this.mapId.set(mapId);
						} else if (item instanceof ItemEmptyMap && worldObj != null) {
							ItemStack newStack = ItemEmptyMap.upgradeToMap(worldObj, stack);
							setInventorySlotContents(slotNumber, newStack);
						} else TileEntityProjector.this.mapId.set(-1);
					} else TileEntityProjector.this.mapId.set(-1);
					sync();

					final boolean isActive = TileEntityProjector.this.mapId() >= 0;
					final BlockPos pos = getPos();
					final IBlockState oldState = worldObj.getBlockState(pos);
					final IBlockState newState = oldState.withProperty(BlockProjector.ACTIVE, isActive);

					if (oldState != newState) {
						worldObj.setBlockState(pos, newState, BlockNotifyFlags.ALL);
					}
				}

				markUpdated();
			}
		}
	};

	private final IAnimationStateMachine asm;
	private final VariableValue lastChange = new VariableValue(Float.NEGATIVE_INFINITY);

	private SyncableByte rotation;
	private SyncableInt mapId;

	public TileEntityProjector() {
		this.asm = OpenMods.proxy.loadAsm(OpenBlocks.location("asms/block/projector.json"),
				ImmutableMap.<String, ITimeValue> of("last_change", lastChange));
	}

	@Override
	protected void onSyncMapCreate(SyncMap syncMap) {
		syncMap.addUpdateListener(this);
	}

	@Override
	protected void createSyncedFields() {
		rotation = new SyncableByte();
		mapId = new SyncableInt(-1);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		return BlockUtils.expandAround(pos, 1, 5, 1);
	}

	@Override
	public boolean shouldRenderInPass(int pass) {
		return pass == 0 || pass == 1;
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		inventory.readFromNBT(tag);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		tag = super.writeToNBT(tag);
		inventory.writeToNBT(tag);
		return tag;
	}

	@Override
	public Object getServerGui(EntityPlayer player) {
		return new ContainerProjector(player.inventory, this);
	}

	@Override
	public Object getClientGui(EntityPlayer player) {
		return new GuiProjector(new ContainerProjector(player.inventory, this));
	}

	@Override
	public boolean canOpenGui(EntityPlayer player) {
		return true;
	}

	@Override
	public void onSync(Set<ISyncableObject> changes) {
		if (changes.contains(mapId)) {
			int mapId = this.mapId.get();
			if (mapId >= 0 && MapDataManager.getMapData(worldObj, mapId).isEmpty()) MapDataManager.requestMapData(worldObj, mapId);
		}
	}

	@Override
	public void rotate(int delta) {
		int value = rotation.get() + delta;
		rotation.set((byte)(value & 0x3));
		sync();
	}

	public byte rotation() {
		return rotation.get();
	}

	public int mapId() {
		return mapId.get();
	}

	public HeightMapData getMap() {
		int mapId = this.mapId.get();
		if (worldObj == null || mapId < 0) return null;

		return MapDataManager.getMapData(worldObj, mapId);
	}

	public void markMapDirty() {
		int mapId = this.mapId.get();
		if (worldObj != null || mapId < 0) MapDataManager.instance.markDataUpdated(worldObj, mapId);
	}

	@Override
	@IncludeInterface
	public IInventory getInventory() {
		return inventory;
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing side) {
		return capability == CapabilityAnimation.ANIMATION_CAPABILITY ||
				capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ||
				super.hasCapability(capability, side);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getCapability(Capability<T> capability, EnumFacing side) {
		if (capability == CapabilityAnimation.ANIMATION_CAPABILITY)
			return (T)asm;

		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
			return (T)inventory.getHandler();

		return super.getCapability(capability, side);
	}

	@Override
	public void updateContainingBlockInfo() {
		super.updateContainingBlockInfo();
		updateAsmState();
	}

	@Override
	public void onLoad() {
		super.onLoad();
		updateAsmState();
	}

	private void updateAsmState() {
		if (asm != null && worldObj != null && worldObj.isRemote) {
			final IBlockState state = worldObj.getBlockState(this.pos);
			if (state.getValue(BlockProjector.ACTIVE)) {
				if (asm.currentState().equals("default")) {
					updateLastChangeTime();
					asm.transition("starting");
				}
			} else {
				if (asm.currentState().equals("moving")) {
					updateLastChangeTime();
					asm.transition("stopping");
				}
			}
		}
	}

	private void updateLastChangeTime() {
		lastChange.setValue(Animation.getWorldTime(getWorld(), Animation.getPartialTickTime()));
	}
}
