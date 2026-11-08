import React from 'react';
import ReactDOM from 'react-dom/client';
import { PlayerProvider } from './context/PlayerContext';
import { App } from './App';
import './style.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <PlayerProvider>
      <App />
    </PlayerProvider>
  </React.StrictMode>
);
