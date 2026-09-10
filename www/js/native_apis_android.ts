/**
 * Android native API compatibility layer.
 *
 * This file replaces native_apis.ts when building with:
 *     node build.js --target=android
 *
 * Native functionality will be provided through Capacitor.
 */

import { Capacitor } from '@capacitor/core';

const NULL = null;

const AndroidBridge = {
    async call(api: string, arg: any = ''): Promise<any> {
        if (!Capacitor.isNativePlatform()) {
            console.warn('[AndroidBridge] Not running on native Android:', api);
            return null;
        }

        try {
            const result = await (window as any).Capacitor?.Plugins?.Blockbench?.call({
                api,
                arg: typeof arg === 'string' ? arg : JSON.stringify(arg),
            });

            return result?.result ?? null;
        } catch (err) {
            console.error('[AndroidBridge]', api, err);
            return null;
        }
    }
};

/**
 * @internal
 */
export const electron = {
    app: {
        name: 'Blockbench',

        setAppUserModelId(id: string) {
            console.log('[Android] app.setAppUserModelId:', id);
        },

        getPath(name: string) {
            return '';
        },

        getVersion() {
            return '5.1.6';
        },

        quit() {
            AndroidBridge.call('app.quit');
        }
    },

    process: {
        platform: 'android',
        env: {},
        arch: 'arm64',
        argv: [],
        versions: {
            electron: 'android',
            node: 'android'
        },
        pid: 0,
        cwd() {
            return '/';
        },
        nextTick(fn: Function) {
            setTimeout(fn, 0);
        }
    },

    nativeTheme: {
        inForcedColorsMode: false
    },

    getCurrentWindow() {
        return {
            on() {},

            once() {},

            isMaximized() {
                return false;
            },

            maximize() {
                console.log('[Android] maximize');
            },

            unmaximize() {
                console.log('[Android] unmaximize');
            },

            minimize() {
                console.log('[Android] minimize');
            },

            close() {
                AndroidBridge.call('app.quit');
            },

            webContents: {
                openDevTools() {
                    console.warn('[Android] DevTools requested');
                }
            }
        };
    },

    dialog: {
        showMessageBoxSync() {
            return 3;
        },

        async showMessageBox() {
            return { response: 3 };
        }
    }
};

export const clipboard = {
    writeText(text: string) {
        AndroidBridge.call('clipboard.writeText', text);
    },

    async readText() {
        return await AndroidBridge.call('clipboard.readText') ?? '';
    }
};

export const shell = {
    openExternal(url: string) {
        AndroidBridge.call('shell.openExternal', url);
    },

    openPath(path: string) {
        AndroidBridge.call('shell.openPath', path);
    },

    showItemInFolder(path: string) {
        AndroidBridge.call('shell.showItemInFolder', path);
    },

    trashItem(path: string) {
        AndroidBridge.call('shell.trashItem', path);
    }
};

export const nativeImage = NULL;
export const ipcRenderer = {
    on() {},
    once() {},
    send() {},
    invoke() {
        return Promise.resolve(null);
    }
};

export const webUtils = {
    getPathForFile(file: File) {
        return (file as any).path || '';
    }
};

export const app = electron.app;

function androidFS() {
    const fs = (window as any).BlockbenchFS;

    if (!fs) {
        throw new Error(
            '[Android] BlockbenchFS is not available'
        );
    }

    return fs;
}

function base64Encode(data: any): string {
    if (typeof data === 'string') {
        const bytes = new TextEncoder().encode(data);
        let binary = '';
        for (const b of bytes) binary += String.fromCharCode(b);
        return btoa(binary);
    }

    if (data instanceof Uint8Array) {
        let binary = '';
        for (const b of data) binary += String.fromCharCode(b);
        return btoa(binary);
    }

    return base64Encode(String(data));
}

function base64Decode(data: string): Uint8Array {
    const binary = atob(data);
    const result = new Uint8Array(binary.length);

    for (let i = 0; i < binary.length; i++) {
        result[i] = binary.charCodeAt(i);
    }

    return result;
}

