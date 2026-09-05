package pl.slogerski.waypointsplus.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

final class FightAlertEditorScreen extends AlertScreen {
    private final Screen parent;
    private final String id;
    private final boolean editing;
    private final boolean enabled;
    private final boolean popupEnabled;
    private String service;
    private String triggerType;
    private int keyCode;
    private final List<Integer> combinationKeys = new ArrayList<>();
    private final List<String> recipients = new ArrayList<>();
    private final List<BodyWidget> body = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Integer> separators = new ArrayList<>();
    private final List<Button> footer = new ArrayList<>();
    private EditBox alertName;
    private EditBox webhook;
    private EditBox pressCount;
    private EditBox timeWindow;
    private EditBox messageInterval;
    private MultiLineEditBox messageBox;
    private Button keyButton;
    private Button createButton;
    private Button testButton;
    private Button helpButton;
    private boolean serviceMenu;
    private boolean triggerMenu;
    private boolean capturingKey;
    private String draftWebhook;
    private String draftName;
    private String draftMessage;
    private String draftPressCount;
    private String draftTimeWindow;
    private String draftMessageInterval;
    private String validation = "";
    private int left;
    private int top;
    private int panelHeight;
    private int contentHeight;
    private double scroll;
    private boolean draggingScrollbar;
    private boolean testing;
    private long nextTestAt;
    private String testResult = "Test";

    FightAlertEditorScreen(Screen parent, FightAlertManager.FightAlert alert) {
        super(Component.literal(FightAlertManager.alerts().stream().anyMatch(a -> a.id().equals(alert.id()))
                ? UiText.get("Edit Alert", "Edytuj alert") : UiText.get("Create Alert", "Utwórz alert")));
        this.parent = parent;
        id = alert.id();
        editing = FightAlertManager.alerts().stream().anyMatch(a -> a.id().equals(id));
        enabled = alert.enabled();
        popupEnabled = FightAlertManager.popupEnabled(alert);
        service = alert.service();
        triggerType = alert.triggerType();
        keyCode = alert.keyCode();
        if (alert.combinationKeys() != null) combinationKeys.addAll(alert.combinationKeys());
        if (alert.recipients() != null) recipients.addAll(alert.recipients());
        draftWebhook = alert.webhookUrl();
        draftName = alert.name();
        draftMessage = alert.message();
        draftPressCount = Integer.toString(alert.pressCount());
        draftTimeWindow = Integer.toString(alert.windowMs());
        draftMessageInterval = Integer.toString(FightAlertManager.messageInterval(alert));
    }

