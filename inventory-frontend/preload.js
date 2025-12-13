const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
    connectSocket: (host, port) => ipcRenderer.invoke('connect-socket', host, port),
    sendMessage: (message) => ipcRenderer.invoke('send-message', message),
    disconnectSocket: () => ipcRenderer.invoke('disconnect-socket'),
    onMessage: (callback) => {
        ipcRenderer.on('socket-message', (event, data) => callback(data));
    },
    removeMessageListener: () => {
        ipcRenderer.removeAllListeners('socket-message');
    }
});