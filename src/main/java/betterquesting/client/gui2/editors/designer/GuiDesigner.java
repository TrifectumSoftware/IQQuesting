package betterquesting.client.gui2.editors.designer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector4f;

import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.client.toolbox.IToolboxTool;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.IGuiCanvas;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestLine;
import betterquesting.api2.client.gui.popups.PopContextMenu;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.client.gui.themes.presets.PresetLine;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.client.toolbox.IToolTab;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.editors.GuiTextEditor;
import betterquesting.client.gui2.editors.nbt.GuiItemSelection;
import betterquesting.client.toolbox.PanelTabMain;
import betterquesting.client.toolbox.ToolboxRegistry;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLineDatabase;

public class GuiDesigner extends GuiScreenCanvas implements IVolatileScreen, INeedsRefresh, IPEventListener {

    // Not final because I hope to support hot swapping in future
    private IQuestLine questLine;
    private final UUID lineID;

    private PanelToolController toolController;
    private IGuiCanvas cvTray;

    private final List<IToolTab> tabList = new ArrayList<>();
    private PanelTextBox tabTitle;
    private IGuiPanel lastTabPanel;
    private int tabIdx = 0;

    private CanvasQuestLine cvQuest;

    private PanelButtonQuest linkSource;

    public GuiDesigner(GuiScreen parent, IQuestLine line) {
        super(parent);
        this.questLine = line;
        this.lineID = QuestLineDatabase.INSTANCE.lookupKey(line);
        this.tabList.addAll(ToolboxRegistry.INSTANCE.getAllTabs());
    }

    @Override
    public void refreshGui() {
        this.questLine = QuestLineDatabase.INSTANCE.get(lineID);

        if (questLine == null) {
            mc.displayGuiScreen(parent);
            return;
        }

        int sx = cvQuest.getScrollX();
        int sy = cvQuest.getScrollY();
        float z = cvQuest.getZoom();
        cvQuest.setQuestLine(questLine);
        cvQuest.setZoom(z); // Always set the scale before attempting to scroll
        cvQuest.setScrollX(sx);
        cvQuest.setScrollY(sy);

        toolController.refreshCanvas();
    }

