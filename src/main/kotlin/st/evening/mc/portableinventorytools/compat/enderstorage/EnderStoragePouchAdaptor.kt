package st.evening.mc.portableinventorytools.compat.enderstorage

import codechicken.enderstorage.api.Frequency
import codechicken.enderstorage.manager.EnderStorageManager
import codechicken.enderstorage.storage.EnderItemStorage
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.wrapper.InvWrapper
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.ServerPortableInvWrapper
import st.evening.mc.portableinventorytools.inventory.prefab.AbstractSyncedAdaptor
import st.evening.mc.portableinventorytools.inventory.prefab.ServerItemHandlerWrapper
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.game.RequireMod
import st.evening.mc.prelude.api.util.game.ServerSide

@RequireMod(EnderStorageCompat.MOD_ID)
object EnderStoragePouchAdaptor : AbstractSyncedAdaptor() {
    override val adaptorId: ResourceLocation = PortableInvTools.resource("${EnderStorageCompat.MOD_ID}_pouch")

    @ServerSide
    override fun wrapServerSide(stack: ItemStack, state: PortableInvState): ServerPortableInvWrapper =
        ServerItemHandlerWrapper(
            InvWrapper(
                EnderStorageManager.instance(false).getStorage(Frequency.readFromStack(stack), "item")
                    as EnderItemStorage
            ),
            true
        )
}
