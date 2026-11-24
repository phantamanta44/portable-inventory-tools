package st.evening.mc.portableinventorytools.capability

import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.NoopPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.PortableInvAdaptor
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.ServerSide

interface PortableInvState {
    val adaptor: PortableInvAdaptor

    var selectedSlot: Int

    @ServerSide
    val serverWrapper: ServerPortableInvWrapper

    @ClientSide.Strong
    val clientWrapper: ClientPortableInvWrapper

    object Noop : PortableInvState {
        override val adaptor: PortableInvAdaptor
            get() = PortableInvAdaptor.Noop

        override var selectedSlot: Int
            get() = 0
            set(_) {}

        @ServerSide
        override val serverWrapper: ServerPortableInvWrapper
            get() = NoopPortableInvWrapper

        @ClientSide.Strong
        override val clientWrapper: ClientPortableInvWrapper
            get() = NoopPortableInvWrapper
    }
}
