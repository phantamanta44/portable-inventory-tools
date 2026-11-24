package st.evening.mc.portableinventorytools.service

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemShulkerBox
import net.minecraft.util.math.MathHelper
import net.minecraftforge.client.event.GuiScreenEvent
import net.minecraftforge.client.event.RenderTooltipEvent
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.client.config.GuiUtils
import net.minecraftforge.fml.common.eventhandler.EventPriority
import org.lwjgl.opengl.GL11
import st.evening.mc.portableinventorytools.PitConfig
import st.evening.mc.portableinventorytools.PortableInvTools
import st.evening.mc.portableinventorytools.capability.PortableInvState
import st.evening.mc.portableinventorytools.inventory.InventoryAccessor
import st.evening.mc.portableinventorytools.util.InvActionType
import st.evening.mc.prelude.api.gui.drawable.GuiDrawable
import st.evening.mc.prelude.api.gui.drawable.GuiSamplable
import st.evening.mc.prelude.api.gui.drawable.drawFullSize
import st.evening.mc.prelude.api.gui.drawable.prefab.DrawableTexture
import st.evening.mc.prelude.api.gui.drawable.sliceSized
import st.evening.mc.prelude.api.registration.ModRegistrar
import st.evening.mc.prelude.api.registration.on
import st.evening.mc.prelude.api.resource
import st.evening.mc.prelude.api.util.game.ClientSide
import st.evening.mc.prelude.api.util.game.onClient
import st.evening.mc.prelude.api.util.math.Vec2i
import st.evening.mc.prelude.api.util.math.ceilDivPos
import st.evening.mc.prelude.api.util.render.RenderingHelper
import st.evening.mc.prelude.api.util.render.TextureResource
import kotlin.math.roundToInt

@ClientSide.Physical
class ClientRenderService internal constructor(reg: ModRegistrar) {
    companion object {
        private val mouseActionIconSheet: GuiSamplable =
            DrawableTexture(22, 11, TextureResource(PortableInvTools.resource("textures/gui/mouse_action.png")))
        private val placeIcon: GuiSamplable = mouseActionIconSheet.sliceSized(11, 11, 0, 0)
        private val pickUpIcon: GuiSamplable = mouseActionIconSheet.sliceSized(11, 11, 11, 0)
    }

    private var tooltipY: Int = 0
    private var tooltipHeight: Int = -1

    init {
        // it might be a bit cleaner to use a mixin to remove the shulker box content tooltip, but quark does it like
        // this, so we may as well just do what the romans do
        reg.on<ItemTooltipEvent>(priority = EventPriority.HIGH) { event ->
            onClient { // check this in case a shulker box tooltip is rendered while not in-game for some reason
                val stack = event.itemStack
                if (
                    !stack.isEmpty && event.toolTip.isNotEmpty() && stack.item is ItemShulkerBox &&
                    stack.hasCapability(PortableInvTools.defns.capabilities.piState, null)
                ) {
                    val iter = event.toolTip.iterator()
                    iter.next() // skip the item name
                    while (iter.hasNext()) {
                        val line = iter.next()
                        if (!line.startsWith('\u00a7') || line.startsWith("o", 1)) {
                            iter.remove()
                        }
                    }
                }
            }
        }
        reg.on<RenderTooltipEvent.PostText> { event ->
            tooltipY = event.y
            tooltipHeight = event.height
        }
        reg.on<GuiScreenEvent.DrawScreenEvent.Post> { event ->
            onClient {
                val player = Minecraft.getMinecraft().player ?: return@on
                val gui = event.gui
                if (gui !is GuiContainer) return@on
                handleRender(gui, player, event.mouseX, event.mouseY, event.renderPartialTicks)
                tooltipHeight = -1
            }
        }
    }