    @Override protected void init() {
        configureScale();
        body.clear();
        labels.clear();
        separators.clear();
        footer.clear();
        left = (width - 390) / 2;
        panelHeight = Math.min(340, height - 8);
        top = (height - panelHeight) / 2;
        helpButton = addRenderableWidget(Button.builder(Component.literal("?"), button -> {
            String language = "pl".equals(WaypointsPlusClient.config().settings().language) ? "pl" : "en";
            Util.getPlatform().openUri("https://slogerski.github.io/Waypoints-Plus/?lang="
                    + language + "#fight-alerts");
        }).pos(left + 369, top + 6).size(14, 14).build());

        int y = 4;
        label(UiText.get("Name", "Nazwa"), 12, y + 6);
        alertName = field(105, y, 267, draftName, 32);
        alertName.setResponder(value -> { draftName = value; refreshCreate(); });
        y += 28;
        label(UiText.get("Destination", "Odbiorca"), 12, y + 6);
        button(service.isEmpty() ? UiText.get("Select", "Wybierz") : service.equals("MSG") ? "MSG" : "Discord",
                105, y, 267, () -> { serviceMenu = !serviceMenu; triggerMenu = false; rebuild(); });
        y += 26;
        if (serviceMenu) {
            separators.add(y);
            y += 5;
            label(">", 94, y + 6);
            label(">", 94, y + 30);
            label(">", 94, y + 54);
            button("Discord", 105, y, 267, () -> selectService("DISCORD"));
            button("MSG", 105, y + 24, 267, () -> selectService("MSG"));
            Button upcoming = button(UiText.get("Telegram (Coming Soon)", "Telegram (wkrótce)"),
                    105, y + 48, 267, () -> {});
            upcoming.active = false;
            y += 74;
            separators.add(y);
            y += 7;
        }
        label(UiText.get("Trigger", "Aktywacja"), 12, y + 6);
        button(triggerLabel(), 105, y, 267, () -> { triggerMenu = !triggerMenu; serviceMenu = false; rebuild(); });
        y += 26;
        if (triggerMenu) {
            separators.add(y);
            y += 5;
            String[] types = {"SINGLE_PRESS", "COMBINATION", "MULTI_PRESS"};
            String[] names = {UiText.get("Single Press", "Pojedyncze naciśnięcie"),
                    UiText.get("Key Combination", "Kombinacja klawiszy"),
                    UiText.get("Multi-Press", "Wielokrotne naciśnięcie")};
            for (int i = 0; i < types.length; i++) {
                String type = types[i];
                label(">", 94, y + 6);
                button(names[i], 105, y, 267, () -> { triggerType = type; triggerMenu = false; rebuild(); });
                y += 24;
            }
            y += 2;
            separators.add(y);
            y += 7;
        }
        label(UiText.get("Keys", "Klawisze"), 12, y + 6);
        keyButton = button(keyLabel(), 105, y, 267, () -> {
            if ("COMBINATION".equals(triggerType)) {
                minecraft.setScreen(new FightAlertCombinationScreen(this, combinationKeys));
            } else {
                capturingKey = true;
                setFocused(null);
                keyButton.setMessage(Component.literal(UiText.get("Press a key…", "Naciśnij klawisz…")));
            }
        });
        y += 28;
        if ("MULTI_PRESS".equals(triggerType)) {
            label(UiText.get("Required Presses", "Liczba naciśnięć"), 12, y);
            label(UiText.get("Time Window (ms)", "Okno czasowe (ms)"), 200, y);
            y += 13;
            pressCount = field(12, y, 174, draftPressCount, 1);
            timeWindow = field(200, y, 172, draftTimeWindow, 5);
            pressCount.setResponder(value -> { draftPressCount = value; refreshCreate(); });
            timeWindow.setResponder(value -> { draftTimeWindow = value; refreshCreate(); });
            y += 28;
        }
        if ("MSG".equals(service)) {
            label(UiText.get("Recipients", "Odbiorcy"), 12, y + 6);
            button(recipients.size() + "/10", 105, y, 267,
                    () -> minecraft.setScreen(new FightAlertRecipientsScreen(this, recipients)));
            y += 28;
            label(UiText.get("Interval (ms)", "Odstęp (ms)"), 12, y + 6);
            messageInterval = field(105, y, 267, draftMessageInterval, 5);
            messageInterval.setResponder(value -> { draftMessageInterval = value; refreshCreate(); });
            y += 28;
        } else {
            label("Webhook", 12, y);
            y += 13;
            webhook = field(12, y, 360, draftWebhook, 512);
            webhook.setResponder(value -> { draftWebhook = value; refreshCreate(); });
            y += 28;
        }
        label(UiText.get("Message", "Wiadomość"), 12, y);
        y += 13;
        messageBox = MultiLineEditBox.builder().setX(left + 12).setY(0).setShowBackground(false)
                .setShowDecorations(false).setTextColor(0xFFE0E0E0)
                .build(font, 360, 88, Component.literal(UiText.get("Message", "Wiadomość")));
        messageBox.setCharacterLimit(Math.max(8000, draftMessage.length()));
        messageBox.setValue(draftMessage);
        messageBox.setValueListener(value -> { draftMessage = value; refreshCreate(); });
        addBody(messageBox, y, true);
        contentHeight = y + 96;
        int bottom = top + panelHeight - 28;
        footer.add(addRenderableWidget(Button.builder(Component.literal(UiText.get("Cancel", "Anuluj")),
                button -> onClose()).pos(left + 12, bottom).size(112, 20).build()));
        testButton = addRenderableWidget(Button.builder(Component.literal(testResult),
                button -> testWebhook())
                .pos(left + 139, bottom).size(112, 20).build());
        testButton.visible = "DISCORD".equals(service);
        footer.add(testButton);
        createButton = addRenderableWidget(Button.builder(Component.literal(editing
                        ? UiText.get("Save", "Zapisz") : UiText.get("Create", "Utwórz")),
                button -> create()).pos(left + 266, bottom).size(112, 20).build());
        footer.add(createButton);
        setScroll(scroll);
        refreshCreate();
    }

