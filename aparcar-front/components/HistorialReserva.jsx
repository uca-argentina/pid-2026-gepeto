import { formatearFechaRegistro } from "@/utils/reservasListado";

const ACCION_ETIQUETA = {
  ALTA: "Alta de reserva",
  CANCELACION: "Cancelación de reserva",
  DESHABILITACION: "Deshabilitación por baja de cochera",
};

/** El backend solo incluye este historial en respuestas para administradores. */
export default function HistorialReserva({ historial = [] }) {
  const tieneAlta = historial.some((movimiento) => movimiento.accion === "ALTA");

  return (
    <details className="mt-2 text-xs text-ink/70">
      <summary className="cursor-pointer font-semibold text-link">Historial de la reserva</summary>
      {!tieneAlta && <p className="mt-2">Alta sin autor registrado.</p>}
      {historial.length === 0 ? (
        <p className="mt-1">Sin movimientos registrados.</p>
      ) : (
        <ol className="mt-2 space-y-2" aria-label="Movimientos de la reserva">
          {historial.map((movimiento) => (
            <li key={movimiento.id}>
              <p className="font-medium text-ink">{ACCION_ETIQUETA[movimiento.accion] ?? movimiento.accion}</p>
              <p className="break-words">
                {movimiento.actorNombre} ({movimiento.actorRol === "ADMIN" ? "Administrador" : "Usuario"})
                {" · "}{movimiento.actorEmail}
              </p>
              <time dateTime={movimiento.fecha}>{formatearFechaRegistro(movimiento.fecha)}</time>
            </li>
          ))}
        </ol>
      )}
    </details>
  );
}
