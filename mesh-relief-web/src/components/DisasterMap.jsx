
import {
  MapContainer,
  TileLayer,
  CircleMarker,
  Popup
} from "react-leaflet";

import "leaflet/dist/leaflet.css";
import { useEffect } from "react";
import { useMap } from "react-leaflet";
function DisasterMap({ alerts = [] }) {
  const MapUpdater = () => {
    const map = useMap();

    useEffect(() => {
      const priorityOrder = {
  high: 3,
  medium: 2,
  low: 1
};

const latestAlert = [...alerts]
  .filter(alert =>
    alert.lat != null &&
    alert.lng != null &&
    Number.isFinite(Number(alert.lat)) &&
    Number.isFinite(Number(alert.lng))
  )
  .sort((a, b) => {
    const priorityDifference =
      (priorityOrder[b.priority?.toLowerCase()] || 0) -
      (priorityOrder[a.priority?.toLowerCase()] || 0);

    if (priorityDifference !== 0) return priorityDifference;

    return new Date(b.timestamp).getTime() -
           new Date(a.timestamp).getTime();
  })[0];

      if (latestAlert) {
        map.setView(
          [Number(latestAlert.lat), Number(latestAlert.lng)],
          14
        );
      }
    }, [alerts, map]);

    return null;
  };
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
        <MapUpdater />
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