    private void rebuild() {
        capturingKey = false;
        setFocused(null);
        rebuildWidgets();
    }

    private void selectService(String selected) {
        service = selected;
        serviceMenu = false;
        rebuild();
    }

    private <T extends AbstractWidget> T addBody(T widget, int y, boolean outlined) {
        addRenderableWidget(widget);
        body.add(new BodyWidget(widget, y, outlined));
        return widget;
    }

    private EditBox field(int x, int y, int fieldWidth, String value, int maxLength) {
        EditBox field = new EditBox(font, left + x + 5, 0,
                fieldWidth - 10, 10, Component.empty());
        field.setBordered(false);
        field.setMaxLength(maxLength);
        field.setValue(value);
        return addBody(field, y + 7, true);
    }

    private Button button(String text, int x, int y, int buttonWidth, Runnable action) {
        return addBody(Button.builder(Component.literal(text), button -> action.run())
                .pos(left + x, 0).size(buttonWidth, 20).build(), y, false);
    }

    private void label(String text, int x, int y) {
        labels.add(new Label(text, x, y));
    }

    private int viewportTop() { return top + 30; }
    private int viewportBottom() { return top + panelHeight - 47; }
    private int maxScroll() { return Math.max(0, contentHeight - (viewportBottom() - viewportTop())); }

    private void setScroll(double value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
        for (BodyWidget entry : body) {
            AbstractWidget widget = entry.widget();
            int y = viewportTop() + entry.y() - (int) scroll;
            widget.setY(y);
            widget.visible = y + widget.getHeight() > viewportTop() && y < viewportBottom();
            if (!widget.visible && widget.isFocused()) setFocused(null);
        }
    }

    private String triggerLabel() {
        return switch (triggerType) {
            case "COMBINATION" -> UiText.get("Key Combination", "Kombinacja klawiszy");
            case "MULTI_PRESS" -> UiText.get("Multi-Press", "Wielokrotne naciśnięcie");
            default -> UiText.get("Single Press", "Pojedyncze naciśnięcie");
        };
    }

    private String keyLabel() {
        if ("COMBINATION".equals(triggerType)) {
            return combinationKeys.isEmpty() ? UiText.get("Select Keys", "Wybierz klawisze")
                    : String.join(" + ", combinationKeys.stream().map(FightAlertEditorScreen::keyName).toList());
        }
        return keyCode < 0 ? UiText.get("Select Key", "Wybierz klawisz") : keyName(keyCode);
    }

    private void refreshCreate() {
        if (createButton == null) return;
        validation = "";
        if (!editing && !FightAlertManager.canAdd()) validation = UiText.get("Maximum: 32 alerts", "Maksymalnie: 32 alerty");
        else if (draftName.isBlank()) validation = UiText.get("Enter a name", "Wpisz nazwę");
        else if (!"DISCORD".equals(service) && !"MSG".equals(service)) validation = UiText.get("Select a destination", "Wybierz odbiorcę");
        else if ("COMBINATION".equals(triggerType) ? combinationKeys.isEmpty() : keyCode < 0)
            validation = UiText.get("Choose trigger keys", "Wybierz klawisze aktywacji");
        else if ("MULTI_PRESS".equals(triggerType) && (!validInteger(draftPressCount, 2, 8)
                || !validInteger(draftTimeWindow, 10, 50_000)))
            validation = UiText.get("Presses: 2-8; window: 10-50000 ms", "Naciśnięcia: 2-8; okno: 10-50000 ms");
        else if ("DISCORD".equals(service) && !FightAlertManager.validWebhook(draftWebhook))
            validation = UiText.get("Enter a valid webhook URL", "Wpisz poprawny adres webhooka");
        else if ("MSG".equals(service) && (recipients.isEmpty() || !recipients.stream().allMatch(FightAlertManager::validRecipient)))
            validation = UiText.get("Choose recipients", "Wybierz odbiorców");
        else if ("MSG".equals(service) && !validInteger(draftMessageInterval, 100, 20_000))
            validation = UiText.get("Interval: 100-20000 ms", "Odstęp: 100-20000 ms");
        else if (draftMessage.isBlank()) validation = UiText.get("Enter a message", "Wpisz wiadomość");
        createButton.active = validation.isEmpty();
        if (testButton != null) testButton.active = "DISCORD".equals(service)
                && FightAlertManager.validWebhook(draftWebhook) && minecraft.player != null
                && !testing && System.nanoTime() >= nextTestAt;
    }

