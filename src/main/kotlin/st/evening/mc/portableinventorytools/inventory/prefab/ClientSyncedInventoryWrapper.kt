package st.evening.mc.portableinventorytools.inventory.prefab

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.InventoryAccessor
import st.evening.mc.prelude.api.util.collection.forEachInline
import st.evening.mc.prelude.api.util.collection.getCyclic
import st.evening.mc.prelude.api.util.game.ClientSide

@ClientSide
open class ClientSyncedInventoryWrapper : ClientPortableInvWrapper, InventoryAccessor.Client {
    protected var slots: Array<ItemStack> = emptyArray()

    override val slotCount: Int
        get() = slots.size

    override fun getStackInSlot(slotIndex: Int): ItemStack = slots.getCyclic(slotIndex)

    override fun getClientInventory(player: EntityPlayer): InventoryAccessor.Client = this

    override fun handleSyncSlots(slotTable: Int2ObjectMap<ItemStack>): Boolean {
        var didWork = false
        slotTable.forEachInline { slotIndex, stack ->
            if (slotIndex < 0 || slotIndex >= slots.size) return@forEachInline
            slots[slotIndex] = stack
            didWork = true
        }
        return didWork
    }

    override fun handleSyncFull(stacks: Array<ItemStack>): Boolean {
        slots = stacks
        return true
    }

    fun cloneStateFrom(src: InventoryAccessor) {
        slots = Array(src.slotCount) { src.getStackInSlot(it) }
    }
}
