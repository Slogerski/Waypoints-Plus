package pl.slogerski.waypointsplus.fabric;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class OgulniegaLanguage {
    enum Result { POLISH, ENGLISH, NOT_READY, UNAVAILABLE }

    private final boolean present;
    private final ClassLoader loader;
    private Method getter;

    OgulniegaLanguage(boolean present) {
        this(present, OgulniegaLanguage.class.getClassLoader());
    }

    OgulniegaLanguage(boolean present, ClassLoader loader) {
        this.present = present;
        this.loader = loader;
    }

    boolean isPresent() { return present; }

    Result read() {
        if (!present) return Result.UNAVAILABLE;
        try {
            if (getter == null) {
                Class<?> api = Class.forName("com.ogulniega.OgulniegaModApi", false, loader);
                Method method = api.getMethod("getLanguage");
                if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != String.class) {
                    return Result.UNAVAILABLE;
                }
                getter = method;
            }
            Object value = getter.invoke(null);
            if (value == null) return Result.NOT_READY;
            return switch ((String) value) {
                case "POLISH" -> Result.POLISH;
                case "ENGLISH" -> Result.ENGLISH;
                default -> Result.UNAVAILABLE;
            };
        } catch (InvocationTargetException exception) {
            return exception.getCause() instanceof NullPointerException ? Result.NOT_READY : Result.UNAVAILABLE;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return Result.UNAVAILABLE;
        }
    }
}
