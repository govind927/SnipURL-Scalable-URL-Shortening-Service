import React, { useState } from 'react';
import { shortenUrl } from '../services/api';

export default function ShortenForm() {
  const [longUrl,      setLongUrl]      = useState('');
  const [customAlias,  setCustomAlias]  = useState('');
  const [expiryTime,   setExpiryTime]   = useState('');
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [loading,      setLoading]      = useState(false);
  const [result,       setResult]       = useState(null);
  const [error,        setError]        = useState('');
  const [copied,       setCopied]       = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setResult(null);
    setLoading(true);

    try {
      const data = await shortenUrl(longUrl, customAlias, expiryTime || null);
      setResult(data);
      setLongUrl('');
      setCustomAlias('');
      setExpiryTime('');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(result.shortUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const formatDate = (iso) =>
    iso ? new Date(iso).toLocaleString() : 'Never';

  return (
    <div>
      {/* ── Hero ─────────────────────────────────────────────── */}
      <div className="hero">
        <div className="hero-badge">⚡ Redis-Powered</div>
        <h1>Shorten any URL <span>instantly</span></h1>
        <p>
          Fast, trackable short links with click analytics,
          custom aliases, and expiry support.
        </p>
      </div>

      {/* ── Form Card ────────────────────────────────────────── */}
      <div className="card">
        <form onSubmit={handleSubmit}>

          {error && (
            <div className="alert alert-error">
              <span>⚠️</span> {error}
            </div>
          )}

          {/* Long URL input */}
          <div className="form-group">
            <label className="form-label">Long URL *</label>
            <input
              className="form-input"
              type="url"
              placeholder="https://example.com/your/very/long/url"
              value={longUrl}
              onChange={(e) => setLongUrl(e.target.value)}
              required
            />
          </div>

          {/* Advanced options toggle */}
          <button
            type="button"
            onClick={() => setShowAdvanced(v => !v)}
            style={{
              background: 'none', border: 'none', cursor: 'pointer',
              fontSize: '0.82rem', fontWeight: 600, color: 'var(--primary)',
              padding: '0 0 16px', display: 'flex', alignItems: 'center', gap: 5
            }}
          >
            {showAdvanced ? '▲' : '▼'} Advanced options
          </button>

          {showAdvanced && (
            <div className="form-row" style={{ marginBottom: 16 }}>
              {/* Custom alias */}
              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Custom Alias (optional)</label>
                <input
                  className="form-input"
                  type="text"
                  placeholder="my-custom-link"
                  value={customAlias}
                  onChange={(e) => setCustomAlias(e.target.value)}
                  pattern="[a-zA-Z0-9_-]*"
                  minLength={3}
                  maxLength={30}
                />
                <p style={{ fontSize: '0.73rem', color: 'var(--text-muted)', marginTop: 4 }}>
                  Letters, numbers, hyphens, underscores only
                </p>
              </div>

              {/* Expiry date/time */}
              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Expiry Date (optional)</label>
                <input
                  className="form-input"
                  type="datetime-local"
                  value={expiryTime}
                  onChange={(e) => setExpiryTime(e.target.value)}
                  min={new Date().toISOString().slice(0, 16)}
                />
                <p style={{ fontSize: '0.73rem', color: 'var(--text-muted)', marginTop: 4 }}>
                  Leave empty for a permanent link
                </p>
              </div>
            </div>
          )}

          <button className="btn btn-primary" type="submit" disabled={loading}>
            {loading ? (
              <>
                <span className="spinner" style={{ width: 16, height: 16, borderWidth: 2 }} />
                Shortening…
              </>
            ) : (
              '✂️  Shorten URL'
            )}
          </button>
        </form>

        {/* ── Result box ─────────────────────────────────────── */}
        {result && (
          <div className="result-box">
            <div className="result-label">✅ Your short link is ready</div>
            <div className="result-url-row">
              <span className="result-url">{result.shortUrl}</span>
              <button
                className={`copy-btn ${copied ? 'copied' : ''}`}
                onClick={handleCopy}
              >
                {copied ? '✓ Copied!' : '📋 Copy'}
              </button>
            </div>
            <div className="result-meta">
              <span className="result-meta-item">
                🗓 Created: {formatDate(result.createdAt)}
              </span>
              {result.expiryTime && (
                <span className="result-meta-item">
                  ⏳ Expires: {formatDate(result.expiryTime)}
                </span>
              )}
              <span className="result-meta-item">
                🔑 Code: <strong style={{ fontFamily: 'var(--font-mono)' }}>{result.shortCode}</strong>
              </span>
            </div>
          </div>
        )}
      </div>

      {/* ── Feature pills ────────────────────────────────────── */}
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', justifyContent: 'center', paddingBottom: 40 }}>
        {[
          ['⚡', 'Redis Cached'],
          ['📊', 'Click Analytics'],
          ['🔗', 'Custom Aliases'],
          ['⏳', 'Link Expiry'],
          ['🛡️', 'Rate Limited'],
        ].map(([icon, label]) => (
          <div key={label} style={{
            display: 'flex', alignItems: 'center', gap: 6,
            padding: '6px 14px', background: 'var(--bg-card)',
            border: '1px solid var(--border)', borderRadius: '100px',
            fontSize: '0.8rem', color: 'var(--text-secondary)'
          }}>
            <span>{icon}</span> {label}
          </div>
        ))}
      </div>
    </div>
  );
}