    @ClientSide
    private fun handleRender(gui: GuiContainer, player: EntityPlayer, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val contSvc = PortableInvTools.defns.clientContainerSvc
        contSvc.checkInterruptTick()

        val heldStack = player.inventory.itemStack
        if (!heldStack.isEmpty) {
            heldStack.getCapability(PortableInvTools.defns.capabilities.piState, null)?.let { state ->
                val inv = state.clientWrapper.getClientInventory(player)
                drawMouseActionIcon(gui, contSvc, state, inv, mouseX, mouseY, partialTicks)
                drawInvTooltip(gui, state, inv, mouseX, mouseY, partialTicks)
                return
            }
        }

        val slotStack = gui.slotUnderMouse?.stack ?: return
        if (slotStack.isEmpty) return
        slotStack.getCapability(PortableInvTools.defns.capabilities.piState, null)?.let {
            drawInvTooltip(gui, it, it.clientWrapper.getClientInventory(player), mouseX, mouseY, partialTicks)
        }
    }

    @ClientSide
    private fun drawMouseActionIcon(
        gui: GuiContainer,
        contSvc: ClientContainerService,
        state: PortableInvState,
        inv: InventoryAccessor,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float
    ) {
        val dragAction = contSvc.getActiveDragAction()
        if (dragAction != null) {
            when (dragAction) {
                InvActionType.PICK_UP -> drawIconOnHeldItem(pickUpIcon, mouseX, mouseY + 2, partialTicks)
                InvActionType.PLACE -> drawIconOnHeldItem(placeIcon, mouseX, mouseY + 2, partialTicks)
                else -> {}
            }
        } else {
            gui.slotUnderMouse?.let {
                if (contSvc.isSlotBlocked(it)) return@let
                when {
                    !it.stack.isEmpty -> drawIconOnHeldItem(pickUpIcon, mouseX, mouseY, partialTicks)
                    inv.slotCount > 0 && !inv.getStackInSlot(state.selectedSlot).isEmpty ->
                        drawIconOnHeldItem(placeIcon, mouseX, mouseY, partialTicks)
                    else -> return@let
                }
            }
        }
    }

    private fun drawIconOnHeldItem(icon: GuiDrawable, mouseX: Int, mouseY: Int, partialTicks: Float) {
        RenderingHelper.resetColour()
        GlStateManager.enableAlpha()
        GlStateManager.disableLighting()
        GlStateManager.disableDepth()
        icon.drawFullSize(partialTicks, mouseX - 10, mouseY)
        GlStateManager.enableDepth()
    }

    @ClientSide
    private fun drawInvTooltip(
        gui: GuiContainer,
        state: PortableInvState,
        inv: InventoryAccessor,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float
    ) {
        val slotCount = inv.slotCount
        if (slotCount <= 0) return
        val config = PortableInvTools.config.client
        val type = selectTooltipType(config, slotCount) ?: return
        val box = type.computeBoundingBox(config, slotCount)

        val candX = mouseX + 8
        val posX = when {
            candX < 0 -> 0 // can this even happen?
            candX + box.x > gui.width -> gui.width - box.x
            else -> candX
        }
        val selCandY: Int
        val selLowerY: Int
        if (tooltipHeight < 0) { // no tooltip is being drawn; we can draw ours wherever
            val candY = mouseY - 18
            val posY = when {
                candY < box.y -> 0
                candY > gui.height -> gui.height - box.y
                else -> candY - box.y
            }
            selCandY = posY
            selLowerY = posY + box.y
            type.drawTooltip(posX, posY, box.x, config, inv, slotCount, state.selectedSlot, partialTicks)
        } else { // there's a tooltip being drawn; try to avoid overlapping it
            val candY = tooltipY - 6
            val posY: Int
            if (candY < box.y) {
                posY = tooltipY + tooltipHeight + 6
                selCandY = -1
                selLowerY = posY + box.y
            } else {
                posY = candY - box.y
                selCandY = posY
                selLowerY = tooltipY + tooltipHeight + 4
            }
            type.drawTooltip(posX, posY, box.x, config, inv, slotCount, state.selectedSlot, partialTicks)
        }

        val selStack = inv.getStackInSlot(state.selectedSlot)
        if (selStack.isEmpty) {
            // alpha test might still be disabled after rendering an item (also reset by drawHoveringText below)
            GlStateManager.enableAlpha()
        } else {
            gui.drawHoveringText(
                "${selStack.item.getForgeRarity(selStack).color}${selStack.displayName}",
                posX - 8,
                if (selCandY >= 18) selCandY - 2 else (selLowerY + 18)
            )
        }
    }

