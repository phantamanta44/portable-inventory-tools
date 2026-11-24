package st.evening.mc.portableinventorytools.inventory.prefab

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.prelude.api.util.game.ServerSide

open class SyncedItemHandlerItemAdaptor(override val adaptorId: ResourceLocation) : AbstractSyncedAdaptor() {
    @ServerSide
    override fun wrapServerSide(stack: ItemStack, state: PortableInvState): ServerPortableInvWrapper =
        ServerItemHandlerWrapper.fromItemHandlerStack(stack, true)
}
