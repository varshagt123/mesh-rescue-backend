import { useState, useEffect, useMemo } from 'react';
import './App.css';
import Auth from './components/Auth';
import DisasterMap from './components/DisasterMap';
import CommunicationPanel from "./components/CommunicationPanel";
import MeshDevices from "./components/MeshDevices";
import ReportsAndAdmin from "./components/ReportsAndAdmin";
import { API, socket } from './services/api';

function App() {
  const [currentUser, setCurrentUser] = useState(() => {
    const token = localStorage.getItem('token');
    const username = localStorage.getItem('username');
    const role = localStorage.getItem('role');
    return token && username ? { username, role: role || 'rescue_team' } : null;
  });

  const [activeTab, setActiveTab] = useState('dashboard');
  const [alerts, setAlerts] = useState([]);
  const [selectedAlert, setSelectedAlert] = useState(null);
  const [stats, setStats] = useState({
    totalSOS: 0,
    pending: 0,
    acknowledged: 0,
    resolved: 0,
    totalUsers: 0
  });
  const [loading, setLoading] = useState(true);
  const [lang, setLang] = useState('en');

  // Search, filter & sort controls for SOS alerts
  const [searchQuery, setSearchQuery] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [sortBy, setSortBy] = useState('time_desc');

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
    socket.disconnect();
    setCurrentUser(null);
    setSelectedAlert(null);
  };

  const formatAlertFromMessage = (msg, fallbackIdx = 0) => {
    const numericId = msg.id || fallbackIdx + 1;
    const rawTime = msg.timestamp || new Date().toISOString();
    return {
      id: `SOS-${String(numericId).padStart(3, '0')}`,
      dbId: msg.id,
      userId: `USR-${String(msg.id || fallbackIdx + 1).padStart(3, '0')}`,
      name: msg.sender || 'Unknown Victim',
      description: msg.content || 'Emergency assistance requested',
      location: msg.location_name || msg.recipient || 'Emergency Zone',
      priority: (msg.priority || (msg.content?.includes('CRITICAL') ? 'CRITICAL' : 'HIGH')).toUpperCase(),
      status: (msg.status || 'PENDING').toUpperCase(),
      time: new Date(rawTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      timestamp: new Date(rawTime).getTime(),
      lat: Number.isFinite(Number(msg.latitude)) ? Number(msg.latitude) : 16.3067 + ((numericId % 7) - 3) * 0.006,
      lng: Number.isFinite(Number(msg.longitude)) ? Number(msg.longitude) : 80.4365 + (((numericId * 3) % 7) - 3) * 0.006
    };
  };

  const fetchDashboardData = async () => {
    try {
      const [sosRes, statsRes] = await Promise.all([
        API.get('/messages/sos'),
        API.get('/stats').catch(() => ({ data: null }))
      ]);

      const formatted = (sosRes.data || []).map((msg, idx) => formatAlertFromMessage(msg, idx));
      setAlerts(formatted);

      if (statsRes.data) {
        setStats(statsRes.data);
      } else {
        setStats({
          totalSOS: formatted.length,
          pending: formatted.filter((a) => a.status === 'PENDING').length,
          acknowledged: formatted.filter((a) => a.status === 'ACKNOWLEDGED').length,
          resolved: formatted.filter((a) => a.status === 'RESOLVED').length,
          totalUsers: 0
        });
      }
    } catch (err) {
      console.error('Failed to load SOS dashboard data:', err);
      if (err.response?.status === 401 || err.response?.status === 403) {
        handleLogout();
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!currentUser) return;

    setLoading(true);
    fetchDashboardData();

    socket.connect();
    socket.emit('register_node', {
      username: currentUser.username,
      role: currentUser.role
    });

    const handleReceiveMessage = (newMessage) => {
      const isEmergency =
        newMessage.content?.toUpperCase().includes('SOS') ||
        newMessage.is_mesh ||
        ['SOS', 'MEDICAL_REQUEST'].includes(newMessage.message_type);

      if (isEmergency) {
        const incoming = formatAlertFromMessage(newMessage, Date.now() % 900);
        setAlerts((prev) => {
          if (prev.some((item) => item.dbId && item.dbId === incoming.dbId)) return prev;
          return [incoming, ...prev];
        });
        setStats((prev) => ({
          ...prev,
          totalSOS: prev.totalSOS + 1,
          pending: incoming.status === 'PENDING' ? prev.pending + 1 : prev.pending
        }));
      }
    };

    const handleStatusUpdate = (updatedMsg) => {
      setAlerts((prev) =>
        prev.map((item) =>
          item.dbId === updatedMsg.id ? { ...item, status: updatedMsg.status } : item
        )
      );
      fetchDashboardData();
    };

    socket.on('receive_message', handleReceiveMessage);
    socket.on('alert_status_updated', handleStatusUpdate);

    return () => {
      socket.off('receive_message', handleReceiveMessage);
      socket.off('alert_status_updated', handleStatusUpdate);
      socket.disconnect();
    };
  }, [currentUser]);

  const handleStatusChange = async (alertItem, newStatus) => {
    setAlerts((prev) =>
      prev.map((item) => (item.id === alertItem.id ? { ...item, status: newStatus } : item))
    );

    if (alertItem.dbId) {
      try {
        await API.patch(`/messages/sos/${alertItem.dbId}/status`, { status: newStatus });
        fetchDashboardData();
      } catch (err) {
        console.error('Failed to persist status update:', err);
      }
    }
  };

  const filteredAndSortedAlerts = useMemo(() => {
    const priorityRank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };

    const filtered = alerts.filter((a) => {
      const matchesQuery =
        !searchQuery.trim() ||
        a.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
        a.userId.toLowerCase().includes(searchQuery.toLowerCase()) ||
        a.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        a.location.toLowerCase().includes(searchQuery.toLowerCase()) ||
        a.description.toLowerCase().includes(searchQuery.toLowerCase());

      const matchesPriority = priorityFilter === 'ALL' || a.priority === priorityFilter;
      const matchesStatus = statusFilter === 'ALL' || a.status === statusFilter;

      return matchesQuery && matchesPriority && matchesStatus;
    });

    return [...filtered].sort((a, b) => {
      if (sortBy === 'priority_desc') {
        return (priorityRank[b.priority] || 0) - (priorityRank[a.priority] || 0);
      }
      if (sortBy === 'time_asc') {
        return a.timestamp - b.timestamp;
      }
      return b.timestamp - a.timestamp;
    });
  }, [alerts, searchQuery, priorityFilter, statusFilter, sortBy]);

  if (!currentUser) {
    return <Auth onLoginSuccess={(userData) => setCurrentUser(userData)} />;
  }

  const navItems = [
    { key: 'dashboard', label: 'Dashboard' },
    { key: 'sos', label: 'SOS Alerts' },
    { key: 'map', label: 'Live Map' },
    { key: 'communication', label: 'Communication' },
    { key: 'devices', label: 'Mesh Devices' },
    { key: 'reports', label: currentUser.role === 'admin' ? 'Reports & Admin' : 'Reports' }
  ];

  const derivedPending = stats.pending || alerts.filter((a) => a.status === 'PENDING').length;
  const derivedAck = stats.acknowledged || alerts.filter((a) => a.status === 'ACKNOWLEDGED').length;
  const derivedResolved = stats.resolved || alerts.filter((a) => a.status === 'RESOLVED').length;
  const derivedTotal = stats.totalSOS || alerts.length;

  return (
    <div className="app-layout" style={{ display: 'flex', minHeight: '100vh' }}>
      {/* Sidebar Navigation */}
      <aside
        className="sidebar"
        style={{
          width: '230px',
          backgroundColor: '#0f172a',
          color: '#f8fafc',
          padding: '1.25rem',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between'
        }}
      >
        <div>
          <h3 style={{ marginTop: 0, marginBottom: '1.5rem' }}>🚨 MeshRelief</h3>
          <nav style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {navItems.map((item) => (
              <button
                key={item.key}
                onClick={() => setActiveTab(item.key)}
                style={{
                  textAlign: 'left',
                  padding: '0.65rem 0.9rem',
                  borderRadius: '6px',
                  border: 'none',
                  cursor: 'pointer',
                  fontWeight: activeTab === item.key ? 'bold' : 'normal',
                  backgroundColor: activeTab === item.key ? '#2563eb' : 'transparent',
                  color: activeTab === item.key ? '#ffffff' : '#cbd5e1'
                }}
              >
                {item.label}
              </button>
            ))}
          </nav>
        </div>

        <div style={{ fontSize: '0.8rem', color: '#22c55e', paddingTop: '1rem', borderTop: '1px solid #1e293b' }}>
          ● System Online
          <div style={{ color: '#94a3b8' }}>Mesh Network Active</div>
        </div>
      </aside>

      {/* Main Content Area */}
      <main style={{ flex: 1, backgroundColor: '#f1f5f9', display: 'flex', flexDirection: 'column' }}>
        {/* Top Header */}
        <header
          style={{
            backgroundColor: '#ffffff',
            padding: '0.85rem 1.5rem',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}
        >
          <span style={{ fontWeight: 'bold', color: '#0f172a' }}>
            Role: <span style={{ textTransform: 'uppercase', color: '#2563eb' }}>{currentUser.role}</span>
          </span>

          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <span>🟢 {currentUser.username}</span>
            <button
              onClick={handleLogout}
              style={{
                padding: '0.4rem 0.85rem',
                borderRadius: '6px',
                border: '1px solid #ef4444',
                background: 'transparent',
                color: '#ef4444',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              Logout
            </button>
          </div>
        </header>

        {/* Active View */}
        <div style={{ flex: 1, padding: '1rem 1.5rem' }}>
          {(activeTab === 'dashboard' || activeTab === 'sos') && (
            <>
              {activeTab === 'dashboard' && (
                <section style={{ marginBottom: '1.5rem' }}>
                  <h2 style={{ margin: '0 0 0.25rem' }}>Overview</h2>
                  <p style={{ color: '#64748b', margin: '0 0 1rem' }}>
                    Emergency situation and response statistics at a glance
                  </p>

                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
                      gap: '1rem'
                    }}
                  >
                    <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', borderTop: '4px solid #2563eb' }}>
                      <div style={{ fontSize: '0.85rem', color: '#64748b' }}>Total SOS Alerts</div>
                      <div style={{ fontSize: '1.75rem', fontWeight: 'bold' }}>{derivedTotal}</div>
                    </div>
                    <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', borderTop: '4px solid #ef4444' }}>
                      <div style={{ fontSize: '0.85rem', color: '#64748b' }}>Pending</div>
                      <div style={{ fontSize: '1.75rem', fontWeight: 'bold' }}>{derivedPending}</div>
                    </div>
                    <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', borderTop: '4px solid #f59e0b' }}>
                      <div style={{ fontSize: '0.85rem', color: '#64748b' }}>Acknowledged</div>
                      <div style={{ fontSize: '1.75rem', fontWeight: 'bold' }}>{derivedAck}</div>
                    </div>
                    <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', borderTop: '4px solid #10b981' }}>
                      <div style={{ fontSize: '0.85rem', color: '#64748b' }}>Resolved</div>
                      <div style={{ fontSize: '1.75rem', fontWeight: 'bold' }}>{derivedResolved}</div>
                    </div>
                    <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', borderTop: '4px solid #6366f1' }}>
                      <div style={{ fontSize: '0.85rem', color: '#64748b' }}>Total Registered Users</div>
                      <div style={{ fontSize: '1.75rem', fontWeight: 'bold' }}>{stats.totalUsers}</div>
                    </div>
                  </div>
                </section>
              )}

              {activeTab === 'dashboard' && (
                <section style={{ marginBottom: '1.5rem' }}>
                  <h3 style={{ margin: '0 0 0.25rem' }}>Emergency Locations</h3>
                  <p style={{ color: '#64748b', margin: '0 0 0.75rem' }}>
                    Live SOS locations of affected users
                  </p>
                  <DisasterMap alerts={filteredAndSortedAlerts} selectedAlert={selectedAlert} />
                </section>
              )}

              {/* SOS Alerts Management Table */}
              <section style={{ background: '#fff', padding: '1.25rem', borderRadius: '8px' }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '0.75rem',
                    marginBottom: '1rem'
                  }}
                >
                  <div>
                    <h3 style={{ margin: 0 }}>
                      {activeTab === 'sos' ? 'SOS Alert Management' : 'Recent SOS Alerts'}
                    </h3>
                    <p style={{ color: '#64748b', margin: '0.2rem 0 0', fontSize: '0.9rem' }}>
                      Emergency requests from affected users — click a row to locate on map
                    </p>
                  </div>

                  <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                    <input
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder="Search victim, ID, location..."
                      style={{ padding: '0.45rem 0.75rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
                    />
                    <select
                      value={priorityFilter}
                      onChange={(e) => setPriorityFilter(e.target.value)}
                      style={{ padding: '0.45rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
                    >
                      <option value="ALL">All Priorities</option>
                      <option value="CRITICAL">CRITICAL</option>
                      <option value="HIGH">HIGH</option>
                      <option value="MEDIUM">MEDIUM</option>
                      <option value="LOW">LOW</option>
                    </select>
                    <select
                      value={statusFilter}
                      onChange={(e) => setStatusFilter(e.target.value)}
                      style={{ padding: '0.45rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
                    >
                      <option value="ALL">All Statuses</option>
                      <option value="PENDING">PENDING</option>
                      <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
                      <option value="RESOLVED">RESOLVED</option>
                    </select>
                    <select
                      value={sortBy}
                      onChange={(e) => setSortBy(e.target.value)}
                      style={{ padding: '0.45rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
                    >
                      <option value="time_desc">Sort: Newest First</option>
                      <option value="time_asc">Sort: Oldest First</option>
                      <option value="priority_desc">Sort: Highest Priority</option>
                    </select>
                  </div>
                </div>

                {loading ? (
                  <p>Loading SOS alerts...</p>
                ) : filteredAndSortedAlerts.length === 0 ? (
                  <p style={{ color: '#64748b' }}>No matching SOS alerts found.</p>
                ) : (
                  <div style={{ overflowX: 'auto' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                      <thead>
                        <tr style={{ background: '#f8fafc', textAlign: 'left', fontSize: '0.85rem', color: '#475569' }}>
                          <th style={{ padding: '0.65rem', borderBottom: '1px solid #e2e8f0' }}>Alert ID</th>
                          <th style={{ padding: '0.65rem', borderBottom: '1px solid #e2e8f0' }}>Victim / User ID</th>
                          <th style={{ padding: '0.65rem', borderBottom: '1px solid #e2e8f0' }}>Emergency Details</th>
                          <th style={{ padding: '0.65rem', borderBottom: '1px solid #e2e8f0' }}>Location</th>
                          <th style={{ padding: '0.65rem', borderBottom: '1px solid #e2e8f0' }}>Priority</th>
                          <th style={{ padding: '0.65rem', borderBottom: '1px solid #e2e8f0' }}>Status</th>
                          <th style={{ padding: '0.65rem', borderBottom: '1px solid #e2e8f0' }}>Time</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredAndSortedAlerts.map((alert) => (
                          <tr
                            key={alert.id}
                            onClick={() => {
                              setSelectedAlert(alert);
                              if (activeTab === 'sos') setActiveTab('map');
                            }}
                            style={{ cursor: 'pointer', borderBottom: '1px solid #f1f5f9' }}
                          >
                            <td style={{ padding: '0.65rem', fontWeight: 'bold' }}>{alert.id}</td>
                            <td style={{ padding: '0.65rem' }}>
                              <div>{alert.name}</div>
                              <small style={{ color: '#64748b' }}>{alert.userId}</small>
                            </td>
                            <td style={{ padding: '0.65rem', maxWidth: '240px' }}>{alert.description}</td>
                            <td style={{ padding: '0.65rem' }}>{alert.location}</td>
                            <td style={{ padding: '0.65rem' }}>
                              <span
                                style={{
                                  padding: '0.2rem 0.5rem',
                                  borderRadius: '4px',
                                  fontSize: '0.75rem',
                                  fontWeight: 'bold',
                                  backgroundColor:
                                    alert.priority === 'CRITICAL' || alert.priority === 'HIGH'
                                      ? '#fee2e2'
                                      : '#fef3c7',
                                  color:
                                    alert.priority === 'CRITICAL' || alert.priority === 'HIGH'
                                      ? '#991b1b'
                                      : '#92400e'
                                }}
                              >
                                {alert.priority}
                              </span>
                            </td>
                            <td style={{ padding: '0.65rem' }} onClick={(e) => e.stopPropagation()}>
                              <select
                                value={alert.status}
                                onChange={(e) => handleStatusChange(alert, e.target.value)}
                                style={{ padding: '0.3rem 0.5rem', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                              >
                                <option value="PENDING">PENDING</option>
                                <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
                                <option value="RESOLVED">RESOLVED</option>
                              </select>
                            </td>
                            <td style={{ padding: '0.65rem' }}>{alert.time}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}

          {activeTab === 'map' && (
            <section style={{ background: '#fff', padding: '1.25rem', borderRadius: '8px' }}>
              <h2 style={{ marginTop: 0 }}>Live Victim Map & Location Tracking</h2>
              <p style={{ color: '#64748b' }}>
                Click any marker to view victim details, last-known location/time, and open map directions.
              </p>
              <DisasterMap alerts={filteredAndSortedAlerts} selectedAlert={selectedAlert} />
            </section>
          )}

          {activeTab === 'communication' && <CommunicationPanel currentUser={currentUser} />}

          {activeTab === 'devices' && <MeshDevices />}

          {activeTab === 'reports' && (
            <ReportsAndAdmin
              alerts={alerts}
              stats={{ ...stats, totalSOS: derivedTotal, pending: derivedPending, resolved: derivedResolved }}
              currentUser={currentUser}
              lang={lang}
              setLang={setLang}
            />
          )}
        </div>
      </main>
    </div>
  );
}

export default App;