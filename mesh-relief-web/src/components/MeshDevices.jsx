import { useState, useEffect } from 'react';
import { API, socket } from '../services/api';

function MeshDevices() {
  const [devices, setDevices] = useState([]);
  const [activeUsernames, setActiveUsernames] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadDevices = async () => {
    try {
      const res = await API.get('/devices', {
  headers: {
    'Cache-Control': 'no-cache',
    'Pragma': 'no-cache'
  }
});
      setDevices(res.data);
    } catch (err) {
      console.error('Failed to fetch devices:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDevices();

    const handleActiveNodes = (usernames) => {
      setActiveUsernames(usernames || []);
      loadDevices();
    };

    const handleNewMessage = () => {
      loadDevices();
    };

    socket.on('update_active_nodes', handleActiveNodes);
    socket.on('receive_message', handleNewMessage);

    return () => {
      socket.off('update_active_nodes', handleActiveNodes);
      socket.off('receive_message', handleNewMessage);
    };
  }, []);

  return (
    <div style={{ padding: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <div>
          <h2>Mesh Devices & Network Monitoring</h2>
          <p style={{ color: '#64748b', margin: 0 }}>
            Registered field devices, last-seen timestamps, connectivity, and sync status
          </p>
        </div>
      <button
  type="button"
  onClick={() => {
    console.log('Refresh button clicked');
    setLoading(true);
    loadDevices();
  }}
  style={{
    padding: '0.5rem 1rem',
    borderRadius: '6px',
    border: '1px solid #cbd5e1',
    backgroundColor: '#ffffff',
    cursor: 'pointer',
    fontWeight: 'bold',
    position: 'relative',
    zIndex: 10
  }}
>
  Refresh Devices
</button>
      </div>

      {activeUsernames.length > 0 && (
        <div style={{
          marginBottom: '1rem', padding: '0.75rem 1rem', borderRadius: '8px',
          backgroundColor: '#ecfdf5', border: '1px solid #a7f3d0', color: '#065f46'
        }}>
          <strong>Live Connected Field Users ({activeUsernames.length}):</strong>{' '}
          {activeUsernames.join(', ')}
        </div>
      )}

      {loading ? (
        <p>Loading registered devices...</p>
      ) : devices.length === 0 ? (
        <p style={{ color: '#64748b' }}>
          No registered mesh devices recorded yet. Devices appear automatically when field nodes register or sync.
        </p>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', backgroundColor: '#ffffff' }}>
            <thead>
              <tr style={{ backgroundColor: '#f1f5f9', textAlign: 'left' }}>
                <th style={{ padding: '0.75rem', borderBottom: '1px solid #cbd5e1' }}>Device ID</th>
                <th style={{ padding: '0.75rem', borderBottom: '1px solid #cbd5e1' }}>Assigned User</th>
                <th style={{ padding: '0.75rem', borderBottom: '1px solid #cbd5e1' }}>Connectivity</th>
                <th style={{ padding: '0.75rem', borderBottom: '1px solid #cbd5e1' }}>Sync Status</th>
                <th style={{ padding: '0.75rem', borderBottom: '1px solid #cbd5e1' }}>Waiting Messages</th>
                <th style={{ padding: '0.75rem', borderBottom: '1px solid #cbd5e1' }}>Last Seen (UTC/Local)</th>
              </tr>
            </thead>
            <tbody>
              {devices.map((dev) => {
                const isOnline = dev.connectivity_status === 'ONLINE';
                return (
                  <tr key={dev.device_id}>
                    <td style={{ padding: '0.75rem', borderBottom: '1px solid #e2e8f0', fontWeight: 'bold' }}>
                      {dev.device_id}
                    </td>
                    <td style={{ padding: '0.75rem', borderBottom: '1px solid #e2e8f0' }}>
                      {dev.username || 'Unassigned'}
                    </td>
                    <td style={{ padding: '0.75rem', borderBottom: '1px solid #e2e8f0' }}>
                      <span style={{
                        padding: '0.25rem 0.6rem', borderRadius: '999px', fontSize: '0.75rem',
                        fontWeight: 'bold',
                        backgroundColor: isOnline ? '#dcfce7' : '#fee2e2',
                        color: isOnline ? '#166534' : '#991b1b'
                      }}>
                        {dev.connectivity_status || 'OFFLINE'}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem', borderBottom: '1px solid #e2e8f0' }}>
                      {dev.sync_status || 'SYNCED'}
                    </td>
                    <td style={{ padding: '0.75rem', borderBottom: '1px solid #e2e8f0' }}>
                      {dev.pending_messages ?? 0}
                    </td>
                    <td style={{ padding: '0.75rem', borderBottom: '1px solid #e2e8f0' }}>
                      {dev.last_seen ? new Date(dev.last_seen).toLocaleString() : 'N/A'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default MeshDevices;