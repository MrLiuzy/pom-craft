package com.github.mrliuzy.pomcraft;

import java.io.PrintStream;

public class Log {

    private static boolean enabled = false;

    public static void setEnabled(boolean enabled) {
        Log.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }

    public static Logger getLogger(Class<?> clazz) {
        return new Logger(clazz.getSimpleName());
    }

    public static class Logger {
        private final String name;

        Logger(String name) { this.name = name; }

        private void log(PrintStream out, String level, String msg, Object... args) {
            if (!enabled) return;
            out.println("[" + level + "] " + name + " - " + format(msg, args));
        }

        private String format(String msg, Object... args) {
            if (args == null || args.length == 0) return msg;
            return String.format(msg.replace("{}", "%s"), args);
        }

        public void info(String msg, Object... args)  { log(System.out, "INFO", msg, args); }
        public void warn(String msg, Object... args)  { log(System.err, "WARN", msg, args); }
        public void error(String msg, Object... args) { log(System.err, "ERROR", msg, args); }
        public void debug(String msg, Object... args) { log(System.out, "DEBUG", msg, args); }

        public boolean isDebugEnabled() { return enabled; }
        public boolean isInfoEnabled()  { return enabled; }
    }
}
