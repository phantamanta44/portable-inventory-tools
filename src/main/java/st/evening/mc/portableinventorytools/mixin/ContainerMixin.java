package st.evening.mc.portableinventorytools.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import st.evening.mc.portableinventorytools.PortableInvTools;
import surreal.bundles.items.ItemBundle;

@Mixin(value = Container.class)
abstract class ContainerMixin {
    @SuppressWarnings("MixinAnnotationTarget") // mixing into bundles ContainerMixin
    @ModifyExpressionValue(
            method = "slotClick(IILnet/minecraft/inventory/ClickType;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
            at = @At("MIXINEXTRAS:EXPRESSION")
    )
    @Definition(id = "ItemBundle", type = ItemBundle.class, remap = false)
    @Expression("? instanceof ItemBundle")
    public boolean slotClick_ItemBundle(final boolean original) {
        return original && !PortableInvTools.INSTANCE.getConfig().getDisableOtherModInvFeatures();
    }
}
