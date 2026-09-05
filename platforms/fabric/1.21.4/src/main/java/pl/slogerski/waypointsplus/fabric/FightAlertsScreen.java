package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

final class FightAlertsScreen extends AlertScreen {
    private static final int ADD_COLOR = 0x805A5A5A;
    private static final int ADD_HOVERED = 0xFFD0D0D0;
    private final Screen parent;
    private final List<AlertRow> alertRows = new ArrayList<>();
    private int left;
    private int top;
    private double scroll;
    private String pendingDeleteId;
    private boolean draggingScrollbar;

    FightAlertsScreen(Screen parent) {
        super(Text.literal(UiText.get("Fights Alerts", "Alerty walki")));
        this.parent = parent;
    }

    @Override protected void init() {
        configureScale();
        left = width / 2 - 172;
        top = Math.max(2, (height - 258) / 2);
        alertRows.clear();
        List<FightAlertManager.FightAlert> alerts = FightAlertManager.alerts();
        for (FightAlertManager.FightAlert alert : alerts) {
            ButtonWidget toggle = addDrawableChild(ButtonWidget.builder(
                    Text.literal(alert.enabled() ? "ON" : "OFF"), button -> {
                        pendingDeleteId = null;
                        boolean enabled = FightAlertManager.toggleEnabled(alert.id());
                        button.setMessage(Text.literal(enabled ? "ON" : "OFF"));
                    }).dimensions(left + 17, 0, 38, 20).build());
            ButtonWidget edit = addDrawableChild(ButtonWidget.builder(Text.literal(alertLabel(alert)), value -> {
                        pendingDeleteId = null;
                        FightAlertManager.FightAlert current = FightAlertManager.alerts().stream()
                                .filter(saved -> saved.id().equals(alert.id())).findFirst().orElse(alert);
                        client.setScreen(new FightAlertEditorScreen(this, current));
                    }).dimensions(left + 59, 0, 212, 20).build());
            ButtonWidget popup = addDrawableChild(ButtonWidget.builder(Text.literal(
                    FightAlertManager.popupEnabled(alert) ? "👁" : "-"), button -> {
                        pendingDeleteId = null;
                        boolean enabled = FightAlertManager.togglePopup(alert.id());
                        button.setMessage(Text.literal(enabled ? "👁" : "-"));
                    }).dimensions(left + 277, 0, 22, 20).build());
            ButtonWidget delete = addDrawableChild(ButtonWidget.builder(Text.literal("X"),
                    value -> deleteAlert(alert.id()))
                    .dimensions(left + 305, 0, 22, 20).build());
            alertRows.add(new AlertRow(alert.id(), toggle, edit, popup, delete));
        }
        addDrawableChild(ButtonWidget.builder(Text.literal(UiText.get("Back", "Wróć")), button -> close())
                .dimensions(left + 104, top + 224, 136, 20).build());
        setScroll(scroll);
    }

    private String alertLabel(FightAlertManager.FightAlert alert) {
        String trigger = switch (alert.triggerType()) {
            case "COMBINATION" -> UiText.get("Key Combination", "Kombinacja klawiszy");
            case "MULTI_PRESS" -> UiText.get("Multi-Press", "Wielokrotne naciśnięcie");
            default -> UiText.get("Single Press", "Pojedyncze naciśnięcie");
        };
        String destination = "MSG".equals(alert.service()) ? "MSG" : "Discord";
        return alert.name() + " • " + destination + " • " + trigger;
    }

    private int viewportTop() {
        return top + 34;
    }

    private int viewportBottom() {
        return top + 218;
    }

