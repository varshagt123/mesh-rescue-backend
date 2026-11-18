
import {
  MapContainer,
  TileLayer,
  CircleMarker,
  Popup
} from "react-leaflet";

import "leaflet/dist/leaflet.css";

function DisasterMap({ alerts = [] }) {

  const getColor = (priority) => {
    switch (priority?.toLowerCase()) {
      case "high":
        return "red";
      case "medium":
        return "orange";
      case "low":
        return "green";
      default:
        return "blue";
    }
  };

  return (
    <div className="map-container">
      <MapContainer
        center={[16.3067, 80.4365]}
        zoom={12}
        style={{ height: "450px", width: "100%" }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {alerts
          .filter(alert =>
            Number.isFinite(Number(alert.lat)) &&
            Number.isFinite(Number(alert.lng))
          )
          .map((alert) => (
            <CircleMarker
              key={alert.id}
              center={[
                Number(alert.lat),
                Number(alert.lng)
              ]}
              radius={10}
              pathOptions={{
                color: getColor(alert.priority),
                fillColor: getColor(alert.priority),
                fillOpacity: 0.8
              }}
            >
              <Popup>
                <strong>{alert.name}</strong>
                <br />
                Alert ID: {alert.id}
                <br />
                Priority: {alert.priority}
                <br />
                Status: {alert.status}
                <br />
                Location: {alert.location}
              </Popup>
            </CircleMarker>
          ))}
      </MapContainer>
    </div>
  );
}

export default DisasterMap;
