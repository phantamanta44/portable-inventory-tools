package st.evening.mc.portableinventorytools.compat.textiles

import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.util.game.RequireMod
import surreal.textiles.ModConfig

object TextilesCompat {
    const val MOD_ID: String = "textiles"

    @RequireMod(MOD_ID)
    fun init(reg: ModRegistrar) {
        if (PortableInvTools.config.disableOtherModInvFeatures) {
            reg.on<FMLPostInitializationEvent> {
                disableInvFeatures()
            }
        }
    }

    @RequireMod(MOD_ID)
    private fun disableInvFeatures() {
        ModConfig.basket.inventoryInteraction = false
        ModConfig.sack.inventoryInteraction = false
    }
}
