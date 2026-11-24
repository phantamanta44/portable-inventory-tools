package st.evening.mc.portableinventorytools.compat.immeng

import blusunrize.immersiveengineering.common.IEContent
import net.minecraft.item.Item
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.inventory.prefab.RejectAdaptor
import st.evening.mc.portableinventorytools.service.AdaptorRegistrationEvent
import st.evening.mc.portableinventorytools.service.AdaptorRegistry
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.game.RequireMod

object ImmEngCompat {
    const val MOD_ID: String = "immersiveengineering"

    @RequireMod(MOD_ID)
    fun init(reg: ModRegistrar) {
        reg.on<AdaptorRegistrationEvent> { event ->
            event.registry.run {
                registerItemAdaptor(IEContent.itemToolbox, ToolboxAdaptor)
                // immeng uses pseudo-invs to store upgrades installed in tools; we don't want to interact with these
                reject("chemthrower", IEContent.itemChemthrower)
                reject("drill", IEContent.itemDrill)
                reject("railgun", IEContent.itemRailgun)
                reject("shield", IEContent.itemShield)
                reject("skyhook", IEContent.itemSkyhook)
                // we'll also leave the revolver alone since it's supposed to be slow and inconvenient to reload
                reject("revolver", IEContent.itemRevolver)
                reject("speedloader", IEContent.itemSpeedloader)
            }
        }
    }

    private fun AdaptorRegistry.reject(name: String, item: Item) {
        registerItemAdaptor(item, RejectAdaptor(PortableInvTools.resource("${MOD_ID}_$name")))
    }
}
