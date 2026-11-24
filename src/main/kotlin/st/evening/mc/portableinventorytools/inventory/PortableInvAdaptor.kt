package st.evening.mc.portableinventorytools.inventory

import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.ICapabilityProvider
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.ServerSide

interface PortableInvAdaptor {
    companion object {
        val COMPARATOR: Comparator<PortableInvAdaptor> = compareByDescending { it.priority }
    }

    val adaptorId: ResourceLocation

    val priority: Int
        get() = 0

    fun checkItem(
        stack: ItemStack,
        parentCaps: ICapabilityProvider?,
        initCaps: Map<ResourceLocation, ICapabilityProvider>?
    ): EnumActionResult

    @ServerSide
    fun wrapServerSide(stack: ItemStack, state: PortableInvState): ServerPortableInvWrapper

    @ClientSide.Strong
    fun wrapClientSide(stack: ItemStack, state: PortableInvState): ClientPortableInvWrapper

    @ClientSide.Strong
    fun cloneClientState(srcWrapper: ClientPortableInvWrapper, dstWrapper: ClientPortableInvWrapper): Boolean

    object Noop : PortableInvAdaptor {
        override val adaptorId: ResourceLocation = PortableInvTools.resource("null")

        override val priority: Int
            get() = Int.MIN_VALUE

        override fun checkItem(
            stack: ItemStack,
            parentCaps: ICapabilityProvider?,
            initCaps: Map<ResourceLocation, ICapabilityProvider>?
        ): EnumActionResult = EnumActionResult.PASS

        @ServerSide
        override fun wrapServerSide(stack: ItemStack, state: PortableInvState): ServerPortableInvWrapper =
            NoopPortableInvWrapper

        @ClientSide.Strong
        override fun wrapClientSide(stack: ItemStack, state: PortableInvState): ClientPortableInvWrapper =
            NoopPortableInvWrapper

        @ClientSide.Strong
        override fun cloneClientState(
            srcWrapper: ClientPortableInvWrapper,
            dstWrapper: ClientPortableInvWrapper
        ): Boolean = true
    }
}
