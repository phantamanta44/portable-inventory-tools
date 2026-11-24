package st.evening.mc.portableinventorytools.init

import net.minecraftforge.common.capabilities.Capability
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.capability

class PitCapabilities internal constructor(reg: ModRegistrar) {
    val piState: Capability<PortableInvState> by reg.capability { PortableInvState.Noop }
}
