import React, { useState } from 'react';
import {
  AreaChart, Area, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer
} from 'recharts';
import { getStats, deleteUrl } from '../services/api';

// ── Custom chart tooltip ──────────────────────────────────────
const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: 'white', border: '1px solid var(--border)',
      borderRadius: 8, padding: '10px 14px',
      boxShadow: 'var(--shadow-md)', fontSize: '0.82rem'
    }}>
      <p style={{ fontWeight: 700, marginBottom: 4 }}>{label}</p>
      <p style={{ color: 'var(--primary)' }}>
        {payload[0].value} clicks
      </p>
    </div>
  );
};

export default function AnalyticsDashboard() {
  const [shortCode, setShortCode] = useState('');
  const [stats,     setStats]     = useState(null);
  const [loading,   setLoading]   = useState(false);
  const [error,     setError]     = useState('');
  const [deleted,   setDeleted]   = useState(false);

  // ── Fetch stats ─────────────────────────────────────────────
  const handleFetch = async (e) => {
    e.preventDefault();
    if (!shortCode.trim()) return;

    setError('');
    setStats(null);
    setDeleted(false);
    setLoading(true);

    try {
      // Support full URL or just the code
      const code = shortCode.trim().replace(/.*\//, '');
      const data = await getStats(code);
      setStats(data);
    } catch (err) {
      setError(
        err.status === 404 ? `No link found for "${shortCode}"` : err.message
      );
    } finally {
      setLoading(false);
    }
  };

  // ── Delete link ─────────────────────────────────────────────
  const handleDelete = async () => {
    if (!window.confirm('Deactivate this short link? This cannot be undone.')) return;
    try {
      await deleteUrl(stats.shortCode);
      setDeleted(true);
      setStats(prev => ({ ...prev, isActive: false }));
    } catch (err) {
      setError(err.message);
    }
  };

  // ── Helpers ──────────────────────────────────────────────────
  const fmt = (iso) =>
    iso ? new Date(iso).toLocaleDateString('en-IN', {
      year: 'numeric', month: 'short', day: 'numeric'
    }) : '—';

  const statusClass = (s) => {
    if (!s.isActive)   return 'status-badge status-inactive';
    if (s.expiryTime && new Date(s.expiryTime) < new Date())
                       return 'status-badge status-expired';
    return 'status-badge status-active';
  };

  const statusText = (s) => {
    if (!s.isActive)   return '⚫ Inactive';
    if (s.expiryTime && new Date(s.expiryTime) < new Date())
                       return '🔴 Expired';
    return '🟢 Active';
  };

  // Normalise chart data (handle null/empty from API)
  const chartData = (stats?.clicksByDay || []).map(d => ({
    date:   new Date(d.date).toLocaleDateString('en-IN', { month: 'short', day: 'numeric' }),
    clicks: Number(d.clicks),
  }));

  const geoData = (stats?.clicksByCountry || []).map(d => ({
    country: d.country || 'Unknown',
    clicks:  Number(d.clicks),
  }));

  return (
    <div style={{ paddingBottom: 40 }}>

      {/* ── Lookup card ────────────────────────────────────────── */}
      <div className="card" style={{ marginTop: 32 }}>
        <div className="card-title">📊 Analytics Lookup</div>

        <form onSubmit={handleFetch}>
          <div className="stats-lookup">
            <input
              className="form-input"
              placeholder="Enter short code or full short URL…"
              value={shortCode}
              onChange={(e) => setShortCode(e.target.value)}
            />
            <button className="btn btn-primary" style={{ width: 'auto', marginTop: 0 }} disabled={loading}>
              {loading ? '…' : 'View Stats'}
            </button>
          </div>
        </form>

        {error && (
          <div className="alert alert-error" style={{ marginTop: 16 }}>
            ⚠️ {error}
          </div>
        )}
      </div>

      {/* ── Loading ──────────────────────────────────────────────── */}
      {loading && (
        <div className="spinner-wrap">
          <div className="spinner" />
          Fetching analytics…
        </div>
      )}

      {/* ── Stats result ─────────────────────────────────────────── */}
      {stats && !loading && (
        <>
          {/* Header */}
          <div className="card">
            <div className="stats-header">
              <div>
                <div className="stats-title">/{stats.shortCode}</div>
                <div className="stats-url">{stats.shortUrl}</div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 4 }}>
                  → {stats.longUrl.length > 60
                      ? stats.longUrl.slice(0, 60) + '…'
                      : stats.longUrl}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                <span className={statusClass(stats)}>{statusText(stats)}</span>
                {stats.isActive && (
                  <button className="btn btn-danger btn-sm" onClick={handleDelete}>
                    🗑 Deactivate
                  </button>
                )}
              </div>
            </div>

            {deleted && (
              <div className="alert alert-error">
                Link has been deactivated. Existing analytics are preserved.
              </div>
            )}

            {/* ── Summary KPIs ─────────────────────────────────── */}
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-value">{stats.clickCount?.toLocaleString()}</div>
                <div className="stat-label">Total Clicks</div>
              </div>
              <div className="stat-card">
                <div className="stat-value">{fmt(stats.createdAt)}</div>
                <div className="stat-label">Created</div>
              </div>
              <div className="stat-card">
                <div className="stat-value" style={{ fontSize: '1.1rem' }}>
                  {stats.expiryTime ? fmt(stats.expiryTime) : '∞'}
                </div>
                <div className="stat-label">Expires</div>
              </div>
              <div className="stat-card">
                <div className="stat-value" style={{ fontSize: '1.4rem' }}>
                  {geoData.length > 0 ? geoData[0].country : '—'}
                </div>
                <div className="stat-label">Top Country</div>
              </div>
            </div>
          </div>

          {/* ── Click trend chart ─────────────────────────────── */}
          {chartData.length > 0 && (
            <div className="card">
              <div className="chart-title">📈 Click Trend (Last 30 Days)</div>
              <ResponsiveContainer width="100%" height={220}>
                <AreaChart data={chartData} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="clickGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#6366f1" stopOpacity={0.2} />
                      <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--text-muted)' }} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: 'var(--text-muted)' }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip content={<CustomTooltip />} />
                  <Area
                    type="monotone"
                    dataKey="clicks"
                    stroke="#6366f1"
                    strokeWidth={2.5}
                    fill="url(#clickGrad)"
                    dot={false}
                    activeDot={{ r: 5, fill: '#6366f1' }}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* ── Geo chart ─────────────────────────────────────── */}
          {geoData.length > 0 && (
            <div className="card">
              <div className="chart-title">🌍 Clicks by Country</div>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={geoData} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="country" tick={{ fontSize: 11, fill: 'var(--text-muted)' }} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: 'var(--text-muted)' }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip content={<CustomTooltip />} />
                  <Bar dataKey="clicks" fill="#6366f1" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* ── Recent clicks table ───────────────────────────── */}
          <div className="card">
            <div className="chart-title">🕒 Recent Clicks</div>
            {stats.recentClicks?.length > 0 ? (
              <table className="clicks-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>IP Address</th>
                    <th>Country</th>
                    <th>Timestamp</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.recentClicks.map((click, i) => (
                    <tr key={i}>
                      <td style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>{i + 1}</td>
                      <td className="td-mono">{click.ipAddress || '—'}</td>
                      <td>{click.country || '—'}</td>
                      <td style={{ fontSize: '0.8rem' }}>
                        {new Date(click.accessedAt).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="empty-state">
                <div className="empty-state-icon">📭</div>
                <h3>No clicks yet</h3>
                <p>Share your short link to start tracking visits.</p>
              </div>
            )}
          </div>
        </>
      )}

      {/* ── Empty / initial state ─────────────────────────────── */}
      {!stats && !loading && !error && (
        <div className="empty-state" style={{ paddingTop: 60 }}>
          <div className="empty-state-icon">🔍</div>
          <h3>Enter a short code above</h3>
          <p>View click counts, trends, and geo data for any short link.</p>
        </div>
      )}
    </div>
  );
}
