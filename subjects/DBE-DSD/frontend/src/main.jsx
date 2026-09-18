import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import App from './App.jsx';
import { AuthProvider } from './auth/AuthContext.jsx';
import ServerGate from './components/ServerGate.jsx';
import './styles.css';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <ServerGate>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ServerGate>
    </BrowserRouter>
  </StrictMode>,
);
