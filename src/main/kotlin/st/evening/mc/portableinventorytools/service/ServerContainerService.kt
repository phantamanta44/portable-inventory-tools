package st.evening.mc.portableinventorytools.service

import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.inventory.Container
import net.minecraft.item.ItemStack
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.init.S2CPiActionFeedback
import st.evening.mc.portableinventorytools.util.InvActionType
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.util.game.ServerSide
import st.evening.mc.prelude.api.util.game.copyWithSize
import st.evening.mc.prelude.api.util.game.isEqual
import st.evening.mc.prelude.api.util.game.stacksWith
import kotlin.math.min

class ServerContainerService internal constructor(reg: ModRegistrar /* not needed but /shrug */) {
    @ServerSide
    fun handlePickUp(player: EntityPlayerMP, cont: Container, slotIndex: Int, precise: Boolean) {
        val heldStack = player.inventory.itemStack
        if (heldStack.isEmpty) return
        val slot = cont.inventorySlots[slotIndex]
        val slotStack = slot.stack
        if (slotStack.isEmpty || !slot.isItemValid(slotStack) || !slot.canTakeStack(player)) return
        val state = heldStack.getCapability(PortableInvTools.defns.capabilities.piState, null) ?: return

        val origHeldStack = heldStack.copy()
        if (precise && slotStack.count > 1) {
            if (
                !state.serverWrapper.getServerInventory(player)
                    .insertItems(slotStack.copyWithSize(1), state.selectedSlot).isEmpty
            ) {
                sendFeedback(player, InvActionType.PICK_UP, precise = true, false)
                return
            }
            slot.onTake(player, slot.decrStackSize(1))
        } else {
            // deals with weird edge cases where the insert operation changes the slot stack size (e.g. an ender pouch
            // picking up an item inside itself)
            val origCount = slotStack.count
            val remStack = state.serverWrapper.getServerInventory(player).insertItems(slotStack, state.selectedSlot)
            if (remStack.count >= origCount) {
                sendFeedback(player, InvActionType.PICK_UP, precise, false)
                return
            }
            val takenStack = slot.decrStackSize(origCount - remStack.count)
            if (slot.stack.isEmpty) { // can't just check remStack because of the above edge case^
                slot.putStack(ItemStack.EMPTY)
            }
            slot.onTake(player, takenStack)
        }
        if (!origHeldStack.isEqual(heldStack)) {
            player.updateHeldItem()
        }
        sendFeedback(player, InvActionType.PICK_UP, precise, true)
    }

    @ServerSide
    fun handlePlace(player: EntityPlayerMP, cont: Container, slotIndex: Int, precise: Boolean) {
        val heldStack = player.inventory.itemStack
        if (heldStack.isEmpty) return
        val state = heldStack.getCapability(PortableInvTools.defns.capabilities.piState, null) ?: return
        val inv = state.serverWrapper.getServerInventory(player)
        if (inv.slotCount <= 0) return
        val selStack = inv.getStackInSlot(state.selectedSlot)
        if (selStack.isEmpty) return
        val slot = cont.inventorySlots[slotIndex]
        if (!slot.isItemValid(selStack)) return

        val slotStack = slot.stack
        val toTransfer: Int
        if (precise) {
            if (
                !slotStack.isEmpty &&
                (slotStack.count >= selStack.maxStackSize || slotStack.count >= slot.slotStackLimit ||
                    !slotStack.stacksWith(selStack))
            ) return
            toTransfer = 1
        } else if (slotStack.isEmpty) {
            toTransfer = min(selStack.maxStackSize, slot.slotStackLimit)
        } else {
            toTransfer = min(selStack.maxStackSize, slot.slotStackLimit) - slotStack.count
            if (toTransfer <= 0 || !slotStack.stacksWith(selStack)) return
        }

        val origHeldStack = heldStack.copy()
        val transferStack = inv.extractItems(state.selectedSlot, toTransfer)
        if (transferStack.isEmpty) {
            sendFeedback(player, InvActionType.PLACE, precise, false)
            return
        }
        transferStack.grow(slotStack.count)
        slot.putStack(transferStack)
        slot.onSlotChanged()
        if (!origHeldStack.isEqual(heldStack)) {
            player.updateHeldItem()
        }
        sendFeedback(player, InvActionType.PLACE, precise, true)
    }

    @ServerSide
    fun handleInsert(player: EntityPlayerMP, cont: Container, slotIndex: Int, precise: Boolean) {
        val heldStack = player.inventory.itemStack
        if (heldStack.isEmpty) return
        val slot = cont.inventorySlots[slotIndex]
        val slotStack = slot.stack
        if (slotStack.isEmpty || !slot.isItemValid(slotStack) || !slot.canTakeStack(player)) return
        val state = slotStack.getCapability(PortableInvTools.defns.capabilities.piState, null) ?: return

        if (precise && heldStack.count > 1) {
            if (
                !state.serverWrapper.getServerInventory(player)
                    .insertItems(heldStack.copyWithSize(1), state.selectedSlot).isEmpty
            ) {
                sendFeedback(player, InvActionType.INSERT, precise = true, false)
                return
            }
            player.inventory.itemStack.shrink(1)
        } else {
            val remStack = state.serverWrapper.getServerInventory(player).insertItems(heldStack, state.selectedSlot)
            if (remStack.count >= heldStack.count) {
                sendFeedback(player, InvActionType.INSERT, precise, false)
                return
            }
            player.inventory.itemStack = remStack
        }
        slot.onSlotChanged()
        player.updateHeldItem()
        sendFeedback(player, InvActionType.INSERT, precise, true)
    }

    @ServerSide
    fun handleExtract(player: EntityPlayerMP, cont: Container, slotIndex: Int, precise: Boolean) {
        val slot = cont.inventorySlots[slotIndex]
        val slotStack = slot.stack
        if (slotStack.isEmpty || !slot.isItemValid(slotStack) || !slot.canTakeStack(player)) return
        val state = slotStack.getCapability(PortableInvTools.defns.capabilities.piState, null) ?: return
        val inv = state.serverWrapper.getServerInventory(player)
        if (inv.slotCount <= 0) return
        val selStack = inv.getStackInSlot(state.selectedSlot)
        if (selStack.isEmpty) return

        val heldStack = player.inventory.itemStack
        val toTransfer: Int
        if (precise) {
            if (!heldStack.isEmpty && (heldStack.count >= selStack.maxStackSize || !heldStack.stacksWith(selStack))) {
                return
            }
            toTransfer = 1
        } else if (heldStack.isEmpty) {
            toTransfer = selStack.maxStackSize
        } else {
            toTransfer = selStack.maxStackSize - heldStack.count
            if (toTransfer <= 0 || !heldStack.stacksWith(selStack)) return
        }

        val transferStack = inv.extractItems(state.selectedSlot, toTransfer)
        if (transferStack.isEmpty) {
            sendFeedback(player, InvActionType.EXTRACT, precise, false)
            return
        }
        transferStack.grow(heldStack.count)
        player.inventory.itemStack = transferStack
        slot.onSlotChanged()
        player.updateHeldItem()
        sendFeedback(player, InvActionType.EXTRACT, precise, true)
    }

    @ServerSide
    private fun sendFeedback(player: EntityPlayerMP, type: InvActionType, precise: Boolean, success: Boolean) {
        PortableInvTools.defns.s2c.piActionFeedback.sendTo(S2CPiActionFeedback(type, precise, success), player)
    }
}
