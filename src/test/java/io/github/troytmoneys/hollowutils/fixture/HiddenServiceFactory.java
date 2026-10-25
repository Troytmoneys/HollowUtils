package io.github.troytmoneys.hollowutils.fixture;

public class HiddenServiceFactory {
    public interface Service { String read(String name); void save(boolean enabled); }
    private static abstract class Base implements Service {
        public String read(String name) { return "group:" + name; }
    }
    private static final class Implementation extends Base {
        public void save(boolean enabled) { if (!enabled) throw new IllegalArgumentException("Save rejected"); }
    }
    public static Object create() { return new Implementation(); }
}
