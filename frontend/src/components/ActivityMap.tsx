import { useEffect, useRef, useState } from "react";
import * as mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css"
interface ActivityMapProps {
  polyline: string;
  height?: number;
}

function decodePolyline(encoded: string): [number, number][] {
  const coords: [number, number][] = [];
  let index = 0;
  let lat = 0;
  let lng = 0;

  while (index < encoded.length) {
    let shift = 0;
    let result = 0;
    let byte;

    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);

    const dlat = result & 1 ? ~(result >> 1) : result >> 1;
    lat += dlat;

    shift = 0;
    result = 0;

    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);

    const dlng = result & 1 ? ~(result >> 1) : result >> 1;
    lng += dlng;

    coords.push([lng / 1e5, lat / 1e5]);
  }

  return coords;
}

export default function ActivityMap({ polyline, height = 200 }: ActivityMapProps) {
  const mapContainer = useRef<HTMLDivElement>(null);
  const map = useRef<mapboxgl.Map | null>(null);
  const [isVisible, setIsVisible] = useState(false);

  // Intersection Observer로 화면에 보일 때만 로드
  useEffect(() => {
    const observer = new IntersectionObserver(
        ([entry]) => {
          if (entry.isIntersecting) {
            setIsVisible(true);
            observer.disconnect();
          }
        },
        { threshold: 0.1 }
    );

    if (mapContainer.current) {
      observer.observe(mapContainer.current);
    }

    return () => observer.disconnect();
  }, []);

  // 지도 로드
  useEffect(() => {
    if (!mapContainer.current || !polyline || !isVisible) return;

    const coords = decodePolyline(polyline);
    if (coords.length === 0) return;

    const mapInstance = new mapboxgl.Map({
      container: mapContainer.current,
      style: "mapbox://styles/mapbox/outdoors-v12",
      center: coords[Math.floor(coords.length / 2)],
      zoom: 11,
      interactive: false,
      accessToken: import.meta.env.VITE_MAPBOX_TOKEN,
    });

    map.current = mapInstance;

    mapInstance.on("load", () => {
      mapInstance.addSource("route", {
        type: "geojson",
        data: {
          type: "Feature",
          properties: {},
          geometry: {
            type: "LineString",
            coordinates: coords,
          },
        },
      });

      mapInstance.addLayer({
        id: "route",
        type: "line",
        source: "route",
        layout: {
          "line-join": "round",
          "line-cap": "round",
        },
        paint: {
          "line-color": "#fc4c02",
          "line-width": 3,
        },
      });

      const bounds = coords.reduce(
          (bounds, coord) => bounds.extend(coord as [number, number]),
          new mapboxgl.LngLatBounds(coords[0], coords[0])
      );

      mapInstance.fitBounds(bounds, {
        padding: 30,
        duration: 0,
      });
    });

    return () => {
      mapInstance.remove();
    };
  }, [polyline, isVisible]);

  return (
      <div
          ref={mapContainer}
          style={{
            width: "100%",
            height: `${height}px`,
            background: isVisible ? undefined : "#f3f4f6"
          }}
      >
        {!isVisible && (
            <div style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              height: "100%",
              color: "#9ca3af"
            }}>
              지도 로딩...
            </div>
        )}
      </div>
  );
}