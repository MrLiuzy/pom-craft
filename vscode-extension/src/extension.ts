import * as vscode from 'vscode';
import { PomCraftEditorProvider } from './pomCraftEditor';

export function activate(context: vscode.ExtensionContext) {
    console.log('Pom-Craft extension is now active!');

    context.subscriptions.push(
        vscode.window.registerCustomEditorProvider(
            'pomCraft.pomEditor',
            new PomCraftEditorProvider(context),
            {
                webviewOptions: {
                    retainContextWhenHidden: true,
                },
            }
        )
    );
}

export function deactivate() { }
