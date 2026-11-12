"use client";

import { useEffect, useMemo, useState } from "react";
import api from "@/app/api";
import EstadoReservaBadge from "@/components/EstadoReservaBadge";
import PatenteVisual from "@/components/PatenteVisual";
import { useAutoRefresco } from "@/hooks/useAutoRefresco";
import { formatearRango } from "@/utils/franjaHoraria";
import { normalizarPatente } from "@/utils/patenteValidation";

const MINIMO_CARACTERES = 3;


const situacion = (reserva, ahora) => {
  if (reserva.estado === "EN_CURSO") return "AHORA";
  if (reserva.estado !== "CONFIRMADA") return "NO_HABILITA";
  const desde = new Date(reserva.desde).getTime();
  const hasta = new Date(reserva.hasta).getTime();
  if (desde <= ahora && ahora < hasta) return "AHORA";
  if (ahora < desde) return "PROXIMA";
  return "NO_HABILITA";
};

const ORDEN_SITUACION = { AHORA: 0, PROXIMA: 1, NO_HABILITA: 2 };

const SITUACION_TEXTO = {
  AHORA: "Habilitada ahora",
  PROXIMA: "Habilitada (próxima)",
  NO_HABILITA: "No habilitada",
};

const SITUACION_ESTILO = {
  AHORA: "bg-emerald-500/10 text-emerald-800 ring-emerald-600/30 dark:text-emerald-300 dark:ring-emerald-400/40",
  PROXIMA: "bg-accent/10 text-link ring-accent/30",
  NO_HABILITA: "bg-ink/5 text-ink/50 ring-ink/10",
};

const textoCochera = (reserva) =>
  reserva.cochera
    ? `${reserva.cochera.numero}${reserva.cochera.sector ? ` · ${reserva.cochera.sector}` : ""}`
    : reserva.estado === "LISTA_ESPERA" ? "Pendiente de asignación" : "Sin cochera asignada";

function Resumen({ reservas }) {
  const vigente = reservas.find((r) => r.situacion === "AHORA");
  const proxima = reservas.find((r) => r.situacion === "PROXIMA");

  if (vigente) {
    return (
      <p className="text-sm text-ink">
        <strong>Tiene una reserva habilitada ahora</strong> en la cochera{" "}
        <strong>{textoCochera(vigente)}</strong>, {formatearRango(vigente.desde, vigente.hasta)}.
      </p>
    );
  }
  if (proxima) {
    return (
      <p className="text-sm text-ink">
        <strong>No tiene una reserva para este momento.</strong> La próxima es en la cochera{" "}
        <strong>{textoCochera(proxima)}</strong>, {formatearRango(proxima.desde, proxima.hasta, { incluirAnio: true })}.
      </p>
    );
  }
  return (
    <p className="text-sm text-ink">
      <strong>No tiene reservas habilitadas</strong>, ni ahora ni más adelante.
    </p>
  );
}

