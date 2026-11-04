"use client";

import { useEffect, useState } from "react";

import api from "@/app/api";
import PuntosFaltas from "@/components/PuntosFaltas";
import { formatearFechaRegistro } from "@/utils/reservasListado";

/** Cómo se nombra cada origen en el historial. */
const ORIGEN_ETIQUETA = {
  MANUAL: "Aplicada por el personal",
  SIN_INGRESO: "No se registró el ingreso",
  SIN_EGRESO: "No se registró el egreso",
};

/**
 * Las faltas de quien está mirando su propio perfil.
 *
 * Muestra dos cosas que se confunden fácil y no son lo mismo:
 *
 *   - Arriba, las faltas que **cuentan hoy** (últimos 3 meses). Son las que
 *     pueden terminar bloqueando la cuenta, y por eso van como puntitos: se
 *     entiende de un vistazo cuánto margen queda.
 *   - Abajo, el **historial completo**, que no vence nunca. Es el desplegable,
 *     cerrado por omisión: es información de consulta, no algo que haga falta
 *     ver cada vez que se entra al perfil.
 *
 * El texto de cada falta es el que escribió el personal, tal cual. Que la
 * persona sancionada lea el motivo es justamente el punto de guardarlo.
 */
export default function MisAmonestaciones() {
  const [resumen, setResumen] = useState(null);
  const [error, setError] = useState(false);
  const [abierto, setAbierto] = useState(false);

  useEffect(() => {
    let vigente = true;
    api
      .get("/api/v1/amonestaciones/mias")
      .then((res) => vigente && setResumen(res.data))
      .catch(() => vigente && setError(true));
    return () => {
      vigente = false;
    };
  }, []);

  if (error) {
    return (
      <section className="ui-card profile-form" aria-labelledby="mis-faltas-titulo">
        <h2 id="mis-faltas-titulo">Faltas</h2>
        <p className="text-sm text-ink/60">No se pudieron cargar tus faltas.</p>
      </section>
    );
  }

  if (!resumen || Array.isArray(resumen)) return null;

  // Valores por defecto a propósito: esta sección vive dentro del perfil, y
  // una respuesta con otra forma no tiene por qué tirar abajo la pantalla
  // entera. Sin esto, `historial.length` rompe todo el perfil.
  const {
    vigentes = 0,
    maxFaltas = 3,
    bloqueado = false,
    bloqueadoHasta = null,
    mesesVentana = 3,
    historial = [],
  } = resumen ?? {};

  const ventana = mesesVentana === 1 ? "el último mes" : `los últimos ${mesesVentana} meses`;

  return (
    <section className="ui-card profile-form" aria-labelledby="mis-faltas-titulo">
      <h2 id="mis-faltas-titulo">Faltas</h2>

      {bloqueado ? (
        <div className="falta-bloqueo" role="alert">
          <p><strong>Tu cuenta está inhabilitada para reservar.</strong></p>
          <p>
            Se restituye sola el{" "}
            <strong>{formatearFechaRegistro(bloqueadoHasta)}</strong>. Mientras tanto
            podés seguir entrando y consultando tus datos.
          </p>
        </div>
      ) : (
        <p className="text-sm text-ink/60">
          Tenés <strong>{vigentes}</strong> de {maxFaltas}{" "}
          {maxFaltas === 1 ? "falta permitida" : "faltas permitidas"} en {ventana}.
          {vigentes === 0 && " Ninguna por ahora."}
        </p>
      )}

      <PuntosFaltas cantidad={vigentes} maximo={maxFaltas} />

      <details className="falta-historial" open={abierto}
        onToggle={(e) => setAbierto(e.currentTarget.open)}>
        <summary>Historial de incidencias ({historial.length})</summary>

        {historial.length === 0 ? (
          <p className="text-sm text-ink/60">No tenés ninguna incidencia registrada.</p>
        ) : (
          <ul className="falta-lista">
            {historial.map((falta) => (
              <li key={falta.id} className={falta.vigente ? "" : "falta-vencida"}>
                <p className="falta-motivo">{falta.motivo}</p>
                <p className="falta-detalle">
                  {formatearFechaRegistro(falta.fecha)}
                  {falta.patente && ` · ${falta.patente}`}
                  {" · "}
                  {ORIGEN_ETIQUETA[falta.origen] ?? falta.origen}
                  {falta.autorNombre && ` · ${falta.autorNombre}`}
                  {/* Una falta vieja sigue en el historial pero ya no suma
                      para el bloqueo: decirlo evita que alguien cuente las de
                      la lista y no entienda por qué no coinciden. */}
                  {!falta.vigente && " · ya no cuenta para el bloqueo"}
                </p>
              </li>
            ))}
          </ul>
        )}
      </details>
    </section>
  );
}
