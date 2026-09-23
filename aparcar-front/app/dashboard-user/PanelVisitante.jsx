"use client";

import { useState } from "react";

import ReservasContent from "@/components/ReservasContent";
import MiPerfilContent from "./MiPerfilContent";

/**
 * Agrupa las dos secciones del dashboard del visitante.
 *
 * Existe por lo mismo que PanelOperativo en el lado del admin: `page.jsx` es un
 * Server Component (valida el rol con requireAuth) y no puede tener estado, así
 * que sin este intermediario no habría dónde guardar el contador que le avisa
 * al formulario de reserva que la lista de patentes cambió.
 */
export default function PanelVisitante() {
  const [vehiculosKey, setVehiculosKey] = useState(0);

  return (
    <div className="visitor-dashboard">
      <MiPerfilContent onVehiculosCambiaron={() => setVehiculosKey((k) => k + 1)} />
      <ReservasContent modo="user" refreshKey={vehiculosKey} />
    </div>
  );
}
