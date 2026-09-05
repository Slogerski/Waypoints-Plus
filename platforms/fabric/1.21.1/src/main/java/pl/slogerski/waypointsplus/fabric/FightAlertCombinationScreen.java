package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

final class FightAlertCombinationScreen extends AlertScreen {
    private static final int ADD_COLOR = 0x805A5A5A;
    private static final int ADD_HOVERED = 0xFFD0D0D0;
    private final FightAlertEditorScreen parent;
    private final List<Integer> keys = new ArrayList<>();
    private final List<ButtonWidget> slots = new ArrayList<>();
    private ButtonWidget applyButton;
    private int selected = -1;
    private int left;
    private int top;

    FightAlertCombinationScreen(FightAlertEditorScreen parent, List<Integer> keys) {
        super(Text.literal(UiText.get("Key Combination", "Kombinacja klawiszy")));
        this.parent = parent;
        keys.stream().filter(key -> key != null && key >= 0).distinct().limit(4).forEach(this.keys::add);
    }

    @Override protected void init() {
        configureScale();
        left = width / 2 - 130;
        top = Math.max(2, (height - 210) / 2);
        slots.clear();
        for (int i = 0; i < 4; i++) {
            int index = i;
            ButtonWidget slot = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
                selected = index;
                updateSlots();
            }).dimensions(left + 20, top + 42 + i * 27, 220, 20).build());
            slots.add(slot);
        }
        applyButton = addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Apply", "Zastosuj")), button -> {
            parent.applyCombination(validKeys());
            client.setScreen(parent);
        }).dimensions(left + 62, top + 178, 136, 20).build());
        updateSlots();
    }

    private void updateSlots() {
        for (int i = 0; i < slots.size(); i++) {
            ButtonWidget slot = slots.get(i);
            slot.visible = i < keys.size();
            if (!slot.visible) continue;
            String label = i == selected
                    ? UiText.get("Press a key…", "Naciśnij klawisz…")
                    : FightAlertEditorScreen.keyName(keys.get(i));
            slot.setMessage(Text.literal(label));
        }
        if (applyButton != null) applyButton.active = !validKeys().isEmpty();
    }

    private List<Integer> validKeys() {
        return keys.stream().filter(key -> key != null && key >= 0).distinct().limit(4).toList();
    }

    private int addX() {
        return left + 20;
    }

    private int addY() {
        return top + 42 + keys.size() * 27;
    }

    @Override protected boolean clickContent(double mouseX, double mouseY, int button) {
        if (button == 0 && keys.size() < 4 && mouseX >= addX() && mouseX < addX() + 220
                && mouseY >= addY() && mouseY < addY() + 20) {
            keys.add(-1);
            selected = keys.size() - 1;
            updateSlots();
            return true;
        }
        return super.clickContent(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selected >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
                    || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                keys.remove(selected);
                selected = -1;
                updateSlots();
                return true;
            }
            if (!keys.contains(keyCode)) keys.set(selected, keyCode);
            else if (keys.get(selected) < 0) keys.remove(selected);
            selected = -1;
            updateSlots();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 260, top + 208);
        super.renderContent(context, mouseX, mouseY, delta);
        AlertScreen.drawTitle(context, textRenderer, title, width / 2.0f, top + 7.0f);
        if (keys.size() < 4) {
            int x = addX();
            int y = addY();
            GuiPalette.inputOutline(context, x, y, x + 220, y + 20);
            boolean hovered = mouseX >= x && mouseX < x + 220 && mouseY >= y && mouseY < y + 20;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(">                 [+]                 <"),
                    x + 110, y + 6, hovered ? ADD_HOVERED : ADD_COLOR);
        }
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override public void close() {
        client.setScreen(parent);
    }
}
