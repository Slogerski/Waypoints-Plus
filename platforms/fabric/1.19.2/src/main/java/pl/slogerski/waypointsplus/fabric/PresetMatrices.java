package pl.slogerski.waypointsplus.fabric;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PresetMatrices {
    private static final FloatBuffer VALUES = ByteBuffer.allocateDirect(16 * Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final Matrix4f READ = new Matrix4f();
    private static final net.minecraft.util.math.Matrix4f WRITE = new net.minecraft.util.math.Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();

    static Matrix4f read(net.minecraft.util.math.Matrix4f matrix) {
        VALUES.clear(); matrix.writeColumnMajor(VALUES); VALUES.rewind();
        return READ.set(VALUES);
    }
    static net.minecraft.util.math.Matrix4f write(Matrix4f matrix) {
        VALUES.clear(); matrix.get(VALUES); VALUES.rewind();
        WRITE.readColumnMajor(VALUES); return WRITE;
    }
    static Quaternionf rotation(net.minecraft.util.math.Quaternion quaternion) {
        return ROTATION.set(quaternion.getX(), quaternion.getY(), quaternion.getZ(), quaternion.getW());
    }
}
