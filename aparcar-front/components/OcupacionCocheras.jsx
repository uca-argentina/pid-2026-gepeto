"use client";

import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";

import api from "@/app/api";
import {
  agruparOcupacion,
  diaEnPalabras,
  diaRelativo,
  etiquetaDeGrupo,
  hoy,
  sumarDias,
} from "@/utils/ocupacion";

/**
 * Panel de ocupación de cocheras para el admin.
 *
 * Dos formas de mirar lo mismo: por tipo de cochera o por piso. Cada grupo es
 * un desplegable y, al abrirlo, muestra al costado qué hay ocupado ahí ese día,
 * con patente, horario, modalidad y el mail de quien reservó.
 *
 * Los grupos no están escritos acá: salen de las cocheras que devuelve el
 * backend, así que un piso nuevo o un tipo nuevo aparecen solos.
 */

const PESTANAS = [
  { clave: "tipo", etiqueta: "Por tipo de cochera" },
  { clave: "sector", etiqueta: "Por piso" },
];

export default function OcupacionCocheras({ refreshKey = 0 }) {
  const [dia, setDia] = useState(hoy);
  const [agrupadoPor, setAgrupadoPor] = useState("tipo");
  const [abierto, setAbierto] = useState(null);
  const [cocheras, setCocheras] = useState([]);
  const [reservas, setReservas] = useState([]);
  const [cargando, setCargando] = useState(true);

  // Las dos pestañas y todos los días se calculan sobre los mismos datos, así
  // que se piden una sola vez: cambiar de pestaña o de día no vuelve a la red.
  useEffect(() => {
    let vigente = true;
    setCargando(true);

    Promise.all([api.get("/api/v1/cocheras"), api.get("/api/v1/reservas")])
      .then(([c, r]) => {
        if (!vigente) return;
        setCocheras(c.data);
        setReservas(r.data);
      })
      .catch(() => {
        if (vigente) toast.error("No se pudo cargar la ocupación de cocheras.");
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });

    return () => {
      vigente = false;
    };
  }, [refreshKey]);

  const grupos = useMemo(
    () => agruparOcupacion(cocheras, reservas, dia, agrupadoPor),
    [cocheras, reservas, dia, agrupadoPor]
  );

  // Al cambiar de pestaña las claves son otras (AUTO vs "Planta Baja"), así que
  // lo que estaba abierto ya no existe.
  const cambiarPestana = (clave) => {
    setAgrupadoPor(clave);
    setAbierto(null);
  };

  const grupoAbierto = grupos.find((g) => g.clave === abierto) ?? null;
  const relativo = diaRelativo(dia);
  const totalOcupadas = grupos.reduce((n, g) => n + g.ocupadas, 0);
  const totalCocheras = grupos.reduce((n, g) => n + g.total, 0);

  return (
    <section className="ocupacion" aria-label="Ocupación de cocheras">
      {/* El día que se está mirando, y cómo moverse entre días. */}
      <div className="ocupacion-banner">
        <button
          type="button"
          className="ocupacion-nav"
          onClick={() => setDia(sumarDias(dia, -1))}
          aria-label="Día anterior"
        >
          ←
        </button>

        <div className="ocupacion-fecha">
          <p className="ocupacion-fecha-dia">
            {relativo && <span className="ocupacion-fecha-relativo">{relativo}</span>}
            <span className="ocupacion-fecha-texto">{diaEnPalabras(dia)}</span>
          </p>
          <p className="ocupacion-fecha-resumen">
            {cargando
              ? "Cargando…"
              : `${totalOcupadas} de ${totalCocheras} cocheras ocupadas`}
          </p>
        </div>

        <div className="ocupacion-banner-acciones">
          {/* Solo aparece cuando sirve para algo: si ya estás en hoy, no. */}
          {relativo !== "Hoy" && (
            <button type="button" className="ocupacion-hoy" onClick={() => setDia(hoy())}>
              Hoy
            </button>
          )}
          <button
            type="button"
            className="ocupacion-nav"
            onClick={() => setDia(sumarDias(dia, 1))}
            aria-label="Día siguiente"
          >
            →
          </button>
        </div>
      </div>

      <div className="ocupacion-tabs" role="tablist" aria-label="Cómo agrupar las cocheras">
        {PESTANAS.map(({ clave, etiqueta }) => (
          <button
            key={clave}
            type="button"
            role="tab"
            id={`ocupacion-tab-${clave}`}
            aria-selected={agrupadoPor === clave}
            aria-controls="ocupacion-grupos"
            className={`ocupacion-tab ${agrupadoPor === clave ? "is-activa" : ""}`}
            onClick={() => cambiarPestana(clave)}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      <div className="ocupacion-cuerpo">
        {/* Los desplegables, pegados uno abajo del otro. */}
        <div
          className="ocupacion-grupos"
          id="ocupacion-grupos"
          role="tabpanel"
          aria-labelledby={`ocupacion-tab-${agrupadoPor}`}
        >
          {grupos.length === 0 && !cargando && (
            <p className="ocupacion-vacio">Todavía no hay cocheras cargadas.</p>
          )}

          {grupos.map((g) => {
            const activo = abierto === g.clave;
            return (
              <button
                key={g.clave}
                type="button"
                className={`ocupacion-grupo ${activo ? "is-abierto" : ""}`}
                aria-expanded={activo}
                onClick={() => setAbierto(activo ? null : g.clave)}
              >
                <span className="ocupacion-grupo-nombre">
                  {etiquetaDeGrupo(g.clave, agrupadoPor)}
                </span>
                <span
                  className={`ocupacion-grupo-contador ${g.ocupadas > 0 ? "is-ocupada" : ""}`}
                >
                  {g.ocupadas}/{g.total}
                </span>
                <span className="ocupacion-grupo-flecha" aria-hidden="true">
                  ›
                </span>
              </button>
            );
          })}
        </div>

        {/* El detalle, al costado en pantalla ancha y abajo en angosta. */}
        <div className="ocupacion-detalle">
          {!grupoAbierto ? (
            <p className="ocupacion-detalle-ayuda">
              Elegí {agrupadoPor === "tipo" ? "un tipo de cochera" : "un piso"} para ver qué
              hay ocupado.
            </p>
          ) : grupoAbierto.ocupaciones.length === 0 ? (
            <p className="ocupacion-detalle-ayuda">
              No hay nada ocupado en {etiquetaDeGrupo(grupoAbierto.clave, agrupadoPor)} este
              día.
            </p>
          ) : (
            <>
              <h3 className="ocupacion-detalle-titulo">
                {etiquetaDeGrupo(grupoAbierto.clave, agrupadoPor)}
                <span className="ocupacion-detalle-cuenta">
                  {grupoAbierto.ocupaciones.length}{" "}
                  {grupoAbierto.ocupaciones.length === 1 ? "reserva" : "reservas"}
                </span>
              </h3>

              <ul className="ocupacion-lista">
                {grupoAbierto.ocupaciones.map((o) => (
                  <li key={o.id} className="ocupacion-item">
                    <div className="ocupacion-item-cabecera">
                      <span className="ocupacion-item-cochera">{o.cochera}</span>
                      <span className="ocupacion-item-patente">{o.patente}</span>
                      <span className="ocupacion-item-modalidad">{o.modalidad}</span>
                    </div>
                    <div className="ocupacion-item-datos">
                      {/* El title trae la franja completa: en el renglón se
                          muestra solo la parte que cae en este día. */}
                      <span
                        className={`ocupacion-item-horario ${o.horario.cruzaElDia ? "cruza" : ""}`}
                        title={o.horario.completo}
                      >
                        {o.horario.texto}
                      </span>
                      <span className="ocupacion-item-email">{o.email}</span>
                    </div>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
