package st.evening.mc.portableinventorytools.inventory.prefab

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.wrapper.EmptyHandler
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.InventoryAccessor
import st.evening.mc.prelude.api.util.game.ClientSide

@ClientSide
open class ClientItemHandlerWrapper(protected val handler: IItemHandler) :
    ClientPortableInvWrapper, InventoryAccessor.Client {
    companion object {
        fun fromItemHandlerItem(stack: ItemStack): ClientItemHandlerWrapper = ClientItemHandlerWrapper(stack)
    }

    // protected to prevent accidentally using this constructor for a non-item-handler item
    protected constructor(stack: ItemStack) :
        this(stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) ?: EmptyHandler.INSTANCE)

    override val slotCount: Int
        get() = handler.slots

    override fun getStackInSlot(slotIndex: Int): ItemStack = handler.getStackInSlot(slotIndex.mod(slotCount))

    override fun canInsert(slotIndex: Int, stack: ItemStack): Boolean =
        handler.insertItem(slotIndex.mod(slotCount), stack, true).count < stack.count

    override fun canExtract(slotIndex: Int): Boolean = !handler.extractItem(slotIndex.mod(slotCount), 1, true).isEmpty

    override fun getClientInventory(player: EntityPlayer): InventoryAccessor.Client = this
}
