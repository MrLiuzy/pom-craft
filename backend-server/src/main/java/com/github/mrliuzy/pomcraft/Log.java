package com.github.mrliuzy.pomcraft;

import java.io.PrintStream;

public class Log {

    public static final Logger ROOT = new Logger("ROOT");

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
            out.println("[" + level + "] " + name + " - " + format(msg, args));
        }

        private String format(String msg, Object... args) {
            if (args == null || args.length == 0) return msg;
            int i = 0;
            return msg.replace("{}", "%s").formatted(args);
        }

        public void info(String msg, Object... args)  { log(System.out, "INFO", msg, args); }
        public void warn(String msg, Object... args)  { log(System.err, "WARN", msg, args); }
        public void error(String msg, Object... args) { log(System.err, "ERROR", msg, args); }
        public void debug(String msg, Object... args) { log(System.out, "DEBUG", msg, args); }

        public boolean isDebugEnabled() { return true; }
        public boolean isInfoEnabled()  { return true; }
    }
}
