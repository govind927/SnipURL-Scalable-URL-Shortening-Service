import React, { useState, useEffect } from 'react';
import { getMyUrls, deleteUrl } from '../services/api';

export default function Dashboard({ user }) {
  const [urls,    setUrls]    = useState([]);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState('');

  useEffect(() => {
    fetchUrls();
  }, []);

  const fetchUrls = async () => {
    setLoading(true);
    try {
      const data = await getMyUrls();
      setUrls(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (shortCode) => {
    if (!window.confirm('Deactivate this link?')) return;
    try {
      await deleteUrl(shortCode);
      setUrls(prev => prev.map(u =>
        u.shortCode === shortCode ? { ...u, isActive: false } : u
      ));
    } catch (err) {
      setError(err.message);
    }
  };

  const handleCopy = (shortUrl) => {
    navigator.clipboard.writeText(shortUrl);
  };

  const fmt = (iso) => iso
    ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : '—';

  const truncate = (str, n) => str?.length > n ? str.slice(0, n) + '…' : str;

  if (loading) return (
    <div className="spinner-wrap" style={{ paddingTop: 60 }}>
      <div className="spinner" />
      Loading your links…
    </div>
  );

  return (
    <div style={{ paddingBottom: 40 }}>

      {/* Header */}
      <div style={{ padding: '32px 0 20px' }}>
        <h2 style={{ fontSize: '1.3rem', fontWeight: 800, color: 'var(--text-primary)' }}>
          👋 Welcome back, {user?.name}
        </h2>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginTop: 4 }}>
          {urls.length} link{urls.length !== 1 ? 's' : ''} created
        </p>
      </div>

      {error && <div className="alert alert-error">⚠️ {error}</div>}

      {/* Summary cards */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <div className="stat-card">
          <div className="stat-value">{urls.length}</div>
          <div className="stat-label">Total Links</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{urls.filter(u => u.isActive).length}</div>
          <div className="stat-label">Active</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">
            {urls.reduce((sum, u) => sum + (u.clickCount || 0), 0).toLocaleString()}
          </div>
          <div className="stat-label">Total Clicks</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">
            {urls.filter(u => u.expiryTime).length}
          </div>
          <div className="stat-label">With Expiry</div>
        </div>
      </div>

      {/* Links table */}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--border)' }}>
          <div className="card-title" style={{ margin: 0 }}>🔗 Your Links</div>
        </div>

        {urls.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">🔗</div>
            <h3>No links yet</h3>
            <p>Go to the Shorten tab and create your first short link.</p>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="clicks-table">
              <thead>
                <tr>
                  <th>Short Link</th>
                  <th>Original URL</th>
                  <th>Clicks</th>
                  <th>Created</th>
                  <th>Expires</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {urls.map(url => (
                  <tr key={url.shortCode}>
                    {/* Short link */}
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <a
                          href={url.shortUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          style={{ color: 'var(--primary)', fontFamily: 'var(--font-mono)', fontSize: '0.82rem', fontWeight: 600 }}
                        >
                          /{url.shortCode}
                        </a>
                        <button
                          onClick={() => handleCopy(url.shortUrl)}
                          style={{
                            background: 'none', border: 'none', cursor: 'pointer',
                            fontSize: '0.75rem', color: 'var(--text-muted)', padding: 2
                          }}
                          title="Copy"
                        >📋</button>
                      </div>
                    </td>

                    {/* Original URL */}
                    <td>
                      <span title={url.longUrl} style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                        {truncate(url.longUrl, 45)}
                      </span>
                    </td>

                    {/* Clicks */}
                    <td>
                      <strong style={{ color: 'var(--primary)' }}>
                        {url.clickCount?.toLocaleString() || 0}
                      </strong>
                    </td>

                    {/* Created */}
                    <td style={{ fontSize: '0.8rem' }}>{fmt(url.createdAt)}</td>

                    {/* Expires */}
                    <td style={{ fontSize: '0.8rem' }}>
                      {url.expiryTime ? fmt(url.expiryTime) : '∞ Never'}
                    </td>

                    {/* Status */}
                    <td>
                      <span className={`status-badge ${url.isActive ? 'status-active' : 'status-inactive'}`}>
                        {url.isActive ? '🟢 Active' : '⚫ Inactive'}
                      </span>
                    </td>

                    {/* Actions */}
                    <td>
                      <div style={{ display: 'flex', gap: 6 }}>
                        <a
                          href={`http://localhost:3000`}
                          onClick={(e) => {
                            e.preventDefault();
                            window.dispatchEvent(new CustomEvent('viewStats', { detail: url.shortCode }));
                          }}
                          style={{
                            padding: '4px 10px', background: 'var(--primary-light)',
                            color: 'var(--primary)', borderRadius: 6, fontSize: '0.75rem',
                            fontWeight: 600, textDecoration: 'none', cursor: 'pointer'
                          }}
                        >
                          📊 Stats
                        </a>
                        {url.isActive && (
                          <button
                            onClick={() => handleDelete(url.shortCode)}
                            style={{
                              padding: '4px 10px', background: 'var(--danger-light)',
                              color: 'var(--danger)', border: 'none', borderRadius: 6,
                              fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer'
                            }}
                          >
                            🗑 Delete
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
