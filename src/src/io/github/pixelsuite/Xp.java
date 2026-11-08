package io.github.pixelsuite;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;

final class Xp {

    private static volatile XposedInterface sX;

    private Xp() {}

    static void attach(XposedInterface x) { sX = x; }
    static XposedInterface x() { return sX; }

    abstract static class Callback {
        protected void beforeHookedMethod(Param param) throws Throwable {}
        protected void afterHookedMethod(Param param) throws Throwable {}
    }

    static final class Param {
        public Executable method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private boolean resultSet;

        Object getResult() { return result; }
        void setResult(Object r) { result = r; resultSet = true; }
    }

    static Set<Object> hookAllMethods(Class<?> clazz, String name, Callback cb) {
        Set<Object> handles = new HashSet<Object>();
        if (clazz == null) return handles;
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            Object h = hook(m, cb);
            if (h != null) handles.add(h);
        }
        return handles;
    }

    static Set<Object> hookAllConstructors(Class<?> clazz, Callback cb) {
        Set<Object> handles = new HashSet<Object>();
        if (clazz == null) return handles;
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            Object h = hook(c, cb);
            if (h != null) handles.add(h);
        }
        return handles;
    }

    static Object findAndHookMethod(Class<?> clazz, String name, Object... typesAndCallback) {
        if (clazz == null || typesAndCallback.length == 0) return null;
        Callback cb = (Callback) typesAndCallback[typesAndCallback.length - 1];
        Class<?>[] types = new Class<?>[typesAndCallback.length - 1];
        for (int i = 0; i < types.length; i++) types[i] = asClass(typesAndCallback[i], clazz);
        try {
            Method m = clazz.getDeclaredMethod(name, types);
            return hook(m, cb);
        } catch (Throwable t) {
            return null;
        }
    }

    static Object hookMethod(Executable m, Callback cb) {
        return hook(m, cb);
    }

    static void deoptimize(Executable executable) {
        XposedInterface x = sX;
        if (x == null || executable == null) return;
        try { x.deoptimize(executable); } catch (Throwable t) { }
    }
    static void deoptimizeAll(Class<?> clazz, String name) {
        if (clazz == null) return;
        for (Method m : clazz.getDeclaredMethods()) if (m.getName().equals(name)) deoptimize(m);
    }

    private static Object hook(Executable executable, final Callback cb) {
        XposedInterface x = sX;
        if (x == null || executable == null) return null;
        try {
            final Executable exec = executable;
            final boolean gboardPlus = GboardMain.owns(cb);
            Hooker hooker = new Hooker() {
                @Override
                public Object intercept(Chain chain) throws Throwable {
                    if (gboardPlus && GboardMain.off()) {
                        List<Object> a0 = chain.getArgs();
                        return chain.proceed(a0 == null ? new Object[0] : a0.toArray());
                    }
                    Param p = new Param();
                    p.method = exec;
                    p.thisObject = chain.getThisObject();
                    List<Object> a = chain.getArgs();
                    p.args = a == null ? new Object[0] : a.toArray();
                    try {
                        cb.beforeHookedMethod(p);
                    } catch (Throwable t) {
                    }
                    if (!p.resultSet) {
                        Object res = chain.proceed(p.args);
                        p.result = res;
                    }
                    try {
                        cb.afterHookedMethod(p);
                    } catch (Throwable t) {
                    }
                    return p.result;
                }
            };
            return x.hook(exec).intercept(hooker);
        } catch (Throwable t) {
            return null;
        }
    }

    static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            throw new RuntimeException("class not found: " + name, t);
        }
    }

    static Method findMethodExactIfExists(Class<?> clazz, String name, Class<?>... types) {
        if (clazz == null) return null;
        try {
            Method m = clazz.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            for (Class<?> c = clazz.getSuperclass(); c != null; c = c.getSuperclass()) {
                try {
                    Method m = c.getDeclaredMethod(name, types);
                    m.setAccessible(true);
                    return m;
                } catch (Throwable ignored) { }
            }
            return null;
        }
    }

    static Object callMethod(Object obj, String name, Object... args) {
        try {
            Method m = bestMethod(obj.getClass(), name, args);
            if (m == null) {
                String field = getterField(name, args);
                if (field != null) {
                    try { return findField(obj.getClass(), field).get(obj); } catch (NoSuchFieldException ignored) { }
                }
                throw new NoSuchMethodException(obj.getClass().getName() + "#" + name);
            }
            return m.invoke(obj, args);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static String otherSpelling(String name) {
        if (name == null || name.length() < 2) return null;
        if (name.charAt(0) == 'm' && Character.isUpperCase(name.charAt(1))) return Character.toLowerCase(name.charAt(1)) + name.substring(2);
        if (Character.isLowerCase(name.charAt(0))) return "m" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return null;
    }

    private static String getterField(String name, Object[] args) {
        if ((args != null && args.length > 0) || name == null || name.length() < 4 || !name.startsWith("get") || !Character.isUpperCase(name.charAt(3))) return null;
        return Character.toLowerCase(name.charAt(3)) + name.substring(4);
    }

    static Object callStaticMethod(Class<?> clazz, String name, Object... args) {
        try {
            Method m = bestMethod(clazz, name, args);
            if (m == null) throw new NoSuchMethodException(clazz.getName() + "#" + name);
            return m.invoke(null, args);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static Object newInstance(Class<?> clazz, Object... args) {
        try {
            Constructor<?> c = bestConstructor(clazz, args);
            if (c == null) {
                throw new NoSuchMethodException(clazz.getName() + ".<init> for given args");
            }
            return c.newInstance(args);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static Object getObjectField(Object obj, String name) {
        try {
            return findField(obj.getClass(), name).get(obj);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void setObjectField(Object obj, String name, Object value) {
        try {
            findField(obj.getClass(), name).set(obj, value);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static boolean getBooleanField(Object obj, String name) {
        try {
            return findField(obj.getClass(), name).getBoolean(obj);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static void setBooleanField(Object obj, String name, boolean value) {
        try {
            findField(obj.getClass(), name).setBoolean(obj, value);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static final WeakHashMap<Object, java.util.HashMap<String, Object>> sExtra =
            new WeakHashMap<Object, java.util.HashMap<String, Object>>();
    private static final int TAG_EXTRAS = 0x7e5c0e01, TAG_SCREEN_EXTRAS = 0x7e5c0e02;

    @SuppressWarnings("unchecked")
    private static java.util.HashMap<String, Object> extras(Object obj, boolean make) {
        android.view.View host = null; int tag = TAG_EXTRAS;
        if (obj instanceof android.view.View) host = (android.view.View) obj;
        else if (obj instanceof android.app.Activity) {
            try { android.view.Window w = ((android.app.Activity) obj).getWindow(); host = w == null ? null : w.peekDecorView(); tag = TAG_SCREEN_EXTRAS; } catch (Throwable ignored) { host = null; }
        }
        if (host != null) {
            try {
                Object t = host.getTag(tag);
                if (t instanceof java.util.HashMap) return (java.util.HashMap<String, Object>) t;
                java.util.HashMap<String, Object> m = null;
                if (!(obj instanceof android.view.View)) synchronized (sExtra) { m = sExtra.remove(obj); }
                if (m == null && !make) return null;
                if (m == null) m = new java.util.HashMap<String, Object>();
                host.setTag(tag, m);
                return m;
            } catch (Throwable ignored) { }
        }
        synchronized (sExtra) {
            java.util.HashMap<String, Object> m = sExtra.get(obj);
            if (m == null && make) { m = new java.util.HashMap<String, Object>(); sExtra.put(obj, m); }
            return m;
        }
    }

    static Object setAdditionalInstanceField(Object obj, String key, Object value) {
        if (obj == null) return null;
        java.util.HashMap<String, Object> m = extras(obj, value != null);
        if (m == null) return null;
        synchronized (m) { return value == null ? m.remove(key) : m.put(key, value); }
    }

    static Object getAdditionalInstanceField(Object obj, String key) {
        if (obj == null) return null;
        java.util.HashMap<String, Object> m = extras(obj, false);
        if (m == null) return null;
        synchronized (m) { return m.get(key); }
    }

    private static Class<?> asClass(Object o, Class<?> ctx) {
        if (o instanceof Class) return (Class<?>) o;
        if (o instanceof String) {
            try {
                return Class.forName((String) o, false, ctx.getClassLoader());
            } catch (Throwable t) {
                throw new RuntimeException("type not found: " + o, t);
            }
        }
        throw new IllegalArgumentException("not a type: " + o);
    }

    private static Method bestMethod(Class<?> clazz, String name, Object[] args) {
        Key key = new Key(clazz, name, args);
        Object hit = sMethods.get(key);
        if (hit == null) {
            Method m = findBestMethod(clazz, name, args);
            hit = m != null ? m : NONE;
            sMethods.put(key, hit);
        }
        return hit == NONE ? null : (Method) hit;
    }

    private static Method findBestMethod(Class<?> clazz, String name, Object[] args) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && accepts(m.getParameterTypes(), args)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        for (Class<?> itf : allInterfaces(clazz)) {
            for (Method m : itf.getMethods()) {
                if (m.getName().equals(name) && accepts(m.getParameterTypes(), args)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static boolean accepts(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            Object a = args[i];
            Class<?> t = types[i];
            if (a == null) {
                if (t.isPrimitive()) return false;
                continue;
            }
            if (!box(t).isInstance(a)) return false;
        }
        return true;
    }

    private static Class<?> box(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class;
        if (t == boolean.class) return Boolean.class;
        if (t == long.class) return Long.class;
        if (t == float.class) return Float.class;
        if (t == double.class) return Double.class;
        if (t == short.class) return Short.class;
        if (t == byte.class) return Byte.class;
        if (t == char.class) return Character.class;
        return t;
    }

    private static Set<Class<?>> allInterfaces(Class<?> clazz) {
        Set<Class<?>> out = new HashSet<Class<?>>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            Collections.addAll(out, c.getInterfaces());
        }
        return out;
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Key key = new Key(clazz, name, null);
        Object hit = sFields.get(key);
        if (hit == null) {
            hit = NONE;
            for (Class<?> c = clazz; c != null && hit == NONE; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    hit = f;
                } catch (NoSuchFieldException ignored) { }
            }
            if (hit == NONE) {
                String other = otherSpelling(name);
                for (Class<?> c = clazz; other != null && c != null && hit == NONE; c = c.getSuperclass()) {
                    try {
                        Field f = c.getDeclaredField(other);
                        f.setAccessible(true);
                        hit = f;
                    } catch (NoSuchFieldException ignored) { }
                }
            }
            sFields.put(key, hit);
        }
        if (hit == NONE) throw new NoSuchFieldException(clazz.getName() + "#" + name);
        return (Field) hit;
    }

    private static Constructor<?> bestConstructor(Class<?> clazz, Object[] args) {
        Key key = new Key(clazz, "<init>", args);
        Object hit = sCtors.get(key);
        if (hit == null) {
            hit = NONE;
            for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                if (accepts(c.getParameterTypes(), args)) {
                    c.setAccessible(true);
                    hit = c;
                    break;
                }
            }
            sCtors.put(key, hit);
        }
        return hit == NONE ? null : (Constructor<?>) hit;
    }

    private static final Object NONE = new Object();
    private static final ConcurrentHashMap<Key, Object> sMethods =
            new ConcurrentHashMap<Key, Object>();
    private static final ConcurrentHashMap<Key, Object> sFields =
            new ConcurrentHashMap<Key, Object>();
    private static final ConcurrentHashMap<Key, Object> sCtors =
            new ConcurrentHashMap<Key, Object>();

    private static final class Key {
        private final Class<?> mClass;
        private final String mName;
        private final Class<?>[] mTypes;
        private final int mHash;

        Key(Class<?> clazz, String name, Object[] args) {
            mClass = clazz;
            mName = name;
            int h = System.identityHashCode(clazz) * 31 + name.hashCode();
            if (args == null) {
                mTypes = null;
            } else {
                mTypes = new Class<?>[args.length];
                for (int i = 0; i < args.length; i++) {
                    Class<?> t = args[i] == null ? null : args[i].getClass();
                    mTypes[i] = t;
                    h = h * 31 + (t == null ? 0 : System.identityHashCode(t));
                }
            }
            mHash = h;
        }

        @Override
        public int hashCode() {
            return mHash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return mHash == k.mHash && mClass == k.mClass && mName.equals(k.mName)
                    && Arrays.equals(mTypes, k.mTypes);
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof RuntimeException) return (RuntimeException) t;
        return new RuntimeException(t);
    }
}
