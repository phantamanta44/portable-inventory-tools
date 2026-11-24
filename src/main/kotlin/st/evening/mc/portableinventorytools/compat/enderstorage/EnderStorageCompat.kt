package st.evening.mc.portableinventorytools.compat.enderstorage

import codechicken.enderstorage.init.ModItems
import st.evening.mc.portableinventorytools.service.AdaptorRegistrationEvent
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.util.game.RequireMod

object EnderStorageCompat {
    const val MOD_ID: String = "enderstorage"

    @RequireMod(MOD_ID)
    fun init(reg: ModRegistrar) {
        reg.on<AdaptorRegistrationEvent> { event ->
            event.registry.registerItemAdaptor(ModItems.enderPouch, EnderStoragePouchAdaptor)
        }
    }
}