    private void testWebhook() {
        if (testing || System.nanoTime() < nextTestAt) return;
        testing = true;
        testResult = UiText.get("Sending…", "Wysyłanie…");
        testButton.setMessage(Component.literal(testResult));
        refreshCreate();
        FightAlertManager.testWebhook(minecraft, draftWebhook.trim()).whenComplete((success, error) ->
                minecraft.execute(() -> {
                    testing = false;
                    nextTestAt = System.nanoTime() + 2_000_000_000L;
                    testResult = error == null && Boolean.TRUE.equals(success)
                            ? UiText.get("Sent", "Wysłano") : UiText.get("Failed", "Niepowodzenie");
                    testButton.setMessage(Component.literal(testResult));
                    refreshCreate();
                }));
    }

    @Override public void tick() {
        if (!testing && nextTestAt != 0 && System.nanoTime() >= nextTestAt) {
            nextTestAt = 0;
            testResult = "Test";
            testButton.setMessage(Component.literal(testResult));
            refreshCreate();
        }
    }

    private void create() {
        refreshCreate();
        if (!createButton.active) return;
        boolean saved = FightAlertManager.save(new FightAlertManager.FightAlert(id, service,
                "MSG".equals(service) ? "MSG" : "WEBHOOK", triggerType, keyCode,
                integer(draftPressCount, 3), integer(draftTimeWindow, 2000), draftWebhook.trim(),
                draftMessage, enabled, List.copyOf(combinationKeys), draftName.trim(), popupEnabled,
                List.copyOf(recipients), integer(draftMessageInterval, 1500)));
        if (saved) minecraft.setScreen(new FightAlertsScreen(parent instanceof FightAlertsScreen list ? list.parentScreen() : parent));
        else {
            refreshCreate();
            validation = UiText.get("Could not save the alert. Check file access.",
                    "Nie zapisano alertu. Sprawdź dostęp do pliku.");
        }
    }

    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static boolean numericText(String value) { return value.chars().allMatch(Character::isDigit); }

    private static boolean validInteger(String value, int minimum, int maximum) {
        int number = integer(value, -1);
        return number >= minimum && number <= maximum;
    }

