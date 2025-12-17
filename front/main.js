const { app, BrowserWindow, ipcMain } = require('electron');
const net = require('net');

let socket;
let win;

function createWindow() {
  win = new BrowserWindow({
    width: 1000,
    height: 700,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });

  win.loadFile('index.html');
}

app.whenReady().then(createWindow);

ipcMain.on('connect-server', () => {
  socket = new net.Socket();
  socket.connect(8080, '127.0.0.1');

  socket.on('data', (data) => {
    win.webContents.send('server-response', data.toString());
  });
});

ipcMain.on('send-data', (event, msg) => {
  if (socket) socket.write(msg + "\n");
});
