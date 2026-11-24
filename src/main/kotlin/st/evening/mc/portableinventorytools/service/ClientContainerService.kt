package st.evening.mc.portableinventorytools.service

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.gui.inventory.GuiContainerCreative
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Container
import net.minecraft.inventory.ContainerPlayer
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraftforge.client.event.GuiContainerEvent
import net.minecraftforge.client.event.GuiScreenEvent
import net.minecraftforge.client.settings.KeyConflictContext
import net.minecraftforge.client.settings.KeyModifier
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Mouse
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.init.C2SCreativePiAction
import st.evening.mc.portableinventorytools.init.C2SPiAction
import st.evening.mc.portableinventorytools.init.C2SPiSelectSlot
import st.evening.mc.portableinventorytools.init.C2SSyncCreativeHeldStack
import st.evening.mc.portableinventorytools.init.S2CPortableInvSyncSelectedSlot
import st.evening.mc.portableinventorytools.init.S2CPortableInvWrapperSyncFull
import st.evening.mc.portableinventorytools.init.S2CPortableInvWrapperSyncSlot
import st.evening.mc.portableinventorytools.util.InvActionType
import st.evening.mc.prelude.api.gui.drawable.GuiDrawable
import st.evening.mc.prelude.api.gui.drawable.GuiSamplable
import st.evening.mc.prelude.api.gui.drawable.drawFullSize
import st.evening.mc.prelude.api.gui.drawable.prefab.DrawableTexture
import st.evening.mc.prelude.api.gui.drawable.sliceSized
import st.evening.mc.prelude.api.network.PacketType
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.collection.forEachInline
import st.evening.mc.prelude.api.util.collection.getOrPut
import st.evening.mc.prelude.api.util.collection.mapToTypedArray
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.SidednessAssertion
import st.evening.mc.prelude.api.util.game.assertLogicalClient
import st.evening.mc.prelude.api.util.game.stacksWith
import st.evening.mc.prelude.api.util.render.RenderingHelper
import st.evening.mc.prelude.api.util.render.TextureResource

@ClientSide.Physical
class ClientContainerService internal constructor(reg: ModRegistrar) {
    companion object {
        private const val SYNC_QUEUE_TTL: Long = 1000L

        private val overlayIconSheet: GuiSamplable =
            DrawableTexture(44, 11, TextureResource(PortableInvTools.resource("textures/gui/slot_overlay.png")))
        private val blockedIcon: GuiSamplable = overlayIconSheet.sliceSized(11, 11, 0, 0)
        private val similarIcon: GuiSamplable = overlayIconSheet.sliceSized(11, 11, 11, 0)
        private val insertIcon: GuiSamplable = overlayIconSheet.sliceSized(11, 11, 22, 0)
        private val insertSimilarIcon: GuiSamplable = overlayIconSheet.sliceSized(11, 11, 33, 0)
    }

    private val syncQueue: Int2ObjectMap<WindowSyncQueue> = Int2ObjectOpenHashMap()
    // if set to true, ensures that an update tick occurs before the next rendering call
    private var interruptTick: Boolean = false

    init {
        reg.on<TickEvent.ClientTickEvent> { event ->
            if (event.phase == TickEvent.Phase.END) {
                tick()
            }
        }
        // increased priority because we want to run before other mods that change mouse behaviour in GUIs
        reg.on<GuiScreenEvent.MouseInputEvent.Pre>(priority = EventPriority.HIGH) { event ->
            if (containerState?.handleMouseEvent(event.gui) == true) {
                event.isCanceled = true
            }
        }
        reg.on<GuiContainerEvent.DrawForeground> { event ->
            containerState?.drawSlotOverlays(event.guiContainer, Minecraft.getMinecraft().renderPartialTicks)
        }
    }

    private var containerState: ContainerState? = null

    @OptIn(SidednessAssertion::class)
    private fun tick() {
        interruptTick = false
        val player = Minecraft.getMinecraft().player
        if (player == null) {
            containerState = null
            return
        }
        assertLogicalClient {
            containerState?.let {
                if (player.openContainer === it.container) {
                    it.tick()
                    flushSyncQueue(it)
                    return
                }
            }
            val newState = ContainerState(player, player.openContainer)
            containerState = newState
            newState.tick()
            flushSyncQueue(newState)
        }
    }

