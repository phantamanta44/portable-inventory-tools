package st.evening.mc.portableinventorytools.inventory.prefab

import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.ICapabilityProvider
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.PortableInvAdaptor
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.ServerSide

open class RejectAdaptor(override val adaptorId: ResourceLocation, override val priority: Int = -10) :
    PortableInvAdaptor {

    override fun checkItem(
        stack: ItemStack,
        parentCaps: ICapabilityProvider?,
        initCaps: Map<ResourceLocation, ICapabilityProvider>?
    ): EnumActionResult = EnumActionResult.FAIL

    @ServerSide
    final override fun wrapServerSide(stack: ItemStack, state: PortableInvState): ServerPortableInvWrapper =
        throw UnsupportedOperationException()

    @ClientSide.Strong
    final override fun wrapClientSide(stack: ItemStack, state: PortableInvState): ClientPortableInvWrapper =
        throw UnsupportedOperationException()

    @ClientSide.Strong
    final override fun cloneClientState(
        srcWrapper: ClientPortableInvWrapper,
        dstWrapper: ClientPortableInvWrapper
    ): Boolean = throw UnsupportedOperationException()
}
