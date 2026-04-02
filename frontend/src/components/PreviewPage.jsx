import React, { useState, useEffect } from 'react';
import { getPreview } from '../services/api';

/**
 * Link Preview Page
 *
 * Shown when user visits /p/{shortCode}
 * Displays og:title, og:image, og:description of the destination,
 * with a 5-second countdown before auto-redirecting.
 *
 * User can also click "Go Now" to skip the countdown.
 */
export default function PreviewPage({ shortCode }) {
  const [preview,   setPreview]   = useState(null);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState('');
  const [countdown, setCountdown] = useState(5);
  const [redirected, setRedirected] = useState(false);

  useEffect(() => {
    if (!shortCode) return;
    fetchPreview();
  }, [shortCode]);

  // Countdown timer — starts after preview loads
  useEffect(() => {
    if (!preview || redirected) return;

    if (countdown <= 0) {
      doRedirect();
      return;
    }

    const timer = setTimeout(() => setCountdown(c => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown, preview, redirected]);

  const fetchPreview = async () => {
    try {
      const data = await getPreview(shortCode);
      setPreview(data);
    } catch (err) {
      setError(err.status === 404 ? 'Link not found or has expired.' : err.message);
    } finally {
      setLoading(false);
    }
  };

  const doRedirect = () => {
    if (redirected || !preview) return;
    setRedirected(true);
    window.location.href = `http://localhost:8080/${shortCode}`;
  };

  const cancelRedirect = () => {
    setRedirected(true);
    setCountdown(0);
  };

  // ── Loading ───────────────────────────────────────────────────
  if (loading) return (
    <div style={styles.page}>
      <div className="spinner-wrap">
        <div className="spinner" />
        Loading preview…
      </div>
    </div>
  );

  // ── Error ─────────────────────────────────────────────────────
  if (error) return (
    <div style={styles.page}>
      <div style={styles.card}>
        <div style={{ fontSize: '3rem', marginBottom: 16 }}>🔗</div>
        <h2 style={styles.title}>Link not found</h2>
        <p style={styles.desc}>{error}</p>
        <a href="http://localhost:3000" style={styles.btnPrimary}>
          Go to SnipURL
        </a>
      </div>
    </div>
  );

  const domain = (() => {
    try { return new URL(preview.longUrl).hostname; } catch { return preview.longUrl; }
  })();

  return (
    <div style={styles.page}>
      <div style={styles.card}>

        {/* Header */}
        <div style={styles.header}>
          <div style={styles.brandBadge}>✂️ SnipURL</div>
          <p style={styles.warningText}>
            You are about to leave SnipURL and visit an external website
          </p>
        </div>

        {/* Destination card */}
        <div style={styles.destCard}>

          {/* og:image */}
          {preview.imageUrl && (
            <div style={styles.imageWrap}>
              <img
                src={preview.imageUrl}
                alt="Preview"
                style={styles.ogImage}
                onError={e => { e.target.style.display = 'none'; }}
              />
            </div>
          )}

          {/* og:title + description */}
          <div style={styles.destInfo}>
            <div style={styles.siteRow}>
              <img
                src={`https://www.google.com/s2/favicons?domain=${domain}&sz=16`}
                alt=""
                style={{ width: 16, height: 16, borderRadius: 3 }}
                onError={e => { e.target.style.display = 'none'; }}
              />
              <span style={styles.siteName}>{preview.siteName || domain}</span>
            </div>

            <h2 style={styles.ogTitle}>
              {preview.title || domain}
            </h2>

            {preview.description && (
              <p style={styles.ogDesc}>{preview.description}</p>
            )}

            <div style={styles.urlPill}>
              🔗 {preview.longUrl.length > 60
                  ? preview.longUrl.slice(0, 60) + '…'
                  : preview.longUrl}
            </div>
          </div>
        </div>

        {/* Countdown + actions */}
        <div style={styles.actions}>

          {/* Countdown ring */}
          {!redirected && (
            <div style={styles.countdownWrap}>
              <svg width="56" height="56" style={{ transform: 'rotate(-90deg)' }}>
                <circle cx="28" cy="28" r="24" fill="none" stroke="#e2e8f0" strokeWidth="4" />
                <circle
                  cx="28" cy="28" r="24" fill="none"
                  stroke="#6366f1" strokeWidth="4"
                  strokeDasharray={`${2 * Math.PI * 24}`}
                  strokeDashoffset={`${2 * Math.PI * 24 * (1 - countdown / 5)}`}
                  style={{ transition: 'stroke-dashoffset 1s linear' }}
                />
              </svg>
              <span style={styles.countdownNum}>{countdown}</span>
            </div>
          )}

          <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', margin: '8px 0 16px' }}>
            {redirected
              ? 'Redirecting…'
              : `Redirecting in ${countdown} second${countdown !== 1 ? 's' : ''}…`}
          </p>

          <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
            <button onClick={doRedirect} style={styles.btnPrimary}>
              🚀 Go Now
            </button>
            <button onClick={cancelRedirect} style={styles.btnSecondary}>
              ✋ Cancel
            </button>
          </div>
        </div>

        {/* Stats footer */}
        <div style={styles.statsFooter}>
          <span>/{shortCode}</span>
          <span>•</span>
          <span>{preview.clickCount?.toLocaleString() || 0} clicks</span>
        </div>

      </div>
    </div>
  );
}

// ── Styles ────────────────────────────────────────────────────────
const styles = {
  page: {
    minHeight: '100vh',
    background: 'linear-gradient(135deg, #f0f4ff 0%, #faf5ff 100%)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    padding: 20, fontFamily: 'var(--font-sans)',
  },
  card: {
    background: 'white', borderRadius: 20,
    boxShadow: '0 20px 60px rgba(0,0,0,0.12)',
    maxWidth: 500, width: '100%', overflow: 'hidden',
  },
  header: {
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    padding: '20px 24px', textAlign: 'center',
  },
  brandBadge: {
    color: 'white', fontWeight: 800, fontSize: '1.1rem', marginBottom: 6,
  },
  warningText: {
    color: 'rgba(255,255,255,0.85)', fontSize: '0.8rem', margin: 0,
  },
  destCard: {
    border: '1px solid #e2e8f0', margin: 16,
    borderRadius: 12, overflow: 'hidden',
  },
  imageWrap: {
    width: '100%', height: 180, overflow: 'hidden',
    background: '#f1f5f9',
  },
  ogImage: {
    width: '100%', height: '100%', objectFit: 'cover',
  },
  destInfo: {
    padding: '14px 16px',
  },
  siteRow: {
    display: 'flex', alignItems: 'center', gap: 6,
    marginBottom: 8,
  },
  siteName: {
    fontSize: '0.72rem', fontWeight: 600,
    color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.04em',
  },
  ogTitle: {
    fontSize: '1rem', fontWeight: 700,
    color: '#0f172a', marginBottom: 8, lineHeight: 1.3,
  },
  ogDesc: {
    fontSize: '0.82rem', color: '#64748b',
    lineHeight: 1.5, marginBottom: 10,
  },
  urlPill: {
    fontSize: '0.72rem', color: '#6366f1',
    background: '#e0e7ff', borderRadius: 6,
    padding: '4px 10px', display: 'inline-block',
    fontFamily: 'var(--font-mono)', wordBreak: 'break-all',
  },
  actions: {
    padding: '16px 24px 20px', textAlign: 'center',
  },
  countdownWrap: {
    position: 'relative', display: 'inline-flex',
    alignItems: 'center', justifyContent: 'center',
    marginBottom: 4,
  },
  countdownNum: {
    position: 'absolute', fontSize: '1.1rem',
    fontWeight: 800, color: '#6366f1',
  },
  btnPrimary: {
    padding: '10px 24px', background: '#6366f1',
    color: 'white', borderRadius: 8, border: 'none',
    fontWeight: 600, fontSize: '0.875rem',
    cursor: 'pointer', textDecoration: 'none',
    display: 'inline-flex', alignItems: 'center', gap: 6,
  },
  btnSecondary: {
    padding: '10px 20px', background: '#f1f5f9',
    color: '#64748b', borderRadius: 8, border: 'none',
    fontWeight: 600, fontSize: '0.875rem', cursor: 'pointer',
  },
  statsFooter: {
    borderTop: '1px solid #e2e8f0',
    padding: '10px 24px',
    display: 'flex', justifyContent: 'center', gap: 8,
    fontSize: '0.75rem', color: '#94a3b8',
    background: '#f8fafc',
  },
};
