package st.evening.mc.portableinventorytools.compat.mekanism

import mekanism.common.inventory.InventoryPersonalChest
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
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

object MekanismPersonalChestAdaptor : PortableInvAdaptor {
    override val adaptorId: ResourceLocation = PortableInvTools.resource("${MekanismCompat.MOD_ID}_personal_chest")

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
            ServerItemHandlerWrapper(InvWrapper(InventoryPersonalChest(stack, EnumHand.MAIN_HAND)), false)

        @ClientSide
        override fun getClientInventory(player: EntityPlayer): InventoryAccessor.Client =
            ClientItemHandlerWrapper(InvWrapper(InventoryPersonalChest(stack, EnumHand.MAIN_HAND)))
    }
}