    private int contentHeight() {
        return Math.max(184, 46 + alertRows.size() * 26);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewportBottom() - viewportTop()));
    }

    private void setScroll(double value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
        updateRows();
    }

    private void updateRows() {
        int y = viewportTop() + 46 - (int)Math.round(scroll);
        for (AlertRow row : alertRows) {
            boolean visible = y >= viewportTop() && y + 20 <= viewportBottom();
            for (ButtonWidget button : row.buttons()) {
                button.setY(y);
                button.visible = visible;
            }
            y += 26;
        }
    }

    private void deleteAlert(String id) {
        if (!id.equals(pendingDeleteId)) {
            pendingDeleteId = id;
            return;
        }
        FightAlertManager.remove(id);
        FightAlertsScreen refreshed = new FightAlertsScreen(parent);
        refreshed.scroll = scroll;
        client.setScreen(refreshed);
    }

    @Override protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 344, top + 256);
        context.enableScissor(left + 4, viewportTop(), left + 335, viewportBottom());
        drawAddAlert(context, mouseX, mouseY);
        for (AlertRow row : alertRows) {
            for (ButtonWidget button : row.buttons()) {
                if (button.visible) button.render(context, mouseX, mouseY, delta);
            }
        }
        context.disableScissor();
        for (AlertRow row : alertRows) {
            if (row.id().equals(pendingDeleteId) && row.delete().visible) {
                context.drawTextWithShadow(textRenderer, UiText.get("Sure?", "Na pewno?"),
                        left + 348, row.delete().getY() + 6, 0xFFFFA0A0);
            }
        }
        for (var child : children()) {
            if (child instanceof ButtonWidget button && !isAlertButton(button)) {
                button.render(context, mouseX, mouseY, delta);
            }
        }
        AlertScreen.drawTitle(context, textRenderer, title, width / 2.0f, top + 7.0f);
        drawScrollbar(context);
    }

    private void drawAddAlert(DrawContext context, int mouseX, int mouseY) {
        int x = left + 17;
        int y = viewportTop() + 8 - (int)Math.round(scroll);
        int width = 310;
        GuiPalette.inputOutline(context, x, y, x + width, y + 22);
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 22;
        String label = FightAlertManager.canAdd() ? ">                 [+]                 <"
                : UiText.get("Limit reached: 32 alerts", "Osiągnięto limit: 32 alerty");
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + width / 2, y + 7,
                hovered ? ADD_HOVERED : ADD_COLOR);
    }

    private void drawScrollbar(DrawContext context) {
        int x = left + 337;
        int height = viewportBottom() - viewportTop();
        context.fill(x + 1, viewportTop(), x + 3, viewportBottom(), 0x805A5A5A);
        int thumb = Math.min(height, Math.max(24, height * height / contentHeight()));
        int travel = height - thumb;
        int y = viewportTop() + (maxScroll() == 0 ? 0 : (int)Math.round(travel * scroll / maxScroll()));
        context.fill(x, y, x + 4, y + thumb, 0xFF969696);
    }

    @Override protected boolean clickContent(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0 && mouseX >= left + 335 && mouseX < left + 344
                && mouseY >= viewportTop() && mouseY < viewportBottom()) {
            pendingDeleteId = null;
            draggingScrollbar = true;
            scrollToMouse(mouseY);
            return true;
        }
        boolean confirmingDelete = pendingDeleteId != null && alertRows.stream()
                .anyMatch(row -> row.id().equals(pendingDeleteId) && row.delete().isMouseOver(mouseX, mouseY));
        if (!confirmingDelete) pendingDeleteId = null;
        int x = left + 17;
        int y = viewportTop() + 8 - (int)Math.round(scroll);
        if (button == 0 && FightAlertManager.canAdd()
                && mouseY >= viewportTop() && mouseY < viewportBottom() && mouseX >= x && mouseX < x + 310 && mouseY >= y && mouseY < y + 22) {
            client.setScreen(new FightAlertEditorScreen(this, FightAlertManager.createDefault()));
            return true;
        }
        return super.clickContent(mouseX, mouseY, button);
    }

    private void scrollToMouse(double y) {
        int view = viewportBottom() - viewportTop();
        int thumb = Math.min(view, Math.max(24, view * view / contentHeight()));
        setScroll((y - viewportTop() - thumb / 2.0) / Math.max(1, view - thumb) * maxScroll());
    }

    @Override protected boolean dragContent(double x, double y, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            scrollToMouse(y);
            return true;
        }
        return super.dragContent(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(x, y, button);
    }

    @Override protected boolean scrollContent(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= left && mouseX < left + 344 && mouseY >= viewportTop() && mouseY < viewportBottom()) {
            pendingDeleteId = null;
            setScroll(scroll - verticalAmount * 18.0);
            return true;
        }
        return super.scrollContent(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override public void close() {
        client.setScreen(parent);
    }

    Screen parentScreen() {
        return parent;
    }

    private boolean isAlertButton(ButtonWidget button) {
        return alertRows.stream().anyMatch(row -> row.buttons().contains(button));
    }

    private record AlertRow(String id, ButtonWidget toggle, ButtonWidget edit, ButtonWidget popup,
                            ButtonWidget delete) {
        List<ButtonWidget> buttons() {
            return List.of(toggle, edit, popup, delete);
        }
    }
}
