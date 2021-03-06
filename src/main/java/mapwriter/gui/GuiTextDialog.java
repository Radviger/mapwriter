package mapwriter.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiTextDialog extends GuiScreen {

    static final int textDialogWidthPercent = 50;

    static final int textDialogTitleY = 80;
    static final int textDialogY = 92;
    static final int textDialogErrorY = 108;
    private final GuiScreen parentScreen;
    String title;
    String text;
    String error;
    GuiTextField textField = null;
    boolean inputValid = false;
    boolean showError = false;
    boolean backToGameOnSubmit = false;

    public GuiTextDialog(GuiScreen parentScreen, String title, String text, String error) {

        this.parentScreen = parentScreen;
        this.title = title;
        this.text = text;
        this.error = error;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float f) {

        if (this.parentScreen != null) {
            this.parentScreen.drawScreen(mouseX, mouseY, f);
        } else {
            this.drawDefaultBackground();
        }

        final int w = this.width * GuiTextDialog.textDialogWidthPercent / 100;
        drawRect((this.width - w) / 2, GuiTextDialog.textDialogTitleY - 4, (this.width - w) / 2 + w, GuiTextDialog.textDialogErrorY + 14, 0x80000000);
        this.drawCenteredString(this.fontRenderer, this.title, this.width / 2, GuiTextDialog.textDialogTitleY, 0xffffff);
        this.textField.drawTextBox();
        if (this.showError) {
            this.drawCenteredString(this.fontRenderer, this.error, this.width / 2, GuiTextDialog.textDialogErrorY, 0xffffff);
        }

        super.drawScreen(mouseX, mouseY, f);
    }

    public int getInputAsHexInt() {

        final String s = this.getInputAsString();
        int value = 0;
        try {
            value = Integer.parseInt(s, 16);
            this.inputValid = true;
            this.showError = false;
        } catch (final NumberFormatException e) {
            this.inputValid = false;
            this.showError = true;
        }
        return value;
    }

    public int getInputAsInt() {

        final String s = this.getInputAsString();
        int value = 0;
        try {
            value = Integer.parseInt(s);
            this.inputValid = true;
            this.showError = false;
        } catch (final NumberFormatException e) {
            this.inputValid = false;
            this.showError = true;
        }
        return value;
    }

    public String getInputAsString() {

        final String s = this.textField.getText().trim();
        this.inputValid = s.length() > 0;
        this.showError = !this.inputValid;
        return s;
    }

    @Override
    public void initGui() {

        this.newTextField();
    }

    public void setText(String s) {

        this.textField.setText(s);
        this.text = s;
    }

    public boolean submit() {

        return false;
    }

    private void newTextField() {

        if (this.textField != null) {
            this.text = this.textField.getText();
        }
        final int w = this.width * GuiTextDialog.textDialogWidthPercent / 100;
        this.textField = new GuiTextField(0, this.fontRenderer, (this.width - w) / 2 + 5, GuiTextDialog.textDialogY, w - 10, 12);
        this.textField.setMaxStringLength(32);
        this.textField.setFocused(true);
        this.textField.setCanLoseFocus(false);
        // this.textField.setEnableBackgroundDrawing(false);
        this.textField.setText(this.text);
    }

    @Override
    protected void keyTyped(char c, int key) {

        switch (key) {
            case Keyboard.KEY_ESCAPE:
                this.mc.displayGuiScreen(this.parentScreen);
                break;

            case Keyboard.KEY_RETURN:
                // when enter pressed, submit current input
                if (this.submit()) {
                    if (!this.backToGameOnSubmit) {
                        this.mc.displayGuiScreen(this.parentScreen);
                    } else {
                        this.mc.displayGuiScreen(null);
                    }
                }
                break;

            default:
                // other characters are processed by the text box
                this.textField.textboxKeyTyped(c, key);
                this.text = this.textField.getText();
                break;
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {

        super.mouseClicked(x, y, button);
    }
}