    private fun selectTooltipType(config: PitConfig.ClientConfig, slotCount: Int): InvTooltipType? {
        var maxMinSlots = Int.MIN_VALUE
        var best: InvTooltipType? = null
        InvTooltipType.entries.forEach {
            val minSlots = it.getMinSlots(config)
            if (minSlots in maxMinSlots..slotCount) {
                maxMinSlots = minSlots
                best = it
            }
        }
        return best
    }
}

@ClientSide.Physical
private enum class InvTooltipType {
    GRID {
        override fun getMinSlots(config: PitConfig.ClientConfig): Int = config.tooltipGridMinSlots

        override fun computeBoundingBox(config: PitConfig.ClientConfig, slotCount: Int): Vec2i {
            val colCount = config.tooltipGridColumns
            val gap = config.tooltipGridGap
            return if (slotCount <= colCount) {
                Vec2i(slotCount * (16 + gap) - gap, 16)
            } else {
                Vec2i(colCount * (16 + gap) - gap, (slotCount ceilDivPos colCount) * (16 + gap) - gap)
            }
        }

        @ClientSide
        override fun drawTooltip(
            posX: Int,
            posY: Int,
            width: Int,
            config: PitConfig.ClientConfig,
            inv: InventoryAccessor,
            slotCount: Int,
            selectedSlot: Int,
            partialTicks: Float
        ) {
            val mc = Minecraft.getMinecraft()
            val renderItem = mc.renderItem
            val fontRenderer = mc.fontRenderer
            val colCount = config.tooltipGridColumns
            val slotOffset = 16 + config.tooltipGridGap
            GlStateManager.disableLighting()
            RenderingHelper.pushMatrix {
                RenderingHelper.translate(posX, posY, 400)
                val slotXLimit = colCount * slotOffset
                var slotX = 0
                var slotY = 0
                for (i in 0..<slotCount) {
                    if (i == selectedSlot) {
                        drawSelectedSlotBackground(slotX, slotY, 16)
                    } else {
                        drawSlotBackground(slotX, slotY, 16)
                    }
                    slotX += slotOffset
                    if (slotX >= slotXLimit) {
                        slotX = 0
                        slotY += slotOffset
                    }
                }
                RenderingHelper.resetColour()
                RenderingHelper.pushAttrib {
                    RenderHelper.enableGUIStandardItemLighting()
                    slotX = 0
                    slotY = 0
                    for (i in 0..<slotCount) {
                        val stack = inv.getStackInSlot(i)
                        if (!stack.isEmpty) {
                            renderItem.renderItemAndEffectIntoGUI(stack, slotX, slotY)
                            renderItem.renderItemOverlayIntoGUI(fontRenderer, stack, slotX, slotY, null)
                        }
                        slotX += slotOffset
                        if (slotX >= slotXLimit) {
                            slotX = 0
                            slotY += slotOffset
                        }
                    }
                }
            }
        }
    },
    SMALL_GRID {
        override fun getMinSlots(config: PitConfig.ClientConfig): Int = config.tooltipSmallGridMinSlots

        private operator fun Float.invoke(value: Int): Int = MathHelper.ceil(this * value)

        override fun computeBoundingBox(config: PitConfig.ClientConfig, slotCount: Int): Vec2i {
            val colCount = config.tooltipSmallGridColumns
            val gap = config.tooltipSmallGridGap
            val scale = config.tooltipSmallGridScale
            return if (slotCount <= colCount) {
                Vec2i(scale((slotCount * (16 + gap) - gap)), scale(16))
            } else {
                Vec2i(
                    scale(colCount * (16 + gap) - gap),
                    scale((slotCount ceilDivPos colCount) * (16 + gap) - gap)
                )
            }
        }

        @ClientSide
        override fun drawTooltip(
            posX: Int,
            posY: Int,
            width: Int,
            config: PitConfig.ClientConfig,
            inv: InventoryAccessor,
            slotCount: Int,
            selectedSlot: Int,
            partialTicks: Float
        ) {
            val mc = Minecraft.getMinecraft()
            val renderItem = mc.renderItem
            val fontRenderer = mc.fontRenderer
            val colCount = config.tooltipSmallGridColumns
            val scale = config.tooltipSmallGridScale
            val slotSize = scale(16)
            val slotOffset = slotSize + config.tooltipSmallGridGap
            GlStateManager.disableLighting()
            RenderingHelper.pushMatrix {
                RenderingHelper.translate(posX, posY, 400)
                val slotXLimit = colCount * slotOffset
                var slotX = 0
                var slotY = 0
                for (i in 0..<slotCount) {
                    if (i == selectedSlot) {
                        drawSelectedSlotBackground(slotX, slotY, slotSize)
                    } else {
                        drawSlotBackground(slotX, slotY, slotSize)
                    }
                    slotX += slotOffset
                    if (slotX >= slotXLimit) {
                        slotX = 0
                        slotY += slotOffset
                    }
                }
                RenderingHelper.resetColour()
                RenderingHelper.pushAttrib {
                    RenderHelper.enableGUIStandardItemLighting()
                    slotX = 0
                    slotY = 0
                    for (i in 0..<slotCount) {
                        val stack = inv.getStackInSlot(i)
                        if (!stack.isEmpty) {
                            RenderingHelper.pushMatrix {
                                if (i == selectedSlot) {
                                    val d = 0.5F * (16 - slotSize)
                                    RenderingHelper.translate2(slotX - d, slotY - d)
                                } else {
                                    RenderingHelper.translate2(slotX, slotY)
                                    GlStateManager.scale(scale, scale, 1F)
                                }
                                renderItem.renderItemAndEffectIntoGUI(stack, 0, 0)
                                renderItem.renderItemOverlayIntoGUI(fontRenderer, stack, 0, 0, null)
                            }
                        }
                        slotX += slotOffset
                        if (slotX >= slotXLimit) {
                            slotX = 0
                            slotY += slotOffset
                        }
                    }
                }
            }
        }
    },
    DECK {
        private var knownN: Int = 0
        private var knownGap: Int = 8
        private var cachedSlotPositions: Array<IntArray> = emptyArray()

        private fun getSlotPositions(n: Int, gap: Int): Array<IntArray> {
            if (n == knownN && gap == knownGap) {
                return cachedSlotPositions
            }
            @Suppress("UNCHECKED_CAST")
            val positions = Array(n) { IntArray(3) }
            if (n > 0) {
                positions[0][2] = MathHelper.ceil(16F - 8F / n)
                positions[0][1] = 8 - positions[0][2] / 2
                for (i in 1..<n) {
                    val prev = positions[i - 1]
                    positions[i][0] = prev[0] + prev[2] * gap / 16
                    positions[i][2] = MathHelper.ceil(16F - 8F * (i + 1) / n)
                    positions[i][1] = 8 - positions[i][2] / 2
                }
            }
            cachedSlotPositions = positions
            knownN = n
            knownGap = gap
            return positions
        }

        override fun getMinSlots(config: PitConfig.ClientConfig): Int = config.tooltipDeckMinSlots

        override fun computeBoundingBox(config: PitConfig.ClientConfig, slotCount: Int): Vec2i {
            val n = config.tooltipDeckExtent
            if (n == 0) return Vec2i(16, 18) // why would you want this?
            return Vec2i((16 + 1.5F * config.tooltipDeckGap * (n - 1)).roundToInt(), 18)
        }

        @ClientSide
        override fun drawTooltip(
            posX: Int,
            posY: Int,
            width: Int,
            config: PitConfig.ClientConfig,
            inv: InventoryAccessor,
            slotCount: Int,
            selectedSlot: Int,
            partialTicks: Float
        ) {
            val mc = Minecraft.getMinecraft()
            val renderItem = mc.renderItem
            val fontRenderer = mc.fontRenderer
            val w2 = width / 2
            val n = config.tooltipDeckExtent
            val positions = getSlotPositions(config.tooltipDeckExtent, config.tooltipDeckGap)
            RenderHelper.enableGUIStandardItemLighting()
            GlStateManager.disableLighting()
            RenderingHelper.pushMatrix {
                RenderingHelper.translate(posX + w2, posY, 400)
                for (i in n downTo 1) {
                    val pos = positions[i - 1]
                    val scale = 0.0625F * pos[2]
                    RenderingHelper.pushMatrix {
                        RenderingHelper.translate2(pos[0], pos[1])
                        GlStateManager.depthFunc(GL11.GL_ALWAYS)
                        drawSlotBackground(0, 0, pos[2])
                        GlStateManager.depthFunc(GL11.GL_LEQUAL)
                        val stack = inv.getStackInSlot((selectedSlot + i).mod(slotCount))
                        if (stack.isEmpty) return@pushMatrix
                        GlStateManager.scale(scale, scale, 1F)
                        renderItem.renderItemAndEffectIntoGUI(stack, 0, 0)
                        renderItem.renderItemOverlayIntoGUI(fontRenderer, stack, 0, 0, null)
                        GlStateManager.disableLighting()
                    }
                    RenderingHelper.pushMatrix {
                        RenderingHelper.translate2(-pos[0] - pos[2], pos[1])
                        GlStateManager.depthFunc(GL11.GL_ALWAYS)
                        drawSlotBackground(0, 0, pos[2])
                        GlStateManager.depthFunc(GL11.GL_LEQUAL)
                        val stack = inv.getStackInSlot((selectedSlot - i).mod(slotCount))
                        if (stack.isEmpty) return@pushMatrix
                        GlStateManager.scale(scale, scale, 1F)
                        renderItem.renderItemAndEffectIntoGUI(stack, 0, 0)
                        renderItem.renderItemOverlayIntoGUI(fontRenderer, stack, 0, 0, null)
                        GlStateManager.disableLighting()
                    }
                    GlStateManager.translate(0F, 0F, 50F)
                }
                GlStateManager.depthFunc(GL11.GL_ALWAYS)
                drawSlotBackground(-8, 0, 16)
                GlStateManager.depthFunc(GL11.GL_LEQUAL)
                val stack = inv.getStackInSlot(selectedSlot)
                if (!stack.isEmpty) {
                    RenderingHelper.resetColour()
                    renderItem.renderItemAndEffectIntoGUI(stack, -8, 0)
                    renderItem.renderItemOverlayIntoGUI(fontRenderer, stack, -8, 0, null)
                    GlStateManager.disableLighting()
                }
                Gui.drawRect(-w2, 17, w2, 18, -0x21DDDDDC)
                val progX = (width - 12) * selectedSlot / (slotCount - 1)
                Gui.drawRect(progX - w2, 17, progX + 12 - w2, 18, -0x232322)
            }
            RenderHelper.disableStandardItemLighting()
            RenderingHelper.resetColour()
        }
    };

    abstract fun getMinSlots(config: PitConfig.ClientConfig): Int

    abstract fun computeBoundingBox(config: PitConfig.ClientConfig, slotCount: Int): Vec2i

    @ClientSide
    abstract fun drawTooltip(
        posX: Int,
        posY: Int,
        width: Int,
        config: PitConfig.ClientConfig,
        inv: InventoryAccessor,
        slotCount: Int,
        selectedSlot: Int,
        partialTicks: Float
    )

    companion object {
        private fun drawSlotBackground(posX: Int, posY: Int, size: Int) {
            GuiUtils.drawGradientRect(0, posX, posY, posX + size, posY + size, -0x21D0D0CF, -0x21DDDDDC)
            Gui.drawRect(posX + 1, posY + 1, posX + size - 1, posY + size - 1, 0x21666666)
        }

        private fun drawSelectedSlotBackground(posX: Int, posY: Int, size: Int) {
            GuiUtils.drawGradientRect(0, posX, posY, posX + size, posY + size, -0x21B68B8A, -0x21CBA09F)
            Gui.drawRect(posX + 1, posY + 1, posX + size - 1, posY + size - 1, 0x21212121)
        }
    }
}
