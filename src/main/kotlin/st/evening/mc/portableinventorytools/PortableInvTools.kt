package st.evening.mc.portableinventorytools

import org.apache.logging.log4j.Logger
import st.evening.mc.portableinventorytools.PortableInvTools.defns
import st.evening.mc.portableinventorytools.compat.PitCompat
import st.evening.mc.portableinventorytools.init.PitC2SPackets
import st.evening.mc.portableinventorytools.init.PitCapabilities
import st.evening.mc.portableinventorytools.init.PitS2CPackets
import st.evening.mc.portableinventorytools.init.PitSounds
import st.evening.mc.portableinventorytools.service.AdaptorService
import st.evening.mc.portableinventorytools.service.ClientContainerService
import st.evening.mc.portableinventorytools.service.ClientRenderService
import st.evening.mc.portableinventorytools.service.ServerContainerService
import st.evening.mc.portableinventorytools.service.ServerPlayerTrackerService
import st.evening.mc.prelude.api.PreludeMod
import st.evening.mc.prelude.api.config.json.JsonConfig
import st.evening.mc.prelude.api.config.json.defaultInit
import st.evening.mc.prelude.api.newModLogger
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.ServerSide
import st.evening.mc.prelude.api.util.game.absurdPhysicalSide
import st.evening.mc.prelude.api.util.game.physicalSided
import st.evening.mc.prelude.loader.PreludeLoader

@PreludeLoader.Load(PitConsts.MOD_ID)
object PortableInvTools : PreludeMod<PitDefinitions> {
    override val modId: String
        get() = PitConsts.MOD_ID

    val logger: Logger = newModLogger()
    private val configData: JsonConfig = JsonConfig(PitConsts.MOD_ID)
    val config: PitConfig = PitConfig(configData.root)
    val defns: PitDefinitions by PreludeLoader.inject()

    override fun register(reg: ModRegistrar): PitDefinitions {
        configData.defaultInit(reg, changeCallback = { defns.onConfigReloaded() })
        return physicalSided({ PitDefinitions.Server(reg) }, { PitDefinitions.Client(reg) })
    }
}

sealed class PitDefinitions(reg: ModRegistrar) {
    val s2c: PitS2CPackets = PitS2CPackets(reg)
    val c2s: PitC2SPackets = PitC2SPackets(reg)
    val capabilities: PitCapabilities = PitCapabilities(reg)
    val sounds: PitSounds = PitSounds(reg)

    val adaptorSvc: AdaptorService = AdaptorService(reg)
    val serverPlayerTrackerSvc: ServerPlayerTrackerService = ServerPlayerTrackerService(reg)
    val serverContainerSvc: ServerContainerService = ServerContainerService(reg)
    @ClientSide.Physical
    abstract val clientContainerSvc: ClientContainerService
    @ClientSide.Physical
    abstract val clientRenderSvc: ClientRenderService

    init {
        PitCompat.init(reg)
    }

    open fun onConfigReloaded() {
        defns.adaptorSvc.reloadAdaptors()
    }

    @ServerSide.Physical
    internal class Server(reg: ModRegistrar) : PitDefinitions(reg) {
        @ClientSide.Physical
        override val clientContainerSvc: ClientContainerService
            get() = absurdPhysicalSide()
        @ClientSide.Physical
        override val clientRenderSvc: ClientRenderService
            get() = absurdPhysicalSide()
    }

    @ClientSide.Physical
    internal class Client(reg: ModRegistrar) : PitDefinitions(reg) {
        override val clientContainerSvc: ClientContainerService = ClientContainerService(reg)
        override val clientRenderSvc: ClientRenderService = ClientRenderService(reg)
    }
}
