"use client";

import { useState } from "react";

import OcupacionCocheras from "@/components/OcupacionCocheras";
import ReservasContent from "@/components/ReservasContent";

/**
 * Junta la ocupación con el alta de reserva.
 *
 * Existe por lo mismo que `PanelOperativo` y `PanelVisitante`: `page.jsx` es un
 * Server Component (valida el rol con `requireAuth`) y no puede tener estado,
 * así que sin este intermediario no habría dónde guardar el contador que le
 * avisa al panel de arriba que se creó o se canceló una reserva.
 *
 * Sin eso el panel mentiría justo después de reservar: la cochera que acabás de
 * ocupar seguiría figurando libre hasta recargar la página.
 */
export default function PanelReservas() {
  const [ocupacionKey, setOcupacionKey] = useState(0);

  return (
    <>
      <div className="mb-10">
        <OcupacionCocheras refreshKey={ocupacionKey} />
      </div>

      <ReservasContent
        modo="admin"
        onOcupacionCambiada={() => setOcupacionKey((k) => k + 1)}
      />
    </>
  );
}
