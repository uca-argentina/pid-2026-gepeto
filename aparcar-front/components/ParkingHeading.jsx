"use client";

import { useEffect, useState } from "react";
import api from "@/app/api";

export default function ParkingHeading() {
  const [perfil, setPerfil] = useState(null);
  useEffect(() => {
    let activo = true;
    api.get("/api/v1/visitantes/me")
      .then(({ data }) => { if (activo) setPerfil(data); })
      .catch(() => {}); // El panel operativo sigue disponible si falla el perfil.
    return () => { activo = false; };
  }, []);

  return (
    <h1 className="parking-heading">{perfil?.nombreEstacionamiento || "Mi estacionamiento"}</h1>
  );
}
