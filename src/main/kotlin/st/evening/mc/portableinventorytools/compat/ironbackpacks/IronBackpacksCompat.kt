package st.evening.mc.portableinventorytools.compat.ironbackpacks

import gr8pefish.ironbackpacks.core.RegistrarIronBackpacks
import st.evening.mc.portableinventorytools.service.AdaptorRegistrationEvent
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.util.game.RequireMod

object IronBackpacksCompat {
    const val MOD_ID: String = "ironbackpacks"

    @RequireMod(MOD_ID)
    fun init(reg: ModRegistrar) {
        reg.on<AdaptorRegistrationEvent> { event ->
            event.registry.registerItemAdaptor(RegistrarIronBackpacks.BACKPACK, IronBackpacksBackpackAdaptor)
        }
    }
}
