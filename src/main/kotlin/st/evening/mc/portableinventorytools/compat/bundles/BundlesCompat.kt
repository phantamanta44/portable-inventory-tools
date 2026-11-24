package st.evening.mc.portableinventorytools.compat.bundles

import st.evening.mc.portableinventorytools.service.AdaptorRegistrationEvent
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.util.game.RequireMod
import surreal.bundles.Bundles

object BundlesCompat {
    const val MOD_ID: String = "bundles"

    @RequireMod(MOD_ID)
    fun init(reg: ModRegistrar) {
        reg.on<AdaptorRegistrationEvent> { event ->
            event.registry.registerItemAdaptor(Bundles.BUNDLE, BundlesBundleAdaptor)
        }
    }
}
