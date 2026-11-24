package st.evening.mc.portableinventorytools.compat.immeng

import blusunrize.immersiveengineering.api.IEApi
import blusunrize.immersiveengineering.api.tool.ToolboxHandler
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.InventoryAccessor
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.prefab.ClientSyncedInventoryWrapper
import st.evening.mc.portableinventorytools.inventory.prefab.ServerItemHandlerWrapper
import st.evening.mc.portableinventorytools.inventory.prefab.SyncedItemHandlerItemAdaptor
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.RequireMod
import st.evening.mc.prelude.api.util.game.ServerSide
import st.evening.mc.prelude.api.util.game.stacksWith

@RequireMod(ImmEngCompat.MOD_ID)
object ToolboxAdaptor : SyncedItemHandlerItemAdaptor(PortableInvTools.resource("${ImmEngCompat.MOD_ID}_toolbox")) {
    private const val FOOD_SLOT_LIMIT: Int = 3
    private const val TOOL_SLOT_LIMIT: Int = 10
    private const val WIRING_SLOT_LIMIT: Int = 16

    @ServerSide
    override fun wrapServerSide(stack: ItemStack, state: PortableInvState): ServerPortableInvWrapper =
        ServerWrapper(stack)

    @ClientSide.Strong
    override fun wrapClientSide(stack: ItemStack, state: PortableInvState): ClientPortableInvWrapper =
        ClientWrapper()

    private fun canInsertIntoSlot(slotIndex: Int, stack: ItemStack, world: World): Boolean =
        IEApi.isAllowedInCrate(stack) && when {
            slotIndex < FOOD_SLOT_LIMIT -> ToolboxHandler.isFood(stack)
            slotIndex < TOOL_SLOT_LIMIT -> ToolboxHandler.isTool(stack)
            slotIndex < WIRING_SLOT_LIMIT -> ToolboxHandler.isWiring(stack, world)
            else -> true
        }

    @ServerSide
    private class ServerWrapper(stack: ItemStack) : ServerItemHandlerWrapper(stack, true) {
        private lateinit var world: World

        override fun getServerInventory(player: EntityPlayer): InventoryAccessor.Server {
            world = player.world
            return this
        }

        override fun insertItems(stack: ItemStack, prefSlot: Int): ItemStack {
            if (!IEApi.isAllowedInCrate(stack)) return stack
            var rem = stack.copy()
            if (prefSlot >= 0 && canInsertIntoSlot(prefSlot, stack, world)) {
                rem = handler.insertItem(prefSlot, rem, false)
                if (rem.isEmpty) return ItemStack.EMPTY
            }
            rem = tryInsert(rem, true)
            if (rem.isEmpty) return ItemStack.EMPTY
            return tryInsert(rem, false)
        }

        private fun tryInsert(stack: ItemStack, stackedOnly: Boolean): ItemStack {
            var rem = stack
            if (ToolboxHandler.isFood(stack)) {
                rem = tryInsertIntoSlotRange(rem, 0, FOOD_SLOT_LIMIT, stackedOnly)
                if (rem.isEmpty) return ItemStack.EMPTY
            }
            if (ToolboxHandler.isTool(stack)) {
                rem = tryInsertIntoSlotRange(rem, FOOD_SLOT_LIMIT, TOOL_SLOT_LIMIT, stackedOnly)
                if (rem.isEmpty) return ItemStack.EMPTY
            }
            if (ToolboxHandler.isWiring(stack, world)) {
                rem = tryInsertIntoSlotRange(rem, TOOL_SLOT_LIMIT, WIRING_SLOT_LIMIT, stackedOnly)
                if (rem.isEmpty) return ItemStack.EMPTY
            }
            return tryInsertIntoSlotRange(rem, WIRING_SLOT_LIMIT, handler.slots, stackedOnly)
        }

        private fun tryInsertIntoSlotRange(
            stack: ItemStack,
            fromSlotIndex: Int,
            toSlotIndex: Int,
            stackedOnly: Boolean
        ): ItemStack {
            var rem = stack
            for (i in fromSlotIndex..<toSlotIndex) {
                if (stackedOnly && !handler.getStackInSlot(i).stacksWith(stack)) continue
                rem = handler.insertItem(i, rem, false)
                if (rem.isEmpty) return ItemStack.EMPTY
            }
            return rem
        }
    }

    @ClientSide
    private class ClientWrapper : ClientSyncedInventoryWrapper() {
        private lateinit var world: World

        override fun getClientInventory(player: EntityPlayer): InventoryAccessor.Client {
            world = player.world
            return this
        }

        override fun canInsert(slotIndex: Int, stack: ItemStack): Boolean = canInsertIntoSlot(slotIndex, stack, world)
    }
}
