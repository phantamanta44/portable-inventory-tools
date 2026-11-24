package st.evening.mc.portableinventorytools.util

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.items.IItemHandler
import st.evening.mc.prelude.api.util.game.forEachSlot
import st.evening.mc.prelude.api.util.game.stacksWith

object PortableInvHelper {
    fun hasCapability(
        capability: Capability<*>,
        stack: ItemStack,
        parentCaps: ICapabilityProvider?,
        initCaps: Map<ResourceLocation, ICapabilityProvider>?
    ): Boolean = stack.hasCapability(capability, null) ||
        (parentCaps != null && parentCaps.hasCapability(capability, null)) ||
        (initCaps != null && initCaps.values.any { it.hasCapability(capability, null) })

    fun insertItem(inv: IItemHandler, stack: ItemStack, prefSlot: Int): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        var rem = stack
        if (prefSlot >= 0) {
            rem = inv.insertItem(prefSlot, rem, false)
            if (rem.isEmpty) return ItemStack.EMPTY
        }
        return insertItemImpl(inv, rem, false)
    }

    fun insertItemStacked(inv: IItemHandler, stack: ItemStack, prefSlot: Int): ItemStack {
        if (!stack.isStackable) return insertItem(inv, stack, prefSlot)
        if (stack.isEmpty) return ItemStack.EMPTY
        var rem = stack
        if (prefSlot >= 0) {
            rem = inv.insertItem(prefSlot, rem, false)
            if (rem.isEmpty) return ItemStack.EMPTY
        }
        rem = insertItemImpl(inv, rem, true)
        if (rem.isEmpty) return ItemStack.EMPTY
        return insertItemImpl(inv, rem, false)
    }

    private fun insertItemImpl(inv: IItemHandler, stack: ItemStack, stackedOnly: Boolean): ItemStack {
        var rem = stack
        inv.forEachSlot { i ->
            if (stackedOnly && !inv.getStackInSlot(i).stacksWith(stack)) return@forEachSlot
            rem = inv.insertItem(i, rem, false)
            if (rem.isEmpty) return ItemStack.EMPTY
        }
        return rem
    }
}
