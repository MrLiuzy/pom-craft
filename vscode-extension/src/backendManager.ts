import * as vscode from 'vscode';
import { spawn, ChildProcess } from 'child_process';
import * as http from 'http';
import * as path from 'path';
import * as fs from 'fs';

export type BackendStatus = 'stopped' | 'starting' | 'ready' | 'error';

export interface ResolveResult {
    success: boolean;
    errorMessage?: string;
    data?: {
        directDependencies: any[];
        allDependencies: any[];
        conflicts: any[];
    };
}

export class BackendManager {
    private process: ChildProcess | null = null;
    private port: number = 0;
    private status: BackendStatus = 'stopped';
    private outputChannel: vscode.OutputChannel;
    private statusBarItem: vscode.StatusBarItem;
    private onStatusChange: ((s: BackendStatus) => void) | null = null;
    private extensionPath: string;

    constructor(extensionPath: string) {
        this.extensionPath = extensionPath;
        this.outputChannel = vscode.window.createOutputChannel('POM Craft');
        this.statusBarItem = vscode.window.createStatusBarItem(
            vscode.StatusBarAlignment.Left, 100
        );
    }

    getStatus(): BackendStatus {
        return this.status;
    }

    setOnStatusChange(cb: (s: BackendStatus) => void) {
        this.onStatusChange = cb;
    }

    private setStatus(s: BackendStatus) {
        this.status = s;
        if (this.onStatusChange) {
            this.onStatusChange(s);
        }
        this.updateStatusBar();
    }

    private updateStatusBar() {
        switch (this.status) {
            case 'starting':
                this.statusBarItem.text = '$(sync~spin) POM Craft: Starting...';
                this.statusBarItem.tooltip = 'Backend server is starting';
                this.statusBarItem.show();
                break;
            case 'ready':
                this.statusBarItem.text = '$(check) POM Craft';
                this.statusBarItem.tooltip = 'Backend server is ready';
                this.statusBarItem.show();
                break;
            case 'error':
                this.statusBarItem.text = '$(error) POM Craft: Error';
                this.statusBarItem.tooltip = 'Backend server error';
                this.statusBarItem.show();
                break;
            default:
                this.statusBarItem.hide();
        }
    }

    async start(): Promise<void> {
        if (this.status === 'ready' || this.status === 'starting') {
            return;
        }

        this.setStatus('starting');

        const config = vscode.workspace.getConfiguration('pomCraft');
        const jdkPath = config.get<string>('jdkPath') || '';

        const javaBin = jdkPath
            ? path.join(jdkPath, 'bin', 'java' + (process.platform === 'win32' ? '.exe' : ''))
            : 'java';

        // Find the JAR — look in extension's backend/ directory
        const jarPath = path.join(this.extensionPath, 'backend', 'backend-server-1.0-SNAPSHOT.jar');

        if (!fs.existsSync(jarPath)) {
            this.outputChannel.appendLine(`[ERROR] JAR not found: ${jarPath}`);
            this.setStatus('error');
            return;
        }

        const env = { ...process.env };
        if (jdkPath) {
            env.JAVA_HOME = jdkPath;
        }

        this.outputChannel.appendLine(`[INFO] Starting backend: ${javaBin} -jar ${jarPath}`);
        this.outputChannel.appendLine(`[INFO] JAVA_HOME: ${jdkPath || process.env.JAVA_HOME || '(default)'}`);

        const args = ['-jar', jarPath];
        if (this.isDebug()) {
            args.push('--debug');
        }

        this.process = spawn(javaBin, args, {
            env,
            stdio: ['pipe', 'pipe', 'pipe'],
        });

        let portReceived = false;

        this.process.stdout?.on('data', (data: Buffer) => {
            const text = data.toString();
            if (this.isDebug()) {
                this.outputChannel.appendLine(`[STDOUT] ${text.trim()}`);
            }

            if (!portReceived) {
                try {
                    const parsed = JSON.parse(text.trim());
                    if (parsed.port) {
                        this.port = parsed.port;
                        portReceived = true;
                        this.setStatus('ready');
                        this.outputChannel.appendLine(`[INFO] Backend ready on port ${this.port}`);
                    }
                } catch {
                    // Not JSON yet, keep waiting
                }
            }
        });

        this.process.stderr?.on('data', (data: Buffer) => {
            if (this.isDebug()) {
                this.outputChannel.appendLine(`[STDERR] ${data.toString().trim()}`);
            }
        });

        this.process.on('error', (err) => {
            this.outputChannel.appendLine(`[ERROR] ${err.message}`);
            this.setStatus('error');
        });

        this.process.on('close', (code) => {
            this.outputChannel.appendLine(`[INFO] Backend exited with code ${code}`);
            if (this.status === 'ready' || this.status === 'starting') {
                this.setStatus('error');
            }
        });

        // Timeout: if not ready in 30s, report error
        setTimeout(() => {
            if (!portReceived && this.status === 'starting') {
                this.outputChannel.appendLine('[ERROR] Backend startup timed out');
                this.setStatus('error');
            }
        }, 30000);
    }

