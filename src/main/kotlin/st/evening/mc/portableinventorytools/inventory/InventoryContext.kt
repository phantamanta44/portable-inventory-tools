package st.evening.mc.portableinventorytools.inventory

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Container

interface InventoryContext {
    val player: EntityPlayer

    val container: Container

    fun isAlive(): Boolean
}
