import React, { useState } from 'react';
import { login, register } from '../services/api';

export default function AuthPage({ onAuthSuccess }) {
  const [mode,     setMode]     = useState('login'); // 'login' | 'register'
  const [name,     setName]     = useState('');
  const [email,    setEmail]    = useState('');
  const [password, setPassword] = useState('');
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const data = mode === 'login'
        ? await login(email, password)
        : await register(name, email, password);

      // Store token and user info
      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify({ email: data.email, name: data.name }));
      onAuthSuccess(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center',
      justifyContent: 'center', background: 'var(--bg)', padding: 20
    }}>
      <div style={{ width: '100%', maxWidth: 420 }}>

        {/* Brand */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{
            width: 48, height: 48, background: 'var(--primary)',
            borderRadius: 12, display: 'flex', alignItems: 'center',
            justifyContent: 'center', fontSize: '1.5rem',
            margin: '0 auto 12px'
          }}>✂️</div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--text-primary)' }}>
            SnipURL
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginTop: 4 }}>
            {mode === 'login' ? 'Sign in to access your dashboard' : 'Create your account'}
          </p>
        </div>

        {/* Card */}
        <div className="card" style={{ padding: 32 }}>

          {/* Tab switcher */}
          <div style={{
            display: 'flex', background: 'var(--bg)',
            borderRadius: 8, padding: 4, marginBottom: 24
          }}>
            {['login', 'register'].map(m => (
              <button
                key={m}
                onClick={() => { setMode(m); setError(''); }}
                style={{
                  flex: 1, padding: '8px 0',
                  borderRadius: 6, border: 'none',
                  fontFamily: 'var(--font-sans)',
                  fontWeight: 600, fontSize: '0.875rem',
                  cursor: 'pointer', transition: 'all 0.15s',
                  background: mode === m ? 'white' : 'transparent',
                  color: mode === m ? 'var(--primary)' : 'var(--text-secondary)',
                  boxShadow: mode === m ? 'var(--shadow-sm)' : 'none',
                }}
              >
                {m === 'login' ? '🔑 Sign In' : '✨ Register'}
              </button>
            ))}
          </div>

          {error && (
            <div className="alert alert-error">⚠️ {error}</div>
          )}

          <form onSubmit={handleSubmit}>
            {mode === 'register' && (
              <div className="form-group">
                <label className="form-label">Your Name</label>
                <input
                  className="form-input"
                  type="text"
                  placeholder="Anshu"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  required
                />
              </div>
            )}

            <div className="form-group">
              <label className="form-label">Email</label>
              <input
                className="form-input"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Password</label>
              <input
                className="form-input"
                type="password"
                placeholder={mode === 'register' ? 'At least 6 characters' : '••••••••'}
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                minLength={6}
              />
            </div>

            <button
              className="btn btn-primary"
              type="submit"
              disabled={loading}
              style={{ marginTop: 8 }}
            >
              {loading
                ? <><span className="spinner" style={{ width: 16, height: 16, borderWidth: 2 }} /> Please wait…</>
                : mode === 'login' ? '🔑 Sign In' : '✨ Create Account'
              }
            </button>
          </form>
        </div>

        {/* Back to home */}
        <p style={{ textAlign: 'center', marginTop: 16, fontSize: '0.82rem', color: 'var(--text-muted)' }}>
          Just want to shorten a URL?{' '}
          <button
            onClick={() => onAuthSuccess(null)}
            style={{ background: 'none', border: 'none', color: 'var(--primary)', cursor: 'pointer', fontWeight: 600 }}
          >
            Continue without login
          </button>
        </p>
      </div>
    </div>
  );
}