    private isDebug(): boolean {
        return vscode.workspace.getConfiguration('pomCraft').get<boolean>('debug', false);
    }

    private debugLog(label: string, data: string) {
        if (this.isDebug()) {
            this.outputChannel.appendLine(`[DEBUG] ${label}:`);
            this.outputChannel.appendLine(data);
        }
    }

    async resolve(targetPom: string, workspaceDirs: string[]): Promise<ResolveResult> {
        if (this.status !== 'ready') {
            return { success: false, errorMessage: 'Backend is not ready' };
        }

        const config = vscode.workspace.getConfiguration('pomCraft');
        const settingsXml = config.get<string>('settingsXml') || '';

        const requestBody = JSON.stringify({
            targetPom,
            workspaceDirectories: workspaceDirs,
            offline: false,
            skipFailedResolution: true,
            settingsXml: settingsXml || undefined,
        });

        this.debugLog('/resolve request', requestBody);

        return new Promise((resolve) => {
            const req = http.request(
                {
                    hostname: '127.0.0.1',
                    port: this.port,
                    path: '/resolve',
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                },
                (res) => {
                    let data = '';
                    res.on('data', (chunk) => (data += chunk));
                    res.on('end', () => {
                        this.debugLog('/resolve response', data);
                        try {
                            resolve(JSON.parse(data));
                        } catch {
                            resolve({ success: false, errorMessage: 'Invalid response from backend' });
                        }
                    });
                }
            );
            req.on('error', (err) => {
                this.outputChannel.appendLine(`[ERROR] HTTP request failed: ${err.message}`);
                resolve({ success: false, errorMessage: err.message });
            });
            req.write(requestBody);
            req.end();
        });
    }

    async effectivePom(targetPom: string, workspaceDirs: string[]): Promise<{ success: boolean; effectivePom?: string; errorMessage?: string }> {
        if (this.status !== 'ready') {
            return { success: false, errorMessage: 'Backend is not ready' };
        }

        const config = vscode.workspace.getConfiguration('pomCraft');
        const settingsXml = config.get<string>('settingsXml') || '';

        const requestBody = JSON.stringify({
            targetPom,
            workspaceDirectories: workspaceDirs,
            settingsXml: settingsXml || undefined,
        });

        this.debugLog('/effective-pom request', requestBody);

        return new Promise((resolve) => {
            const req = http.request(
                {
                    hostname: '127.0.0.1',
                    port: this.port,
                    path: '/effective-pom',
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                },
                (res) => {
                    let data = '';
                    res.on('data', (chunk) => (data += chunk));
                    res.on('end', () => {
                        this.debugLog('/effective-pom response', data);
                        try { resolve(JSON.parse(data)); } catch {
                            resolve({ success: false, errorMessage: 'Invalid response from backend' });
                        }
                    });
                }
            );
            req.on('error', (err) => {
                resolve({ success: false, errorMessage: err.message });
            });
            req.write(requestBody);
            req.end();
        });
    }

    stop() {
        if (this.process) {
            this.process.kill();
            this.process = null;
        }
        this.setStatus('stopped');
        this.statusBarItem.dispose();
        this.outputChannel.dispose();
    }

    showOutput() {
        this.outputChannel.show();
    }
}
