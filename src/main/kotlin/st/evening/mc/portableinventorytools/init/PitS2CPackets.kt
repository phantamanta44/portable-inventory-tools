package st.evening.mc.portableinventorytools.init

import net.minecraft.client.Minecraft
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.network.NetHandlerPlayClient
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketBuffer
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.util.InvActionType
import st.evening.mc.prelude.api.data.ser.NetworkSerializer
import st.evening.mc.prelude.api.network.PacketType
import st.evening.mc.prelude.api.network.S2CMessageHandler
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.s2cPacket
import st.evening.mc.prelude.api.util.data.BitVector
import st.evening.mc.prelude.api.util.data.readAndIterateBitVector
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.network.onClientThread
import kotlin.random.Random

class PitS2CPackets internal constructor(reg: ModRegistrar) {
    val piSyncSelectedSlot: PacketType.S2C<S2CPortableInvSyncSelectedSlot>
        by reg.s2cPacket(S2CPortableInvSyncSelectedSlot.Serializer, S2CPortableInvSyncSelectedSlot.Handler)
    val piWrapperSyncSlot: PacketType.S2C<S2CPortableInvWrapperSyncSlot>
        by reg.s2cPacket(S2CPortableInvWrapperSyncSlot.Serializer, S2CPortableInvWrapperSyncSlot.Handler)
    val piWrapperSyncFull: PacketType.S2C<S2CPortableInvWrapperSyncFull>
        by reg.s2cPacket(S2CPortableInvWrapperSyncFull.Serializer, S2CPortableInvWrapperSyncFull.Handler)
    val piActionFeedback: PacketType.S2C<S2CPiActionFeedback>
        by reg.s2cPacket(S2CPiActionFeedback.Serializer, S2CPiActionFeedback.Handler)
}

class S2CPortableInvSyncSelectedSlot(val windowId: Int, val contSlotIndex: Int, val selectedSlotIndex: Int) {
    object Serializer : NetworkSerializer<S2CPortableInvSyncSelectedSlot> {
        override fun serializeToNetwork(buf: PacketBuffer, x: S2CPortableInvSyncSelectedSlot) {
            buf.writeByte(x.windowId)
            buf.writeVarInt(x.contSlotIndex)
            buf.writeVarInt(x.selectedSlotIndex)
        }

        override fun deserializeFromNetwork(buf: PacketBuffer): S2CPortableInvSyncSelectedSlot =
            S2CPortableInvSyncSelectedSlot(buf.readByte().toInt(), buf.readVarInt(), buf.readVarInt())
    }

    object Handler : S2CMessageHandler<S2CPortableInvSyncSelectedSlot> {
        @ClientSide.Strong
        override fun handleS2CMessage(netHandler: NetHandlerPlayClient, message: S2CPortableInvSyncSelectedSlot) {
            netHandler.onClientThread {
                PortableInvTools.defns.clientContainerSvc.enqueueSyncSelectedSlot(message)
            }
        }
    }
}

class S2CPortableInvWrapperSyncSlot(
    val windowId: Int,
    val contSlotIndex: Int,
    val piSlotIndex: Int,
    val stack: ItemStack
) {
    object Serializer : NetworkSerializer<S2CPortableInvWrapperSyncSlot> {
        override fun serializeToNetwork(buf: PacketBuffer, x: S2CPortableInvWrapperSyncSlot) {
            buf.writeByte(x.windowId)
            buf.writeVarInt(x.contSlotIndex)
            buf.writeVarInt(x.piSlotIndex)
            buf.writeItemStack(x.stack)
        }

        override fun deserializeFromNetwork(buf: PacketBuffer): S2CPortableInvWrapperSyncSlot =
            S2CPortableInvWrapperSyncSlot(
                buf.readByte().toInt(), buf.readVarInt(), buf.readVarInt(), buf.readItemStack()
            )
    }

    object Handler : S2CMessageHandler<S2CPortableInvWrapperSyncSlot> {
        @ClientSide.Strong
        override fun handleS2CMessage(netHandler: NetHandlerPlayClient, message: S2CPortableInvWrapperSyncSlot) {
            netHandler.onClientThread {
                PortableInvTools.defns.clientContainerSvc.enqueueSyncSlot(message)
            }
        }
    }
}

