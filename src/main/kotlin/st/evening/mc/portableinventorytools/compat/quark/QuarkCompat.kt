package st.evening.mc.portableinventorytools.compat.quark

import net.minecraftforge.fml.common.event.FMLInitializationEvent
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.util.game.RequireMod
import vazkii.quark.base.module.ModuleLoader

object QuarkCompat {
    const val MOD_ID: String = "quark"

    @RequireMod(MOD_ID)
    fun init(reg: ModRegistrar) {
        if (PortableInvTools.config.disableOtherModInvFeatures) {
            reg.on<FMLInitializationEvent> {
                disableInvFeatures()
            }
        }
    }

    @RequireMod(MOD_ID)
    private fun disableInvFeatures() {
        if (
            disableFeature("client", "Shulker box tooltip") or
            disableFeature("management", "Right click add to shulker box")
        ) {
            ModuleLoader.forEachEnabled { it.setupConfig() }
        }
    }

    @RequireMod(MOD_ID)
    private fun disableFeature(category: String, feature: String): Boolean {
        if (ModuleLoader.config.hasKey(category, feature)) {
            val prop = ModuleLoader.config.get(category, feature, false)
            if (prop.boolean) {
                prop.setValue(false)
                return true
            }
        }
        return false
    }
}