    @Override public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (capturingKey) {
            if (key != GLFW.GLFW_KEY_ESCAPE) keyCode = key;
            capturingKey = false;
            keyButton.setMessage(Component.literal(keyLabel()));
            refreshCreate();
            return true;
        }
        return super.keyPressed(input);
    }

    void applyCombination(List<Integer> keys) {
        combinationKeys.clear();
        keys.stream().filter(key -> key != null && key >= 0).distinct().limit(4).forEach(combinationKeys::add);
    }

    void applyRecipients(List<String> names) {
        recipients.clear();
        names.stream().filter(FightAlertManager::validRecipient).distinct().limit(10).forEach(recipients::add);
    }

    Screen parentScreen() { return parent; }

    static String keyName(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> "Super";
            default -> InputConstants.getKey(new KeyEvent(keyCode, 0, 0)).getDisplayName().getString();
        };
    }

    private boolean insideBody(double x, double y) {
        return x >= left + 8 && x < left + 376 && y >= viewportTop() && y < viewportBottom();
    }

    @Override protected boolean clickContent(double x, double y, int button) {
        if (capturingKey) {
            capturingKey = false;
            keyButton.setMessage(Component.literal(keyLabel()));
        }
        if (button == 0 && maxScroll() > 0 && x >= left + 379 && x < left + 387
                && y >= viewportTop() && y < viewportBottom()) {
            draggingScrollbar = true;
            scrollToMouse(y);
            return true;
        }
        if (insideBody(x, y)) {
            for (BodyWidget entry : body) {
                if (entry.widget().mouseClicked(AlertScreen.mouse(x, y, button), false)) {
                    if (minecraft.screen == this && children().contains(entry.widget())) {
                        setFocused(entry.widget());
                        setDragging(button == 0);
                    }
                    return true;
                }
            }
        } else {
            if (helpButton.mouseClicked(AlertScreen.mouse(x, y, button), false)) return true;
            for (Button widget : footer)
                if (widget.mouseClicked(AlertScreen.mouse(x, y, button), false)) return true;
        }
        setFocused(null);
        return false;
    }

    private void scrollToMouse(double y) {
        int view = viewportBottom() - viewportTop();
        int thumb = Math.max(20, view * view / Math.max(view, contentHeight));
        setScroll((y - viewportTop() - thumb / 2.0) / Math.max(1, view - thumb) * maxScroll());
    }

    @Override protected boolean dragContent(double x, double y, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) { scrollToMouse(y); return true; }
        return super.dragContent(x, y, button, dx, dy);
    }

    @Override protected boolean releaseContent(double x, double y, int button) {
        draggingScrollbar = false;
        return super.releaseContent(x, y, button);
    }

    @Override protected boolean scrollContent(double x, double y, double horizontal, double vertical) {
        if (!insideBody(x, y) && !(x >= left + 376 && x < left + 390 && y >= viewportTop() && y < viewportBottom())) return false;
        if (messageBox.visible && messageBox.isMouseOver(x, y)
                && messageBox.mouseScrolled(x, y, horizontal, vertical)) return true;
        setScroll(scroll - vertical * 20);
        return true;
    }

    @Override protected void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        GuiPalette.panel(context, left, top, left + 390, top + panelHeight);
        AlertScreen.drawTitle(context, font, title, left + 195, top + 7);
        helpButton.extractRenderState(context, mouseX, mouseY, delta);
        context.enableScissor(left + 8, viewportTop(), left + 376, viewportBottom());
        for (int offset : separators) {
            int y = viewportTop() + offset - (int) scroll;
            context.fill(left + 105, y, left + 372, y + 1, 0x80969696);
        }
        for (Label label : labels) {
            context.text(font, label.text(), left + label.x(),
                    viewportTop() + label.y() - (int) scroll, 0xFFD9E2F0);
        }
        for (BodyWidget entry : body) {
            AbstractWidget widget = entry.widget();
            if (!widget.visible) continue;
            if (entry.outlined()) {
                if (widget instanceof EditBox)
                    GuiPalette.input(context, widget.getX() - 5, widget.getY() - 7, widget.getWidth() + 10, 22);
                else GuiPalette.input(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            }
            widget.extractRenderState(context, insideBody(mouseX, mouseY) ? mouseX : -1,
                    insideBody(mouseX, mouseY) ? mouseY : -1, delta);
        }
        context.disableScissor();
        if (maxScroll() > 0) {
            int view = viewportBottom() - viewportTop();
            int thumb = Math.max(20, view * view / contentHeight);
            int y = viewportTop() + (int) ((view - thumb) * scroll / maxScroll());
            context.fill(left + 381, viewportTop(), left + 383, viewportBottom(), 0x805A5A5A);
            context.fill(left + 380, y, left + 384, y + thumb, 0xFF969696);
        }
        context.centeredText(font, Component.literal(validation),
                left + 195, top + panelHeight - 40, 0xFFE8A6A6);
        for (Button widget : footer) widget.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private record BodyWidget(AbstractWidget widget, int y, boolean outlined) {}
    private record Label(String text, int x, int y) {}
}
