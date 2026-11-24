import { useState, useEffect } from 'react';
import { API } from '../services/api';

const TRANSLATIONS = {
  en: {
    reportsTitle: 'Emergency Reports & Analytics',
    downloadCsv: 'Download Emergency Report (CSV)',
    total: 'Total Emergencies',
    resolved: 'Resolved Emergencies',
    pending: 'Pending Emergencies',
    byLocation: 'Emergency Requests by Location',
    safetyGuide: 'Offline Emergency Safety Guide',
    adminSection: 'Administrator Account & Role Management'
  },
  hi: {
    reportsTitle: 'आपातकालीन रिपोर्ट और विश्लेषण',
    downloadCsv: 'आपातकालीन रिपोर्ट डाउनलोड करें (CSV)',
    total: 'कुल आपात स्थितियाँ',
    resolved: 'हल की गई आपात स्थितियाँ',
    pending: 'लंबित आपात स्थितियाँ',
    byLocation: 'स्थान के अनुसार आपातकालीन अनुरोध',
    safetyGuide: 'ऑफ़लाइन आपातकालीन सुरक्षा मार्गदर्शिका',
    adminSection: 'व्यवस्थापक खाता और भूमिका प्रबंधन'
  },
  kn: {
    reportsTitle: 'ತುರ್ತು ವರದಿಗಳು ಮತ್ತು ವಿಶ್ಲೇಷಣೆ',
    downloadCsv: 'ತುರ್ತು ವರದಿ ಡೌನ್‌ಲೋಡ್ ಮಾಡಿ (CSV)',
    total: 'ಒಟ್ಟು ತುರ್ತು ಪರಿಸ್ಥಿತಿಗಳು',
    resolved: 'ಪರಿಹರಿಸಲಾದ ತುರ್ತು ಪರಿಸ್ಥಿತಿಗಳು',
    pending: 'ಬಾಕಿ ಇರುವ ತುರ್ತು ಪರಿಸ್ಥಿತಿಗಳು',
    byLocation: 'ಸ್ಥಳದ ಪ್ರಕಾರ ತುರ್ತು ವಿನಂತಿಗಳು',
    safetyGuide: 'ಆಫ್‌ಲೈನ್ ತುರ್ತು ಸುರಕ್ಷತಾ ಮಾರ್ಗದರ್ಶಿ',
    adminSection: 'ನಿರ್ವಾಹಕ ಖಾತೆ ಮತ್ತು ಪಾತ್ರ ನಿರ್ವಹಣೆ'
  }
};

