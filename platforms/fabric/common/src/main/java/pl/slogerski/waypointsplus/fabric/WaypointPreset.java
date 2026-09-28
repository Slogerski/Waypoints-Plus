package pl.slogerski.waypointsplus.fabric;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

final class WaypointPreset {
    static final String DEFAULT_ICON_ID = "c57198e2-e806-4fb2-8778-437366612d97";
    static final String SINGLE_ICON_ID = "40ab3b83-174c-4ffa-a745-8c74b2373349";
    static final String DESIGNED_ID = "d56e6920-4908-4b42-a864-242110272a88";
    String id = UUID.randomUUID().toString();
    String name = "";
    String item = "minecraft:grass_block";
    String png = "";
    TextPart label = new TextPart(true, 0, 0, 1);
    TextPart distance = new TextPart(true, 0, 12, 1);
    TextPart coordinates = new TextPart(false, 0, 24, 1);
    TextPart profile = new TextPart(false, 0, 36, 1);
    boolean icon = false;
    boolean pngIcon = false;
    float iconX = -40;
    float iconY = 0;
    float iconScale = 1;
    Anchor iconHorizontal;
    Anchor iconVertical;
    boolean corners = true;
    boolean border = true;
    boolean background = true;
    boolean labelLinked = true;
    boolean distanceLinked = true;
    boolean coordinatesLinked = true;
    boolean profileLinked = true;
    boolean iconLinked = true;
    float borderSize = 1;
    float padding = 5;
    boolean favorite = false;

    static WaypointPreset classic() {
        WaypointPreset preset = new WaypointPreset();
        WaypointSettings settings = WaypointsPlusClient.config().settings();
        preset.distance.enabled = settings.showDistance;
        preset.coordinates.enabled = settings.showCoordinates;
        preset.background = settings.background;
        return preset;
    }

    static WaypointPreset defaultIcon() {
        WaypointPreset preset = classic();
        preset.id = DEFAULT_ICON_ID;
        preset.name = UiText.get("Default + Icon", "Domyślny + ikona");
        preset.icon = true;
        preset.iconLinked = false;
        preset.iconY = -8;
        return preset;
    }

    static WaypointPreset singleIcon() {
        WaypointPreset preset = new WaypointPreset();
        preset.id = SINGLE_ICON_ID;
        preset.name = "Single icon";
        preset.item = "minecraft:grass_block";
        preset.label.enabled = false;
        preset.distance.x = 0;
        preset.icon = true;
        preset.iconX = -8.64219f;
        preset.iconY = -5.341125f;
        preset.corners = true;
        preset.border = false;
        preset.padding = 2;
        return preset;
    }

    static WaypointPreset designed() {
        WaypointPreset preset = new WaypointPreset();
        preset.id = DESIGNED_ID;
        preset.name = "Designed";
        preset.item = "minecraft:ender_eye";
        preset.label = new TextPart(true, 0, 0, 1);
        preset.distance = new TextPart(true, -0.11119853f, 0.9129925f, 0.8f);
        preset.distance.vertical = new Anchor(5, 2, 3, -0.5536284f);
        preset.coordinates = new TextPart(true, 0.19271216f, 25.38925f, 0.6f);
        preset.coordinates.vertical = new Anchor(5, 3, 2, 2.1946318f);
        preset.icon = true;
        preset.iconX = -9.896601f;
        preset.iconY = -21.466366f;
        preset.iconScale = 1.2f;
        preset.iconVertical = new Anchor(5, 2, 3, -7.8271127f);
        preset.border = false;
        preset.distanceLinked = false;
        preset.coordinatesLinked = false;
        preset.profileLinked = false;
        preset.iconLinked = false;
        preset.padding = 2;
        preset.favorite = true;
        return preset;
    }

    static boolean builtIn(String id) {
        return "default".equals(id) || DEFAULT_ICON_ID.equals(id) || SINGLE_ICON_ID.equals(id) || DESIGNED_ID.equals(id);
    }

    boolean valid() {
        if (id == null || !id.matches("default|[0-9a-fA-F-]{36}") || name == null || name.isBlank()
                || name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) return false;
        return WaypointPresetStore.itemExists(item) && png != null
                && label != null && label.valid() && distance != null && distance.valid()
                && coordinates != null && coordinates.valid() && profile != null && profile.valid()
                && position(iconX) && position(iconY) && scale(iconScale)
                && range(borderSize, 0.5f, 8) && range(padding, 0, 32)
                && (!icon || !pngIcon || !png.isEmpty()) && (png.isEmpty() || validPng(png)) && WaypointPresetLayout.valid(this);
    }

    boolean linked(int index) {
        return switch (index) {
            case 0 -> labelLinked;
            case 1 -> distanceLinked;
            case 2 -> coordinatesLinked;
            case 3 -> profileLinked;
            default -> iconLinked;
        };
    }

    TextPart part(int index) {
        return switch (index) {
            case 0 -> label;
            case 1 -> distance;
            case 2 -> coordinates;
            default -> profile;
        };
    }

    Anchor anchor(int index, int axis) {
        return index == 4 ? (axis == 0 ? iconHorizontal : iconVertical)
                : (axis == 0 ? part(index).horizontal : part(index).vertical);
    }

    void setAnchor(int index, int axis, Anchor anchor) {
        if (index == 4) {
            if (axis == 0) iconHorizontal = anchor; else iconVertical = anchor;
        } else {
            if (axis == 0) part(index).horizontal = anchor; else part(index).vertical = anchor;
        }
    }

    void setLinked(int index, boolean linked) {
        if (linked(index) != linked) toggleLinked(index);
    }

    void toggleLinked(int index) {
        switch (index) {
            case 0 -> labelLinked = !labelLinked;
            case 1 -> distanceLinked = !distanceLinked;
            case 2 -> coordinatesLinked = !coordinatesLinked;
            case 3 -> profileLinked = !profileLinked;
            default -> iconLinked = !iconLinked;
        }
    }

    static boolean validPng(String encoded) {
        if (encoded == null || encoded.length() > 44000) return false;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length < 33 || bytes.length > 32768) return false;
            ByteBuffer header = ByteBuffer.wrap(bytes);
            if (header.getLong() != 0x89504E470D0A1A0AL || header.getInt() != 13
                    || header.getInt() != 0x49484452) return false;
            int width = header.getInt();
            int height = header.getInt();
            return width == height && (width == 16 || width == 32 || width == 64);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean position(float value) {
        return range(value, -512, 512);
    }

    static boolean scale(float value) {
        return range(value, 0.25f, 8);
    }

    static boolean range(float value, float minimum, float maximum) {
        return Float.isFinite(value) && value >= minimum && value <= maximum;
    }

    static final class Anchor {
        int target;
        int edge;
        int ownEdge;
        float offset;

        Anchor(int target, int edge, int ownEdge, float offset) {
            this.target = target;
            this.edge = edge;
            this.ownEdge = ownEdge;
            this.offset = offset;
        }

        boolean valid(int axis) {
            return target >= 0 && target <= 5 && edge >= 0 && edge <= 3 && ownEdge >= 0 && ownEdge <= 3
                    && edge / 2 == axis && ownEdge / 2 == axis && position(offset);
        }
    }

    static final class TextPart {
        boolean enabled;
        float x;
        float y;
        float scale;
        Anchor horizontal;
        Anchor vertical;

        TextPart(boolean enabled, float x, float y, float scale) {
            this.enabled = enabled;
            this.x = x;
            this.y = y;
            this.scale = scale;
        }

        boolean valid() {
            return position(x) && position(y) && WaypointPreset.scale(scale);
        }
    }
}
