"use client";

import { useState } from "react";
import MisVehiculos from "@/components/MisVehiculos";
import ReservasContent from "@/components/ReservasContent";

export default function RecurrentesContent() {
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight text-ink">Mis reservas recurrentes</h1>
        <p className="mt-2 text-sm text-ink/60">
          Reservá lugares para tus propios vehículos de manera semanal. Esta sección es exclusiva de tu cuenta de administrador.
        </p>
      </div>
      <MisVehiculos admin onVehiculosCambiaron={() => setRefreshKey((valor) => valor + 1)} />
      <ReservasContent modo="recurrentes" refreshKey={refreshKey} layout="columnas" />
    </div>
  );
}
