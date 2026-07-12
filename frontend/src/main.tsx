import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { GoogleOAuthProvider } from '@react-oauth/google';

// Self-hosted fonts (no external CDN) — client-contract B.2 type stack.
import '@fontsource/fraunces/400.css';
import '@fontsource/fraunces/500.css';
import '@fontsource/fraunces/600.css';
import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import '@fontsource/jetbrains-mono/400.css';
import '@fontsource/jetbrains-mono/500.css';

import './styles/tokens.css';
import './styles/base.css';
import './styles/components.css';

import { App } from './App';
import { AuthProvider } from './auth/AuthContext';
import { ErrorBoundary } from './components/ErrorBoundary';
import { queryClient } from './lib/queryClient';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Root element #root not found');

// When a Google client id is configured, wrap the app so the real Google Identity
// Services button works; otherwise the app renders without it (button hidden).
const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <ErrorBoundary>
            {googleClientId ? (
              <GoogleOAuthProvider clientId={googleClientId}>
                <App />
              </GoogleOAuthProvider>
            ) : (
              <App />
            )}
          </ErrorBoundary>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
