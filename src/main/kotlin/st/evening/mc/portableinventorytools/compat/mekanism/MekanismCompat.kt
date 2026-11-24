package st.evening.mc.portableinventorytools.compat.mekanism

import mekanism.common.MekanismBlocks
import st.evening.mc.portableinventorytools.service.AdaptorRegistrationEvent
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.util.game.RequireMod

object MekanismCompat {
    const val MOD_ID: String = "mekanism"

    @RequireMod(MOD_ID)
    fun init(reg: ModRegistrar) {
        reg.on<AdaptorRegistrationEvent> { event ->
            event.registry.registerItemAdaptor(MekanismBlocks.MachineBlock, 13, MekanismPersonalChestAdaptor)
        }
    }
}
