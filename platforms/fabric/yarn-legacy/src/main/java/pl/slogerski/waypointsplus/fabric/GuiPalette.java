package pl.slogerski.waypointsplus.fabric;

final class GuiPalette {
    private static final int PANEL_FILL_START = 0xCF292929;
    private static final int PANEL_FILL_END = 0xCF171717;
    private static final int PANEL_BORDER = 0xFF322A2A;
    private static final int INPUT_FILL_START = 0xCF404040;
    private static final int INPUT_FILL_END = 0xCF2E2E2E;
    private static final int INPUT_BORDER_START = 0xCF212121;
    private static final int INPUT_BORDER_END = 0xCF171717;

    private GuiPalette() {}

    static void panel(DrawContext context, int left, int top, int right, int bottom) {
        gradientPanel(context, left, top, right, bottom,
                PANEL_FILL_START, PANEL_FILL_END, 180.0, PANEL_BORDER, PANEL_BORDER, 180.0);
    }

    static void panel(DrawContext context, int left, int top, int right, int bottom, int border) {
        gradientPanel(context, left, top, right, bottom,
                PANEL_FILL_START, PANEL_FILL_END, 180.0, border, border, 180.0);
    }

    static void input(DrawContext context, int x, int y, int width, int height) {
        gradientPanel(context, x, y, x + width, y + height,
                INPUT_FILL_START, INPUT_FILL_END, 170.0,
                INPUT_BORDER_START, INPUT_BORDER_END, 90.0);
    }

    static void input(DrawContext context, int x, int y, int width, int height, int border) {
        gradientPanel(context, x, y, x + width, y + height,
                INPUT_FILL_START, INPUT_FILL_END, 170.0, border, border, 90.0);
    }

    static void inputOutline(DrawContext context, int left, int top, int right, int bottom) {
        outline(context, left, top, right, bottom, INPUT_BORDER_START, INPUT_BORDER_END, 90.0);
    }

    private static void gradientPanel(DrawContext context, int left, int top, int right, int bottom,
                                      int fillStart, int fillEnd, double fillAngle,
                                      int borderStart, int borderEnd, double borderAngle) {
        int centerX = (left + right - 1) / 2;
        for (int y = top; y < bottom; y++) {
            int inset = y < top + 2 || y >= bottom - 2 ? 2 : 0;
            context.fill(left + inset, y, right - inset, y + 1,
                    color(fillStart, fillEnd, fillAngle, left, top, right, bottom, centerX, y));
        }
        outline(context, left, top, right, bottom, borderStart, borderEnd, borderAngle);
    }

    private static void outline(DrawContext context, int left, int top, int right, int bottom,
                                int start, int end, double angle) {
        for (int x = left + 2; x < right - 2; x++) {
            context.fill(x, top, x + 1, top + 1, color(start, end, angle, left, top, right, bottom, x, top));
            context.fill(x, bottom - 1, x + 1, bottom, color(start, end, angle, left, top, right, bottom, x, bottom - 1));
        }
        for (int y = top + 2; y < bottom - 2; y++) {
            context.fill(left, y, left + 1, y + 1, color(start, end, angle, left, top, right, bottom, left, y));
            context.fill(right - 1, y, right, y + 1, color(start, end, angle, left, top, right, bottom, right - 1, y));
        }
        context.fill(left + 1, top + 1, left + 2, top + 2, color(start, end, angle, left, top, right, bottom, left + 1, top + 1));
        context.fill(right - 2, top + 1, right - 1, top + 2, color(start, end, angle, left, top, right, bottom, right - 2, top + 1));
        context.fill(left + 1, bottom - 2, left + 2, bottom - 1, color(start, end, angle, left, top, right, bottom, left + 1, bottom - 2));
        context.fill(right - 2, bottom - 2, right - 1, bottom - 1, color(start, end, angle, left, top, right, bottom, right - 2, bottom - 2));
    }

    private static int color(int start, int end, double angle, int left, int top,
                             int right, int bottom, int x, int y) {
        double radians = Math.toRadians(angle);
        double dx = Math.sin(radians);
        double dy = -Math.cos(radians);
        double centerX = (left + right - 1) * 0.5;
        double centerY = (top + bottom - 1) * 0.5;
        double extent = Math.abs(dx) * (right - left - 1) * 0.5 + Math.abs(dy) * (bottom - top - 1) * 0.5;
        float t = extent <= 0.0 ? 0.0f : (float)(((x - centerX) * dx + (y - centerY) * dy + extent) / (extent * 2.0));
        return mix(start, end, Math.max(0.0f, Math.min(1.0f, t)));
    }

    private static int mix(int start, int end, float t) {
        int a = (int)(((start >>> 24) & 255) + (((end >>> 24) & 255) - ((start >>> 24) & 255)) * t);
        int r = (int)(((start >>> 16) & 255) + (((end >>> 16) & 255) - ((start >>> 16) & 255)) * t);
        int g = (int)(((start >>> 8) & 255) + (((end >>> 8) & 255) - ((start >>> 8) & 255)) * t);
        int b = (int)((start & 255) + ((end & 255) - (start & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
