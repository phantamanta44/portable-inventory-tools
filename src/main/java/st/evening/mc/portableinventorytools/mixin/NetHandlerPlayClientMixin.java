package st.evening.mc.portableinventorytools.mixin;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketSetSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import st.evening.mc.portableinventorytools.PortableInvTools;

@Mixin(NetHandlerPlayClient.class)
abstract class NetHandlerPlayClientMixin {
    @Inject(
            method = "handleSetSlot(Lnet/minecraft/network/play/server/SPacketSetSlot;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;setItemStack(Lnet/minecraft/item/ItemStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    public void handleSetSlot_setItemStack(final SPacketSetSlot packetIn, final CallbackInfo ci) {
        PortableInvTools.INSTANCE.getDefns().getClientContainerSvc().scheduleInterruptTick();
    }
}
