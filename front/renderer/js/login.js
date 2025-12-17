// login.js - Login page logic

const loginForm = document.getElementById('loginForm');
const usernameInput = document.getElementById('username');
const passwordInput = document.getElementById('password');
const loginBtn = document.getElementById('loginBtn');
const messageDiv = document.getElementById('message');

function showMessage(text, isError = false) {
    messageDiv.textContent = text;
    messageDiv.className = isError ? 'message error-message' : 'message success-message';
    messageDiv.style.display = 'block';
}

function hideMessage() {
    messageDiv.style.display = 'none';
}

loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const username = usernameInput.value.trim();
    const password = passwordInput.value.trim();

    if (!username || !password) {
        showMessage('Please enter username and password', true);
        return;
    }

    loginBtn.textContent = 'Connecting...';
    loginBtn.disabled = true;
    hideMessage();

    try {
        // Connect to server
        await socketClient.connect('localhost', 8080);
        showMessage('Connected to server...', false);

        // Attempt login
        loginBtn.textContent = 'Logging in...';
        const response = await socketClient.login(username, password);

        if (response.success) {
            showMessage('Login successful! Redirecting...', false);

            // Save user data
            sessionStorage.setItem('currentUser', JSON.stringify(response.data));
            socketClient.setCurrentUser(response.data);

            // Redirect to dashboard
            setTimeout(() => {
                window.location.href = 'dashboard.html';
            }, 1000);
        } else {
            showMessage(response.message || 'Login failed', true);
            loginBtn.textContent = 'Login';
            loginBtn.disabled = false;
        }
    } catch (error) {
        console.error('Login error:', error);
        showMessage('Connection failed. Please ensure server is running on localhost:8080', true);
        loginBtn.textContent = 'Login';
        loginBtn.disabled = false;
    }
});

// Auto-focus username field
window.addEventListener('load', () => {
    usernameInput.focus();
});