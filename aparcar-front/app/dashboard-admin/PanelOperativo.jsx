"use client";

import VisitantesContent from "./VisitantesContent";

/**
 * El dashboard ADMIN se dividió en tres pantallas independientes:
 * cocheras y reservas ahora tienen su propia página (/dashboard-admin/cocheras
 * y /dashboard-admin/reservas), porque mezclarlas todas acá no escalaba a
 * medida que crecía la cantidad de cocheras/reservas. Acá solo queda el alta
 * rápida de visitante, que es la acción del día a día en portería.
 */
export default function PanelOperativo() {
  return <VisitantesContent />;
}