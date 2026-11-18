import { useState } from 'react'
import './App.css'
import DisasterMap from './components/DisasterMap'

function App() {
  
const [alerts, setAlerts] = useState([
  {
    id: 'SOS-001',
    name: 'Rahul Kumar',
    location: 'North Zone',
    priority: 'CRITICAL',
    status: 'PENDING',
    time: '10:30 AM',
    lat: 16.3067,
    lng: 80.4365
  },
  {
    id: 'SOS-002',
    name: 'Priya Sharma',
    location: 'East Zone',
    priority: 'HIGH',
    status: 'ACKNOWLEDGED',
    time: '10:25 AM',
    lat: 16.5062,
    lng: 80.6480
  },
  {
    id: 'SOS-003',
    name: 'Arun Kumar',
    location: 'South Zone',
    priority: 'MEDIUM',
    status: 'RESOLVED',
    time: '10:15 AM',
    lat: 16.2430,
    lng: 80.6400
  }
])

const updateStatus = (id, newStatus) => {
  setAlerts((prevAlerts) =>
    prevAlerts.map((alert) =>
      alert.id === id
        ? { ...alert, status: newStatus }
        : alert
    )
  )
}
const totalAlerts = alerts.length

const pendingCount = alerts.filter(
  (alert) => alert.status === 'PENDING'
).length

const acknowledgedCount = alerts.filter(
  (alert) => alert.status === 'ACKNOWLEDGED'
).length

const resolvedCount = alerts.filter(
  (alert) => alert.status === 'RESOLVED'
).length
  return (
    <div className="dashboard">

      {/* Sidebar */}
      <aside className="sidebar">
        <h2>🚨 MeshRelief</h2>
        <p className="subtitle">DISASTER RESPONSE SYSTEM</p>

        <nav>
          <a className="active" href="#overview">Dashboard</a>
          <a href="#alerts">SOS Alerts</a>
          <a href="#map">Live Map</a>
          <a href="#communication">Communication</a>
          <a href="#devices">Mesh Devices</a>
          <a href="#reports">Reports</a>
        </nav>

        <div className="sidebar-bottom">
          <p>● System Online</p>
          <small>Mesh Network Active</small>
        </div>
      </aside>

      {/* Main Content */}
      <main className="main-content">

        <header className="topbar">
          <div>
            <h2>Disaster Response Dashboard</h2>
            <p>Monitor emergencies and coordinate rescue operations</p>
          </div>
          <div className="admin">
            <span>🟢 Admin</span>
          </div>
        </header>

        <section id="overview">
          <h2>Overview</h2>
          <p className="description">Emergency situation at a glance</p>

          
<div className="stats">

  <div className="stat-card">
    <p>Total SOS Alerts</p>
    <h1>{totalAlerts}</h1>
  </div>

  <div className="stat-card critical">
    <p>Pending</p>
    <h1>{pendingCount}</h1>
  </div>

  <div className="stat-card acknowledged">
    <p>Acknowledged</p>
    <h1>{acknowledgedCount}</h1>
  </div>

  <div className="stat-card resolved">
    <p>Resolved</p>
    <h1>{resolvedCount}</h1>
  </div>

</div>

        </section>

        {/* Sprint 3 - Interactive Disaster Map */}

        <section id="map" className="map-section">

          <h2>Emergency Locations</h2>

          <p className="description">
            Live SOS locations of affected users
          </p>

          <DisasterMap alerts={alerts} />

        </section>


        <section id="alerts" className="alerts-section">
          <h2>Recent SOS Alerts</h2>
          <p className="description">Latest emergency requests from affected users</p>

          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>Alert ID</th>
                  <th>Victim Name</th>
                  <th>Location</th>
                  <th>Priority</th>
                  <th>Status</th>
                  <th>Time</th>
                </tr>
              </thead>

              <tbody>
                {alerts.map((alert) => (
                  <tr key={alert.id}>
                    <td>{alert.id}</td>
                    <td>{alert.name}</td>
                    <td>{alert.location}</td>
                    <td>
                      <span className={`badge ${alert.priority.toLowerCase()}`}>
                        {alert.priority}
                      </span>
                    </td>
                    
<td>
  <select
    value={alert.status}
    onChange={(e) => updateStatus(alert.id, e.target.value)}
  >
    <option value="PENDING">PENDING</option>
    <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
    <option value="RESOLVED">RESOLVED</option>
  </select>
</td>

                    <td>{alert.time}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

      </main>
    </div>
  )
}

export default App
