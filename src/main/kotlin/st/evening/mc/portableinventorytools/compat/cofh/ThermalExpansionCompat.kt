package st.evening.mc.portableinventorytools.compat.cofh

import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.ForgeRegistries
import st.evening.mc.portableinventorytools.service.AdaptorRegistrationEvent
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.util.game.RequireMod

object ThermalExpansionCompat {
    const val MOD_ID: String = "thermalexpansion"

    fun resource(path: String): ResourceLocation = ResourceLocation(MOD_ID, path)

    @RequireMod(MOD_ID)
    fun init(reg: ModRegistrar) {
        reg.on<AdaptorRegistrationEvent> { event ->
            ForgeRegistries.ITEMS.getValue(resource("satchel"))?.let {
                event.registry.registerItemAdaptor(it, ThermalExpansionSatchelAdaptor)
            }
        }
    }
}
