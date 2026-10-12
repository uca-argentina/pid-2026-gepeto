"use client";

import OcupacionCocheras from "@/components/OcupacionCocheras";

/**
 * Con qué abre el dashboard del admin.
 *
 * El dashboard se dividió en pantallas independientes (cocheras, reservas,
 * usuarios y el alta de visitante), porque mezclarlas todas acá no escalaba a
 * medida que crecía la cantidad de cocheras y reservas. Lo que queda de entrada
 * es la foto del predio: qué hay ocupado hoy y qué está libre, que es lo que se
 * mira primero al llegar. El alta de visitante pasó a `/dashboard-admin/visitantes`,
 * a un clic del encabezado.
 */
export default function PanelOperativo() {
  return <OcupacionCocheras />;
}
