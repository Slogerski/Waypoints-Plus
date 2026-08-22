package pl.slogerski.waypointsplus.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import pl.slogerski.waypointsplus.core.Waypoint;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;

/** Executed with Gson 2.8.9 to guard Minecraft 1.19.2 compatibility. */
public final class LegacyGsonCompatibilityCheck {
    private LegacyGsonCompatibilityCheck() {
    }

    public static void main(String[] args) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Waypoint.class, new WaypointGsonAdapter())
                .create();
        Type listType = new TypeToken<List<Waypoint>>() { }.getType();
        String json = """
                [{
                  "id": "bb6a70f4-d76a-40ae-a211-c2814ab40825",
                  "name": "Home",
                  "serverKey": "example.net",
                  "profile": "Default",
                  "dimension": "minecraft:overworld",
                  "x": 12,
                  "y": 64,
                  "z": -30,
                  "colorArgb": "EE00F5FF"
                }]
                """;

        List<Waypoint> decoded = gson.fromJson(json, listType);
        require(decoded.size() == 1, "Expected one waypoint");
        Waypoint waypoint = decoded.get(0);
        require(waypoint.id().equals(UUID.fromString("bb6a70f4-d76a-40ae-a211-c2814ab40825")),
                "UUID was not preserved");
        require(waypoint.name().equals("Home"), "Name was not preserved");
        require(waypoint.x() == 12 && waypoint.y() == 64 && waypoint.z() == -30,
                "Coordinates were not preserved");

        List<Waypoint> roundTrip = gson.fromJson(gson.toJson(decoded, listType), listType);
        require(roundTrip.equals(decoded), "Waypoint JSON round trip changed data");
        WaypointSettings legacySettings = gson.fromJson("{\"scale\":1.0}", WaypointSettings.class);
        legacySettings.sanitize();
        require(legacySettings.schemaVersion == 1, "Legacy settings schema was not upgraded");
        require(legacySettings.topDonateExpanded, "Legacy settings should expand supporters by default");
        String deeplyNested = "[".repeat(65) + "]".repeat(65);
        try {
            WaypointTransfer.importText(deeplyNested);
            throw new AssertionError("Deep clipboard JSON was accepted");
        } catch (IllegalArgumentException expected) {
        }
        pl.slogerski.waypointsplus.fabric.remote.RemoteContentCompatibilityCheck.run();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
