package st.evening.mc.portableinventorytools.compat.bundles

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.EnumActionResult
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.items.ItemHandlerHelper
import net.minecraftforge.items.ItemStackHandler
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.InventoryAccessor
import st.evening.mc.portableinventorytools.inventory.PortableInvAdaptor
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.data.runAction
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.RequireMod
import st.evening.mc.prelude.api.util.game.ServerSide
import st.evening.mc.prelude.api.util.game.copyWithSize
import st.evening.mc.prelude.api.util.game.forEachSlot
import st.evening.mc.prelude.api.util.game.getOrCreateDataTag
import surreal.bundles.config.ConfigHandler
import surreal.bundles.items.ItemBundle

@RequireMod(BundlesCompat.MOD_ID)
object BundlesBundleAdaptor : PortableInvAdaptor {
    override val adaptorId: ResourceLocation = PortableInvTools.resource("${BundlesCompat.MOD_ID}_bundle")

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
        override fun getServerInventory(player: EntityPlayer): InventoryAccessor.Server = Snapshot(stack)

        @ClientSide
        override fun getClientInventory(player: EntityPlayer): InventoryAccessor.Client = Snapshot(stack)
    }

    private class Snapshot(private val bundleStack: ItemStack) : InventoryAccessor.Server, InventoryAccessor.Client {
        private var cachedInventory: ItemStackHandler? = null
        private var fillAmount: Int = ItemBundle.getItemAmount(bundleStack)

        override val slotCount: Int
            get() = cachedInventory?.slots ?: (ItemBundle.getSlotCount(bundleStack) + 1)

        override val canInsertInventory: Boolean
            get() = ConfigHandler.allowStorageItems

        private fun getInventory(): ItemStackHandler {
            cachedInventory?.let { return it }
            val inv = ItemBundle.getItems(bundleStack)?.let { stacks ->
                ItemStackHandler(stacks.size + 1).also { handler ->
                    stacks.forEachIndexed { slotIndex, stack ->
                        handler.setStackInSlot(slotIndex, stack)
                    }
                }
            } ?: ItemStackHandler(1)
            cachedInventory = inv
            return inv
        }

        override fun getStackInSlot(slotIndex: Int): ItemStack = getInventory().getStackInSlot(slotIndex)

        override fun canInsert(slotIndex: Int, stack: ItemStack): Boolean = ConfigHandler.canPutItem(stack)

        override fun insertItems(stack: ItemStack, prefSlot: Int): ItemStack { // preferred slot is meaningless here
            val toTransfer = stack.count.coerceAtMost(ConfigHandler.bundleLimit - fillAmount)
            if (toTransfer <= 0 || !ConfigHandler.canPutItem(stack)) return stack
            val transferStack = stack.copyWithSize(toTransfer)
            val inv = getInventory()
            // don't need insertItemStacked because there shouldn't be any gaps
            val rem = ItemHandlerHelper.insertItem(inv, transferStack, false)
            if (rem.count >= toTransfer) return stack
            fillAmount += toTransfer - rem.count
            writeToStack(inv)
            return rem
        }

        override fun extractItems(slotIndex: Int, amount: Int): ItemStack {
            val inv = getInventory()
            val stack = inv.extractItem(slotIndex.mod(inv.slots), amount, false)
            if (stack.isEmpty) return ItemStack.EMPTY
            fillAmount -= stack.count
            writeToStack(inv)
            return stack
        }

        private fun writeToStack(inv: ItemStackHandler) {
            bundleStack.getOrCreateDataTag().runAction {
                "Items" tag NBTTagList().also { itemsTag ->
                    inv.forEachSlot {
                        val stack = inv.getStackInSlot(it)
                        if (!stack.isEmpty) {
                            itemsTag.appendTag(stack.serializeNBT())
                        }
                    }
                }
                "Amount" int fillAmount
            }
        }
    }
}