    @Override
    public void initPanel() {
        super.initPanel();

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);
        Keyboard.enableRepeatEvents(true);

        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 96, 0), 0),
            PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        cvTray = new CanvasTextured(
            new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-96, 0, 0, 0), 0),
            PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvTray);

        cvBackground.addPanel(
            new PanelButton(
                new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0),
                0,
                QuestTranslation.translate("gui.done")));

        PanelGeneric pnFrame = new PanelGeneric(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(16, 16, 16, 16), 0),
            PresetTexture.AUX_FRAME_0.getTexture());
        cvBackground.addPanel(pnFrame);

        cvQuest = new CanvasQuestLine(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(16, 16, 16, 16), 0), 1);
        cvQuest.enableBlocking(false); // Designer tools move panels without rebuilding culling regions.
        cvBackground.addPanel(cvQuest);
        cvQuest.setQuestLine(questLine);

        PanelButton btnTabLeft = new PanelButton(
            new GuiTransform(new Vector4f(0F, 0F, 0.5F, 0F), new GuiPadding(16, 32, 0, -40), 0),
            2,
            "") {

            @Override
            public void onButtonClick() {
                tabIdx--;
                refreshToolTab();
            }
        };
        btnTabLeft.setIcon(PresetIcon.ICON_LEFT.getTexture());
        cvTray.addPanel(btnTabLeft);

        PanelButton btnTabRight = new PanelButton(
            new GuiTransform(new Vector4f(0.5F, 0F, 1F, 0F), new GuiPadding(0, 32, 16, -40), 0),
            3,
            "") {

            @Override
            public void onButtonClick() {
                tabIdx++;
                refreshToolTab();
            }
        };
        btnTabRight.setIcon(PresetIcon.ICON_RIGHT.getTexture());
        cvTray.addPanel(btnTabRight);

        tabTitle = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(16, 20, 16, -32), 0), "?")
            .setAlignment(1)
            .setColor(PresetColor.TEXT_HEADER.getColor());
        cvTray.addPanel(tabTitle);

        if (toolController != null) {
            cvBackground.addPanel(toolController);
            cvQuest.setScrollDriverX(toolController.getScrollX());
            cvQuest.setScrollDriverY(toolController.getScrollY());
            toolController.changeCanvas(cvQuest);
        } else {
            toolController = new PanelToolController(
                new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(16, 16, 16, 16), -1),
                cvQuest);
            cvBackground.addPanel(toolController);
            cvQuest.setScrollDriverX(toolController.getScrollX());
            cvQuest.setScrollDriverY(toolController.getScrollY());
            cvQuest.fitToWindow();
        }

        refreshToolTab();
    }

    private void refreshToolTab() {
        if (lastTabPanel != null) cvTray.removePanel(lastTabPanel);

        if (tabList.size() <= 0) return;
        if (tabIdx < 0) while (tabIdx < 0) tabIdx += tabList.size();
        tabIdx %= tabList.size();

        lastTabPanel = tabList.get(tabIdx)
            .getTabGui(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(16, 48, 16, 16), 0), cvQuest, toolController);
        tabTitle.setText(
            QuestTranslation.translate(
                tabList.get(tabIdx)
                    .getUnlocalisedName()));

        if (lastTabPanel != null) cvTray.addPanel(lastTabPanel);
    }

    @Override
    public void onPanelEvent(PanelEvent event) {
        if (event instanceof PEventButton) {
            onButtonPress((PEventButton) event);
        }
    }

    private void onButtonPress(PEventButton event) {
        IPanelButton btn = event.getButton();

        if (btn.getButtonID() == 0) // Exit
        {
            mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        if (click == 1) {
            PanelButtonQuest btn = getQuestButtonAt(mx, my);
            if (btn != null) {
                openQuestContextMenu(mx, my, btn);
                return true;
            }
        } else if (click == 0 && GuiScreen.isCtrlKeyDown()) {
            PanelButtonQuest btn = getQuestButtonAt(mx, my);
            if (btn != null) {
                linkSource = btn;
                return true;
            }
        }

        return super.onMouseClick(mx, my, click);
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int click) {
        if (click == 0 && linkSource != null) {
            PanelButtonQuest target = getQuestButtonAt(mx, my);
            if (target != null && target != linkSource) {
                toggleDependency(linkSource, target);
            }
            linkSource = null;
            return true;
        }

        return super.onMouseRelease(mx, my, click);
    }

    @Override
    public boolean onKeyTyped(char c, int keycode) {
        if (super.onKeyTyped(c, keycode)) return true;

        int idx = getToolIndex(keycode);
        if (idx >= 0) {
            List<IToolboxTool> tools = PanelTabMain.getTools();
            if (idx < tools.size()) {
                toolController.setActiveTool(tools.get(idx));
                refreshToolTab();
                return true;
            }
        }

        return false;
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        super.drawPanel(mx, my, partialTick);

        if (linkSource != null && cvQuest != null) {
            drawLinkLine(mx, my, partialTick);
        }
    }

    private PanelButtonQuest getQuestButtonAt(int mx, int my) {
        if (cvQuest == null || !cvQuest.getTransform()
            .contains(mx, my)) return null;
        return cvQuest.getButtonAt(mx, my);
    }

    private void openQuestContextMenu(int mx, int my, PanelButtonQuest btn) {
        Map.Entry<UUID, IQuest> entry = btn.getStoredValue();
        UUID questId = entry.getKey();
        IQuest quest = entry.getValue();

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int maxWidth = 0;
        String[] labels = { QuestTranslation.translate("betterquesting.btn.edit_text"),
            QuestTranslation.translate("betterquesting.btn.change_icon"),
            QuestTranslation.translate("betterquesting.btn.change_frame"),
            QuestTranslation.translate("betterquesting.btn.delete") };
        for (String label : labels) {
            maxWidth = Math.max(maxWidth, fr.getStringWidth(label));
        }

        PopContextMenu popup = new PopContextMenu(new GuiRectangle(mx, my, maxWidth + 20, labels.length * 16), true);

        popup.addButton("betterquesting.btn.edit_text", null, () -> {
            closePopup();
            mc.displayGuiScreen(new GuiTextEditor(this, quest.getProperty(NativeProps.DESC), true, value -> {
                quest.setProperty(NativeProps.DESC, value);
                NetQuestEdit.requestEdit(Collections.singletonMap(questId, quest));
            }));
        });

        popup.addButton("betterquesting.btn.change_icon", null, () -> {
            closePopup();
            mc.displayGuiScreen(new GuiItemSelection(this, quest.getProperty(NativeProps.ICON), value -> {
                QuestInstance.applyIcon(quest, value);
                NetQuestEdit.requestEdit(Collections.singletonMap(questId, quest));
            }));
        });

        popup.addButton("betterquesting.btn.change_frame", null, () -> {
            closePopup();
            quest.setProperty(NativeProps.MAIN, !quest.getProperty(NativeProps.MAIN));
            NetQuestEdit.requestEdit(Collections.singletonMap(questId, quest));
        });

        popup.addButton("betterquesting.btn.delete", null, () -> {
            closePopup();
            NetQuestEdit.requestDelete(Collections.singletonList(questId));
        });

        openPopup(popup);
    }

    private void toggleDependency(PanelButtonQuest source, PanelButtonQuest target) {
        IQuest targetQuest = target.getStoredValue()
            .getValue();
        UUID sourceId = source.getStoredValue()
            .getKey();
        UUID targetId = target.getStoredValue()
            .getKey();

        boolean changed;
        if (targetQuest.getRequirements()
            .contains(sourceId)) {
            changed = targetQuest.getRequirements()
                .remove(sourceId);
        } else {
            changed = targetQuest.getRequirements()
                .add(sourceId);
        }

        if (changed) {
            NetQuestEdit.requestEdit(Collections.singletonMap(targetId, targetQuest));
        }
    }

    private void drawLinkLine(int mx, int my, float partialTick) {
        IGuiRect bounds = cvQuest.getTransform();
        float zs = cvQuest.getZoom();
        int lsx = cvQuest.getScrollX();
        int lsy = cvQuest.getScrollY();
        int tx = bounds.getX();
        int ty = bounds.getY();
        int smx = (int) ((mx - tx) / zs) + lsx;
        int smy = (int) ((my - ty) / zs) + lsy;

        GuiRectangle mouseRect = new GuiRectangle(smx, smy, 0, 0);

        GL11.glPushMatrix();
        RenderUtils.startScissor(bounds);
        GL11.glTranslatef(tx - lsx * zs, ty - lsy * zs, 0F);
        GL11.glScalef(zs, zs, zs);
        PresetLine.QUEST_COMPLETE.getLine()
            .drawLine(linkSource.rect, mouseRect, 2, PresetColor.QUEST_LINE_COMPLETE.getColor(), partialTick);
        RenderUtils.endScissor();
        GL11.glPopMatrix();
    }

    private static int getToolIndex(int keycode) {
        switch (keycode) {
            case Keyboard.KEY_1:
            case Keyboard.KEY_NUMPAD1:
                return 0;
            case Keyboard.KEY_2:
            case Keyboard.KEY_NUMPAD2:
                return 1;
            case Keyboard.KEY_3:
            case Keyboard.KEY_NUMPAD3:
                return 2;
            case Keyboard.KEY_4:
            case Keyboard.KEY_NUMPAD4:
                return 3;
            case Keyboard.KEY_5:
            case Keyboard.KEY_NUMPAD5:
                return 4;
            case Keyboard.KEY_6:
            case Keyboard.KEY_NUMPAD6:
                return 5;
            case Keyboard.KEY_7:
            case Keyboard.KEY_NUMPAD7:
                return 6;
            case Keyboard.KEY_8:
            case Keyboard.KEY_NUMPAD8:
                return 7;
            case Keyboard.KEY_9:
            case Keyboard.KEY_NUMPAD9:
                return 8;
            default:
                return -1;
        }
    }
}
