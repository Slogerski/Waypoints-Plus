package pl.slogerski.waypointsplus.fabric;

import com.mojang.blaze3d.platform.InputConstants;

final class LegacyKeyCodes {
    static final int LEFT_SHIFT = 340;
    static final int LEFT_CONTROL = 341;
    static final int LEFT_ALT = 342;
    static final int LEFT_SUPER = 343;
    static final int RIGHT_SHIFT = 344;
    static final int RIGHT_CONTROL = 345;
    static final int RIGHT_ALT = 346;
    static final int RIGHT_SUPER = 347;

    private LegacyKeyCodes() { }

    static int fromNative(int key) {
        if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) return 65 + key - InputConstants.KEY_A;
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) return 49 + key - InputConstants.KEY_1;
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) return 290 + key - InputConstants.KEY_F1;
        if (key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24) return 302 + key - InputConstants.KEY_F13;
        if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) return 321 + key - InputConstants.KEY_NUMPAD1;
        return switch (key) {
            case InputConstants.KEY_0 -> 48;
            case InputConstants.KEY_SPACE -> 32;
            case InputConstants.KEY_APOSTROPHE -> 39;
            case InputConstants.KEY_COMMA -> 44;
            case InputConstants.KEY_MINUS -> 45;
            case InputConstants.KEY_PERIOD -> 46;
            case InputConstants.KEY_SLASH -> 47;
            case InputConstants.KEY_SEMICOLON -> 59;
            case InputConstants.KEY_EQUALS -> 61;
            case InputConstants.KEY_LBRACKET -> 91;
            case InputConstants.KEY_BACKSLASH -> 92;
            case InputConstants.KEY_RBRACKET -> 93;
            case InputConstants.KEY_GRAVE -> 96;
            case InputConstants.KEY_ESCAPE -> 256;
            case InputConstants.KEY_RETURN -> 257;
            case InputConstants.KEY_TAB -> 258;
            case InputConstants.KEY_BACKSPACE -> 259;
            case InputConstants.KEY_INSERT -> 260;
            case InputConstants.KEY_DELETE -> 261;
            case InputConstants.KEY_RIGHT -> 262;
            case InputConstants.KEY_LEFT -> 263;
            case InputConstants.KEY_DOWN -> 264;
            case InputConstants.KEY_UP -> 265;
            case InputConstants.KEY_PAGEUP -> 266;
            case InputConstants.KEY_PAGEDOWN -> 267;
            case InputConstants.KEY_HOME -> 268;
            case InputConstants.KEY_END -> 269;
            case InputConstants.KEY_CAPSLOCK -> 280;
            case InputConstants.KEY_SCROLLLOCK -> 281;
            case InputConstants.KEY_NUMLOCK -> 282;
            case InputConstants.KEY_PRINTSCREEN -> 283;
            case InputConstants.KEY_PAUSE -> 284;
            case InputConstants.KEY_NUMPAD0 -> 320;
            case 99 -> 330;
            case 84 -> 331;
            case InputConstants.KEY_MULTIPLY -> 332;
            case 86 -> 333;
            case InputConstants.KEY_ADD -> 334;
            case InputConstants.KEY_NUMPADENTER -> 335;
            case InputConstants.KEY_NUMPADEQUALS -> 336;
            case InputConstants.KEY_LSHIFT -> LEFT_SHIFT;
            case InputConstants.KEY_LCONTROL -> LEFT_CONTROL;
            case InputConstants.KEY_LALT -> LEFT_ALT;
            case InputConstants.KEY_LGUI -> LEFT_SUPER;
            case InputConstants.KEY_RSHIFT -> RIGHT_SHIFT;
            case InputConstants.KEY_RCONTROL -> RIGHT_CONTROL;
            case InputConstants.KEY_RALT -> RIGHT_ALT;
            case InputConstants.KEY_RGUI -> RIGHT_SUPER;
            default -> -1;
        };
    }

    static int toNative(int key) {
        if (key >= 65 && key <= 90) return InputConstants.KEY_A + key - 65;
        if (key >= 49 && key <= 57) return InputConstants.KEY_1 + key - 49;
        if (key >= 290 && key <= 301) return InputConstants.KEY_F1 + key - 290;
        if (key >= 302 && key <= 313) return InputConstants.KEY_F13 + key - 302;
        if (key >= 321 && key <= 329) return InputConstants.KEY_NUMPAD1 + key - 321;
        return switch (key) {
            case 48 -> InputConstants.KEY_0;
            case 32 -> InputConstants.KEY_SPACE;
            case 39 -> InputConstants.KEY_APOSTROPHE;
            case 44 -> InputConstants.KEY_COMMA;
            case 45 -> InputConstants.KEY_MINUS;
            case 46 -> InputConstants.KEY_PERIOD;
            case 47 -> InputConstants.KEY_SLASH;
            case 59 -> InputConstants.KEY_SEMICOLON;
            case 61 -> InputConstants.KEY_EQUALS;
            case 91 -> InputConstants.KEY_LBRACKET;
            case 92 -> InputConstants.KEY_BACKSLASH;
            case 93 -> InputConstants.KEY_RBRACKET;
            case 96 -> InputConstants.KEY_GRAVE;
            case 256 -> InputConstants.KEY_ESCAPE;
            case 257 -> InputConstants.KEY_RETURN;
            case 258 -> InputConstants.KEY_TAB;
            case 259 -> InputConstants.KEY_BACKSPACE;
            case 260 -> InputConstants.KEY_INSERT;
            case 261 -> InputConstants.KEY_DELETE;
            case 262 -> InputConstants.KEY_RIGHT;
            case 263 -> InputConstants.KEY_LEFT;
            case 264 -> InputConstants.KEY_DOWN;
            case 265 -> InputConstants.KEY_UP;
            case 266 -> InputConstants.KEY_PAGEUP;
            case 267 -> InputConstants.KEY_PAGEDOWN;
            case 268 -> InputConstants.KEY_HOME;
            case 269 -> InputConstants.KEY_END;
            case 280 -> InputConstants.KEY_CAPSLOCK;
            case 281 -> InputConstants.KEY_SCROLLLOCK;
            case 282 -> InputConstants.KEY_NUMLOCK;
            case 283 -> InputConstants.KEY_PRINTSCREEN;
            case 284 -> InputConstants.KEY_PAUSE;
            case 320 -> InputConstants.KEY_NUMPAD0;
            case 330 -> 99;
            case 331 -> 84;
            case 332 -> InputConstants.KEY_MULTIPLY;
            case 333 -> 86;
            case 334 -> InputConstants.KEY_ADD;
            case 335 -> InputConstants.KEY_NUMPADENTER;
            case 336 -> InputConstants.KEY_NUMPADEQUALS;
            case LEFT_SHIFT -> InputConstants.KEY_LSHIFT;
            case LEFT_CONTROL -> InputConstants.KEY_LCONTROL;
            case LEFT_ALT -> InputConstants.KEY_LALT;
            case LEFT_SUPER -> InputConstants.KEY_LGUI;
            case RIGHT_SHIFT -> InputConstants.KEY_RSHIFT;
            case RIGHT_CONTROL -> InputConstants.KEY_RCONTROL;
            case RIGHT_ALT -> InputConstants.KEY_RALT;
            case RIGHT_SUPER -> InputConstants.KEY_RGUI;
            default -> InputConstants.UNKNOWN.getValue();
        };
    }

    static boolean isSupported(int key) {
        return key >= 0 && toNative(key) != InputConstants.UNKNOWN.getValue();
    }
}
