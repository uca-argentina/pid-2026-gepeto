"use client";

import {
  MINUTOS_JORNADA_COMPLETA,
  MINUTOS_MEDIA_JORNADA,
  MODALIDADES,
  minutosEntre,
  sumarMinutosLocal,
} from "@/utils/franjaHoraria";

/**
 * Atajos para reservar media jornada o jornada completa.
 *
 * Dejan el fin en 12 h o 24 h después del inicio, que son exactamente los
 * umbrales con los que el backend clasifica la modalidad: al usar un atajo, la
 * reserva queda etiquetada como esa jornada, no cerca de ella.
 *
 * No hay un tercer botón de "por franja horaria" porque esa es la forma de
 * reservar por defecto: se escriben los horarios y listo.
 */

const ATAJOS = [
  { minutos: MINUTOS_MEDIA_JORNADA, ...MODALIDADES.MEDIA_JORNADA, detalle: "12 h" },
  { minutos: MINUTOS_JORNADA_COMPLETA, ...MODALIDADES.JORNADA_COMPLETA, detalle: "24 h" },
];

export default function AtajosJornada({ desde, hasta, onChange, idPrefijo = "atajo" }) {
  const duracion = desde && hasta ? minutosEntre(desde, hasta) : 0;

  return (
    <div className="atajos-jornada">
      <span className="atajos-jornada-titulo">Atajos</span>
      <div className="atajos-jornada-botones">
        {ATAJOS.map(({ minutos, clave, etiqueta, detalle }) => {
          const activo = duracion === minutos;
          return (
            <button
              key={clave}
              type="button"
              id={`${idPrefijo}-${clave.toLowerCase()}`}
              onClick={() => onChange(sumarMinutosLocal(desde, minutos))}
              aria-pressed={activo}
              disabled={!desde}
              className={`atajos-jornada-chip ${activo ? "is-activa" : ""}`}
            >
              {etiqueta}
              <span className="atajos-jornada-detalle">{detalle}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