export const fs = {
    existsSync(path: string) {
        return androidFS().existsSync(path);
    },

    mkdirSync(path: string, options?: any) {
        const result = androidFS().mkdirSync(path);

        if (!result && !androidFS().existsSync(path)) {
            throw new Error('mkdirSync failed: ' + path);
        }

        return result;
    },

    readdirSync(path: string) {
        return Array.from(androidFS().readdirSync(path));
    },

    unlinkSync(path: string) {
        const result = androidFS().unlinkSync(path);

        if (!result) {
            throw new Error('unlinkSync failed: ' + path);
        }

        return result;
    },

    readFileSync(path: string, options?: any) {
        const base64 = androidFS().readFileBase64Sync(path);
        const bytes = base64Decode(base64);

        if (options === 'utf8' || options?.encoding === 'utf8') {
            return new TextDecoder().decode(bytes);
        }

        return bytes;
    },

    writeFileSync(path: string, data: any, options?: any) {
        const base64 = base64Encode(data);
        const result = androidFS().writeFileBase64Sync(path, base64);

        if (!result) {
            throw new Error('writeFileSync failed: ' + path);
        }

        return result;
    },

    statSync(path: string) {
        const bridge = androidFS();

        if (!bridge.existsSync(path)) {
            throw new Error('ENOENT: ' + path);
        }

        return {
            size: bridge.statSizeSync(path),
            mtimeMs: bridge.mtimeSync(path),
            isFile: () => bridge.isFileSync(path),
            isDirectory: () => bridge.isDirectorySync(path)
        };
    },

    renameSync(oldPath: string, newPath: string) {
        const result = androidFS().renameSync(oldPath, newPath);

        if (!result) {
            throw new Error(
                'renameSync failed: ' + oldPath
            );
        }

        return result;
    },

    copyFileSync(oldPath: string, newPath: string) {
        const result = androidFS().copyFileSync(
            oldPath,
            newPath
        );

        if (!result) {
            throw new Error(
                'copyFileSync failed: ' + oldPath
            );
        }

        return result;
    }
};
export const NodeBuffer = globalThis.Buffer ?? NULL;
export const zlib = NULL;
export const child_process = NULL;
export const https = NULL;

export const PathModule = {
    join: (...parts: string[]) => parts.join('/'),
    resolve: (...parts: string[]) => parts.join('/'),
    dirname: (path: string) => {
        const i = path.lastIndexOf('/');
        return i >= 0 ? path.slice(0, i) : '.';
    },
    basename: (path: string) => {
        const i = path.lastIndexOf('/');
        return i >= 0 ? path.slice(i + 1) : path;
    },
    extname: (path: string) => {
        const name = path.split('/').pop() || '';
        const i = name.lastIndexOf('.');
        return i > 0 ? name.slice(i) : '';
    },
    sep: '/'
};

export const os = {
    platform: () => 'android',
    arch: () => 'arm64',
    homedir: () => '',
    tmpdir: () => '',
    version: () => 'Android'
};

export const currentwindow = electron.getCurrentWindow();
export const dialog = electron.dialog;

export const process = {
    platform: 'android',
    arch: 'arm64',
    env: {},
    pid: 0,
    cwd: () => '/',
    nextTick: (fn: Function) => setTimeout(fn, 0)
};

export const SystemInfo = {
    platform: 'android',
    home_directory: '',
    appdata_directory: '',
    user_data_directory: '',
    desktop_directory: '',
    temp_directory: '',
    arch: 'arm64',
    os_version: 'Android'
};

export function getPCUsername() {
    return '';
}

export function openFileInEditor(file_path: string, editor: string) {
    console.warn('[Android] openFileInEditor:', file_path, editor);
}

export function openDevTools() {
    console.warn('[Android] DevTools requested');
}

export function getPluginScopedRequire(plugin: any) {
    return function require(module_id: string, options?: any) {
        console.warn('[Android] Plugin require:', module_id, options);

        if (module_id === 'clipboard') return clipboard;
        if (module_id === 'shell') return shell;
        if (module_id === 'os') return os;
        if (module_id === 'process') return process;
        if (module_id === 'path') return PathModule;

        throw new Error(`Android does not support module "${module_id}"`);
    };
}

export function revokePluginPermissions(plugin: any): string[] {
    return [];
}

export function getPluginPermissions(plugin: any) {
    return {};
}

export function exposeNativeApisInDevTools() {
    console.warn('[Android] Native API exposure is not available');
}

// @ts-ignore
window.SystemInfo = SystemInfo;

// @ts-ignore
window.openDevTools = openDevTools;
