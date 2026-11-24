package st.evening.mc.portableinventorytools.compat.botania

import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.ForgeRegistries
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.inventory.prefab.SyncedItemHandlerItemAdaptor
import st.evening.mc.portableinventorytools.service.AdaptorRegistrationEvent
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.game.RequireMod

object BotaniaCompat {
    const val MOD_ID: String = "botania"

    fun resource(path: String): ResourceLocation = ResourceLocation(MOD_ID, path)

    @RequireMod(MOD_ID)
    fun init(reg: ModRegistrar) {
        reg.on<AdaptorRegistrationEvent> { event ->
            ForgeRegistries.ITEMS.getValue(resource("flowerbag"))?.let {
                event.registry.registerItemAdaptor(
                    it, SyncedItemHandlerItemAdaptor(PortableInvTools.resource("${MOD_ID}_flower_pouch"))
                )
            }
        }
    }
}
