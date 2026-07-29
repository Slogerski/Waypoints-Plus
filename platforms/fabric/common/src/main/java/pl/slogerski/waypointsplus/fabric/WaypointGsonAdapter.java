package pl.slogerski.waypointsplus.fabric;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import pl.slogerski.waypointsplus.core.Waypoint;

import java.io.IOException;
import java.util.UUID;

/**
 * Explicit waypoint codec compatible with the Gson 2.8.9 bundled by Minecraft
 * 1.19.2. It avoids reflective writes to final record fields.
 */
final class WaypointGsonAdapter extends TypeAdapter<Waypoint> {
    @Override
    public void write(JsonWriter out, Waypoint waypoint) throws IOException {
        if (waypoint == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("id").value(waypoint.id().toString());
        out.name("name").value(waypoint.name());
        out.name("serverKey").value(waypoint.serverKey());
        out.name("profile").value(waypoint.profile());
        out.name("dimension").value(waypoint.dimension());
        out.name("x").value(waypoint.x());
        out.name("y").value(waypoint.y());
        out.name("z").value(waypoint.z());
        out.name("colorArgb").value(waypoint.colorArgb());
        out.endObject();
    }

    @Override
    public Waypoint read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        UUID id = UUID.randomUUID();
        String name = "Waypoint";
        String serverKey = null;
        String profile = "Default";
        String dimension = "minecraft:overworld";
        String colorArgb = "EE00F5FF";
        int x = 0;
        int y = 0;
        int z = 0;

        in.beginObject();
        while (in.hasNext()) {
            String field = in.nextName();
            switch (field) {
                case "id" -> id = readUuid(in, id);
                case "name" -> name = readString(in, name);
                case "serverKey" -> serverKey = readNullableString(in);
                case "profile" -> profile = readString(in, profile);
                case "dimension" -> dimension = readString(in, dimension);
                case "x" -> x = readInt(in, x);
                case "y" -> y = readInt(in, y);
                case "z" -> z = readInt(in, z);
                case "colorArgb" -> colorArgb = readString(in, colorArgb);
                default -> in.skipValue();
            }
        }
        in.endObject();
        return new Waypoint(id, name, serverKey, profile, dimension, x, y, z, colorArgb);
    }

    private static UUID readUuid(JsonReader in, UUID fallback) throws IOException {
        String value = readNullableString(in);
        if (value == null) return fallback;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String readString(JsonReader in, String fallback) throws IOException {
        String value = readNullableString(in);
        return value == null ? fallback : value;
    }

    private static String readNullableString(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return in.nextString();
    }

    private static int readInt(JsonReader in, int fallback) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return fallback;
        }
        try {
            return in.nextInt();
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
