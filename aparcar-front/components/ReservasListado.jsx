"use client";

import { useId, useMemo, useRef, useState } from "react";
import styles from "./ReservasListado.module.css";
import EstadoReservaBadge from "@/components/EstadoReservaBadge";
import HistorialReserva from "@/components/HistorialReserva";
import PatenteVisual from "@/components/PatenteVisual";
import { formatearRango, MODALIDAD_ETIQUETA } from "@/utils/franjaHoraria";
import { formatearPrecio } from "@/utils/tarifas";
import {
  FILTROS_RESERVAS, ORDENES_RESERVAS, formatearFechaRegistro, seleccionarReservas,
} from "@/utils/reservasListado";

const POR_PAGINA = 10;

/** Presentación compartida; solo el modo admin muestra ocupantes y auditoría. */
export default function ReservasListado({ modo = "user", reservas, cargando, error, onReintentar, onCancelar }) {
  const esAdmin = modo === "admin";
  const id = useId();
  const resultadosRef = useRef(null);
  const [filtro, setFiltro] = useState("CONFIRMADA");
  const [orden, setOrden] = useState("INICIO_ASC");
  const [pagina, setPagina] = useState(1);
  const [cancelando, setCancelando] = useState(null);
  const { visibles, cantidades } = useMemo(
    () => seleccionarReservas(reservas, filtro, orden), [reservas, filtro, orden]
  );
  // Una cancelación o recarga puede dejar vacía la última página.
  const paginas = Math.max(1, Math.ceil(visibles.length / POR_PAGINA));
  const paginaActual = Math.min(pagina, paginas);
  // También se corrige la selección: si después llegan nuevas reservas no se
  // debe saltar de vuelta a una página que ya había desaparecido.
  if (pagina > paginas) setPagina(paginas);
  const inicio = (paginaActual - 1) * POR_PAGINA;
  const paginaReservas = visibles.slice(inicio, inicio + POR_PAGINA);
  const hayResultados = !cargando && !error && visibles.length > 0;

  const cambiarFiltro = (clave) => {
    setFiltro(clave);
    setPagina(1);
  };

  const cambiarPagina = (numero) => {
    setPagina(numero);
    resultadosRef.current?.focus({ preventScroll: true });
    resultadosRef.current?.scrollIntoView?.({ block: "start" });
  };

  const cancelar = async (reserva) => {
    if (cancelando !== null) return;
    setCancelando(reserva.id);
    try {
      await onCancelar(reserva);
    } finally {
      setCancelando(null);
    }
  };

  return (
    <section className={styles.listado} aria-labelledby={`${id}-titulo`}>
      <div className={styles.encabezado}>
        <h2 id={`${id}-titulo`} className="text-xl font-bold text-ink">{esAdmin ? "Todas las reservas" : "Mis reservas"}</h2>
        <p>{esAdmin ? "Consultá los próximos ingresos y el historial de cada reserva."
          : "Consultá tus próximas reservas y las que ya finalizaron o se cancelaron."}</p>
      </div>

      <div className="ui-card">
        <div className={styles.controles}>
          <div className={styles.filtros} role="group" aria-label="Filtrar reservas por estado">
            {FILTROS_RESERVAS.map(({ clave, etiqueta }) => (
              <button
                key={clave} type="button" aria-pressed={filtro === clave}
                aria-controls={`${id}-resultados`} onClick={() => cambiarFiltro(clave)}
              >
                {etiqueta}{" "}<span>{cargando || error ? "—" : cantidades[clave]}</span>
              </button>
            ))}
          </div>
          <div className={styles.orden}>
            <div>
              <label className="ui-label" htmlFor={`${id}-orden`}>Ordenar por</label>
              <select id={`${id}-orden`} className="ui-input" value={orden} onChange={(e) => {
                setOrden(e.target.value);
                setPagina(1);
              }}>
                {ORDENES_RESERVAS.map(({ clave, etiqueta }) => <option key={clave} value={clave}>{etiqueta}</option>)}
              </select>
            </div>
            <p className={styles.ayuda}>
              {filtro === "CONFIRMADA" ? "Activas: en curso y próximas reservas."
                : filtro === "CANCELADA" ? "Incluye cancelaciones y deshabilitaciones."
                : filtro === "FINALIZADA" ? "Reservas que ya terminaron."
                : "Todas las reservas, cualquiera sea su estado."}
            </p>
          </div>
        </div>

        <div id={`${id}-resultados`} ref={resultadosRef} tabIndex={-1} aria-busy={cargando}>
          <p className={styles.resumen} role="status">
            {cargando ? "Cargando reservas…" : error ? "No se pudo actualizar el listado."
              : visibles.length === 0 ? "0 reservas"
              : `${inicio + 1}–${Math.min(inicio + POR_PAGINA, visibles.length)} de ${visibles.length} reservas`}
          </p>
          {error ? (
            <div className={styles.vacio} role="alert">
              <p>No se pudieron cargar las reservas.</p>
              <button type="button" className={styles.boton} onClick={onReintentar} disabled={cargando}>Reintentar</button>
            </div>
          ) : cargando ? (
            <div className={styles.vacio} aria-hidden="true">
              <div className="h-6 w-6 animate-spin rounded-full border-4 border-accent border-t-transparent" />
            </div>
          ) : visibles.length === 0 ? (
            <div className={styles.vacio}>
              <p>{reservas.length === 0 ? (esAdmin ? "Todavía no hay reservas cargadas." : "Todavía no tenés reservas.")
                : FILTROS_RESERVAS.find((opcion) => opcion.clave === filtro).vacio}</p>
              {reservas.length > 0 && filtro !== "TODAS" && (
                <button type="button" className={styles.boton} onClick={() => cambiarFiltro("TODAS")}>Ver todas las reservas</button>
              )}
            </div>
          ) : (
            <ul aria-label={esAdmin ? "Reservas del administrador" : "Mis reservas"}>
              {paginaReservas.map((reserva) => (
                <li key={reserva.id} className={styles.item}>
                  <div className={styles.cabecera}>
                    <div>
                      <p className={styles.etiqueta}>{esAdmin ? "Ocupante · Vehículo" : "Vehículo"}</p>
                      <h3>{esAdmin
                        ? <>{reserva.visitante?.nombre ?? "Sin nombre"} — {reserva.vehiculo?.patente ?? "Sin patente"}</>
                        : <PatenteVisual patente={reserva.vehiculo?.patente} tipo={reserva.vehiculo?.tipo} tamano="sm" />}</h3>
                    </div>
                    <EstadoReservaBadge estado={reserva.estado} motivoCancelacion={reserva.motivoCancelacion} esAdmin={esAdmin} />
                  </div>

                  <dl className={styles.datos}>
                    <div>
                      <dt>Horario de la reserva</dt>
                      <dd>{formatearRango(reserva.desde, reserva.hasta, { incluirAnio: true }) || "Sin horario registrado"}</dd>
                      {reserva.modalidad && <dd className="franja-modalidad-badge">{MODALIDAD_ETIQUETA[reserva.modalidad] ?? reserva.modalidad}</dd>}
                    </div>
                    <div>
                      <dt>Cochera</dt>
                      <dd>{reserva.cochera?.numero ?? "—"} <span className={styles.sector}>· {reserva.cochera?.sector ?? "Sin sector"}</span></dd>
                    </div>
                  </dl>

                  <div className={styles.pie}>
                    <div>
                      <p className={styles.importe}>{reserva.precioTotal == null ? "Sin importe registrado" : `Total: ${formatearPrecio(reserva.precioTotal)} ARS`}</p>
                      <p className={styles.alta}>{esAdmin ? "Alta" : "Fecha de alta"}: {formatearFechaRegistro(reserva.fechaCreacion)}</p>
                    </div>
                    {reserva.estado === "CONFIRMADA" && (
                      <button type="button" className={`${styles.boton} ${styles.cancelar}`}
                        disabled={cancelando !== null} onClick={() => cancelar(reserva)}>
                        {cancelando === reserva.id ? "Cancelando…" : "Cancelar"}
                      </button>
                    )}
                  </div>
                  {esAdmin && <div className={styles.historial}><HistorialReserva historial={reserva.historial ?? []} /></div>}
                </li>
              ))}
            </ul>
          )}
        </div>

        {hayResultados && paginas > 1 && (
          <nav className={styles.paginacion} aria-label="Páginas de reservas">
            <button type="button" className={styles.boton} disabled={paginaActual === 1}
              onClick={() => cambiarPagina(paginaActual - 1)}>Anterior</button>
            <span>Página {paginaActual} de {paginas}</span>
            <button type="button" className={styles.boton} disabled={paginaActual === paginas}
              onClick={() => cambiarPagina(paginaActual + 1)}>Siguiente</button>
          </nav>
        )}
      </div>
    </section>
  );
}
