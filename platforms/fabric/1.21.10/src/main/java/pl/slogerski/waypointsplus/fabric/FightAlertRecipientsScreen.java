package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

final class FightAlertRecipientsScreen extends AlertScreen {
    private static final int MAX_RECIPIENTS = 10;
    private static final int ROW_HEIGHT = 25;
    private final FightAlertEditorScreen parent;
    private final List<String> draftNames = new ArrayList<>();
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private final List<ButtonWidget> removeButtons = new ArrayList<>();
    private ButtonWidget applyButton;
    private int count;
    private int left;
    private int top;
    private double scroll;

    FightAlertRecipientsScreen(FightAlertEditorScreen parent, List<String> names) {
        super(Text.literal(UiText.get("Trusted Recipients", "Zaufani odbiorcy")));
        this.parent = parent;
        draftNames.addAll(names.stream().filter(FightAlertManager::validRecipient)
                .distinct().limit(MAX_RECIPIENTS).toList());
        count = draftNames.size();
    }

    @Override protected void init() {
        configureScale();
        if (!fields.isEmpty()) {
            draftNames.clear();
            for (int i = 0; i < count; i++) draftNames.add(fields.get(i).getText());
        }
        left = width / 2 - 145;
        top = Math.max(2, (height - 242) / 2);
        fields.clear();
        removeButtons.clear();
        for (int i = 0; i < MAX_RECIPIENTS; i++) {
            int index = i;
            TextFieldWidget field = new TextFieldWidget(textRenderer, left + 20, 0, 226, 10,
                    Text.literal(UiText.get("Player name", "Nazwa gracza")));
            field.setDrawsBackground(false);
            field.setMaxLength(16);
            field.setTextPredicate(value -> value.matches("[A-Za-z0-9_]*"));
            if (i < draftNames.size()) field.setText(draftNames.get(i));
            field.setChangedListener(value -> refreshApply());
            fields.add(addDrawableChild(field));
            removeButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), button -> remove(index))
                    .dimensions(left + 261, 0, 17, 18).build()));
        }
        applyButton = addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Apply", "Zastosuj")), button -> {
            parent.applyRecipients(names());
            client.setScreen(parent);
        }).dimensions(left + 77, top + 211, 136, 20).build());
        updateRows();
        refreshApply();
    }

    private int viewportTop() {
        return top + 34;
    }

    private int viewportBottom() {
        return top + 204;
    }

    private int contentHeight() {
        return (count + (count < MAX_RECIPIENTS ? 1 : 0)) * ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewportBottom() - viewportTop()));
    }

    private void setScroll(double value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
        updateRows();
    }

    private void updateRows() {
        for (int i = 0; i < MAX_RECIPIENTS; i++) {
            int y = viewportTop() + i * ROW_HEIGHT - (int)Math.round(scroll);
            boolean visible = i < count && y >= viewportTop() && y + 20 <= viewportBottom();
            TextFieldWidget field = fields.get(i);
            ButtonWidget remove = removeButtons.get(i);
            field.setPosition(left + 20, y + 6);
            remove.setY(y + 1);
            field.visible = visible;
            remove.visible = visible;
        }
    }

    private void remove(int index) {
        if (index < 0 || index >= count) return;
        for (int i = index; i < count - 1; i++) fields.get(i).setText(fields.get(i + 1).getText());
        fields.get(count - 1).setText("");
        count--;
        setScroll(scroll);
        refreshApply();
    }

    private List<String> names() {
        return fields.subList(0, count).stream().map(TextFieldWidget::getText).toList();
    }

    private void refreshApply() {
        if (applyButton == null) return;
        List<String> names = names();
        applyButton.active = !names.isEmpty() && names.stream().allMatch(FightAlertManager::validRecipient)
                && names.stream().distinct().count() == names.size();
    }

    private int addY() {
        return viewportTop() + count * ROW_HEIGHT - (int)Math.round(scroll);
    }

    @Override protected boolean clickContent(double mouseX, double mouseY, int button) {
        int y = addY();
        if (button == 0 && count < MAX_RECIPIENTS && y >= viewportTop() && y + 20 <= viewportBottom()
                && mouseX >= left + 20 && mouseX < left + 278 && mouseY >= y && mouseY < y + 20) {
            count++;
            updateRows();
            setInitialFocus(fields.get(count - 1));
            refreshApply();
            return true;
        }
        return super.clickContent(mouseX, mouseY, button);
    }

    @Override protected boolean scrollContent(double mouseX, double mouseY,
                                           double horizontalAmount, double verticalAmount) {
        if (mouseY >= viewportTop() && mouseY < viewportBottom()) {
            setScroll(scroll - verticalAmount * 18.0);
            return true;
        }
        return super.scrollContent(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 290, top + 240);
        context.enableScissor(left + 4, viewportTop(), left + 282, viewportBottom());
        for (int i = 0; i < count; i++) {
            if (!fields.get(i).visible) continue;
            int y = fields.get(i).getY() - 6;
            GuiPalette.input(context, left + 15, y, 236, 20);
        }
        int addY = addY();
        if (count < MAX_RECIPIENTS && addY >= viewportTop() && addY + 20 <= viewportBottom()) {
            GuiPalette.inputOutline(context, left + 20, addY, left + 278, addY + 20);
            boolean hovered = mouseX >= left + 20 && mouseX < left + 278
                    && mouseY >= addY && mouseY < addY + 20;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(">              [+]              <"),
                    left + 149, addY + 6, hovered ? 0xFFD0D0D0 : 0x805A5A5A);
        }
        for (int i = 0; i < count; i++) {
            if (!fields.get(i).visible) continue;
            fields.get(i).render(context, mouseX, mouseY, delta);
            ButtonWidget remove = removeButtons.get(i);
            remove.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("X"),
                    remove.getX() + remove.getWidth() / 2 + 1, remove.getY() + 5, 0xFFFFFFFF);
        }
        context.disableScissor();
        applyButton.render(context, mouseX, mouseY, delta);
        AlertScreen.drawTitle(context, textRenderer, title, width / 2.0f, top + 7.0f);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override public void close() {
        client.setScreen(parent);
    }
}
