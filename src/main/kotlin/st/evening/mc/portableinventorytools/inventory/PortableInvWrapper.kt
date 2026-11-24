package st.evening.mc.portableinventorytools.inventory

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import st.evening.mc.portableinventorytools.util.TickableWorker
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.ServerSide

interface ServerPortableInvWrapper {
    @ServerSide
    val needsInventorySync: Boolean
        get() = false

    @ServerSide
    fun newInventoryWorker(context: InventoryContext): TickableWorker? = null

    @ServerSide
    fun getServerInventory(player: EntityPlayer): InventoryAccessor.Server
}

interface ClientPortableInvWrapper {
    @ClientSide
    fun getClientInventory(player: EntityPlayer): InventoryAccessor.Client

    @ClientSide
    fun handleSyncSlots(slotTable: Int2ObjectMap<ItemStack>): Boolean = false

    @ClientSide
    fun handleSyncFull(stacks: Array<ItemStack>): Boolean = false
}

object NoopPortableInvWrapper : ServerPortableInvWrapper, ClientPortableInvWrapper {
    @ServerSide
    override fun getServerInventory(player: EntityPlayer): InventoryAccessor.Server = InventoryAccessor.Server.Empty

    @ClientSide
    override fun getClientInventory(player: EntityPlayer): InventoryAccessor.Client = InventoryAccessor.Client.Empty
}
