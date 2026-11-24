package st.evening.mc.portableinventorytools

import st.evening.mc.prelude.api.config.json.JsonConfig
import st.evening.mc.prelude.api.config.json.JsonConfig.RestartRequirement

class PitConfig(config: JsonConfig.ConfigObject) {
    val disabledAdaptors: List<String> by config.stringArray(
        emptyList(), "The names of portable inventory adaptors to disable.",
        restartRequirement = RestartRequirement.REQUIRES_MC_RESTART
    )

    val disableOtherModInvFeatures: Boolean by config.bool(
        true, "Should similar inventory interaction features from other mods be automatically disabled?",
        restartRequirement = RestartRequirement.REQUIRES_MC_RESTART
    )

    val client: ClientConfig by config.obj("Client-side configuration", false, ::ClientConfig)

    class ClientConfig(config: JsonConfig.ConfigObject) {
        val selectedSlotFastOffset: Int by config.int(
            3,
            "The offset by which the selected slot in an inventory should change when holding shift while scrolling.",
            minValue = 0
        )

        val tooltipGridMinSlots: Int by config.int(
            -1, "The minimum number of slots an inventory should have for the grid tooltip renderer to be used.",
            minValue = -1
        )

        val tooltipGridColumns: Int by config.int(
            9, "The number of columns to display in grid mode.",
            minValue = 1
        )

        val tooltipGridGap: Int by config.int(
            1, "The gap between slots in grid mode.",
            minValue = 0
        )

        val tooltipSmallGridMinSlots: Int by config.int(
            28, "The minimum number of slots an inventory should have for the small-grid tooltip renderer to be used.",
            minValue = -1
        )

        val tooltipSmallGridColumns: Int by config.int(
            12, "The number of columns to display in small-grid mode.",
            minValue = 1
        )

        val tooltipSmallGridScale: Float by config.float(
            0.75F, "The scale factor for slots in small-grid mode.",
            minValue = 0F, maxValue = 1F
        )

        val tooltipSmallGridGap: Int by config.int(
            1, "The gap between slots in small-grid mode.",
            minValue = 0
        )

        val tooltipDeckMinSlots: Int by config.int(
            60, "The minimum number of slots an inventory should have for the deck tooltip renderer to be used.",
            minValue = -1
        )

        val tooltipDeckExtent: Int by config.int(
            4, "The number of inventory before above and after the selected one to display in deck mode.",
            minValue = 0
        )

        val tooltipDeckGap: Int by config.int(
            12, "The gap between slots in deck mode.",
            minValue = 0
        )
    }
}
