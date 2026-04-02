import React, { useState, useEffect } from 'react';
import ShortenForm        from './components/ShortenForm';
import AnalyticsDashboard from './components/AnalyticsDashboard';
import AuthPage           from './components/AuthPage';
import Dashboard          from './components/Dashboard';
import PreviewPage        from './components/PreviewPage';

export default function App() {
  // Handle /p/{shortCode} preview routes
  const path = window.location.pathname;
  const previewMatch = path.match(/^\/p\/([a-zA-Z0-9_-]+)$/);
  if (previewMatch) {
    return <PreviewPage shortCode={previewMatch[1]} />;
  }

  const [activeTab, setActiveTab] = useState('shorten');
  const [user,      setUser]      = useState(null);
  const [showAuth,  setShowAuth]  = useState(false);

  // Load user from localStorage on mount
  useEffect(() => {
    const stored = localStorage.getItem('user');
    const token  = localStorage.getItem('token');
    if (stored && token) {
      setUser(JSON.parse(stored));
    }
  }, []);

  // Listen for "viewStats" event from Dashboard
  useEffect(() => {
    const handler = (e) => {
      setActiveTab('analytics');
    };
    window.addEventListener('viewStats', handler);
    return () => window.removeEventListener('viewStats', handler);
  }, []);

  const handleAuthSuccess = (data) => {
    if (data) {
      setUser({ email: data.email, name: data.name });
    }
    setShowAuth(false);
    setActiveTab('shorten');
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
    setActiveTab('shorten');
  };

  const handleDashboardClick = () => {
    if (!user) {
      setShowAuth(true);
    } else {
      setActiveTab('dashboard');
    }
  };

  // Show auth page
  if (showAuth) {
    return <AuthPage onAuthSuccess={handleAuthSuccess} />;
  }

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

          {/* Tabs + Auth */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div className="nav-tabs">
              <button
                className={`nav-tab ${activeTab === 'shorten' ? 'active' : ''}`}
                onClick={() => setActiveTab('shorten')}
              >✂️ Shorten</button>

              <button
                className={`nav-tab ${activeTab === 'analytics' ? 'active' : ''}`}
                onClick={() => setActiveTab('analytics')}
              >📊 Analytics</button>

              <button
                className={`nav-tab ${activeTab === 'dashboard' ? 'active' : ''}`}
                onClick={handleDashboardClick}
              >🗂 Dashboard</button>
            </div>

            {/* Auth button */}
            {user ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 8 }}>
                <span style={{
                  fontSize: '0.78rem', color: 'var(--text-secondary)',
                  fontWeight: 600, maxWidth: 120, overflow: 'hidden',
                  textOverflow: 'ellipsis', whiteSpace: 'nowrap'
                }}>
                  👤 {user.name}
                </span>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={handleLogout}
                >Logout</button>
              </div>
            ) : (
              <button
                className="btn btn-primary btn-sm"
                style={{ marginLeft: 8, marginTop: 0, width: 'auto', padding: '7px 16px' }}
                onClick={() => setShowAuth(true)}
              >Login</button>
            )}
          </div>
        </div>
      </nav>

      {/* ── Page content ─────────────────────────────────────── */}
      <main className="container" style={{ flex: 1 }}>
        {activeTab === 'shorten'   && <ShortenForm />}
        {activeTab === 'analytics' && <AnalyticsDashboard />}
        {activeTab === 'dashboard' && user && <Dashboard user={user} />}
        {activeTab === 'dashboard' && !user && <ShortenForm />}
      </main>

      {/* ── Footer ───────────────────────────────────────────── */}
      <footer className="footer">
        SnipURL — Java Spring Boot · Redis · MySQL · React &nbsp;|&nbsp;
        <span style={{ color: 'var(--primary)' }}>College Project</span>
      </footer>
    </div>
  );
}
