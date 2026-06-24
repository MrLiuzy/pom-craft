import * as vscode from 'vscode';
import { PomCraftEditorProvider } from './pomCraftEditor';
import { BackendManager } from './backendManager';

let backendManager: BackendManager;

export function activate(context: vscode.ExtensionContext) {
    console.log('POM Craft extension is now active!');

    // Create and start backend
    backendManager = new BackendManager(context.extensionPath);
    backendManager.start();

    // Register command to show output
    context.subscriptions.push(
        vscode.commands.registerCommand('pomCraft.showOutput', () => {
            backendManager.showOutput();
        })
    );

    // Scan workspace for Maven project directories
    const workspaceDirs = scanWorkspaceForMavenProjects();
    console.log('Maven workspace directories:', workspaceDirs);

    // Register custom editor
    context.subscriptions.push(
        vscode.window.registerCustomEditorProvider(
            'pomCraft.pomEditor',
            new PomCraftEditorProvider(context, backendManager, workspaceDirs),
            {
                webviewOptions: {
                    retainContextWhenHidden: true,
                },
            }
        )
    );
}

export function deactivate() {
    console.log('POM Craft extension is now deactivate!');
    if (backendManager) {
        backendManager.stop();
    }
}

function scanWorkspaceForMavenProjects(): string[] {
    const dirs: string[] = [];
    const folders = vscode.workspace.workspaceFolders;
    if (!folders) {
        return dirs;
    }

    // For now, return all workspace folder roots that contain pom.xml
    // plus their subdirectories that contain pom.xml (not nested under another pom.xml)
    for (const folder of folders) {
        scanDirectory(folder.uri.fsPath, dirs);
    }

    return dirs;
}

function scanDirectory(rootPath: string, results: string[]): void {
    const fs = require('fs');
    const path = require('path');

    try {
        const entries = fs.readdirSync(rootPath, { withFileTypes: true });

        // Check if this directory has a pom.xml
        const hasPomXml = entries.some(
            (e: any) => e.isFile() && e.name === 'pom.xml'
        );

        if (hasPomXml) {
            results.push(rootPath);
        }

        // Recurse into subdirectories (skip node_modules, .git, target)
        for (const entry of entries) {
            if (entry.isDirectory() && !entry.name.startsWith('.') && entry.name !== 'node_modules' && entry.name !== 'target') {
                scanDirectory(path.join(rootPath, entry.name), results);
            }
        }
    } catch {
        // Skip inaccessible directories
    }
}
