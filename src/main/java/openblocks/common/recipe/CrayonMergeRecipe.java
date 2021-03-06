package openblocks.common.recipe;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import openblocks.OpenBlocks;
import openblocks.common.item.ItemImaginary;
import openblocks.common.item.ItemImaginary.PlacementMode;
import openmods.utils.OptionalInt;

public class CrayonMergeRecipe implements IRecipe {

	@Override
	public boolean matches(InventoryCrafting inv, World worldIn) {
		OptionalInt color = OptionalInt.ABSENT;
		OptionalInt meta = OptionalInt.ABSENT;
		int count = 0;

		for (int i = 0; i < inv.getSizeInventory(); i++) {
			final ItemStack stack = inv.getStackInSlot(i);
			if (stack == null) continue;
			if (!(stack.getItem() instanceof ItemImaginary)) return false;

			final int stackMeta = stack.getMetadata();
			if (meta.isPresent()) {
				if (meta.get() != stackMeta) return false;
			} else {
				meta = OptionalInt.of(stackMeta);
			}

			final NBTTagCompound tag = stack.getTagCompound();
			if (tag != null) {
				final Integer stackColor = ItemImaginary.getColor(tag);
				if (stackColor != null) {
					if (color.isPresent()) {
						if (color.get() != stackColor) return false;
					} else {
						color = OptionalInt.of(stackColor);
					}
				}
			}

			count++;
		}

		return count >= 2;
	}

	@Override
	public ItemStack getCraftingResult(InventoryCrafting inv) {
		OptionalInt color = OptionalInt.ABSENT;
		OptionalInt meta = OptionalInt.ABSENT;
		float uses = 0;

		for (int i = 0; i < inv.getSizeInventory(); i++) {
			final ItemStack stack = inv.getStackInSlot(i);
			if (stack == null) continue;
			if (!(stack.getItem() instanceof ItemImaginary)) return null;

			final int stackMeta = stack.getMetadata();
			if (meta.isPresent()) {
				if (meta.get() != stackMeta) return null;
			} else {
				meta = OptionalInt.of(stackMeta);
			}

			final Integer stackColor = ItemImaginary.getColor(stack);
			if (stackColor != null) {
				if (color.isPresent()) {
					if (color.get() != stackColor) return null;
				} else {
					color = OptionalInt.of(stackColor);
				}
			}

			uses += ItemImaginary.getUses(stack);
		}

		if (!meta.isPresent() || uses == 0) return null;
		return ItemImaginary.setupValues(new ItemStack(OpenBlocks.Blocks.imaginary, 1, meta.get()), color.asNullable(), PlacementMode.BLOCK, uses);
	}

	@Override
	public int getRecipeSize() {
		return 9;
	}

	@Override
	public ItemStack getRecipeOutput() {
		return null;
	}

	@Override
	public ItemStack[] getRemainingItems(InventoryCrafting inv) {
		// no remaining items - all is consumed
		return new ItemStack[inv.getSizeInventory()];
	}

}
