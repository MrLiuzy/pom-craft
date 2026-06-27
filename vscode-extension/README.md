# POM Craft

A Visual Studio Code extension that provides a rich, visual editing experience for Maven `pom.xml` files — with dependency hierarchy visualization, effective POM preview, and managed version locking.

## Features

### 🎨 Visual POM Editor

Open any `pom.xml` file and get a structured, form-based editor that makes it easy to inspect and modify your Maven project configuration:

- **Project Overview** — View and edit artifact coordinates (groupId, artifactId, version, packaging) and parent POM information at a glance.
- **Properties Panel** — Manage `<properties>` entries in a table view with inline editing.
- **Dependencies Table** — Browse all declared dependencies with scope indicators, version highlighting, and quick actions.

### 🔗 Dependency Hierarchy

One-click resolution of the full Maven dependency tree:

- **Direct Dependencies** — All first-level dependencies declared in the POM.
- **Transitive Dependencies** — The complete resolved tree, showing where each transitive dependency comes from.
- **Conflict Detection** — Automatically highlights version conflicts and shows which version Maven resolves (nearest-wins).

### 📄 Effective POM

View the fully resolved "effective POM" — the result of Maven's inheritance, interpolation, and profile activation. See exactly what Maven will build, without leaving VS Code.

### 🔒 Version Locking

Lock a dependency's version into `<dependencyManagement>` with a single click. The extension automatically:

- Adds the dependency to `dependencyManagement` if it doesn't exist yet.
- Updates the version if the dependency is already managed.
- Creates the `<dependencyManagement>` section if needed.

### ⚙️ Smart Backend Management

A lightweight Java backend handles Maven resolution. It starts automatically when you open your first `pom.xml`, and stops when all POM Craft editors are closed — no manual server management needed.

- **Status Bar Indicator** — See backend status at a glance (starting / ready / error).
- **Output Channel** — Run the "POM Craft: Show Output" command to inspect backend logs.

## Requirements

- **VS Code** `^1.124.2` or newer.
- **JDK** 8 or later (JDK 21 recommended). The extension needs a Java runtime to resolve Maven dependencies.

## Extension Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `pomCraft.jdkPath` | `string` | `""` | JDK installation path (e.g., `C:/jdk/zulu21-jdk`). When empty, uses the system `java` or `JAVA_HOME`. |
| `pomCraft.settingsXml` | `string` | `""` | Path to a custom Maven `settings.xml`. When empty, falls back to the `java.configuration.maven.userSettings` VS Code setting. |
| `pomCraft.debug` | `boolean` | `false` | Enable debug output in the POM Craft output channel to see raw backend request/response data. |

## Commands

- **POM Craft: Show Output** — Opens the POM Craft output channel to view backend logs and debug information.

## How It Works

POM Craft uses a client-server architecture:

1. **Frontend** — A VS Code custom editor webview (HTML/CSS/JS) renders the visual POM editor.
2. **Backend** — An embedded Jetty HTTP server written in Java that uses Maven Resolver APIs to compute dependency trees and effective POMs.
3. **Communication** — The extension spawns and manages the backend process, sending REST API calls over `localhost`.

When you open a `pom.xml`:
- The extension parses the XML in TypeScript for instant display (overview, properties, dependencies).
- Press the "Resolve" button to have the Java backend compute the full dependency tree and conflicts.
- Press the "Effective POM" button to see the fully interpolated POM.

## Project Structure

```
vscode-extension/
├── src/
│   ├── extension.ts          # Extension activation, Maven project scanning
│   ├── pomCraftEditor.ts     # Custom editor provider, POM parsing, webview messaging
│   └── backendManager.ts     # Backend process lifecycle, HTTP client, status bar
├── media/
│   ├── pomEditor.html        # Webview HTML template
│   ├── pomEditor.css         # Visual editor styles
│   └── pomEditor.js          # Frontend logic (form binding, hierarchy rendering)
└── backend/
    └── backend-server-1.0-SNAPSHOT.jar   # Java backend (Jetty + Maven Resolver)
```

## Development

```bash
# Install dependencies
pnpm install

# Compile TypeScript
pnpm run compile

# Watch mode
pnpm run watch

# Lint
pnpm run lint

# Run tests
pnpm test
```

## License

This extension is published under the [MIT License](LICENSE).
