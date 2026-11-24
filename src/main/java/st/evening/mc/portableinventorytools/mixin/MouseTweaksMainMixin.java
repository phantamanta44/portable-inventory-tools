package st.evening.mc.portableinventorytools.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import st.evening.mc.portableinventorytools.PortableInvTools;
import st.evening.mc.prelude.api.util.game.ClientSide;
import yalter.mousetweaks.Main;
import yalter.mousetweaks.impl.IGuiScreenHandler;

// bundles backport has a really nasty overwrite for onUpdateInGui, so we set a higher priority to get around it
// in principle, this shouldn't be a problem because we can do the inventory handling for bundles instead
@Mixin(value = Main.class, priority = 1100, remap = false)
abstract class MouseTweaksMainMixin {
    @WrapOperation(
            method = "onUpdateInGui(Lnet/minecraft/client/gui/GuiScreen;)V",
            at = @At(
                    value = "INVOKE", // note this only works with mt-unofficial because they moved some classes around
                    target = "Lyalter/mousetweaks/impl/IGuiScreenHandler;isIgnored(Lnet/minecraft/inventory/Slot;)Z"
            )
    )
    @ClientSide.Strong
    private static boolean onUpdateInGui_isIgnored(final IGuiScreenHandler instance, final Slot slot,
                                                   final Operation<Boolean> original) {
        return original.call(instance, slot)
                || PortableInvTools.INSTANCE.getDefns().getClientContainerSvc().isMouseGrabbed(slot);
    }

    @WrapOperation(
            method = "handleWheel(Lnet/minecraft/inventory/Slot;)V",
            at = @At(
                    value = "INVOKE", // ditto^
                    target = "Lyalter/mousetweaks/impl/IGuiScreenHandler;isIgnored(Lnet/minecraft/inventory/Slot;)Z"
            )
    )
    @ClientSide.Strong
    private static boolean handleWheel_isIgnored(final IGuiScreenHandler instance, final Slot slot,
                                                 final Operation<Boolean> original) {
        return original.call(instance, slot)
                || PortableInvTools.INSTANCE.getDefns().getClientContainerSvc().isMouseGrabbed(slot);
    }
}