class S2CPortableInvWrapperSyncFull(val windowId: Int, val contSlotIndex: Int, val stacks: Array<ItemStack>) {
    object Serializer : NetworkSerializer<S2CPortableInvWrapperSyncFull> {
        override fun serializeToNetwork(buf: PacketBuffer, x: S2CPortableInvWrapperSyncFull) {
            buf.writeByte(x.windowId)
            buf.writeVarInt(x.contSlotIndex)
            buf.writeVarInt(x.stacks.size)
            val mask = BitVector(x.stacks.size)
            x.stacks.forEachIndexed { slotIndex, stack ->
                if (!stack.isEmpty) {
                    mask.set(slotIndex)
                }
            }
            mask.writeToNetwork(buf)
            x.stacks.forEach {
                if (!it.isEmpty) {
                    buf.writeItemStack(it)
                }
            }
        }

        override fun deserializeFromNetwork(buf: PacketBuffer): S2CPortableInvWrapperSyncFull {
            val windowId = buf.readByte()
            val contSlotIndex = buf.readVarInt()
            val stacks = Array(buf.readVarInt()) { ItemStack.EMPTY }
            buf.readAndIterateBitVector(stacks.size) {
                stacks[it] = buf.readItemStack()
            }
            return S2CPortableInvWrapperSyncFull(windowId.toInt(), contSlotIndex, stacks)
        }
    }

    object Handler : S2CMessageHandler<S2CPortableInvWrapperSyncFull> {
        @ClientSide.Strong
        override fun handleS2CMessage(netHandler: NetHandlerPlayClient, message: S2CPortableInvWrapperSyncFull) {
            netHandler.onClientThread {
                PortableInvTools.defns.clientContainerSvc.enqueueSyncFull(message)
            }
        }
    }
}

class S2CPiActionFeedback(val type: InvActionType, val precise: Boolean, val success: Boolean) {
    object Serializer : NetworkSerializer<S2CPiActionFeedback> {
        override fun serializeToNetwork(buf: PacketBuffer, x: S2CPiActionFeedback) {
            buf.writeByte(x.type.ordinal or (if (x.precise) 0x80 else 0) or (if (x.success) 0x40 else 0))
        }

        override fun deserializeFromNetwork(buf: PacketBuffer): S2CPiActionFeedback {
            val k = buf.readByte().toInt()
            return S2CPiActionFeedback(InvActionType.entries[k and 0x0F], k and 0x80 != 0, k and 0x40 != 0)
        }
    }

    object Handler : S2CMessageHandler<S2CPiActionFeedback> {
        @ClientSide.Strong
        override fun handleS2CMessage(netHandler: NetHandlerPlayClient, message: S2CPiActionFeedback) {
            netHandler.onClientThread {
                if (message.success) {
                    Minecraft.getMinecraft().soundHandler.playSound(
                        PositionedSoundRecord.getRecord(
                            when (message.type) {
                                InvActionType.PICK_UP -> PortableInvTools.defns.sounds.invPickUp(message.precise)
                                InvActionType.PLACE -> PortableInvTools.defns.sounds.invPlace(message.precise)
                                InvActionType.INSERT -> PortableInvTools.defns.sounds.invInsert(message.precise)
                                InvActionType.EXTRACT -> PortableInvTools.defns.sounds.invExtract(message.precise)
                            },
                            0.95F + 0.1F * Random.nextFloat(), 0.6F
                        )
                    )
                } else if (!message.precise) {
                    Minecraft.getMinecraft().soundHandler.playSound(
                        PositionedSoundRecord.getMasterRecord(PortableInvTools.defns.sounds.invFail, 0.6F)
                    )
                }
            }
        }
    }
}
