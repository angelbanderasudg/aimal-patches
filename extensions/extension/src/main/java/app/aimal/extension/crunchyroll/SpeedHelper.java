package app.aimal.extension.crunchyroll;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class SpeedHelper {

    private static final String TAG = "AimalSpeed";

    private static final List<Float> SPEEDS_DESC = Arrays.asList(
        3.0f, 2.75f, 2.5f, 2.25f, 2.0f, 1.75f, 1.5f
    );

    private static final List<Float> SPEEDS_ASC = Arrays.asList(
        1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f
    );

    public static void replaceSpeedList(Object viewModel) {
        if (viewModel == null) return;
        try {
            Log.d(TAG, "replaceSpeedList invoked for " + viewModel.getClass().getName());
            for (Field field : viewModel.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(viewModel);
                if (value == null) continue;

                // Check if the field itself is directly a List of speed options
                if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    if (isSpeedList(list)) {
                        List<Float> replacement = getReplacementList(list);
                        field.set(viewModel, replacement);
                        Log.d(TAG, "Replaced speed List field directly on viewModel");
                        return;
                    }
                }

                // Check if the field holds an object (e.g. LiveData/StateFlow) containing the list
                for (String name : new String[]{"getValue", "a", "b", "c", "d", "e", "f", "g"}) {
                    try {
                        Method getter = value.getClass().getMethod(name);
                        getter.setAccessible(true);
                        Object result = getter.invoke(value);
                        if (result instanceof List) {
                            List<?> list = (List<?>) result;
                            if (isSpeedList(list)) {
                                List<Float> replacement = getReplacementList(list);
                                if (setInternalValue(value, replacement)) {
                                    Log.d(TAG, "Successfully replaced speed list in LiveData/StateFlow");
                                    return;
                                }
                            }
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error replacing speed list", t);
        }
    }

    private static boolean isSpeedList(List<?> list) {
        if (list == null || list.isEmpty()) return false;
        boolean hasFloat = false;
        boolean has1 = false;
        boolean has05 = false;
        for (Object item : list) {
            if (item instanceof Float) {
                hasFloat = true;
                float f = (Float) item;
                if (Math.abs(f - 1.0f) < 0.01f) has1 = true;
                if (Math.abs(f - 0.5f) < 0.01f) has05 = true;
            }
        }
        return hasFloat && has1 && has05;
    }

    private static List<Float> getReplacementList(List<?> originalList) {
        if (originalList != null && originalList.size() >= 2) {
            Object first = originalList.get(0);
            Object last = originalList.get(originalList.size() - 1);
            if (first instanceof Float && last instanceof Float) {
                if (((Float) first) < ((Float) last)) {
                    return SPEEDS_ASC;
                }
            }
        }
        return SPEEDS_DESC;
    }

    private static boolean setInternalValue(Object target, List<Float> replacement) {
        boolean set = false;
        // Try calling setValue / emit methods if present
        for (String setterName : new String[]{"setValue", "b", "setValue-impl", "emit"}) {
            try {
                for (Method m : target.getClass().getMethods()) {
                    if (m.getName().equals(setterName) && m.getParameterTypes().length == 1) {
                        m.setAccessible(true);
                        m.invoke(target, replacement);
                        set = true;
                        break;
                    }
                }
            } catch (Exception ignored) {}
            if (set) return true;
        }

        // Try setting field in target class or any superclass
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object inner = f.get(target);
                    if (inner instanceof List && isSpeedList((List<?>) inner)) {
                        f.set(target, replacement);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Fallback: any field holding a List
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object inner = f.get(target);
                    if (inner instanceof List) {
                        f.set(target, replacement);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }
        return set;
    }
}
