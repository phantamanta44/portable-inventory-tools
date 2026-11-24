package st.evening.mc.portableinventorytools.init

import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.inventory.Container
import net.minecraft.inventory.ContainerPlayer
import net.minecraft.item.ItemStack
import net.minecraft.network.NetHandlerPlayServer
import net.minecraft.network.PacketBuffer
import net.minecraftforge.common.util.PacketUtil
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.prelude.api.data.ser.NetworkSerializer
import st.evening.mc.prelude.api.network.C2SMessageHandler
import st.evening.mc.prelude.api.network.PacketType
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.c2sPacket
import st.evening.mc.prelude.api.util.collection.getCyclic
import st.evening.mc.prelude.api.util.game.ServerSide
import st.evening.mc.prelude.api.util.network.onServerThread

class PitC2SPackets internal constructor(reg: ModRegistrar) {
    val piPickUp: PacketType.C2S<C2SPiAction> by reg.c2sPacket(C2SPiAction.Serializer, C2SPiAction.PickUpHandler)
    val piPlace: PacketType.C2S<C2SPiAction> by reg.c2sPacket(C2SPiAction.Serializer, C2SPiAction.PlaceHandler)
    val piInsert: PacketType.C2S<C2SPiAction> by reg.c2sPacket(C2SPiAction.Serializer, C2SPiAction.InsertHandler)
    val piExtract: PacketType.C2S<C2SPiAction> by reg.c2sPacket(C2SPiAction.Serializer, C2SPiAction.ExtractHandler)
    val piSelectSlot: PacketType.C2S<C2SPiSelectSlot>
        by reg.c2sPacket(C2SPiSelectSlot.Serializer, C2SPiSelectSlot.Handler)

    val piPickUpCreative: PacketType.C2S<C2SCreativePiAction>
        by reg.c2sPacket(C2SCreativePiAction.Serializer, C2SCreativePiAction.PickUpHandler)
    val piPlaceCreative: PacketType.C2S<C2SCreativePiAction>
        by reg.c2sPacket(C2SCreativePiAction.Serializer, C2SCreativePiAction.PlaceHandler)
    val piInsertCreative: PacketType.C2S<C2SCreativePiAction>
        by reg.c2sPacket(C2SCreativePiAction.Serializer, C2SCreativePiAction.InsertHandler)
    val piExtractCreative: PacketType.C2S<C2SCreativePiAction>
        by reg.c2sPacket(C2SCreativePiAction.Serializer, C2SCreativePiAction.ExtractHandler)
    val syncCreativeHeldStack: PacketType.C2S<C2SSyncCreativeHeldStack>
        by reg.c2sPacket(C2SSyncCreativeHeldStack.Serializer, C2SSyncCreativeHeldStack.Handler)
}

class C2SPiAction(val windowId: Int, val slotIndex: Int, val precise: Boolean) {
    private fun isValid(cont: Container): Boolean =
        slotIndex >= 0 &&
            (if (windowId == -2) cont is ContainerPlayer else windowId == cont.windowId) &&
            slotIndex < cont.inventorySlots.size

    @ServerSide
    private inline fun handle(
        netHandler: NetHandlerPlayServer,
        crossinline action: (EntityPlayerMP, Container) -> Unit
    ) {
        netHandler.onServerThread {
            val player = netHandler.player
            val cont = player.openContainer
            if (isValid(cont)) {
                action(player, cont)
            }
        }
    }

    object Serializer : NetworkSerializer<C2SPiAction> {
        override fun serializeToNetwork(buf: PacketBuffer, x: C2SPiAction) {
            buf.writeByte((x.windowId and 0x7F) or if (x.precise) 0x80 else 0)
            buf.writeVarInt(x.slotIndex)
        }

        override fun deserializeFromNetwork(buf: PacketBuffer): C2SPiAction {
            val mask = buf.readByte().toInt()
            return C2SPiAction((mask and 0x7F) shl 25 shr 25, buf.readVarInt(), mask and 0x80 != 0)
        }
    }

    object PickUpHandler : C2SMessageHandler<C2SPiAction> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SPiAction) {
            message.handle(netHandler) { player, cont ->
                PortableInvTools.defns.serverContainerSvc.handlePickUp(player, cont, message.slotIndex, message.precise)
            }
        }
    }

    object PlaceHandler : C2SMessageHandler<C2SPiAction> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SPiAction) {
            message.handle(netHandler) { player, cont ->
                PortableInvTools.defns.serverContainerSvc.handlePlace(player, cont, message.slotIndex, message.precise)
            }
        }
    }

    object InsertHandler : C2SMessageHandler<C2SPiAction> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SPiAction) {
            message.handle(netHandler) { player, cont ->
                PortableInvTools.defns.serverContainerSvc.handleInsert(player, cont, message.slotIndex, message.precise)
            }
        }
    }

    object ExtractHandler : C2SMessageHandler<C2SPiAction> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SPiAction) {
            message.handle(netHandler) { player, cont ->
                PortableInvTools.defns.serverContainerSvc.handleExtract(
                    player, cont, message.slotIndex, message.precise
                )
            }
        }
    }
}

