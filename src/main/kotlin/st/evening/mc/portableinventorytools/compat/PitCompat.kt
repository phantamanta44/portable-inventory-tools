package st.evening.mc.portableinventorytools.compat

import st.evening.mc.portableinventorytools.compat.botania.BotaniaCompat
import st.evening.mc.portableinventorytools.compat.bundles.BundlesCompat
import st.evening.mc.portableinventorytools.compat.cofh.ThermalExpansionCompat
import st.evening.mc.portableinventorytools.compat.enderstorage.EnderStorageCompat
import st.evening.mc.portableinventorytools.compat.immeng.ImmEngCompat
import st.evening.mc.portableinventorytools.compat.ironbackpacks.IronBackpacksCompat
import st.evening.mc.portableinventorytools.compat.mekanism.MekanismCompat
import st.evening.mc.portableinventorytools.compat.quark.QuarkCompat
import st.evening.mc.portableinventorytools.compat.textiles.TextilesCompat
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.util.game.ifModLoaded

object PitCompat {
    internal fun init(reg: ModRegistrar) {
        ifModLoaded(BotaniaCompat.MOD_ID) { BotaniaCompat.init(reg) }
        ifModLoaded(BundlesCompat.MOD_ID) { BundlesCompat.init(reg) }
        ifModLoaded(EnderStorageCompat.MOD_ID) { EnderStorageCompat.init(reg) }
        ifModLoaded(ImmEngCompat.MOD_ID) { ImmEngCompat.init(reg) }
        ifModLoaded(IronBackpacksCompat.MOD_ID) { IronBackpacksCompat.init(reg) }
        ifModLoaded(MekanismCompat.MOD_ID) { MekanismCompat.init(reg) }
        ifModLoaded(QuarkCompat.MOD_ID) { QuarkCompat.init(reg) }
        ifModLoaded(TextilesCompat.MOD_ID) { TextilesCompat.init(reg) }
        ifModLoaded(ThermalExpansionCompat.MOD_ID) { ThermalExpansionCompat.init(reg) }
    }
}
