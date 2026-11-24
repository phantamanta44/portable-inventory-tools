package st.evening.mc.portableinventorytools.init

import net.minecraft.util.SoundEvent
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.soundEvent

class PitSounds internal constructor(reg: ModRegistrar) {
    companion object {
        const val INV_INTERACT: String = "inventory.interact"
    }

    val invFail: SoundEvent by reg.soundEvent("$INV_INTERACT.fail")
    val invPickUp: SoundEvent by reg.soundEvent("$INV_INTERACT.pick_up")
    val invPickUpPrecise: SoundEvent by reg.soundEvent("$INV_INTERACT.pick_up.precise")
    fun invPickUp(precise: Boolean): SoundEvent = if (precise) invPickUpPrecise else invPickUp
    val invPlace: SoundEvent by reg.soundEvent("$INV_INTERACT.place")
    val invPlacePrecise: SoundEvent by reg.soundEvent("$INV_INTERACT.place.precise")
    fun invPlace(precise: Boolean): SoundEvent = if (precise) invPlacePrecise else invPlace
    val invInsert: SoundEvent by reg.soundEvent("$INV_INTERACT.insert")
    val invInsertPrecise: SoundEvent by reg.soundEvent("$INV_INTERACT.insert.precise")
    fun invInsert(precise: Boolean): SoundEvent = if (precise) invInsertPrecise else invInsert
    val invExtract: SoundEvent by reg.soundEvent("$INV_INTERACT.extract")
    val invExtractPrecise: SoundEvent by reg.soundEvent("$INV_INTERACT.extract.precise")
    fun invExtract(precise: Boolean): SoundEvent = if (precise) invExtractPrecise else invExtract
    val invCycleUp: SoundEvent by reg.soundEvent("$INV_INTERACT.cycle.up")
    val invCycleDown: SoundEvent by reg.soundEvent("$INV_INTERACT.cycle.down")
    fun invCycle(up: Boolean): SoundEvent = if (up) invCycleUp else invCycleDown
}
