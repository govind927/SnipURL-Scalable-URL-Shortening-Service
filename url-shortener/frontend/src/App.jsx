import React, { useState } from 'react';
import ShortenForm         from './components/ShortenForm';
import AnalyticsDashboard  from './components/AnalyticsDashboard';

export default function App() {
  const [activeTab, setActiveTab] = useState('shorten');

  return (
    <div className="app-wrapper">

      {/* ── Navbar ───────────────────────────────────────────── */}
      <nav className="navbar">
        <div className="container navbar-inner">

          {/* Brand */}
          <div className="navbar-brand" onClick={() => setActiveTab('shorten')}>
            <div className="brand-icon">✂️</div>
            <span>SnipURL</span>
          </div>

          {/* Tabs */}
          <div className="nav-tabs">
            <button
              className={`nav-tab ${activeTab === 'shorten' ? 'active' : ''}`}
              onClick={() => setActiveTab('shorten')}
            >
              ✂️ Shorten
            </button>
            <button
              className={`nav-tab ${activeTab === 'analytics' ? 'active' : ''}`}
              onClick={() => setActiveTab('analytics')}
            >
              📊 Analytics
            </button>
          </div>
        </div>
      </nav>

      {/* ── Page content ─────────────────────────────────────── */}
      <main className="container" style={{ flex: 1 }}>
        {activeTab === 'shorten'   && <ShortenForm />}
        {activeTab === 'analytics' && <AnalyticsDashboard />}
      </main>

      {/* ── Footer ───────────────────────────────────────────── */}
      <footer className="footer">
        SnipURL — Built with Java Spring Boot · Redis · MySQL · React &nbsp;|&nbsp;
        <span style={{ color: 'var(--primary)' }}>College Project</span>
      </footer>

    </div>
  );
}
