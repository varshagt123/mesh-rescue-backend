
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
  console.log("MAP ALERTS:", alerts);
  console.log("FIRST ALERT:", alerts[0]);
  console.log("COORDINATES:", {
  latitude: alerts[0]?.latitude,
  longitude: alerts[0]?.longitude,
  lat: alerts[0]?.lat,
  lng: alerts[0]?.lng,
  priority: alerts[0]?.priority
});

const isValidCoordinates = (alert) => {
  if (
    alert.lat == null ||
    alert.lng == null ||
    alert.lat === '' ||
    alert.lng === ''
  ) {
    return false;
  }

  const lat = Number(alert.lat);
  const lng = Number(alert.lng);

  return (
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    lat >= -90 && lat <= 90 &&
    lng >= -180 && lng <= 180 &&
    !(lat === 0 && lng === 0)
  );
};


const validAlerts = alerts.filter(isValidCoordinates);
console.log("VALID ALERT COUNT:", validAlerts.length);
console.log("MAP MARKER DATA:", validAlerts.map(a => ({
  id: a.id,
  lat: a.lat,
  lng: a.lng,
  priority: a.priority
})));
console.log("TOTAL ALERTS:", alerts.length);
console.log("VALID ALERTS:", validAlerts.length);
console.log("FIRST VALID ALERT:", validAlerts[0]);
  const MapUpdater = () => {
    const map = useMap();

    useEffect(() => {
      const priorityOrder = {
        critical: 4,
  high: 3,
  medium: 2,
  low: 1
};

const latestAlert = [...validAlerts]
 
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
        [
  Number(latestAlert.lat),
  Number(latestAlert.lng)
],
          14
        );
      }
    }, [validAlerts, map]);

    return null;
  };
  const getColor = (priority) => {
  switch (priority?.toLowerCase()) {
    case "critical":
      return "#8B0000"; // Dark red
    case "high":
      return "#FF0000"; // Bright red
    case "medium":
      return "#FFA500"; // Orange
    case "low":
      return "#008000"; // Green
    default:
      return "#0000FF";
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

       {validAlerts.map((alert) => (
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
