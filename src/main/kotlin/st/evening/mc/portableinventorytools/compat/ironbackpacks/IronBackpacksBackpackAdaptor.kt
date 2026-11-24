package st.evening.mc.portableinventorytools.compat.ironbackpacks

import gr8pefish.ironbackpacks.api.backpack.BackpackInfo
import gr8pefish.ironbackpacks.item.ItemBackpack
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.ICapabilityProvider
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

object IronBackpacksBackpackAdaptor : PortableInvAdaptor {
    override val adaptorId: ResourceLocation = PortableInvTools.resource("${IronBackpacksCompat.MOD_ID}_backpack")

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
        override fun getServerInventory(player: EntityPlayer): InventoryAccessor.Server = ServerInv(stack)

        @ClientSide
        override fun getClientInventory(player: EntityPlayer): InventoryAccessor.Client =
            ClientItemHandlerWrapper(BackpackInfo.fromStack(stack).inventory)
    }

    @ServerSide
    private class ServerInv(private val bpStack: ItemStack, private val bpInfo: BackpackInfo) :
        ServerItemHandlerWrapper(bpInfo.inventory, false) {

        constructor(bpStack: ItemStack) : this(bpStack, BackpackInfo.fromStack(bpStack))

        override fun insertItems(stack: ItemStack, prefSlot: Int): ItemStack {
            val result = super.insertItems(stack, prefSlot)
            if (result.count < stack.count) {
                (bpStack.item as? ItemBackpack)?.updateBackpack(bpStack, bpInfo)
            }
            return result
        }

        override fun extractItems(slotIndex: Int, amount: Int): ItemStack {
            val stack = super.extractItems(slotIndex, amount)
            if (!stack.isEmpty) {
                (bpStack.item as? ItemBackpack)?.updateBackpack(bpStack, bpInfo)
            }
            return stack
        }
    }
}
