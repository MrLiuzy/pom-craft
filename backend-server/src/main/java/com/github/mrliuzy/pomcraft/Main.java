package com.github.mrliuzy.pomcraft;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.github.mrliuzy.pomcraft.config.PomParserConfig;
import com.github.mrliuzy.pomcraft.model.ConflictInfo;
import com.github.mrliuzy.pomcraft.model.DependencyInfo;
import com.github.mrliuzy.pomcraft.model.ParsedPomResult;
import com.github.mrliuzy.pomcraft.resolver.MavenPomResolver;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = 0;
        for (String arg : args) {
            if ("--debug".equals(arg)) {
                Log.setEnabled(true);
            } else {
                try { port = Integer.parseInt(arg); } catch (NumberFormatException ignored) {}
            }
        }

        Server server = new Server(port);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                               HttpServletRequest request, HttpServletResponse response)
                    throws IOException {
                response.setContentType("application/json;charset=utf-8");
                response.setHeader("Access-Control-Allow-Origin", "*");
                baseRequest.setHandled(true);

                try {
                    switch (target) {
                        case "/health":
                            sendJson(response, 200, "{\"status\":\"ok\"}");
                            break;
                        case "/resolve":
                            if (!"POST".equals(request.getMethod())) {
                                sendJson(response, 405, "{\"error\":\"Method not allowed\"}");
                                return;
                            }
                            handleResolve(request, response);
                            break;
                        case "/effective-pom":
                            if (!"POST".equals(request.getMethod())) {
                                sendJson(response, 405, "{\"error\":\"Method not allowed\"}");
                                return;
                            }
                            handleEffectivePom(request, response);
                            break;
                        default:
                            sendJson(response, 404, "{\"error\":\"Not found\"}");
                    }
                } catch (Exception e) {
                    sendJson(response, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        server.start();
        int actualPort = server.getURI().getPort();

        System.out.println("{\"port\":" + actualPort + "}");
        System.out.flush();

        server.join();
    }

    private static void handleResolve(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String body = readBody(request);
        PomParserConfig config = buildConfig(body);
        MavenPomResolver resolver = new MavenPomResolver(config);
        ParsedPomResult result = resolver.resolve();
        sendJson(response, 200, toJson(result));
    }

    private static void handleEffectivePom(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String body = readBody(request);
        PomParserConfig config = buildConfig(body);
        MavenPomResolver resolver = new MavenPomResolver(config);
        String xml = resolver.getEffectivePomXml();

        if (xml != null) {
            sendJson(response, 200,
                "{\"success\":true,\"effectivePom\":\"" + escapeJson(xml) + "\"}");
        } else {
            sendJson(response, 500,
                "{\"success\":false,\"errorMessage\":\"Failed to build effective POM\"}");
        }
    }

    private static PomParserConfig buildConfig(String body) {
        String targetPom = extractJsonString(body, "targetPom");
        boolean offline = "true".equals(extractJsonString(body, "offline"));
        boolean skipFailed = "true".equals(extractJsonString(body, "skipFailedResolution"));
        String settingsXml = extractJsonString(body, "settingsXml");
        List<String> wsDirs = extractJsonArray(body, "workspaceDirectories");

        PomParserConfig config = new PomParserConfig();
        config.setTargetPom(new File(targetPom));
        config.setOffline(offline);
        config.setSkipFailedResolution(skipFailed);

        if (settingsXml != null && !settingsXml.isEmpty()) {
            config.setSettingsXml(new File(settingsXml));
        }
        if (wsDirs != null) {
            for (String dir : wsDirs) {
                File f = new File(dir);
                if (f.isDirectory()) {
                    config.getWorkspaceDirectories().add(f);
                }
            }
        }
        return config;
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        java.io.BufferedReader reader = request.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    // ---- JSON helpers ----

    private static String toJson(ParsedPomResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":").append(r.isSuccess());
        sb.append(",\"errorMessage\":").append(jsonStr(r.getErrorMessage()));
        sb.append(",\"data\":{");

        sb.append("\"directDependencies\":[");
        boolean first = true;
        for (DependencyInfo d : r.getDirectDependencies()) {
            if (!first) sb.append(",");
            sb.append(toJson(d));
            first = false;
        }
        sb.append("],\"allDependencies\":[");
        first = true;
        for (DependencyInfo d : r.getAllDependencies()) {
            if (!first) sb.append(",");
            sb.append(toJson(d));
            first = false;
        }
        sb.append("],\"conflicts\":[");
        first = true;
        for (ConflictInfo c : r.getConflicts()) {
            if (!first) sb.append(",");
            sb.append(toJson(c));
            first = false;
        }
        sb.append("]}");
        sb.append("}");
        return sb.toString();
    }

    private static String toJson(DependencyInfo d) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"groupId\":").append(jsonStr(d.getGroupId()));
        sb.append(",\"artifactId\":").append(jsonStr(d.getArtifactId()));
        sb.append(",\"version\":").append(jsonStr(d.getVersion()));
        sb.append(",\"scope\":").append(jsonStr(d.getScope()));
        sb.append(",\"optional\":").append(d.isOptional());
        sb.append(",\"type\":").append(jsonStr(d.getType()));
        sb.append(",\"classifier\":").append(jsonStr(d.getClassifier()));
        sb.append(",\"resolved\":").append(d.isResolved());
        sb.append(",\"errorMessage\":").append(jsonStr(d.getErrorMessage()));
        sb.append(",\"managedVersion\":").append(jsonStr(d.getManagedVersion()));
        sb.append(",\"children\":[");
        boolean first = true;
        for (DependencyInfo child : d.getChildren()) {
            if (!first) sb.append(",");
            sb.append(toJson(child));
            first = false;
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static String toJson(ConflictInfo c) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"groupId\":").append(jsonStr(c.getGroupId()));
        sb.append(",\"artifactId\":").append(jsonStr(c.getArtifactId()));
        sb.append(",\"version\":").append(jsonStr(c.getVersion()));
        sb.append(",\"conflictingVersions\":[");
        boolean first = true;
        if (c.getConflictingVersions() != null) {
            for (String v : c.getConflictingVersions()) {
                if (!first) sb.append(",");
                sb.append(jsonStr(v));
                first = false;
            }
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + escapeJson(s) + "\"";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(":", keyIdx + search.length());
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            int end = start + 1;
            while (end < json.length()) {
                if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') {
                    return json.substring(start + 1, end)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t");
                }
                end++;
            }
            return null;
        }
        if (json.startsWith("true", start)) return "true";
        if (json.startsWith("false", start)) return "false";
        return null;
    }

    private static List<String> extractJsonArray(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(":", keyIdx + search.length());
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != '[') return null;
        List<String> result = new ArrayList<>();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ']') break;
            if (c == '"') {
                int end = i + 1;
                while (end < json.length() && !(json.charAt(end) == '"' && json.charAt(end - 1) != '\\')) {
                    end++;
                }
                result.add(json.substring(i + 1, end)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\"));
                i = end + 1;
            } else {
                i++;
            }
        }
        return result;
    }

    private static void sendJson(HttpServletResponse response, int code, String json) throws IOException {
        response.setStatus(code);
        response.getWriter().write(json);
    }
}