    @ClientSide
    private fun flushSyncQueue(state: ContainerState) {
        if (syncQueue.isEmpty()) return
        val expiryCutoff = System.currentTimeMillis() - SYNC_QUEUE_TTL
        val iter = syncQueue.int2ObjectEntrySet().iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.lastUpdate < expiryCutoff) {
                iter.remove()
            } else if (state.container.windowId == entry.intKey) {
                state.handleSyncQueue(entry.value)
                iter.remove()
            }
        }
    }

    @ClientSide
    fun isSlotBlocked(slot: Slot): Boolean = containerState?.isSlotBlocked(slot) == true

    fun markSlotDirty(slotIndex: Int) {
        containerState?.markSlotDirty(slotIndex)
    }

    fun enqueueSyncSelectedSlot(message: S2CPortableInvSyncSelectedSlot) {
        getWindowSyncQueue(message.windowId).enqueueSyncSelectedSlot(message.contSlotIndex, message.selectedSlotIndex)
    }

    fun enqueueSyncSlot(message: S2CPortableInvWrapperSyncSlot) {
        getWindowSyncQueue(message.windowId).enqueueSyncSlot(message.contSlotIndex, message.piSlotIndex, message.stack)
    }

    fun enqueueSyncFull(message: S2CPortableInvWrapperSyncFull) {
        getWindowSyncQueue(message.windowId).enqueueSyncFull(message.contSlotIndex, message.stacks)
    }

    private fun getWindowSyncQueue(windowId: Int): WindowSyncQueue = syncQueue.getOrPut(windowId) { WindowSyncQueue() }

    fun isMouseGrabbed(slot: Slot): Boolean = !Mouse.isButtonDown(0) && containerState?.isMouseGrabbed(slot) == true

    fun getActiveDragAction(): InvActionType? = containerState?.getActiveDragAction()

    fun scheduleInterruptTick() {
        interruptTick = true
    }

    @ClientSide
    fun checkInterruptTick() {
        if (interruptTick) {
            tick()
        }
    }

    private class ContainerState(private val player: EntityPlayer, val container: Container) {
        private val contHandler: ContainerHandler = if (container is GuiContainerCreative.ContainerCreative) {
            ContainerHandler.Creative(container, player)
        } else {
            ContainerHandler.Remote(container)
        }

        private var slotStates: Array<SlotState> = newSlotStateArray()
        private var heldStackState: HeldStackState = HeldStackState(player.inventory.itemStack)
        private var lastLeftMouseButtonSlot: Int = -1

        private fun newSlotStateArray(): Array<SlotState> =
            contHandler.getSlots().mapToTypedArray { SlotState(it, it.stack) }

        @ClientSide
        fun tick() {
            val currentHeldStack = player.inventory.itemStack
            val prevHeldStackState = heldStackState
            if (prevHeldStackState.isDifferentStack(currentHeldStack)) {
                val newState = HeldStackState(currentHeldStack)
                run {
                    if (lastLeftMouseButtonSlot >= 0) {
                        slotStates.getOrNull(lastLeftMouseButtonSlot)?.let {
                            if (newState.cloneFrom(it)) return@run
                        }
                    }
                    newState.cloneFrom(prevHeldStackState)
                }
                heldStackState = newState
                contHandler.syncHeldStack(prevHeldStackState.piState, newState.piState, newState.stack)
                slotStates.forEach {
                    it.invalidate()
                }
            }

            val slots = contHandler.getSlots()
            if (slots.size != slotStates.size) {
                slotStates = newSlotStateArray()
                heldStackState.resetMouseState()
            } else {
                slots.forEachIndexed { slotIndex, slot ->
                    val state = slotStates[slotIndex]
                    val currentStack = slot.stack
                    if (state.isDifferentStack(currentStack)) {
                        val newState = SlotState(slot, currentStack)
                        if (lastLeftMouseButtonSlot != slotIndex || !newState.cloneFrom(prevHeldStackState)) {
                            newState.cloneFrom(state)
                        }
                        slotStates[slotIndex] = newState
                    }
                }
            }
            lastLeftMouseButtonSlot = -1
        }

        @ClientSide
        fun isSlotBlocked(slot: Slot): Boolean =
            contHandler.mapSlotIndex(slot)?.let { slotStates.getOrNull(it) }?.status == SlotStatus.BLOCKED

        @ClientSide
        fun handleSyncQueue(queue: WindowSyncQueue) {
            queue.slotTasks.forEachInline { contSlotIndex, task ->
                if (contSlotIndex >= 0 && contSlotIndex < slotStates.size) {
                    slotStates[contSlotIndex].runSyncTask(task)
                }
            }
            queue.heldStackTask?.let { task ->
                heldStackState.runSyncTask(task)
                slotStates.forEach { it.invalidate() } // held stack changed; any slot could have new status
            }
        }

        fun markSlotDirty(slotIndex: Int) {
            if (slotIndex < 0) { // held stack changed; any slot could have new status
                slotStates.forEach { it.invalidate() }
            } else if (slotIndex < slotStates.size) {
                slotStates[slotIndex].invalidate()
            }
        }

        @OptIn(SidednessAssertion::class)
        fun handleMouseEvent(gui: GuiScreen): Boolean {
            if (gui !is GuiContainer || gui.inventorySlots !== container) return false
            assertLogicalClient {
                return handleMouseEventImpl(gui)
            }
        }

        @ClientSide
        private fun handleMouseEventImpl(gui: GuiContainer): Boolean {
            val slot = gui.slotUnderMouse
            var consume = false

            if (slot != null) {
                when (Mouse.getEventButton()) {
                    0 -> {
                        contHandler.mapSlotIndex(slot)?.let {
                            lastLeftMouseButtonSlot = it
                        }
                    }
                    1 -> {
                        if (heldStackState.stack.isEmpty) {
                            if (Mouse.getEventButtonState() && tryExtractIntoEmptyHeld(slot, false)) {
                                gui.ignoreMouseUp = true
                                consume = true
                            }
                        } else if (heldStackState.handleMouseButton(gui, slot)) {
                            consume = true
                        }
                    }
                    -1 -> heldStackState.handleMouseMovement(slot)
                }
            }

            val wheelDelta = Mouse.getEventDWheel()
            if (wheelDelta != 0) {
                if (wheelDelta > 0) {
                    if (KeyModifier.CONTROL.isActive(KeyConflictContext.GUI)) {
                        if (slot != null && !heldStackState.stack.isEmpty && heldStackState.handleScrollUp(slot)) {
                            consume = true
                        }
                    } else if (tryCycleSelectedSlot(slot, true)) {
                        consume = true
                    }
                } else if (KeyModifier.CONTROL.isActive(KeyConflictContext.GUI)) {
                    if (slot != null) {
                        if (heldStackState.stack.isEmpty) {
                            if (tryExtractIntoEmptyHeld(slot, true)) {
                                consume = true
                            }
                        } else if (heldStackState.handleScrollDown(slot)) {
                            consume = true
                        }
                    }
                } else if (tryCycleSelectedSlot(slot, false)) {
                    consume = true
                }
            }
            return consume
        }

        @ClientSide
        private fun tryExtractIntoEmptyHeld(slot: Slot, forcePrecise: Boolean): Boolean {
            val slotStack = slot.stack
            if (slotStack.isEmpty) return false
            val state = slotStack.getCapability(PortableInvTools.defns.capabilities.piState, null) ?: return false
            val inv = state.clientWrapper.getClientInventory(player)
            if (inv.slotCount <= 0) return false
            contHandler.doExtract(slot, forcePrecise || KeyModifier.CONTROL.isActive(KeyConflictContext.GUI))
            return true
        }

        @ClientSide
        private fun tryCycleSelectedSlot(slot: Slot?, reverse: Boolean): Boolean {
            val offset = if (KeyModifier.SHIFT.isActive(KeyConflictContext.GUI)) {
                if (reverse) {
                    -PortableInvTools.config.client.selectedSlotFastOffset
                } else {
                    PortableInvTools.config.client.selectedSlotFastOffset
                }
            } else {
                if (reverse) -1 else 1
            }

            if (!heldStackState.stack.isEmpty && heldStackState.tryCycleSelectedSlot(offset)) {
                return true
            }

            if (slot == null) return false
            val slotStack = slot.stack
            if (slotStack.isEmpty) return false
            val state = slotStack.getCapability(PortableInvTools.defns.capabilities.piState, null) ?: return false
            val slotCount = state.clientWrapper.getClientInventory(player).slotCount
            if (slotCount <= 0) return false
            state.selectedSlot = (state.selectedSlot + offset).mod(slotCount)
            contHandler.doSelectSlot(slot, state.selectedSlot)
            Minecraft.getMinecraft().soundHandler.playSound(
                PositionedSoundRecord.getRecord(PortableInvTools.defns.sounds.invCycle(offset < 0), 1F, 0.35F)
            )
            return true
        }

        fun isMouseGrabbed(slot: Slot): Boolean = heldStackState.piState != null ||
            contHandler.mapSlotIndex(slot)?.let { slotStates.getOrNull(it) }?.piState != null

        fun getActiveDragAction(): InvActionType? = heldStackState.getActiveDragAction()

        @OptIn(SidednessAssertion::class)
        fun drawSlotOverlays(gui: GuiContainer, partialTicks: Float) {
            if (gui.inventorySlots !== container) return
            assertLogicalClient {
                GlStateManager.disableLighting()
                RenderingHelper.resetColour()
                GlStateManager.disableDepth()
                contHandler.drawSlotOverlays(slotStates, partialTicks)
                GlStateManager.enableDepth()
            }
        }

        abstract class StackState(stack: ItemStack) {
            val stack: ItemStack = if (stack.isEmpty) ItemStack.EMPTY else stack
            val item: Item = stack.item // we want to be able to tell what the item was even if the stack is emptied
            val piState: PortableInvState? =
                stack.getCapability(PortableInvTools.defns.capabilities.piState, null)

            fun isDifferentStack(other: ItemStack): Boolean = stack !== if (other.isEmpty) ItemStack.EMPTY else other

            @ClientSide
            open fun cloneFrom(other: StackState): Boolean {
                if (piState == null) return false
                val oState = other.piState ?: return false
                return piState.adaptor === oState.adaptor && item === other.item &&
                    oState.adaptor.cloneClientState(oState.clientWrapper, piState.clientWrapper)
            }

            @ClientSide
            open fun runSyncTask(task: SyncTask) {
                piState?.let {
                    when (task) {
                        is SyncTask.Selected -> {}
                        is SyncTask.Slots -> it.clientWrapper.handleSyncSlots(task.slotTable)
                        is SyncTask.Full -> it.clientWrapper.handleSyncFull(task.stacks)
                    }
                    if (task.selectedSlot >= 0) {
                        it.selectedSlot = task.selectedSlot
                    }
                }
            }
        }

        inner class SlotState(val slot: Slot, stack: ItemStack) : StackState(stack) {
            private var statusCache: SlotStatus? = null

            @ClientSide
            val status: SlotStatus
                get() = statusCache ?: computeSlotStatus().also { statusCache = it }

            @ClientSide
            private fun computeSlotStatus(): SlotStatus {
                if (stack.isEmpty) return SlotStatus.NONE
                val heldStack = heldStackState.stack
                if (heldStack.isEmpty) return SlotStatus.NONE

                heldStackState.piState?.let { state ->
                    val heldInv = state.clientWrapper.getClientInventory(player)
                    if (piState != null && !heldInv.canInsertInventory) return SlotStatus.BLOCKED
                    for (i in 0..<heldInv.slotCount) {
                        val slotStack = heldInv.getStackInSlot(i)
                        if (!slotStack.isEmpty && slotStack.stacksWith(stack)) {
                            return SlotStatus.HAS_SIMILAR
                        }
                    }
                    return SlotStatus.NONE
                }

                val slotInv = piState?.clientWrapper?.getClientInventory(player) ?: return SlotStatus.NONE
                if (!slot.isItemValid(stack) || !slot.canTakeStack(player)) {
                    return SlotStatus.BLOCKED
                }
                var canInsert = false
                var hasSimilar = false
                for (i in 0..<slotInv.slotCount) {
                    val slotStack = slotInv.getStackInSlot(i)
                    if (slotStack.isEmpty) {
                        if (!canInsert && slotInv.canInsert(i, heldStack)) {
                            if (hasSimilar) { // short-circuit
                                return SlotStatus.CAN_INSERT_SIMILAR
                            }
                            canInsert = true
                        }
                    } else if (slotStack.stacksWith(heldStack)) {
                        if (canInsert || slotInv.canInsert(i, heldStack)) {
                            return SlotStatus.CAN_INSERT_SIMILAR
                        }
                        hasSimilar = true
                    }
                }
                return when {
                    hasSimilar -> SlotStatus.HAS_SIMILAR
                    canInsert -> SlotStatus.CAN_INSERT
                    else -> SlotStatus.BLOCKED
                }
            }

            fun invalidate() {
                statusCache = null
            }

            @ClientSide
            override fun runSyncTask(task: SyncTask) {
                super.runSyncTask(task)
                invalidate()
            }
        }

        private inner class HeldStackState(stack: ItemStack) : StackState(stack) {
            private var activeDrag: DragState? = null

            fun getActiveDragAction(): InvActionType? = activeDrag?.actionType

            fun resetMouseState() {
                activeDrag = null
            }

            @ClientSide
            override fun cloneFrom(other: StackState): Boolean {
                if (super.cloneFrom(other)) {
                    if (other is HeldStackState) {
                        activeDrag = other.activeDrag
                    }
                    return true
                } else {
                    return false
                }
            }

            @ClientSide
            fun handleMouseButton(gui: GuiContainer, slot: Slot): Boolean {
                if (Mouse.getEventButtonState()) {
                    if (piState != null) {
                        if (activeDrag == null) {
                            contHandler.mapSlotIndex(slot)?.let {
                                val newDrag = DragState(it)
                                handleSlotClick(slot, newDrag)
                                activeDrag = newDrag
                                return true
                            }
                        }
                    } else {
                        if (handleSlotClick(slot, null)) {
                            gui.ignoreMouseUp = true
                            return true
                        }
                    }
                } else if (activeDrag != null) {
                    activeDrag = null
                    return true
                }
                return false
            }

            @ClientSide
            fun handleMouseMovement(slot: Slot) {
                activeDrag?.let { drag ->
                    contHandler.mapSlotIndex(slot)?.let {
                        if (drag.checkSlot(it)) {
                            handleSlotClick(slot, drag)
                        }
                    }
                }
            }

            @ClientSide
            private fun handleSlotClick(slot: Slot, drag: DragState?): Boolean {
                if (isSlotBlocked(slot)) {
                    Minecraft.getMinecraft().soundHandler.playSound(
                        PositionedSoundRecord.getMasterRecord(PortableInvTools.defns.sounds.invFail, 0.6F)
                    )
                    return true
                }
                val slotStack = slot.stack
                if (piState != null) {
                    if (slotStack.isEmpty) {
                        if (drag?.checkActionType(InvActionType.PLACE) != false) {
                            contHandler.doPlace(slot, KeyModifier.CONTROL.isActive(KeyConflictContext.GUI))
                        }
                    } else {
                        if (drag?.checkActionType(InvActionType.PICK_UP) != false) {
                            contHandler.doPickUp(slot, KeyModifier.CONTROL.isActive(KeyConflictContext.GUI))
                        }
                    }
                    return true
                } else if (
                    !slotStack.isEmpty && slotStack.hasCapability(PortableInvTools.defns.capabilities.piState, null)
                ) {
                    if (drag?.checkActionType(InvActionType.INSERT) != false) {
                        contHandler.doInsert(slot, KeyModifier.CONTROL.isActive(KeyConflictContext.GUI))
                    }
                    return true
                } else {
                    return false
                }
            }

            @ClientSide
            fun handleScrollUp(slot: Slot): Boolean {
                if (isSlotBlocked(slot)) return true
                val slotStack = slot.stack
                if (piState != null) {
                    contHandler.doPlace(slot, KeyModifier.CONTROL.isActive(KeyConflictContext.GUI))
                    return true
                } else if (
                    !slotStack.isEmpty && slot.stack.hasCapability(PortableInvTools.defns.capabilities.piState, null)
                ) {
                    contHandler.doInsert(slot, KeyModifier.CONTROL.isActive(KeyConflictContext.GUI))
                    return true
                } else {
                    return false
                }
            }

            @ClientSide
            fun handleScrollDown(slot: Slot): Boolean {
                if (isSlotBlocked(slot)) return true
                val slotStack = slot.stack
                if (piState != null) {
                    contHandler.doPickUp(slot, KeyModifier.CONTROL.isActive(KeyConflictContext.GUI))
                    return true
                } else if (
                    !slotStack.isEmpty && slotStack.hasCapability(PortableInvTools.defns.capabilities.piState, null)
                ) {
                    contHandler.doExtract(slot, KeyModifier.CONTROL.isActive(KeyConflictContext.GUI))
                    return true
                } else {
                    return false
                }
            }

            @ClientSide
            fun tryCycleSelectedSlot(offset: Int): Boolean {
                val state = piState ?: return false
                val slotCount = state.clientWrapper.getClientInventory(player).slotCount
                if (slotCount <= 1) return false
                state.selectedSlot = (state.selectedSlot + offset).mod(slotCount)
                contHandler.doSelectSlot(null, state.selectedSlot)
                Minecraft.getMinecraft().soundHandler.playSound(
                    PositionedSoundRecord.getRecord(PortableInvTools.defns.sounds.invCycle(offset < 0), 1F, 0.35F)
                )
                return true
            }
        }
    }

    private class DragState(slotIndex: Int) {
        private val draggedSlots: IntSet = IntOpenHashSet()
        var actionType: InvActionType? = null
            private set

        init {
            draggedSlots.add(slotIndex)
        }

        fun checkSlot(slotIndex: Int): Boolean = draggedSlots.add(slotIndex)

        fun checkActionType(query: InvActionType): Boolean {
            if (actionType == null) {
                actionType = query
                return true
            } else {
                return actionType == query
            }
        }
    }

    private enum class SlotStatus(val overlay: GuiDrawable?) {
        NONE(null),
        BLOCKED(blockedIcon),
        HAS_SIMILAR(similarIcon),
        CAN_INSERT(insertIcon),
        CAN_INSERT_SIMILAR(insertSimilarIcon)
    }

    private class WindowSyncQueue {
        var heldStackTask: SyncTask? = null
        val slotTasks: Int2ObjectMap<SyncTask> = Int2ObjectOpenHashMap()
        var lastUpdate: Long = System.currentTimeMillis()

        private inline fun upsertTask(
            contSlotIndex: Int,
            create: () -> SyncTask,
            upsert: (SyncTask) -> SyncTask?
        ) {
            lastUpdate = System.currentTimeMillis()
            if (contSlotIndex < 0) {
                heldStackTask?.let { task ->
                    upsert(task)?.let {
                        heldStackTask = it
                    }
                    return
                }
                heldStackTask = create()
            } else {
                slotTasks[contSlotIndex]?.let { task ->
                    upsert(task)?.let {
                        slotTasks[contSlotIndex] = it
                    }
                    return
                }
                slotTasks[contSlotIndex] = create()
            }
        }

        fun enqueueSyncSelectedSlot(contSlotIndex: Int, selectedSlotIndex: Int) {
            upsertTask(
                contSlotIndex,
                { SyncTask.Selected(selectedSlotIndex) },
                {
                    it.selectedSlot = selectedSlotIndex
                    return@upsertTask null
                }
            )
        }

        fun enqueueSyncSlot(contSlotIndex: Int, piSlotIndex: Int, stack: ItemStack) {
            upsertTask(contSlotIndex, { SyncTask.Slots(piSlotIndex, stack) }, { it.upsertSlot(piSlotIndex, stack) })
        }

        fun enqueueSyncFull(contSlotIndex: Int, stacks: Array<ItemStack>) {
            upsertTask(contSlotIndex, { SyncTask.Full(stacks) }, { SyncTask.Full(stacks, it.selectedSlot) })
        }
    }

    private sealed interface SyncTask {
        var selectedSlot: Int

        fun upsertSlot(slotIndex: Int, stack: ItemStack): SyncTask?

        class Selected(override var selectedSlot: Int) : SyncTask {
            override fun upsertSlot(slotIndex: Int, stack: ItemStack): SyncTask = Slots(slotIndex, stack, selectedSlot)
        }

        class Slots(slotIndex: Int, stack: ItemStack, override var selectedSlot: Int = -1) : SyncTask {
            val slotTable: Int2ObjectMap<ItemStack> = Int2ObjectOpenHashMap()

            init {
                slotTable[slotIndex] = stack
            }

            override fun upsertSlot(slotIndex: Int, stack: ItemStack): SyncTask? {
                slotTable[slotIndex] = stack
                return null
            }
        }

        class Full(val stacks: Array<ItemStack>, override var selectedSlot: Int = -1) : SyncTask {
            override fun upsertSlot(slotIndex: Int, stack: ItemStack): SyncTask? {
                if (slotIndex >= 0 && slotIndex < stacks.size) {
                    stacks[slotIndex] = stack
                }
                return null
            }
        }
    }

    private interface ContainerHandler {
        fun getSlots(): List<Slot>

        fun mapSlotIndex(slot: Slot): Int?

        @ClientSide
        fun syncHeldStack(oldState: PortableInvState?, newState: PortableInvState?, stack: ItemStack)

        @ClientSide
        fun doPickUp(slot: Slot, precise: Boolean)

        @ClientSide
        fun doPlace(slot: Slot, precise: Boolean)

        @ClientSide
        fun doInsert(slot: Slot, precise: Boolean)

        @ClientSide
        fun doExtract(slot: Slot, precise: Boolean)

        @ClientSide
        fun doSelectSlot(slot: Slot?, piSlotIndex: Int)

        @ClientSide
        fun drawSlotOverlays(slotStates: Array<ContainerState.SlotState>, partialTicks: Float)

        class Remote(private val container: Container) : ContainerHandler {
            private val windowId: Int = if (container is ContainerPlayer) -2 else container.windowId

            override fun getSlots(): List<Slot> = container.inventorySlots

            override fun mapSlotIndex(slot: Slot): Int = slot.slotNumber

            @ClientSide
            override fun syncHeldStack(oldState: PortableInvState?, newState: PortableInvState?, stack: ItemStack) {
            }

            @ClientSide
            private fun sendPiAction(packetType: PacketType.C2S<C2SPiAction>, slot: Slot, precise: Boolean) {
                packetType.sendToServer(C2SPiAction(windowId, slot.slotNumber, precise))
            }

            @ClientSide
            override fun doPickUp(slot: Slot, precise: Boolean) {
                sendPiAction(PortableInvTools.defns.c2s.piPickUp, slot, precise)
            }

            @ClientSide
            override fun doPlace(slot: Slot, precise: Boolean) {
                sendPiAction(PortableInvTools.defns.c2s.piPlace, slot, precise)
            }

            @ClientSide
            override fun doInsert(slot: Slot, precise: Boolean) {
                sendPiAction(PortableInvTools.defns.c2s.piInsert, slot, precise)
            }

            @ClientSide
            override fun doExtract(slot: Slot, precise: Boolean) {
                sendPiAction(PortableInvTools.defns.c2s.piExtract, slot, precise)
            }

            @ClientSide
            override fun doSelectSlot(slot: Slot?, piSlotIndex: Int) {
                PortableInvTools.defns.c2s.piSelectSlot.sendToServer(
                    if (slot != null) {
                        C2SPiSelectSlot(windowId, slot.slotNumber, piSlotIndex)
                    } else {
                        C2SPiSelectSlot(-1, -1, piSlotIndex)
                    }
                )
            }

            @ClientSide
            override fun drawSlotOverlays(slotStates: Array<ContainerState.SlotState>, partialTicks: Float) {
                slotStates.forEach { state ->
                    state.status.overlay?.let {
                        it.drawFullSize(partialTicks, state.slot.xPos + 16 - it.width + 1, state.slot.yPos - 1)
                    }
                }
            }
        }

        // it's rather inefficient to sync with the server for every inventory interaction in creative mode, but it's
        // sometimes necessary for server-only inventories (e.g. ender pouches) and it saves us from having to duplicate
        // the inventory interaction logic between the server and client
        @ClientSide.Physical
        class Creative(
            private val container: GuiContainerCreative.ContainerCreative,
            private val player: EntityPlayer
        ) : ContainerHandler {
            private val remapping: Int2IntMap = Int2IntOpenHashMap()

            init {
                remapping.defaultReturnValue(-1)
                player.inventoryContainer.inventorySlots.forEachIndexed { slotIndex, slot ->
                    remapping.put(slot.slotIndex, slotIndex)
                }
            }

            override fun getSlots(): List<Slot> = player.inventoryContainer.inventorySlots

            private fun remapSlot(slot: Slot): Slot? =
                player.inventoryContainer.inventorySlots.getOrNull(remapping[slot.slotIndex])

            override fun mapSlotIndex(slot: Slot): Int? =
                if (slot.inventory === player.inventory) remapSlot(slot)?.slotNumber else null

            @ClientSide
            override fun syncHeldStack(oldState: PortableInvState?, newState: PortableInvState?, stack: ItemStack) {
                if (newState != null) {
                    PortableInvTools.defns.c2s.syncCreativeHeldStack.sendToServer(C2SSyncCreativeHeldStack(stack))
                } else if (oldState != null) {
                    PortableInvTools.defns.c2s.syncCreativeHeldStack
                        .sendToServer(C2SSyncCreativeHeldStack(ItemStack.EMPTY))
                }
            }

            @ClientSide
            private fun sendPiAction(packetType: PacketType.C2S<C2SCreativePiAction>, slot: Slot, precise: Boolean) {
                if (slot.inventory === player.inventory) {
                    remapSlot(slot)?.let {
                        packetType.sendToServer(C2SCreativePiAction(player.inventory.itemStack, it.slotNumber, precise))
                    }
                }
            }

            @ClientSide
            override fun doPickUp(slot: Slot, precise: Boolean) {
                sendPiAction(PortableInvTools.defns.c2s.piPickUpCreative, slot, precise)
            }

            @ClientSide
            override fun doPlace(slot: Slot, precise: Boolean) {
                sendPiAction(PortableInvTools.defns.c2s.piPlaceCreative, slot, precise)
            }

            @ClientSide
            override fun doInsert(slot: Slot, precise: Boolean) {
                sendPiAction(PortableInvTools.defns.c2s.piInsertCreative, slot, precise)
            }

            @ClientSide
            override fun doExtract(slot: Slot, precise: Boolean) {
                sendPiAction(PortableInvTools.defns.c2s.piExtractCreative, slot, precise)
            }

            @ClientSide
            override fun doSelectSlot(slot: Slot?, piSlotIndex: Int) {
                if (slot != null && slot.inventory === player.inventory) {
                    PortableInvTools.defns.c2s.piSelectSlot.sendToServer(
                        C2SPiSelectSlot(-2, (remapSlot(slot) ?: return).slotNumber, piSlotIndex)
                    )
                } // don't need to bother with syncing the item on the cursor, since it'll get synced on use later
            }

            @ClientSide
            override fun drawSlotOverlays(slotStates: Array<ContainerState.SlotState>, partialTicks: Float) {
                container.inventorySlots.forEach { slot ->
                    if (slot.inventory === player.inventory) {
                        remapSlot(slot)?.let { slotStates.getOrNull(it.slotNumber) }?.status?.overlay?.let {
                            it.drawFullSize(partialTicks, slot.xPos + 16 - it.width + 1, slot.yPos - 1)
                        }
                    }
                }
            }
        }
    }
}