class C2SPiSelectSlot(val windowId: Int, val slotIndex: Int, val piSlotIndex: Int) {
    object Serializer : NetworkSerializer<C2SPiSelectSlot> {
        override fun serializeToNetwork(buf: PacketBuffer, x: C2SPiSelectSlot) {
            buf.writeByte(x.windowId)
            buf.writeVarInt(x.slotIndex + 1) // -1 means target the held stack
            buf.writeByte(x.piSlotIndex)
        }

        override fun deserializeFromNetwork(buf: PacketBuffer): C2SPiSelectSlot =
            C2SPiSelectSlot(buf.readByte().toInt(), buf.readVarInt() - 1, buf.readByte().toInt())
    }

    object Handler : C2SMessageHandler<C2SPiSelectSlot> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SPiSelectSlot) {
            netHandler.onServerThread {
                val stack: ItemStack
                when (message.windowId) {
                    -1 -> stack = netHandler.player.inventory.itemStack
                    -2 -> stack = netHandler.player.inventoryContainer.inventorySlots.getCyclic(message.slotIndex).stack
                    else -> {
                        val cont = netHandler.player.openContainer
                        if (cont.windowId != message.windowId || cont.inventorySlots.isEmpty()) return@onServerThread
                        stack = cont.inventorySlots.getCyclic(message.slotIndex).stack
                    }
                }
                if (stack.isEmpty) return@onServerThread
                stack.getCapability(PortableInvTools.defns.capabilities.piState, null)?.let {
                    val slotCount = it.serverWrapper.getServerInventory(netHandler.player).slotCount
                    if (slotCount > 0) {
                        it.selectedSlot = message.piSlotIndex.mod(slotCount)
                        PortableInvTools.defns.serverPlayerTrackerSvc.updateKnownSelectedSlot(
                            netHandler.player, message.slotIndex, it.selectedSlot
                        )
                    }
                }
            }
        }
    }
}

class C2SCreativePiAction(val heldStack: ItemStack, val slotIndex: Int, val precise: Boolean) {
    @ServerSide
    private inline fun handle(
        netHandler: NetHandlerPlayServer,
        crossinline action: (EntityPlayerMP, Container) -> Unit
    ) {
        netHandler.onServerThread {
            val player = netHandler.player
            if (player.capabilities.isCreativeMode) {
                player.inventory.itemStack = heldStack.copy()
                action(player, player.inventoryContainer)
                if (!player.inventory.itemStack.hasCapability(PortableInvTools.defns.capabilities.piState, null)) {
                    player.inventory.itemStack = ItemStack.EMPTY
                }
            }
        }
    }

    object Serializer : NetworkSerializer<C2SCreativePiAction> {
        override fun serializeToNetwork(buf: PacketBuffer, x: C2SCreativePiAction) {
            PacketUtil.writeItemStackFromClientToServer(buf, x.heldStack)
            buf.writeVarInt(x.slotIndex).writeBoolean(x.precise)
        }

        override fun deserializeFromNetwork(buf: PacketBuffer): C2SCreativePiAction =
            C2SCreativePiAction(buf.readItemStack(), buf.readVarInt(), buf.readBoolean())
    }

    object PickUpHandler : C2SMessageHandler<C2SCreativePiAction> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SCreativePiAction) {
            message.handle(netHandler) { player, cont ->
                PortableInvTools.defns.serverContainerSvc.handlePickUp(player, cont, message.slotIndex, message.precise)
            }
        }
    }

    object PlaceHandler : C2SMessageHandler<C2SCreativePiAction> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SCreativePiAction) {
            message.handle(netHandler) { player, cont ->
                PortableInvTools.defns.serverContainerSvc.handlePlace(player, cont, message.slotIndex, message.precise)
            }
        }
    }

    object InsertHandler : C2SMessageHandler<C2SCreativePiAction> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SCreativePiAction) {
            message.handle(netHandler) { player, cont ->
                PortableInvTools.defns.serverContainerSvc.handleInsert(player, cont, message.slotIndex, message.precise)
            }
        }
    }

    object ExtractHandler : C2SMessageHandler<C2SCreativePiAction> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SCreativePiAction) {
            message.handle(netHandler) { player, cont ->
                PortableInvTools.defns.serverContainerSvc.handleExtract(
                    player, cont, message.slotIndex, message.precise
                )
            }
        }
    }
}

class C2SSyncCreativeHeldStack(val stack: ItemStack) {
    object Serializer : NetworkSerializer<C2SSyncCreativeHeldStack> {
        override fun serializeToNetwork(buf: PacketBuffer, x: C2SSyncCreativeHeldStack) {
            PacketUtil.writeItemStackFromClientToServer(buf, x.stack)
        }

        override fun deserializeFromNetwork(buf: PacketBuffer): C2SSyncCreativeHeldStack =
            C2SSyncCreativeHeldStack(buf.readItemStack())
    }

    object Handler : C2SMessageHandler<C2SSyncCreativeHeldStack> {
        @ServerSide
        override fun handleC2SMessage(netHandler: NetHandlerPlayServer, message: C2SSyncCreativeHeldStack) {
            netHandler.onServerThread {
                if (netHandler.player.capabilities.isCreativeMode) {
                    netHandler.player.inventory.itemStack = message.stack.copy()
                }
            }
        }
    }
}
