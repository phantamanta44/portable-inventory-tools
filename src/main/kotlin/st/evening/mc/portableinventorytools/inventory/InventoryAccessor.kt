package st.evening.mc.portableinventorytools.inventory

import net.minecraft.item.ItemStack

interface InventoryAccessor {
    val slotCount: Int

    fun getStackInSlot(slotIndex: Int): ItemStack

    interface Server : InventoryAccessor {
        fun insertItems(stack: ItemStack, prefSlot: Int): ItemStack

        fun extractItems(slotIndex: Int, amount: Int): ItemStack

        object Empty : Server {
            override val slotCount: Int
                get() = 0

            override fun getStackInSlot(slotIndex: Int): ItemStack =
                throw IndexOutOfBoundsException(slotIndex.toString())

            override fun insertItems(stack: ItemStack, prefSlot: Int): ItemStack = stack

            override fun extractItems(slotIndex: Int, amount: Int): ItemStack = ItemStack.EMPTY
        }
    }

    interface Client : InventoryAccessor {
        val canInsertInventory: Boolean
            get() = false

        fun canInsert(slotIndex: Int, stack: ItemStack): Boolean = true

        fun canExtract(slotIndex: Int): Boolean = true

        object Empty : Client {
            override val slotCount: Int
                get() = 0

            override fun getStackInSlot(slotIndex: Int): ItemStack =
                throw IndexOutOfBoundsException(slotIndex.toString())

            override fun canInsert(slotIndex: Int, stack: ItemStack): Boolean =
                throw IndexOutOfBoundsException(slotIndex.toString())

            override fun canExtract(slotIndex: Int): Boolean =
                throw IndexOutOfBoundsException(slotIndex.toString())
        }
    }
}
