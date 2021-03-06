package mapwriter.gui;

import mapwriter.MapWriter;
import mapwriter.config.MapModeConfig;
import mapwriter.config.SmallMapModeConfig;
import mapwriter.gui.ModGuiConfig.ModNumberSliderEntry;
import mapwriter.map.MapRenderer;
import mapwriter.map.MapMode;
import mapwriter.util.Reference;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.client.config.GuiConfigEntries.CategoryEntry;
import net.minecraftforge.fml.client.config.GuiConfigEntries.IConfigEntry;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.awt.*;
import java.io.IOException;
import java.util.List;

public class ModGuiConfigHUD extends GuiConfig {
    public static class MapPosConfigEntry extends CategoryEntry {
        public MapPosConfigEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement prop) {
            super(owningScreen, owningEntryList, prop);
        }

        @Override
        protected GuiScreen buildChildScreen() {
            final String QualifiedName = this.configElement.getQualifiedName();
            final String config = QualifiedName.substring(0, QualifiedName.indexOf(Configuration.CATEGORY_SPLITTER)).replace(Configuration.CATEGORY_SPLITTER, "");

            return new ModGuiConfigHUD(this.owningScreen, this.getConfigElement().getChildElements(), this.owningScreen.modID, null, this.configElement.requiresWorldRestart() || this.owningScreen.allRequireWorldRestart, this.configElement.requiresMcRestart() || this.owningScreen.allRequireMcRestart, this.owningScreen.title, config);
        }
    }

    private final MapWriter mw;
    public MapMode mapMode;

    private final MapRenderer map;

    private Boolean draggingMap = false;

    private MapModeConfig dummyMapConfig;

    public ModGuiConfigHUD(GuiScreen parentScreen, List<IConfigElement> configElements, String modID, String configID, boolean allRequireWorldRestart, boolean allRequireMcRestart, String title, String Config) {
        super(parentScreen, configElements, modID, configID, allRequireWorldRestart, allRequireMcRestart, title, "Use right click and hold to move the map");

        if (Config.equals(Reference.CAT_FULL_MAP_CONFIG)) {
            this.dummyMapConfig = new MapModeConfig(Reference.CAT_FULL_MAP_CONFIG);
        } else if (Config.equals(Reference.CAT_SMALL_MAP_CONFIG)) {
            this.dummyMapConfig = new SmallMapModeConfig(Reference.CAT_SMALL_MAP_CONFIG);
        }
        this.dummyMapConfig.setDefaults();
        this.dummyMapConfig.loadConfig();

        this.mw = MapWriter.getInstance();
        this.mapMode = new MapMode(this.dummyMapConfig);
        this.map = new MapRenderer(this.mw, this.mapMode, null);
    }

    // also called every frame
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.updateButtonValues();
        // draw the map
        this.map.drawDummy();
    }

    @Override
    public void initGui() {
        super.initGui();
        final int topLeftWidth = Math.max(this.mc.fontRenderer.getStringWidth(I18n.format("mw.config.map.ctgy.position.topleft")) + 20, 100);
        final int topRightWidth = Math.max(this.mc.fontRenderer.getStringWidth(I18n.format("mw.config.map.ctgy.position.topright")) + 20, 100);
        final int bottomLeftWidth = Math.max(this.mc.fontRenderer.getStringWidth(I18n.format("mw.config.map.ctgy.position.botleft")) + 20, 100);
        final int bottomRightWidth = Math.max(this.mc.fontRenderer.getStringWidth(I18n.format("mw.config.map.ctgy.position.botright")) + 20, 100);
        final int centerTopWidth = Math.max(this.mc.fontRenderer.getStringWidth(I18n.format("mw.config.map.ctgy.position.centertop")) + 20, 100);
        final int centerBottomWidth = Math.max(this.mc.fontRenderer.getStringWidth(I18n.format("mw.config.map.ctgy.position.centerbottom")) + 20, 100);
        final int centerWidth = Math.max(this.mc.fontRenderer.getStringWidth(I18n.format("mw.config.map.ctgy.position.center")) + 20, 100);
        final int centerLeft = Math.max(this.mc.fontRenderer.getStringWidth(I18n.format("mw.config.map.ctgy.position.centerleft")) + 20, 100);
        final int centerRight = Math.max(this.mc.fontRenderer.getStringWidth(I18n.format("mw.config.map.ctgy.position.centerright")) + 20, 100);

        final int buttonWidthHalf1 = (bottomLeftWidth + 5 + bottomRightWidth + centerTopWidth + 5) / 2;
        final int buttonWidthHalf2 = (centerLeft + 5 + centerWidth + centerRight + 5) / 2;
        final int buttonWidthHalf3 = (topLeftWidth + 5 + topRightWidth + centerBottomWidth + 5) / 2;

        final int buttonHeigth1 = this.height - 29 - 29 - 29 - 29;
        final int buttonHeigth2 = this.height - 29 - 29 - 29;
        final int buttonHeigth3 = this.height - 29 - 29;

        this.buttonList.add(new GuiButtonExt(3000, this.width / 2 - buttonWidthHalf1, buttonHeigth1, topLeftWidth, 20, I18n.format("mw.config.map.ctgy.position.topleft")));
        this.buttonList.add(new GuiButtonExt(3001, this.width / 2 - buttonWidthHalf1 + topLeftWidth + 5, buttonHeigth1, centerTopWidth, 20, I18n.format("mw.config.map.ctgy.position.centertop")));
        this.buttonList.add(new GuiButtonExt(3002, this.width / 2 - buttonWidthHalf1 + topLeftWidth + 5 + centerTopWidth + 5, buttonHeigth1, topRightWidth, 20, I18n.format("mw.config.map.ctgy.position.topright")));
        this.buttonList.add(new GuiButtonExt(3010, this.width / 2 - buttonWidthHalf2, buttonHeigth2, centerLeft, 20, I18n.format("mw.config.map.ctgy.position.centerleft")));
        this.buttonList.add(new GuiButtonExt(3011, this.width / 2 - buttonWidthHalf2 + centerLeft + 5, buttonHeigth2, centerWidth, 20, I18n.format("mw.config.map.ctgy.position.center")));
        this.buttonList.add(new GuiButtonExt(3012, this.width / 2 - buttonWidthHalf2 + centerLeft + 5 + centerWidth + 5, buttonHeigth2, centerRight, 20, I18n.format("mw.config.map.ctgy.position.centerright")));
        this.buttonList.add(new GuiButtonExt(3020, this.width / 2 - buttonWidthHalf3, buttonHeigth3, bottomLeftWidth, 20, I18n.format("mw.config.map.ctgy.position.botleft")));
        this.buttonList.add(new GuiButtonExt(3021, this.width / 2 - buttonWidthHalf3 + bottomLeftWidth + 5, buttonHeigth3, centerBottomWidth, 20, I18n.format("mw.config.map.ctgy.position.centerbottom")));
        this.buttonList.add(new GuiButtonExt(3022, this.width / 2 - buttonWidthHalf3 + bottomLeftWidth + 5 + centerBottomWidth + 5, buttonHeigth3, bottomRightWidth, 20, I18n.format("mw.config.map.ctgy.position.botright")));
        this.updateParentSettings();
    }

    @Override
    public void mouseClicked(int x, int y, int mouseEvent) throws IOException {
        if (mouseEvent != 1 || !this.mapMode.posWithin(x, y)) {
            // this.entryList.mouseClickedPassThru(x, y, mouseEvent);
            super.mouseClicked(x, y, mouseEvent);
        } else {
            this.draggingMap = true;
        }
    }

    @Override
    public void mouseReleased(int x, int y, int mouseEvent) {
        if (this.draggingMap) {
            this.draggingMap = false;
        } else {
            super.mouseReleased(x, y, mouseEvent);
        }
    }

    private void updateButtonValues() {
        for (final IConfigEntry entry : this.entryList.listEntries) {
            switch (entry.getName()) {
                case "xPos":
                    this.dummyMapConfig.xPos = (Double) entry.getCurrentValue();
                    break;
                case "yPos":
                    this.dummyMapConfig.yPos = (Double) entry.getCurrentValue();
                    break;
                case "heightPercent":
                    this.dummyMapConfig.heightPercent = (Double) entry.getCurrentValue();
                    break;
                case "widthPercent":
                    this.dummyMapConfig.widthPercent = (Double) entry.getCurrentValue();
                    if (this.mapMode.getConfig().circular) {
                        ((ModNumberSliderEntry) entry).setEnabled(false);
                    } else {
                        ((ModNumberSliderEntry) entry).setEnabled(true);
                    }
                    break;
            }
        }
    }

    private void updateMap(Point.Double point) {
        for (final IConfigEntry entry : this.entryList.listEntries) {
            if (entry instanceof ModNumberSliderEntry) {
                if (entry.getName().equals("xPos")) {
                    ((ModNumberSliderEntry) entry).setValue(point.getX());
                } else if (entry.getName().equals("yPos")) {
                    ((ModNumberSliderEntry) entry).setValue(point.getY());
                }
            }
        }
    }

    private void updateParentSettings() {
        if (this.parentScreen != null && this.parentScreen instanceof GuiConfig) {
            final GuiConfig parrent = (GuiConfig) this.parentScreen;

            if (parrent.entryList != null && parrent.entryList.listEntries != null) {
                for (final IConfigEntry entry : parrent.entryList.listEntries) {
                    switch (entry.getName()) {
                        case "circular":
                            this.dummyMapConfig.circular = (Boolean) entry.getCurrentValue();
                            break;
                        case "coordsMode":
                            this.dummyMapConfig.coordsMode = (String) entry.getCurrentValue();
                            break;
                        case "borderMode":
                            this.dummyMapConfig.borderMode = (Boolean) entry.getCurrentValue();
                            break;
                        case "playerArrowSize":
                            this.dummyMapConfig.playerArrowSize = Integer.valueOf((String) entry.getCurrentValue());
                            break;
                        case "biomeMode":
                            this.dummyMapConfig.biomeMode = (String) entry.getCurrentValue();
                            break;
                    }
                }
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        double bottomOffset = 0;
        if (!this.mapMode.getConfig().biomeMode.equals(MapModeConfig.TEXT_MODES[0])) {
            bottomOffset = bottomOffset + this.mc.fontRenderer.FONT_HEIGHT + 3;
        }
        if (!this.mapMode.getConfig().biomeMode.equals(MapModeConfig.TEXT_MODES[0])) {
            bottomOffset = bottomOffset + this.mc.fontRenderer.FONT_HEIGHT + 3;
        }
        bottomOffset = bottomOffset / this.height * 100;

        final double smallMarginY = 10.00 / (this.height - this.mapMode.getH()) * 100.0;
        final double smallMarginX = 10.00 / (this.width - this.mapMode.getW()) * 100.0;
        final double largeMarginBottom = 40.00 / (this.height - this.mapMode.getH()) * 100.0;

        bottomOffset = bottomOffset < smallMarginY ? smallMarginY : bottomOffset;
        switch (button.id) {
            // top left
            case 3000:
                this.updateMap(new Point.Double(smallMarginX, smallMarginY));
                break;
            // top center
            case 3001:
                this.updateMap(new Point.Double(50, smallMarginY));
                break;
            // top right
            case 3002:
                this.updateMap(new Point.Double(100 - smallMarginX, smallMarginY));
                break;
            // center left
            case 3010:
                this.updateMap(new Point.Double(smallMarginX, 50));
                break;
            // center
            case 3011:
                this.updateMap(new Point.Double(50, 50));
                break;
            // center right
            case 3012:
                this.updateMap(new Point.Double(100 - smallMarginX, 50));
                break;
            // bottom left
            case 3020:
                this.updateMap(new Point.Double(smallMarginX, 100 - bottomOffset));
                break;
            // bottom center
            case 3021:
                this.updateMap(new Point.Double(50, 100 - largeMarginBottom));
                break;
            // bottom right
            case 3022:
                this.updateMap(new Point.Double(100 - smallMarginX, 100 - bottomOffset));
                break;
            default:
                super.actionPerformed(button);
                break;
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int mouseEvent, long timeSinceLastClick) {
        if (this.draggingMap) {
            this.updateMap(this.mapMode.getNewPosPoint(x, y));
        } else {
            super.mouseClickMove(x, y, mouseEvent, timeSinceLastClick);
        }
    }
}
