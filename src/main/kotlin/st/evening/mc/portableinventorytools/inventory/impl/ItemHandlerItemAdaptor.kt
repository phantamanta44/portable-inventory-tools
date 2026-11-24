package st.evening.mc.portableinventorytools.inventory.impl

import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.items.CapabilityItemHandler
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.PortableInvAdaptor
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.prefab.ClientItemHandlerWrapper
import st.evening.mc.portableinventorytools.inventory.prefab.ServerItemHandlerWrapper
import st.evening.mc.portableinventorytools.util.PortableInvHelper
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.ServerSide

object ItemHandlerItemAdaptor : PortableInvAdaptor {
    override val adaptorId: ResourceLocation = PortableInvTools.resource("item_handler_item")

    override val priority: Int
        get() = -100

    override fun checkItem(
        stack: ItemStack,
        parentCaps: ICapabilityProvider?,
        initCaps: Map<ResourceLocation, ICapabilityProvider>?
    ): EnumActionResult = if (
        PortableInvHelper.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, stack, parentCaps, initCaps)
    ) {
        EnumActionResult.SUCCESS
    } else {
        EnumActionResult.PASS
    }

    // we assume the server is sending enough information for the client item handler instance to be fully accurate;
    // otherwise, the client would not have enough information to reconstruct the stack for creative mode c2s sync
    // if this is not the case for a particular item handler item, then that item will probably need a custom adaptor
    // (notable items broken in this way include the botania flower bag and the immersive engineering toolbox)
    @ServerSide
    override fun wrapServerSide(stack: ItemStack, state: PortableInvState): ServerPortableInvWrapper =
        ServerItemHandlerWrapper.fromItemHandlerStack(stack, false)

    @ClientSide.Strong
    override fun wrapClientSide(stack: ItemStack, state: PortableInvState): ClientPortableInvWrapper =
        ClientItemHandlerWrapper.fromItemHandlerItem(stack)

    @ClientSide.Strong
    override fun cloneClientState(srcWrapper: ClientPortableInvWrapper, dstWrapper: ClientPortableInvWrapper): Boolean {
        // no state of ours to clone here; in theory, the mod owning the item handler item should've already handled it
        return true
    }
}
