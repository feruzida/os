const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const net = require('net');

let mainWindow;
let socket = null;

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1400,
        height: 900,
        minWidth: 1200,
        minHeight: 700,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            preload: path.join(__dirname, 'preload.js')
        },
        icon: path.join(__dirname, 'assets/icon.png'),
        title: 'Inventory Management System'
    });

    mainWindow.loadFile(path.join(__dirname, 'renderer', 'login.html'));

    // Open DevTools in development
    if (process.argv.includes('--dev')) {
        mainWindow.webContents.openDevTools();
    }

    mainWindow.on('closed', () => {
        mainWindow = null;
        if (socket) {
            socket.destroy();
        }
    });
}

// Socket connection handlers
ipcMain.handle('connect-socket', async (event, host, port) => {
    return new Promise((resolve, reject) => {
        socket = new net.Socket();

        socket.connect(port, host, () => {
            console.log(`Connected to server ${host}:${port}`);
            resolve({ success: true, message: 'Connected to server' });
        });

        socket.on('error', (error) => {
            console.error('Socket error:', error);
            reject({ success: false, message: error.message });
        });

        socket.on('close', () => {
            console.log('Socket connection closed');
            socket = null;
        });

        socket.on('data', (data) => {
            const messages = data.toString().split('\n').filter(msg => msg.trim());
            messages.forEach(msg => {
                try {
                    const response = JSON.parse(msg);
                    mainWindow.webContents.send('socket-message', response);
                } catch (error) {
                    console.error('Failed to parse message:', error);
                }
            });
        });
    });
});

ipcMain.handle('send-message', async (event, message) => {
    return new Promise((resolve, reject) => {
        if (!socket || socket.destroyed) {
            reject({ success: false, message: 'Not connected to server' });
            return;
        }

        socket.write(message + '\n');
        resolve({ success: true });
    });
});

ipcMain.handle('disconnect-socket', async () => {
    if (socket) {
        socket.destroy();
        socket = null;
    }
    return { success: true };
});

// App lifecycle
app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit();
    }
});

app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
    }
});