function ReportsAndAdmin({ alerts = [], stats = {}, currentUser, lang = 'en', setLang }) {
  const [locationBreakdown, setLocationBreakdown] = useState([]);
  const [usersList, setUsersList] = useState([]);
  const [pendingResponders, setPendingResponders] = useState([]);
  const t = TRANSLATIONS[lang] || TRANSLATIONS.en;

  useEffect(() => {
    async function loadReportData() {
      try {
        const repRes = await API.get('/reports');
        setLocationBreakdown(repRes.data.byLocation || []);
      } catch (err) {
        console.error('Failed to load report breakdown:', err);
      }

     if (currentUser?.role?.toUpperCase() === 'ADMIN') {
        try {
        const usersRes = await API.get('/users');
          setUsersList(usersRes.data || []);
          const requestsRes = await API.get('/responder/requests');
setPendingResponders(requestsRes.data || []);
        } catch (err) {
          console.error('Failed to load users for admin:', err);
        }
      }
    }
    loadReportData();
  }, [alerts, currentUser]);

  const handleDownloadCSV = () => {
    const headers = ['Alert ID', 'Victim Name', 'Location', 'Priority', 'Status', 'Time'];
    const rows = alerts.map((a) => [
      `"${a.id}"`,
      `"${a.name}"`,
      `"${a.location}"`,
      `"${a.priority}"`,
      `"${a.status}"`,
      `"${a.time}"`
    ]);

    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
      link.setAttribute('href', encodedUri);
      link.setAttribute('download', `mesh_rescue_report_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div style={{ padding: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', marginBottom: '1rem' }}>
        <h2>{t.reportsTitle}</h2>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <label style={{ fontSize: '0.85rem', fontWeight: 'bold' }}>Language:</label>
          <select
            value={lang}
            onChange={(e) => setLang(e.target.value)}
            style={{ padding: '0.45rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
          >
            <option value="en">English</option>
            <option value="hi">हिन्दी (Hindi)</option>
            <option value="kn">ಕನ್ನಡ (Kannada)</option>
          </select>
          <button
            onClick={handleDownloadCSV}
            style={{
              padding: '0.5rem 1rem', borderRadius: '6px', border: 'none',
              backgroundColor: '#16a34a', color: '#ffffff', fontWeight: 'bold', cursor: 'pointer'
            }}
          >
            ⬇ {t.downloadCsv}
          </button>
        </div>
      </div>

      {/* Summary Metrics */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        <div style={{ padding: '1rem', borderRadius: '8px', border: '1px solid #e2e8f0', background: '#fff' }}>
          <div style={{ fontSize: '0.85rem', color: '#64748b' }}>{t.total}</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 'bold' }}>{stats.totalSOS ?? alerts.length}</div>
        </div>
        <div style={{ padding: '1rem', borderRadius: '8px', border: '1px solid #e2e8f0', background: '#fff' }}>
          <div style={{ fontSize: '0.85rem', color: '#64748b' }}>{t.resolved}</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 'bold', color: '#16a34a' }}>
            {stats.resolved ?? alerts.filter((a) => a.status === 'RESOLVED').length}
          </div>
        </div>
        <div style={{ padding: '1rem', borderRadius: '8px', border: '1px solid #e2e8f0', background: '#fff' }}>
          <div style={{ fontSize: '0.85rem', color: '#64748b' }}>{t.pending}</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 'bold', color: '#dc2626' }}>
            {stats.pending ?? alerts.filter((a) => a.status === 'PENDING').length}
          </div>
        </div>
      </div>

      {/* Emergencies by Location */}
      <div style={{ marginBottom: '1.5rem', padding: '1rem', borderRadius: '8px', border: '1px solid #e2e8f0', background: '#fff' }}>
        <h3>{t.byLocation}</h3>
        {locationBreakdown.length === 0 ? (
          <p style={{ color: '#64748b' }}>No location breakdown data yet.</p>
        ) : (
          <ul style={{ margin: 0, paddingLeft: '1.2rem' }}>
            {locationBreakdown.map((item, idx) => (
              <li key={idx} style={{ marginBottom: '0.35rem' }}>
                <strong>{item.location}</strong>: {item.count} alert(s)
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Offline Safety Guide */}
      <div style={{ marginBottom: '1.5rem', padding: '1rem', borderRadius: '8px', border: '1px solid #fde68a', background: '#fffbeb' }}>
        <h3>{t.safetyGuide}</h3>
        <ul style={{ margin: 0, paddingLeft: '1.2rem', lineHeight: 1.6 }}>
          <li><strong>Stay Put & Conserve Battery:</strong> Enable airplane/mesh mode when cellular towers are down and keep screen brightness low.</li>
          <li><strong>Share Exact Coordinates:</strong> Include GPS latitude/longitude and nearby landmarks in every SOS or medical request.</li>
          <li><strong>Medical & Hazard Precautions:</strong> Apply firm pressure to bleeding wounds, avoid live downed wires or floodwaters, and wait for verified rescue instructions.</li>
        </ul>
      </div>

{/* Pending Responder Requests */}
<div style={{
  padding: '1rem',
  marginBottom: '1.5rem',
  borderRadius: '8px',
  border: '1px solid #fbbf24',
  background: '#fffbeb'
}}>
  <h3>Pending Responder Access Requests</h3>

  {pendingResponders.length === 0 ? (
    <p>No pending responder requests.</p>
  ) : (
    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
      <thead>
        <tr style={{ textAlign: 'left' }}>
          <th style={{ padding: '8px' }}>Name</th>
          <th style={{ padding: '8px' }}>Username</th>
          <th style={{ padding: '8px' }}>Organization</th>
          <th style={{ padding: '8px' }}>Action</th>
        </tr>
      </thead>
      <tbody>
        {pendingResponders.map((r) => (
          <tr key={r.id}>
            <td style={{ padding: '8px' }}>{r.name}</td>
            <td style={{ padding: '8px' }}>{r.username}</td>
            <td style={{ padding: '8px' }}>{r.organization}</td>
            <td style={{ padding: '8px' }}>
              <button
                onClick={async () => {
                  try {
                    await API.put(`/responder/approve/${r.id}`);
                    setPendingResponders(prev =>
                      prev.filter(item => item.id !== r.id)
                    );
                    alert('Responder approved successfully.');
                  } catch (err) {
                    alert(err.response?.data?.message || 'Approval failed.');
                  }
                }}
                style={{
                  background: '#16a34a',
                  color: 'white',
                  border: 'none',
                  padding: '6px 10px',
                  borderRadius: '5px',
                  marginRight: '6px',
                  cursor: 'pointer'
                }}
              >
                Approve
              </button>

              <button
                onClick={async () => {
                  try {
                    await API.put(`/responder/reject/${r.id}`);
                    setPendingResponders(prev =>
                      prev.filter(item => item.id !== r.id)
                    );
                    alert('Responder request rejected.');
                  } catch (err) {
                    alert(err.response?.data?.message || 'Rejection failed.');
                  }
                }}
                style={{
                  background: '#dc2626',
                  color: 'white',
                  border: 'none',
                  padding: '6px 10px',
                  borderRadius: '5px',
                  cursor: 'pointer'
                }}
              >
                Reject
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )}
</div>

      {/* Administrator User Management */}
   {currentUser?.role?.toUpperCase() === 'ADMIN' && (
        <div style={{ padding: '1rem', borderRadius: '8px', border: '1px solid #cbd5e1', background: '#f8fafc' }}>
          <h3>{t.adminSection}</h3>
          <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '0.5rem' }}>
           <thead>
  <tr style={{ textAlign: 'left', borderBottom: '1px solid #cbd5e1' }}>
    <th style={{ padding: '0.5rem' }}>User ID</th>
    <th style={{ padding: '0.5rem' }}>Username</th>
    <th style={{ padding: '0.5rem' }}>Role</th>
    <th style={{ padding: '0.5rem' }}>Action</th>
  </tr>
</thead>
            <tbody>
             {usersList.map((u) => (
  <tr key={u.id} style={{ borderBottom: '1px solid #e2e8f0' }}>
    <td style={{ padding: '0.5rem' }}>{u.id}</td>
    <td style={{ padding: '0.5rem' }}>{u.username}</td>
    <td style={{ padding: '0.5rem', fontWeight: 'bold' }}>{u.role}</td>
    <td style={{ padding: '0.5rem' }}>
      {u.role?.toUpperCase() === 'ADMIN' ? (
        <span>Protected</span>
      ) : (
        <button
          onClick={async () => {
            const confirmed = window.confirm(
              `Are you sure you want to permanently delete ${u.username}?`
            );

            if (!confirmed) return;

            try {
              await API.delete(`/users/${u.id}`);

              setUsersList((prev) =>
                prev.filter((user) => user.id !== u.id)
              );

              alert('User deleted successfully.');
            } catch (err) {
              console.error('Delete user failed:', err);
              alert(err.response?.data?.error || 'Failed to delete user.');
            }
          }}
          style={{
            background: '#dc2626',
            color: 'white',
            border: 'none',
            padding: '6px 12px',
            borderRadius: '5px',
            cursor: 'pointer'
          }}
        >
          Delete
        </button>
      )}
    </td>
  </tr>
))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default ReportsAndAdmin;