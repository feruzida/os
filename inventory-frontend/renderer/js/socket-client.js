// socket-client.js - Socket communication wrapper

class SocketClient {
    constructor() {
        this.connected = false;
        this.currentUser = null;
        this.messageCallbacks = [];
    }

    async connect(host, port) {
        try {
            await window.api.connectSocket(host, port);
            this.connected = true;

            // Setup message listener
            window.api.onMessage((data) => {
                this.messageCallbacks.forEach(callback => callback(data));
            });

            console.log('Socket connected successfully');
            return { success: true };
        } catch (error) {
            console.error('Socket connection failed:', error);
            this.connected = false;
            throw error;
        }
    }

    async login(username, password) {
        const request = {
            action: 'login',
            username: username,
            password: password
        };

        return this.sendAndWait(request);
    }

    async send(data) {
        if (!this.connected) {
            throw new Error('Not connected to server');
        }

        return this.sendAndWait(data);
    }

    sendAndWait(data) {
        return new Promise((resolve, reject) => {
            const message = JSON.stringify(data);

            // Setup one-time listener for response
            const responseHandler = (response) => {
                this.messageCallbacks = this.messageCallbacks.filter(cb => cb !== responseHandler);
                resolve(response);
            };

            this.messageCallbacks.push(responseHandler);

            // Send message
            window.api.sendMessage(message).catch(error => {
                this.messageCallbacks = this.messageCallbacks.filter(cb => cb !== responseHandler);
                reject(error);
            });

            // Timeout after 10 seconds
            setTimeout(() => {
                this.messageCallbacks = this.messageCallbacks.filter(cb => cb !== responseHandler);
                reject(new Error('Request timeout'));
            }, 10000);
        });
    }

    disconnect() {
        if (this.connected) {
            window.api.disconnectSocket();
            this.connected = false;
            this.currentUser = null;
            window.api.removeMessageListener();
        }
    }

    isConnected() {
        return this.connected;
    }

    setCurrentUser(user) {
        this.currentUser = user;
    }

    getCurrentUser() {
        return this.currentUser;
    }
}

// Create singleton instance
const socketClient = new SocketClient();

// Make available globally
if (typeof window !== 'undefined') {
    window.socketClient = socketClient;
}