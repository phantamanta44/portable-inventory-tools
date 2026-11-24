package st.evening.mc.portableinventorytools.service

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.block.Block
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.common.util.INBTSerializable
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.common.eventhandler.EventPriority
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ClientPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.PortableInvAdaptor
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.impl.ItemHandlerItemAdaptor
import st.evening.mc.portableinventorytools.inventory.impl.ShulkerBoxAdaptor
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.collection.getOrPutNonNull
import st.evening.mc.prelude.api.util.data.NbtDsl
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.ServerSide
import st.evening.mc.prelude.api.util.game.SidednessAssertion
import st.evening.mc.prelude.api.util.game.assertLogicalServer
import st.evening.mc.prelude.api.util.game.assertStrongClient
import st.evening.mc.prelude.api.util.game.parentCapabilities

interface AdaptorRegistry {
    fun registerGlobalAdaptor(adaptor: PortableInvAdaptor)

    fun registerItemAdaptor(item: Item, adaptor: PortableInvAdaptor)

    fun registerItemAdaptor(block: Block, adaptor: PortableInvAdaptor) {
        registerItemAdaptor(Item.getItemFromBlock(block), adaptor)
    }

    fun registerItemAdaptor(item: Item, meta: Int, adaptor: PortableInvAdaptor)

    fun registerItemAdaptor(block: Block, meta: Int, adaptor: PortableInvAdaptor) {
        registerItemAdaptor(Item.getItemFromBlock(block), meta, adaptor)
    }
}

class AdaptorService internal constructor(reg: ModRegistrar) {
    companion object {
        private val CAP_PI_STATE: ResourceLocation = PortableInvTools.resource("s")
    }

    private val globalAdaptors: MutableList<PortableInvAdaptor> = mutableListOf()
    private val itemAdaptors: MutableMap<Item, ItemAdaptors> = mutableMapOf()
    // we don't implement this ourselves so consumers must use the event instead of registering adaptors whenever
    private val registryIfc: AdaptorRegistry = AdaptorRegistryImpl()

    init {
        reg.on<AdaptorRegistrationEvent> { event ->
            event.registry.run {
                registerGlobalAdaptor(ItemHandlerItemAdaptor)
                registerGlobalAdaptor(ShulkerBoxAdaptor)
            }
        }
        reg.on<FMLInitializationEvent> {
            reloadAdaptors()
        }
        reg.on<AttachCapabilitiesEvent<ItemStack>>(EventPriority.LOWEST) { event ->
            getAdaptor(event.`object`, event.parentCapabilities, event.capabilities)?.let {
                event.addCapability(CAP_PI_STATE, PortableInvStateImpl(event.`object`, it))
            }
        }
    }

    fun reloadAdaptors() {
        PortableInvTools.logger.info("Loading portable inventory adaptors...")
        globalAdaptors.clear()
        itemAdaptors.clear()
        MinecraftForge.EVENT_BUS.post(AdaptorRegistrationEvent(registryIfc))
        PortableInvTools.logger.info(
            "Loaded {} global and {} item-specific adaptor(s)",
            globalAdaptors.size,
            itemAdaptors.values.sumOf { adaptors ->
                adaptors.byMeta.values.sumOf { it.size } + adaptors.wildcard.size
            }
        )
        globalAdaptors.sortWith(PortableInvAdaptor.COMPARATOR)
        itemAdaptors.values.forEach { adaptors ->
            adaptors.wildcard.sortWith(PortableInvAdaptor.COMPARATOR)
            adaptors.byMeta.values.forEach {
                it.sortWith(PortableInvAdaptor.COMPARATOR)
            }
        }
    }

    fun getAdaptor(
        stack: ItemStack,
        parentCaps: ICapabilityProvider?,
        initCaps: Map<ResourceLocation, ICapabilityProvider>?
    ): PortableInvAdaptor? {
        forEachAdaptor(stack) {
            when (it.checkItem(stack, parentCaps, initCaps)) {
                EnumActionResult.SUCCESS -> return it
                EnumActionResult.FAIL -> return null
                EnumActionResult.PASS -> {}
            }
        }
        return null
    }

    private inline fun forEachAdaptor(stack: ItemStack, f: (PortableInvAdaptor) -> Unit) {
        itemAdaptors[stack.item]?.let { adaptors ->
            adaptors.byMeta[stack.metadata]?.forEach(f)
            adaptors.wildcard.forEach(f)
        }
        globalAdaptors.forEach(f)
    }

    private class ItemAdaptors {
        val wildcard: MutableList<PortableInvAdaptor> = mutableListOf()
        val byMeta: Int2ObjectMap<MutableList<PortableInvAdaptor>> = Int2ObjectOpenHashMap()
    }

    private inner class AdaptorRegistryImpl : AdaptorRegistry {
        private val blacklist: Set<ResourceLocation> =
            PortableInvTools.config.disabledAdaptors.mapTo(mutableSetOf()) { ResourceLocation(it) }

        private fun checkAdaptor(item: Item?, adaptor: PortableInvAdaptor): Boolean {
            if (adaptor.adaptorId in blacklist) {
                PortableInvTools.logger.info("Adaptor disabled by config: {}", adaptor.adaptorId)
                return false
            } else if (item == Items.AIR) {
                PortableInvTools.logger.warn("Cannot register adaptor for air item: ${adaptor.adaptorId}")
                return false
            } else {
                PortableInvTools.logger.debug("Registering adaptor: {}", adaptor.adaptorId)
                return true
            }
        }

        override fun registerGlobalAdaptor(adaptor: PortableInvAdaptor) {
            if (checkAdaptor(null, adaptor)) {
                globalAdaptors += adaptor
            }
        }

        override fun registerItemAdaptor(item: Item, adaptor: PortableInvAdaptor) {
            if (checkAdaptor(item, adaptor)) {
                itemAdaptors.getOrPut(item) { ItemAdaptors() }.wildcard += adaptor
            }
        }

        override fun registerItemAdaptor(item: Item, meta: Int, adaptor: PortableInvAdaptor) {
            if (checkAdaptor(item, adaptor)) {
                itemAdaptors.getOrPut(item) { ItemAdaptors() }.byMeta
                    .getOrPutNonNull(meta) { mutableListOf() } += adaptor
            }
        }
    }

    private class PortableInvStateImpl(stack: ItemStack, override val adaptor: PortableInvAdaptor) :
        PortableInvState, ICapabilityProvider, INBTSerializable<NBTTagCompound> {
        companion object {
            private const val SER_SLOT: String = "slot"
        }

        override var selectedSlot: Int = 0

        @ServerSide
        override val serverWrapper: ServerPortableInvWrapper
            by lazy @OptIn(SidednessAssertion::class) { assertLogicalServer { adaptor.wrapServerSide(stack, this) } }

        @ClientSide.Strong
        override val clientWrapper: ClientPortableInvWrapper
            by lazy @OptIn(SidednessAssertion::class) { assertStrongClient { adaptor.wrapClientSide(stack, this) } }

        override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean =
            capability == PortableInvTools.defns.capabilities.piState

        @Suppress("UNCHECKED_CAST")
        override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? = when (capability) {
            PortableInvTools.defns.capabilities.piState -> this as T
            else -> null
        }

        override fun serializeNBT(): NBTTagCompound = NbtDsl.compound {
            if (selectedSlot != 0) {
                SER_SLOT int selectedSlot
            }
        }

        override fun deserializeNBT(nbt: NBTTagCompound) {
            selectedSlot = nbt.getInteger(SER_SLOT)
        }
    }
}

class AdaptorRegistrationEvent(val registry: AdaptorRegistry) : Event()
