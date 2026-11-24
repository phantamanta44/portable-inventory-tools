package st.evening.mc.portableinventorytools.compat.cofh

import cofh.core.gui.container.InventoryContainerItemWrapper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.items.wrapper.InvWrapper
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.InventoryAccessor
import st.evening.mc.portableinventorytools.inventory.PortableInvAdaptor
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.prefab.ClientItemHandlerWrapper
import st.evening.mc.portableinventorytools.inventory.prefab.ServerItemHandlerWrapper
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.ServerSide

object ThermalExpansionSatchelAdaptor : PortableInvAdaptor {
    override val adaptorId: ResourceLocation = PortableInvTools.resource("${ThermalExpansionCompat.MOD_ID}_satchel")

    override fun checkItem(
        stack: ItemStack,
        parentCaps: ICapabilityProvider?,
        initCaps: Map<ResourceLocation, ICapabilityProvider>?
    ): EnumActionResult = EnumActionResult.SUCCESS

    @ServerSide
    override fun wrapServerSide(stack: ItemStack, state: PortableInvState): ServerPortableInvWrapper = Wrapper(stack)

    @ClientSide.Strong
    override fun wrapClientSide(stack: ItemStack, state: PortableInvState): ClientPortableInvWrapper = Wrapper(stack)

    @ClientSide.Strong
    override fun cloneClientState(srcWrapper: ClientPortableInvWrapper, dstWrapper: ClientPortableInvWrapper): Boolean =
        true

    private class Wrapper(private val stack: ItemStack) : ServerPortableInvWrapper, ClientPortableInvWrapper {
        @ServerSide
        override fun getServerInventory(player: EntityPlayer): InventoryAccessor.Server =
            ServerItemHandlerWrapper(InvWrapper(InventoryContainerItemWrapper(stack)), false)

        @ClientSide
        override fun getClientInventory(player: EntityPlayer): InventoryAccessor.Client =
            ClientItemHandlerWrapper(InvWrapper(InventoryContainerItemWrapper(stack)))
    }
}
