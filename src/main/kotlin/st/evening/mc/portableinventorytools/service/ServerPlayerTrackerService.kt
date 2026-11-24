package st.evening.mc.portableinventorytools.service

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.inventory.Container
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.init.S2CPortableInvSyncSelectedSlot
import st.evening.mc.portableinventorytools.init.S2CPortableInvWrapperSyncFull
import st.evening.mc.portableinventorytools.init.S2CPortableInvWrapperSyncSlot
import st.evening.mc.portableinventorytools.inventory.InventoryAccessor
import st.evening.mc.portableinventorytools.inventory.InventoryContext
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.portableinventorytools.util.TickableWorker
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.util.game.ServerSide
import st.evening.mc.prelude.api.util.game.SidednessAssertion
import st.evening.mc.prelude.api.util.game.assertLogicalServer
import st.evening.mc.prelude.api.util.game.isEqual
import java.util.IdentityHashMap
import java.util.UUID

class ServerPlayerTrackerService internal constructor(reg: ModRegistrar) {
    @ServerSide
    private val trackedPlayers: MutableMap<UUID, PlayerState> = mutableMapOf()
    @ServerSide
    private val trackedPlayerEntities: MutableMap<EntityPlayer, PlayerState> = IdentityHashMap()

    init {
        reg.on<PlayerEvent.PlayerLoggedInEvent> @OptIn(SidednessAssertion::class) { event ->
            assertLogicalServer {
                val newState = PlayerState(event.player as EntityPlayerMP)
                trackedPlayers[event.player.uniqueID] = newState
                trackedPlayerEntities[event.player] = newState
                newState.sendSyncFull()
            }
        }
        reg.on<net.minecraftforge.event.entity.player.PlayerEvent.Clone> @OptIn(SidednessAssertion::class) { event ->
            assertLogicalServer {
                val state = trackedPlayers[event.entityPlayer.uniqueID]
                if (state != null) {
                    state.player = event.entityPlayer as EntityPlayerMP
                    trackedPlayerEntities -= event.original
                    trackedPlayerEntities[event.entityPlayer] = state
                } else { // failsafe; this really shouldn't be able to happen
                    val newState = PlayerState(event.entityPlayer as EntityPlayerMP)
                    trackedPlayers[event.entityPlayer.uniqueID] = newState
                    trackedPlayerEntities[event.entityPlayer] = newState
                }
            }
        }
        reg.on<PlayerEvent.PlayerLoggedOutEvent> @OptIn(SidednessAssertion::class) { event ->
            assertLogicalServer {
                trackedPlayers.remove(event.player.uniqueID)
                trackedPlayerEntities.remove(event.player)
            }
        }
        reg.on<TickEvent.ServerTickEvent> @OptIn(SidednessAssertion::class) { event ->
            if (event.phase == TickEvent.Phase.END) {
                assertLogicalServer {
                    trackedPlayers.values.forEach {
                        it.tick()
                    }
                }
            }
        }
    }

    @ServerSide
    fun updateKnownSelectedSlot(player: EntityPlayer, contSlotIndex: Int, piSlotIndex: Int) {
        trackedPlayerEntities[player]?.updateKnownSelectedSlot(contSlotIndex, piSlotIndex)
    }

    @ServerSide
    private class PlayerState(var player: EntityPlayerMP) {
        private var currentCtx: OpenContainerContext = OpenContainerContext(player.openContainer)

        fun tick() {
            if (player.openContainer != currentCtx.container) {
                currentCtx = OpenContainerContext(player.openContainer)
                sendSyncFull()
            }
            currentCtx.tick()
        }

        fun sendSyncFull() {
            currentCtx.sendSyncFull()
        }

        fun updateKnownSelectedSlot(contSlotIndex: Int, piSlotIndex: Int) {
            currentCtx.updateKnownSelectedSlot(contSlotIndex, piSlotIndex)
        }