export default function BuscarReservaPatente() {
  const [reservas, setReservas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(false);
  const [busqueda, setBusqueda] = useState("");
  const [verHistorial, setVerHistorial] = useState({});

  
  const cargar = async ({ silencioso = false } = {}) => {
    if (!silencioso) {
      setCargando(true);
      setError(false);
    }
    try {
      const res = await api.get("/api/v1/reservas");
      setReservas(Array.isArray(res.data) ? res.data : []);
      setError(false);
    } catch {
      if (!silencioso) setError(true);
    } finally {
      if (!silencioso) setCargando(false);
    }
  };

  useEffect(() => {
    cargar();
  }, []);

  useAutoRefresco(() => cargar({ silencioso: true }));

  const consulta = normalizarPatente(busqueda);
  const buscando = consulta.length >= MINIMO_CARACTERES;

  
  const grupos = useMemo(() => {
    if (!buscando) return [];
    const ahora = Date.now();
    const porPatente = new Map();
    for (const reserva of reservas) {
      const patente = normalizarPatente(reserva.vehiculo?.patente);
      if (!patente.includes(consulta)) continue;
      if (!porPatente.has(patente)) {
        porPatente.set(patente, { patente, vehiculo: reserva.vehiculo, visitante: reserva.visitante, reservas: [] });
      }
      porPatente.get(patente).reservas.push({ ...reserva, situacion: situacion(reserva, ahora) });
    }
    for (const grupo of porPatente.values()) {
      grupo.reservas.sort((a, b) => {
        const porSituacion = ORDEN_SITUACION[a.situacion] - ORDEN_SITUACION[b.situacion];
        if (porSituacion !== 0) return porSituacion;
        const diferencia = new Date(a.desde) - new Date(b.desde);
        return a.situacion === "NO_HABILITA" ? -diferencia : diferencia;
      });
    }
    
    return [...porPatente.values()].sort(
      (a, b) => (b.patente === consulta) - (a.patente === consulta) || a.patente.localeCompare(b.patente)
    );
  }, [reservas, consulta, buscando]);

  return (
    <section className="mx-auto max-w-3xl space-y-6" aria-labelledby="buscar-patente-titulo">
      <div>
        <h1 id="buscar-patente-titulo" className="text-3xl font-extrabold tracking-tight text-ink mb-2">
          Buscar reservas por patente
        </h1>
        <p className="text-sm text-ink/60">
          Ingresá la patente, completa o una parte, para ver si el vehículo tiene una reserva habilitada, en qué cochera y en qué franja horaria.
        </p>
      </div>

      <div className="ui-card p-6">
        <label className="ui-label" htmlFor="buscar-patente">Patente</label>
        <input
          id="buscar-patente"
          type="search"
          className="ui-input uppercase"
          placeholder="Ej: AB123CD"
          autoComplete="off"
          autoFocus
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />
        <p className="mt-2 text-xs text-ink/50">
          Se ignoran espacios y guiones. Escribí al menos {MINIMO_CARACTERES} caracteres.
        </p>
      </div>

      <div role="status" aria-live="polite" aria-busy={cargando}>
        {error ? (
          <div className="ui-card p-6 text-sm" role="alert">
            <p className="text-ink">No se pudieron cargar las reservas.</p>
            <button type="button" className="mt-3 font-semibold text-link hover:underline" onClick={() => cargar()}>
              Reintentar
            </button>
          </div>
        ) : cargando ? (
          <p className="text-sm text-ink/60">Cargando reservas…</p>
        ) : !buscando ? null : grupos.length === 0 ? (
          <div className="ui-card p-6 text-sm text-ink/70">
            No hay reservas para ninguna patente que contenga <strong>{consulta}</strong>.
          </div>
        ) : (
          <ul className="space-y-6" aria-label="Resultados de la búsqueda">
            {grupos.map((grupo) => {
              const habilitantes = grupo.reservas.filter((r) => r.situacion !== "NO_HABILITA");
              const historial = grupo.reservas.filter((r) => r.situacion === "NO_HABILITA");
              const abierto = Boolean(verHistorial[grupo.patente]);
              const visibles = abierto ? grupo.reservas : habilitantes;
              return (
                <li key={grupo.patente} className="ui-card p-6 space-y-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <PatenteVisual patente={grupo.vehiculo?.patente ?? grupo.patente} tipo={grupo.vehiculo?.tipo} tamano="sm" />
                    <p className="text-sm text-ink/70">{grupo.visitante?.nombre ?? "Sin nombre"}</p>
                  </div>

                  <Resumen reservas={grupo.reservas} />

                  {visibles.length > 0 && (
                    <div className="overflow-x-auto">
                      <table className="min-w-full text-sm">
                        <thead>
                          <tr className="text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                            <th scope="col" className="py-2 pr-4">Situación</th>
                            <th scope="col" className="py-2 pr-4">Cochera</th>
                            <th scope="col" className="py-2 pr-4">Franja horaria</th>
                            <th scope="col" className="py-2">Estado</th>
                          </tr>
                        </thead>
                        <tbody>
                          {visibles.map((reserva) => (
                            <tr key={reserva.id} className="border-t border-ink/10 align-top">
                              <td className="py-2 pr-4 whitespace-nowrap">
                                <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${SITUACION_ESTILO[reserva.situacion]}`}>
                                  {SITUACION_TEXTO[reserva.situacion]}
                                </span>
                              </td>
                              <td className="py-2 pr-4 text-ink">{textoCochera(reserva)}</td>
                              <td className="py-2 pr-4 text-ink">
                                {formatearRango(reserva.desde, reserva.hasta, { incluirAnio: true }) || "Sin horario registrado"}
                              </td>
                              <td className="py-2">
                                <EstadoReservaBadge estado={reserva.estado} motivoCancelacion={reserva.motivoCancelacion} esAdmin />
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}

                  {historial.length > 0 && (
                    <button
                      type="button"
                      className="text-sm font-semibold text-link hover:underline"
                      aria-expanded={abierto}
                      onClick={() => setVerHistorial((prev) => ({ ...prev, [grupo.patente]: !abierto }))}
                    >
                      {abierto ? "Ocultar historial" : `Ver historial (${historial.length})`}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}
