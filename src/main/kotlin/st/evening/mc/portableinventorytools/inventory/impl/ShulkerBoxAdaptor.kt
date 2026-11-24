package st.evening.mc.portableinventorytools.inventory.impl

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.EnumActionResult
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.items.ItemStackHandler
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.InventoryAccessor
import st.evening.mc.portableinventorytools.inventory.PortableInvAdaptor
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.portableinventorytools.util.PortableInvHelper
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.data.forEachCompound
import st.evening.mc.prelude.api.util.data.getCompoundOrNull
import st.evening.mc.prelude.api.util.data.getListOrNull
import st.evening.mc.prelude.api.util.data.getOrCreateCompound
import st.evening.mc.prelude.api.util.data.plusAssign
import st.evening.mc.prelude.api.util.data.set
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.ServerSide
import st.evening.mc.prelude.api.util.game.dataTagOrNull
import st.evening.mc.prelude.api.util.game.forEachSlot
import st.evening.mc.prelude.api.util.game.getOrCreateDataTag

object ShulkerBoxAdaptor : PortableInvAdaptor {
    private const val SLOT_COUNT: Int = 27

    override val adaptorId: ResourceLocation = PortableInvTools.resource("shulker_box")

    override fun checkItem(
        stack: ItemStack,
        parentCaps: ICapabilityProvider?,
        initCaps: Map<ResourceLocation, ICapabilityProvider>?
    ): EnumActionResult = if (stack.item is ItemShulkerBox) EnumActionResult.SUCCESS else EnumActionResult.PASS

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

    private class Snapshot(private val boxStack: ItemStack) : InventoryAccessor.Server, InventoryAccessor.Client {
        private val inv: ItemStackHandler by lazy {
            val result = ItemStackHandler(SLOT_COUNT)
            boxStack.dataTagOrNull?.getCompoundOrNull("BlockEntityTag")?.getListOrNull("Items")?.let { itemsDto ->
                itemsDto.forEachCompound {
                    result.setStackInSlot(it.getByte("Slot").toInt() and 0xFF, ItemStack(it))
                }
            }
            return@lazy result
        }

        override val slotCount: Int
            get() = SLOT_COUNT

        override fun getStackInSlot(slotIndex: Int): ItemStack = inv.getStackInSlot(slotIndex.mod(SLOT_COUNT))

        override val canInsertInventory: Boolean
            get() = true

        override fun canInsert(slotIndex: Int, stack: ItemStack): Boolean = stack.item !is ItemShulkerBox

        override fun insertItems(stack: ItemStack, prefSlot: Int): ItemStack {
            if (stack.item is ItemShulkerBox) return stack
            val rem = PortableInvHelper.insertItemStacked(inv, stack, prefSlot)
            if (rem.count >= stack.count) return stack
            writeToStack()
            return rem
        }

        override fun extractItems(slotIndex: Int, amount: Int): ItemStack {
            val stack = inv.extractItem(slotIndex.mod(SLOT_COUNT), amount, false)
            if (stack.isEmpty) return ItemStack.EMPTY
            writeToStack()
            return stack
        }

        private fun writeToStack() {
            val itemsDto = NBTTagList()
            inv.forEachSlot {
                val stack = inv.getStackInSlot(it)
                if (stack.isEmpty) return@forEachSlot
                val itemDto = stack.serializeNBT()
                itemDto.setByte("Slot", it.toByte())
                itemsDto += itemDto
            }
            boxStack.getOrCreateDataTag().getOrCreateCompound("BlockEntityTag")["Items"] = itemsDto
        }
    }
}