        private inner class OpenContainerContext(override val container: Container) : InventoryContext {
            override val player: EntityPlayerMP
                get() = this@PlayerState.player

            private var slotStates: Array<StackState> = newSlotStateArray()
            private var heldStackState: StackState = StackState(-1, player.inventory.itemStack)

            private fun newSlotStateArray(): Array<StackState> =
                Array(container.inventorySlots.size) { StackState(it, container.inventorySlots[it].stack) }

            override fun isAlive(): Boolean = currentCtx === this

            fun tick() {
                val slots = container.inventorySlots
                if (slots.size != slotStates.size) {
                    slotStates.forEach { it.kill() }
                    slotStates = newSlotStateArray()
                    slotStates.forEach { it.sendSyncFull() }
                } else {
                    slots.forEachIndexed { slotIndex, slot ->
                        tickState(slotIndex, slotStates[slotIndex], slot.stack)
                    }
                }
                tickState(-1, heldStackState, player.inventory.itemStack)
            }

            private fun tickState(slotIndex: Int, state: StackState, currentStack: ItemStack) {
                if (state.stack !== currentStack) {
                    state.kill()
                    val newState = StackState(slotIndex, currentStack)
                    if (slotIndex == -1) {
                        heldStackState = newState
                    } else {
                        slotStates[slotIndex] = newState
                    }
                    newState.sendSyncFull()
                }
                state.tick()
            }

            fun sendSyncFull() {
                slotStates.forEach { it.sendSyncFull() }
                heldStackState.sendSyncFull()
            }

            fun updateKnownSelectedSlot(contSlotIndex: Int, piSlotIndex: Int) {
                if (contSlotIndex == -1) {
                    heldStackState.updateKnownSelectedSlot(piSlotIndex)
                } else {
                    slotStates.getOrNull(contSlotIndex)?.updateKnownSelectedSlot(piSlotIndex)
                }
            }

            private inner class StackState(private val contSlotIndex: Int, val stack: ItemStack) {
                private val piState: StackPortableInvState? =
                    stack.getCapability(PortableInvTools.defns.capabilities.piState, null)
                        ?.let { StackPortableInvState(it) }

                fun tick() {
                    piState?.tick()
                }

                fun sendSyncFull() {
                    piState?.sendSyncFull()
                }

                fun updateKnownSelectedSlot(piSlotIndex: Int) {
                    piState?.updateKnownSelectedSlot(piSlotIndex)
                }

                fun kill() {
                    piState?.kill()
                }

                private inner class StackPortableInvState(private val piState: PortableInvState) {
                    private val wrapper: ServerPortableInvWrapper = piState.serverWrapper
                    private val worker: TickableWorker? = wrapper.newInventoryWorker(this@OpenContainerContext)
                    private val trackerState: InvTracker? = if (wrapper.needsInventorySync) InvTracker() else null
                    private var knownSelectedSlot: Int = 0

                    fun tick() {
                        val selectedSlot = piState.selectedSlot
                        if (selectedSlot != knownSelectedSlot) {
                            knownSelectedSlot = selectedSlot
                            PortableInvTools.defns.s2c.piSyncSelectedSlot.sendTo(
                                S2CPortableInvSyncSelectedSlot(container.windowId, contSlotIndex, selectedSlot),
                                this@PlayerState.player
                            )
                        }
                        worker?.tick()
                        trackerState?.tick()
                    }

                    fun sendSyncFull() {
                        trackerState?.sendSyncFull()
                    }

                    fun updateKnownSelectedSlot(piSlotIndex: Int) {
                        knownSelectedSlot = piSlotIndex
                    }

                    fun kill() {
                        worker?.kill()
                    }

                    private inner class InvTracker {
                        private var knownStacks: Array<ItemStack> =
                            copyStacks(wrapper.getServerInventory(this@PlayerState.player))

                        private fun copyStacks(inv: InventoryAccessor): Array<ItemStack> =
                            Array(inv.slotCount) { inv.getStackInSlot(it).copy() }

                        fun tick() {
                            val inv = wrapper.getServerInventory(this@PlayerState.player)
                            if (inv.slotCount != knownStacks.size) {
                                knownStacks = copyStacks(inv)
                                sendSyncFull()
                            } else {
                                knownStacks.forEachIndexed { slotIndex, knownStack ->
                                    val stack = inv.getStackInSlot(slotIndex)
                                    if (!knownStack.isEqual(stack)) {
                                        knownStacks[slotIndex] = stack.copy()
                                        sendSyncSlot(slotIndex, stack)
                                    }
                                }
                            }
                        }

                        fun sendSyncSlot(slotIndex: Int, stack: ItemStack) {
                            PortableInvTools.defns.s2c.piWrapperSyncSlot.sendTo(
                                S2CPortableInvWrapperSyncSlot(container.windowId, contSlotIndex, slotIndex, stack),
                                this@PlayerState.player
                            )
                        }

                        fun sendSyncFull() {
                            PortableInvTools.defns.s2c.piWrapperSyncFull.sendTo(
                                S2CPortableInvWrapperSyncFull(container.windowId, contSlotIndex, knownStacks),
                                this@PlayerState.player
                            )
                        }
                    }
                }
            }
        }
    }
}
