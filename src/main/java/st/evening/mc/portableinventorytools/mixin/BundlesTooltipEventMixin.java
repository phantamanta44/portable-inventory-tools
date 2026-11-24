package st.evening.mc.portableinventorytools.mixin;

import net.minecraftforge.client.event.RenderTooltipEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import st.evening.mc.portableinventorytools.PortableInvTools;
import surreal.bundles.client.TooltipEvent;

@Mixin(value = TooltipEvent.class, remap = false)
abstract class BundlesTooltipEventMixin {
    @Inject(
            method = "tooltipEventPre(Lnet/minecraftforge/client/event/RenderTooltipEvent$Pre;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    public void tooltipEventPre(final RenderTooltipEvent.Pre event, final CallbackInfo ci) {
        if (PortableInvTools.INSTANCE.getConfig().getDisableOtherModInvFeatures()) {
            ci.cancel();
        }
    }
}
