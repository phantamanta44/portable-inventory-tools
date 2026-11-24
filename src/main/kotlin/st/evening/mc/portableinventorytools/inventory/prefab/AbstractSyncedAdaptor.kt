package st.evening.mc.portableinventorytools.inventory.prefab

import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.ICapabilityProvider
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.PortableInvAdaptor
import st.evening.mc.prelude.api.util.game.ClientSide

abstract class AbstractSyncedAdaptor : PortableInvAdaptor {
    override fun checkItem(
        stack: ItemStack,
        parentCaps: ICapabilityProvider?,
        initCaps: Map<ResourceLocation, ICapabilityProvider>?
    ): EnumActionResult = EnumActionResult.SUCCESS

    @ClientSide.Strong
    override fun wrapClientSide(stack: ItemStack, state: PortableInvState): ClientPortableInvWrapper =
        ClientSyncedInventoryWrapper()

    @ClientSide.Strong
    override fun cloneClientState(srcWrapper: ClientPortableInvWrapper, dstWrapper: ClientPortableInvWrapper): Boolean {
        (dstWrapper as ClientSyncedInventoryWrapper).cloneStateFrom(srcWrapper as ClientSyncedInventoryWrapper)
        return true
    }
}
