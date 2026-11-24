package st.evening.mc.portableinventorytools.inventory.prefab

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.wrapper.EmptyHandler
import st.evening.mc.portableinventorytools.inventory.InventoryAccessor
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.portableinventorytools.util.PortableInvHelper
import st.evening.mc.prelude.api.util.game.ServerSide

@ServerSide
open class ServerItemHandlerWrapper(
    protected val handler: IItemHandler,
    override val needsInventorySync: Boolean
) : ServerPortableInvWrapper, InventoryAccessor.Server {
    companion object {
        fun fromItemHandlerStack(stack: ItemStack, needsInventorySync: Boolean): ServerItemHandlerWrapper =
            ServerItemHandlerWrapper(stack, needsInventorySync)
    }

    // protected to prevent accidentally using this constructor for a non-item-handler item
    protected constructor(stack: ItemStack, needsInventorySync: Boolean) : this(
        stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) ?: EmptyHandler.INSTANCE,
        needsInventorySync
    )

    override val slotCount: Int
        get() = handler.slots

    override fun getStackInSlot(slotIndex: Int): ItemStack = handler.getStackInSlot(slotIndex.mod(slotCount))

    override fun insertItems(stack: ItemStack, prefSlot: Int): ItemStack =
        PortableInvHelper.insertItemStacked(handler, stack.copy(), prefSlot)

    override fun extractItems(slotIndex: Int, amount: Int): ItemStack =
        handler.extractItem(slotIndex.mod(slotCount), amount, false)

    override fun getServerInventory(player: EntityPlayer): InventoryAccessor.Server = this
}
