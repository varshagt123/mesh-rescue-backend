
import { useState } from 'react';
import { API } from '../services/api';

function Auth({ onLoginSuccess }) {
  const [isRegister, setIsRegister] = useState(false);

  const [form, setForm] = useState({
    name: '',
    username: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: ''
  });

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const updateField = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
    setError('');
    setSuccess('');
  };

  const switchMode = (register) => {
    setIsRegister(register);
    setError('');
    setSuccess('');
    setForm({
      name: '',
      username: '',
      email: '',
      phone: '',
      password: '',
      confirmPassword: ''
    });
  };

  const validateForm = () => {
    const username = form.username.trim();

    if (username.length < 4 || username.length > 20) {
      return 'Username must be between 4 and 20 characters.';
    }

    if (!/^[a-zA-Z0-9_]+$/.test(username)) {
      return 'Username can contain only letters, numbers and underscores.';
    }

    if (!form.password) {
      return 'Please enter your password.';
    }

    if (isRegister) {
      if (!form.name.trim()) return 'Please enter your full name.';

      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
        return 'Please enter a valid email address.';
      }

      if (!/^\+?[0-9]{10,15}$/.test(form.phone.trim())) {
        return 'Please enter a valid phone number (10–15 digits).';
      }

      if (form.password.length < 8 || form.password.length > 20) {
        return 'Password must be between 8 and 20 characters.';
      }

      if (
        !/[A-Z]/.test(form.password) ||
        !/[a-z]/.test(form.password) ||
        !/[0-9]/.test(form.password) ||
        !/[^A-Za-z0-9]/.test(form.password) ||
        /\s/.test(form.password)
      ) {
        return 'Password needs uppercase, lowercase, number and special character, with no spaces.';
      }

      if (form.password !== form.confirmPassword) {
        return 'Passwords do not match.';
      }
    }

    return '';
  };

const handleSubmit = async (e) => {
  e.preventDefault();
  setError('');
  setSuccess('');

  const validationError = validateForm();

  if (validationError) {
    setError(validationError);
    return;
  }

  setLoading(true);

  try {
    if (isRegister) {
      const res = await API.post('/register', {
        name: form.name.trim(),
        username: form.username.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        password: form.password
      });

      // Save token and user details
      localStorage.setItem('token', res.data.token);
      localStorage.setItem('username', res.data.user.username);
      localStorage.setItem('role', res.data.user.role);

      // Directly open the dashboard
      onLoginSuccess(res.data.user);

    } else {
      const res = await API.post('/login', {
        username: form.username.trim(),
        password: form.password
      });

      localStorage.setItem('token', res.data.token);
      localStorage.setItem('username', res.data.user.username);
      localStorage.setItem('role', res.data.user.role);

      onLoginSuccess(res.data.user);
    }
  } catch (err) {
    setError(
      err.response?.data?.error ||
      (isRegister
        ? 'Registration failed. Please try again.'
        : 'Invalid username or password.')
    );
  } finally {
    setLoading(false);
  }
};

 

  const inputStyle = {
    width: '100%',
    padding: '12px',
    marginBottom: '13px',
    border: '1px solid #cbd5e1',
    borderRadius: '6px',
    boxSizing: 'border-box',
    fontSize: '14px'
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      background: '#f1f5f9',
      padding: '20px',
      boxSizing: 'border-box'
    }}>
      <form onSubmit={handleSubmit} style={{
        width: '100%',
        maxWidth: '400px',
        padding: '30px',
        background: 'white',
        borderRadius: '12px',
        boxShadow: '0 4px 15px rgba(0,0,0,0.1)',
        boxSizing: 'border-box'
      }}>
        <h2 style={{ textAlign: 'center', color: '#1e293b' }}>
          Mesh Rescue
        </h2>

        <p style={{ textAlign: 'center', color: '#64748b' }}>
          {isRegister ? 'Create your Field User account' : 'Sign in to your account'}
        </p>

        {isRegister && (
          <>
            <input
              name="name"
              value={form.name}
              onChange={updateField}
              placeholder="Full Name"
              autoComplete="name"
              style={inputStyle}
              required
            />

            <input
              name="email"
              type="email"
              value={form.email}
              onChange={updateField}
              placeholder="Email Address"
              autoComplete="email"
              style={inputStyle}
              required
            />

            <input
              name="phone"
              type="tel"
              value={form.phone}
              onChange={updateField}
              placeholder="Phone Number"
              autoComplete="tel"
              style={inputStyle}
              required
            />
          </>
        )}

        <input
          name="username"
          value={form.username}
          onChange={updateField}
          placeholder="Username"
          autoComplete="username"
          style={inputStyle}
          minLength={4}
          maxLength={20}
          required
        />

        {isRegister && (
          <p style={{ fontSize: '12px', color: '#64748b', marginTop: '-8px' }}>
            4–20 characters. Letters, numbers and underscores only.
          </p>
        )}

        <div style={{ display: 'flex', marginBottom: '13px' }}>
          <input
            name="password"
            type={showPassword ? 'text' : 'password'}
            value={form.password}
            onChange={updateField}
            placeholder="Password"
            autoComplete={isRegister ? 'new-password' : 'current-password'}
            style={{ ...inputStyle, marginBottom: 0, flex: 1, minWidth: 0 }}
            required
          />

          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            style={{ marginLeft: '5px', cursor: 'pointer' }}
          >
            {showPassword ? 'Hide' : 'Show'}
          </button>
        </div>

        {isRegister && (
          <>
            <p style={{ fontSize: '12px', color: '#64748b' }}>
              Password: 8–20 characters, uppercase, lowercase, number and
              special character. No spaces.
            </p>

            <div style={{ display: 'flex', marginBottom: '13px' }}>
              <input
                name="confirmPassword"
                type={showConfirmPassword ? 'text' : 'password'}
                value={form.confirmPassword}
                onChange={updateField}
                placeholder="Confirm Password"
                autoComplete="new-password"
                style={{ ...inputStyle, marginBottom: 0, flex: 1, minWidth: 0 }}
                required
              />

              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                style={{ marginLeft: '5px', cursor: 'pointer' }}
              >
                {showConfirmPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </>
        )}

        {error && (
          <p style={{ color: '#dc2626', fontSize: '13px' }}>{error}</p>
        )}

        {success && (
          <p style={{ color: '#15803d', fontSize: '13px' }}>{success}</p>
        )}

        <button
          type="submit"
          disabled={loading}
          style={{
            width: '100%',
            padding: '12px',
            background: '#2563eb',
            color: 'white',
            border: 'none',
            borderRadius: '6px',
            cursor: loading ? 'not-allowed' : 'pointer',
            opacity: loading ? 0.7 : 1
          }}
        >
          {loading
            ? (isRegister ? 'Creating Account...' : 'Logging in...')
            : (isRegister ? 'Create Account' : 'Login')}
        </button>

        <p style={{ textAlign: 'center', marginTop: '20px', fontSize: '14px' }}>
          {isRegister ? 'Already have an account?' : "Don't have an account?"}
          {' '}
          <button
            type="button"
            onClick={() => switchMode(!isRegister)}
            style={{
              border: 'none',
              background: 'none',
              color: '#2563eb',
              cursor: 'pointer',
              fontWeight: 'bold'
            }}
          >
            {isRegister ? 'Login' : 'Register'}
          </button>
        </p>
      </form>
    </div>
  );
}

export default Auth;
