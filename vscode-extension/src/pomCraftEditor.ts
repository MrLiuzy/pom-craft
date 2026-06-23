import * as vscode from 'vscode';

// ---- POM data types ----

interface ArtifactInfo {
    groupId: string;
    artifactId: string;
    version: string;
    packaging: string;
}

interface ParentInfo {
    groupId: string;
    artifactId: string;
    version: string;
}

interface PomData {
    artifact: ArtifactInfo;
    parent: ParentInfo | null;
    properties: Record<string, string>;
}

// ---- POM Parser ----

function parsePomXml(xml: string): PomData {
    // Extract <parent>
    let parent: ParentInfo | null = null;
    const parentMatch = xml.match(/<parent>([\s\S]*?)<\/parent>/);
    if (parentMatch) {
        parent = {
            groupId: extractTag(parentMatch[1], 'groupId'),
            artifactId: extractTag(parentMatch[1], 'artifactId'),
            version: extractTag(parentMatch[1], 'version'),
        };
    }

    // Extract artifact info from root <project>
    const artifact: ArtifactInfo = {
        groupId: extractTag(xml, 'groupId') || parent?.groupId || '',
        artifactId: extractTag(xml, 'artifactId') || '',
        version: extractTag(xml, 'version') || parent?.version || '',
        packaging: extractTag(xml, 'packaging') || 'jar',
    };

    // Extract <properties>
    const properties: Record<string, string> = {};
    const propsMatch = xml.match(/<properties>([\s\S]*?)<\/properties>/);
    if (propsMatch) {
        const propRegex = /<([a-zA-Z0-9._-]+)>([^<]*)<\/\1>/g;
        let propMatch: RegExpExecArray | null;
        while ((propMatch = propRegex.exec(propsMatch[1])) !== null) {
            properties[propMatch[1]] = propMatch[2];
        }
    }

    return { artifact, parent, properties };
}

function extractTag(xml: string, tag: string): string {
    const match = xml.match(new RegExp(`<${tag}>([^<]*)<\\/${tag}>`));
    return match ? match[1].trim() : '';
}

// ---- Nonce generator ----

function getNonce(): string {
    let text = '';
    const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    for (let i = 0; i < 32; i++) {
        text += possible.charAt(Math.floor(Math.random() * possible.length));
    }
    return text;
}

// ---- Custom Editor Provider ----

export class PomCraftEditorProvider implements vscode.CustomTextEditorProvider {

    constructor(private readonly context: vscode.ExtensionContext) { }

    async resolveCustomTextEditor(
        document: vscode.TextDocument,
        webviewPanel: vscode.WebviewPanel,
        _token: vscode.CancellationToken
    ): Promise<void> {
        webviewPanel.webview.options = {
            enableScripts: true,
        };

        webviewPanel.webview.html = await this.buildHtml(webviewPanel.webview);

        const sendPomData = () => {
            const text = document.getText();
            const pomData = parsePomXml(text);
            const deps = parseDependencies(text);
            webviewPanel.webview.postMessage({
                type: 'setPomData',
                rawText: text,
                pomData: { ...pomData, dependencies: deps },
            });
        };

        webviewPanel.webview.onDidReceiveMessage(message => {
            switch (message.type) {
                case 'ready':
                    sendPomData();
                    break;
                case 'updatePom':
                    this.handleUpdatePom(document, webviewPanel, message.text);
                    break;
            }
        });

        const changeListener = vscode.workspace.onDidChangeTextDocument(e => {
            if (e.document.uri.toString() === document.uri.toString()) {
                sendPomData();
            }
        });

        webviewPanel.onDidDispose(() => {
            changeListener.dispose();
        });
    }

    private async handleUpdatePom(
        document: vscode.TextDocument,
        webviewPanel: vscode.WebviewPanel,
        newText: string
    ): Promise<void> {
        // Replace entire document content
        const fullRange = new vscode.Range(
            document.positionAt(0),
            document.positionAt(document.getText().length)
        );
        const wsEdit = new vscode.WorkspaceEdit();
        wsEdit.replace(document.uri, fullRange, newText);
        await vscode.workspace.applyEdit(wsEdit);

        // Re-parse and send fresh data back
        const pomData = parsePomXml(newText);
        const deps = parseDependencies(newText);
        webviewPanel.webview.postMessage({
            type: 'pomUpdated',
            rawText: newText,
            pomData: { ...pomData, dependencies: deps },
        });
    }

    private async buildHtml(webview: vscode.Webview): Promise<string> {
        const nonce = getNonce();

        // Build webview URIs for CSS and JS
        const styleUri = webview.asWebviewUri(
            vscode.Uri.joinPath(this.context.extensionUri, 'media', 'pomEditor.css')
        );
        const scriptUri = webview.asWebviewUri(
            vscode.Uri.joinPath(this.context.extensionUri, 'media', 'pomEditor.js')
        );

        // Read HTML template
        const htmlPath = vscode.Uri.joinPath(this.context.extensionUri, 'media', 'pomEditor.html');
        const htmlBytes = await vscode.workspace.fs.readFile(htmlPath);
        let html = new TextDecoder().decode(htmlBytes);

        // Replace placeholders
        html = html.replace(/\$\{cspSource\}/g, webview.cspSource);
        html = html.replace(/\$\{nonce\}/g, nonce);
        html = html.replace(/\$\{styleUri\}/g, styleUri.toString());
        html = html.replace(/\$\{scriptUri\}/g, scriptUri.toString());

        return html;
    }
}

// ---- Dependency parsing ----

interface Dependency {
    groupId: string;
    artifactId: string;
    version: string;
    scope?: string;
}

function parseDependencies(xml: string): Dependency[] {
    const deps: Dependency[] = [];
    const depsMatch = xml.match(/<dependencies>([\s\S]*?)<\/dependencies>/);
    if (depsMatch) {
        const depRegex = /<dependency>([\s\S]*?)<\/dependency>/g;
        let m: RegExpExecArray | null;
        while ((m = depRegex.exec(depsMatch[1])) !== null) {
            const dx = m[1];
            deps.push({
                groupId: extractTag(dx, 'groupId'),
                artifactId: extractTag(dx, 'artifactId'),
                version: extractTag(dx, 'version'),
                scope: extractTag(dx, 'scope') || undefined,
            });
        }
    }
    return deps;
}
