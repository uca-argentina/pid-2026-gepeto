"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import api from "@/app/api";
import DashboardIcon from "@/components/DashboardIcon";
import styles from "./DashboardContent.module.css";

const numero = (valor) => new Intl.NumberFormat("es-AR", { maximumFractionDigits: 1 }).format(valor);
// El servidor entrega fechas y horas del predio. No convertirlas a la zona del navegador.
const horaLocal = (valor) => valor.slice(11, 16);
const fechaLarga = (fecha) => new Intl.DateTimeFormat("es-AR", {
  weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC",
}).format(new Date(`${fecha}T12:00:00Z`));

function Metrica({ titulo, icono, valor, unidad, children, destacada = false }) {
  return (
    <article aria-label={titulo} className={`ui-card ${styles.metric} ${destacada ? styles.featured : ""}`}>
      <div className={styles.metricHeading}><h2>{titulo}</h2><DashboardIcon name={icono} /></div>
      <p className={styles.metricValue}>{valor}<span>{unidad}</span></p>
      <div className={styles.metricDetail}>{children}</div>
    </article>
  );
}

function Actividad({ horas, operacion }) {
  const maximo = Math.max(1, ...horas.flatMap(({ ingresos, egresos }) => [ingresos, egresos]));
  const techo = Math.max(2, Math.ceil(maximo / 2) * 2);
  const hayActividad = operacion.ingresosHoy + operacion.egresosHoy > 0;
  const ancho = 720;
  const izquierda = 36;
  const base = 170;
  const paso = (ancho - izquierda - 8) / 24;

  return (
    <section className={`ui-card ${styles.card}`} aria-labelledby="actividad-titulo">
      <div className={styles.cardHeading}>
        <div><h2 id="actividad-titulo">Actividad del día</h2><p>Ingresos y egresos registrados, hora por hora.</p></div>
        <span className={styles.tag}>HOY</span>
      </div>
      <div className={styles.flowTotals}>
        <div><span className={styles.entryDot} />Ingresos<strong>{numero(operacion.ingresosHoy)}</strong></div>
        <div><span className={styles.exitDot} />Egresos<strong>{numero(operacion.egresosHoy)}</strong></div>
        <Link href="/dashboard-admin/accesos">Control de acceso <span aria-hidden="true">↗</span></Link>
      </div>
      <figure className={styles.chart}>
        <svg viewBox={`0 0 ${ancho} 200`} role="img" aria-label={`Actividad por hora: ${operacion.ingresosHoy} ingresos y ${operacion.egresosHoy} egresos hoy.`}>
          {[0, techo / 2, techo].map((valor) => {
            const y = base - (valor / techo) * 148;
            return <g key={valor}><line x1={izquierda} x2={ancho} y1={y} y2={y} className={styles.gridLine} /><text x={izquierda - 10} y={y + 4} textAnchor="end">{numero(valor)}</text></g>;
          })}
          {horas.map(({ hora, ingresos, egresos }) => (
            <g key={hora}>
              <title>{`${String(hora).padStart(2, "0")}:00 · ${ingresos} ingresos · ${egresos} egresos`}</title>
              <rect x={izquierda + hora * paso + 3} y={base - ingresos / techo * 148} width={8} height={ingresos / techo * 148} rx="2" className={styles.entryBar} />
              <rect x={izquierda + hora * paso + 13} y={base - egresos / techo * 148} width={8} height={egresos / techo * 148} rx="2" className={styles.exitBar} />
              {(hora % 4 === 0 || hora === 23) && <text x={izquierda + hora * paso + 11} y="194" textAnchor="middle">{String(hora).padStart(2, "0")} h</text>}
            </g>
          ))}
        </svg>
        {!hayActividad && <figcaption className={styles.quietNote}>Todavía no se registraron movimientos hoy.</figcaption>}
      </figure>
      <details className={styles.chartDetails}>
        <summary>Ver detalle por hora</summary>
        <div className={styles.tableScroll}>
          <table>
            <caption className={styles.srOnly}>Movimientos registrados por hora</caption>
            <thead><tr><th scope="col">Hora</th><th scope="col">Ingresos</th><th scope="col">Egresos</th></tr></thead>
            <tbody>{horas.map(({ hora, ingresos, egresos }) => <tr key={hora}><th scope="row">{String(hora).padStart(2, "0")}:00</th><td>{numero(ingresos)}</td><td>{numero(egresos)}</td></tr>)}</tbody>
          </table>
        </div>
      </details>
    </section>
  );
}

function Sectores({ sectores }) {
  const [todos, setTodos] = useState(false);
  const maximo = Math.max(1, ...sectores.map((sector) => sector.ingresosHoy));
  const visibles = todos ? sectores : sectores.slice(0, 5);

  return (
    <section className={`ui-card ${styles.card}`} aria-labelledby="sectores-titulo">
      <div className={styles.cardHeading}><div><h2 id="sectores-titulo">Sectores más utilizados</h2><p>Ordenados por ingresos de hoy.</p></div><DashboardIcon name="parking" /></div>
      {sectores.length === 0 ? <div className={styles.empty}><p>Aún no hay sectores para mostrar.</p><Link href="/dashboard-admin/cocheras">Gestionar cocheras →</Link></div> : (
        <ol className={styles.sectors}>
          {visibles.map((sector, indice) => (
            <li key={sector.sector}>
              <div className={styles.sectorHeading}><span className={styles.rank}>{String(indice + 1).padStart(2, "0")}</span><h3>{sector.sector}</h3><strong>{numero(sector.ingresosHoy)} <span>ingresos</span></strong></div>
              <div className={styles.sectorTrack} aria-hidden="true"><span style={{ width: `${sector.ingresosHoy / maximo * 100}%` }} /></div>
              <p>{numero(sector.ocupadas)} ocupadas ahora · {numero(sector.libres)} libres{sector.ocupadasFueraServicio > 0 && <span className={styles.sectorWarning}> · {numero(sector.ocupadasFueraServicio)} ocupadas fuera de servicio</span>}</p>
            </li>
          ))}
        </ol>
      )}
      {sectores.length > 5 && <button className={styles.textButton} type="button" aria-expanded={todos} onClick={() => setTodos(!todos)}>{todos ? "Mostrar menos" : `Ver todos los sectores (${sectores.length})`}</button>}
    </section>
  );
}

function EstadoReservas({ reservas }) {
  const estados = [
    { clave: "pendientes", nombre: "Por ingresar", color: "var(--color-accent)" },
    { clave: "enCurso", nombre: "En curso", color: "var(--dash-teal)" },
    { clave: "finalizadas", nombre: "Finalizadas", color: "var(--dash-complete)" },
    { clave: "noPresentados", nombre: "No presentados", color: "var(--dash-amber)" },
    { clave: "canceladas", nombre: "Canceladas", color: "var(--dash-cancelled)" },
  ];
  return (
    <section className={`ui-card ${styles.card}`} aria-labelledby="reservas-titulo">
      <div className={styles.cardHeading}><div><h2 id="reservas-titulo">Estado de las reservas</h2><p>Reservas cuyo inicio está previsto para hoy.</p></div><Link href="/dashboard-admin/reservas" className={styles.smallLink}>Ver reservas ↗</Link></div>
      <div className={styles.reservationTotal}><strong>{numero(reservas.total)}</strong><span>reservas del día</span></div>
      <div className={styles.stackedBar} aria-hidden="true">
        {estados.map(({ clave, color }) => reservas[clave] > 0 && <span key={clave} style={{ width: `${reservas[clave] / reservas.total * 100}%`, background: color }} />)}
      </div>
      {reservas.total === 0 && <p className={styles.quietNote}>No hay reservas con inicio para hoy.</p>}
      <dl className={styles.states}>{estados.map(({ clave, nombre, color }) => <div key={clave}><dt><i style={{ background: color }} />{nombre}</dt><dd>{numero(reservas[clave])}</dd></div>)}</dl>
    </section>
  );
}

function ProximasLlegadas({ reservas, total, fecha }) {
  return (
    <section className={`ui-card ${styles.card}`} aria-labelledby="llegadas-titulo">
      <div className={styles.cardHeading}><div><h2 id="llegadas-titulo">Próximas llegadas</h2><p>Reservas pendientes que comienzan en las próximas 2 horas.</p></div><span className={styles.count}>{numero(total)}</span></div>
      {reservas.length === 0 ? <div className={styles.empty}><DashboardIcon name="calendar" /><p>Sin llegadas previstas en las próximas 2 horas.</p><span>Los próximos ingresos aparecerán acá.</span></div> : (
        <ul className={styles.arrivals}>{reservas.map((reserva) => <li key={reserva.id}>
          <div className={styles.arrivalTime}><time dateTime={reserva.desde}>{horaLocal(reserva.desde)}</time><span>{reserva.desde.slice(0, 10) === fecha ? "Hoy" : "Mañana"}</span></div>
          <div className={styles.arrivalSpot}><strong>Cochera {reserva.cocheraNumero}</strong><span>{reserva.sector}</span></div>
          <span className={styles.arrivalStatus}>Por ingresar</span>
        </li>)}</ul>
      )}
      {total > reservas.length && <p className={styles.quietNote}>Mostrando las primeras {reservas.length} de {numero(total)} llegadas.</p>}
    </section>
  );
}

export default function DashboardContent() {
  const [datos, setDatos] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(false);
  const solicitud = useRef(null);

  const cargar = useCallback(async () => {
    if (solicitud.current) return;
    const controller = new AbortController();
    solicitud.current = controller;
    setCargando(true);
    try {
      const respuesta = await api.get("/api/v1/dashboard", { signal: controller.signal, timeout: 15000 });
      if (!controller.signal.aborted) {
        setDatos(respuesta.data);
        setError(false);
      }
    } catch {
      if (!controller.signal.aborted) setError(true);
    } finally {
      if (solicitud.current === controller) {
        solicitud.current = null;
        setCargando(false);
      }
    }
  }, []);

  useEffect(() => {
    cargar();
    const actualizarSiVisible = () => { if (document.visibilityState === "visible") cargar(); };
    const intervalo = setInterval(actualizarSiVisible, 60000);
    document.addEventListener("visibilitychange", actualizarSiVisible);
    return () => {
      clearInterval(intervalo);
      document.removeEventListener("visibilitychange", actualizarSiVisible);
      solicitud.current?.abort();
      solicitud.current = null;
    };
  }, [cargar]);

  return (
    <main className={styles.dashboard}>
      <div className={styles.heading}>
        <div><p className="eyebrow">PANORAMA DEL ESTACIONAMIENTO</p><h1>Dashboard</h1><p className={styles.intro}>Todo lo que necesitás para seguir la operación de hoy.</p></div>
        <div className={styles.refreshGroup}>
          <button type="button" className={styles.refreshButton} disabled={cargando} onClick={cargar}><DashboardIcon name="refresh" />{cargando ? "Actualizando…" : "Actualizar"}</button>
          <span className={styles.updated} role="status">{datos ? `${error ? "Última actualización" : "Actualizado"} ${horaLocal(datos.actualizadoAt)} · cada 60 s` : "Actualización automática cada 60 s"}</span>
        </div>
      </div>

      {error && <div role="alert" className={styles.error}><p>{datos ? "No se pudo actualizar. Mostramos los últimos datos disponibles." : "No se pudo cargar el dashboard."}</p><button type="button" disabled={cargando} onClick={cargar}>Reintentar</button></div>}
      {!datos && !error && <div className={`ui-card ${styles.loading}`} role="status"><DashboardIcon name="dashboard" /><p>Cargando dashboard…</p></div>}

      {datos && <>
        <div className={styles.dateLine}><span className={styles.date}><DashboardIcon name="calendar" />{fechaLarga(datos.fecha)}</span><span>Hora del estacionamiento · {datos.zonaHoraria.split("/").at(-1).replaceAll("_", " ")}</span></div>
        <div className={styles.metrics}>
          <Metrica titulo="Ocupación actual" icono="parking" valor={datos.ocupacion.habilitadas ? numero(datos.ocupacion.porcentaje) : "—"} unidad={datos.ocupacion.habilitadas ? "%" : ""} destacada>
            <div className={styles.occupancyTrack} aria-hidden="true"><span style={{ width: `${Math.min(100, datos.ocupacion.porcentaje)}%` }} /></div>
            <p><strong>{numero(datos.ocupacion.ocupadasHabilitadas)}</strong> de {numero(datos.ocupacion.habilitadas)} habilitadas · <strong>{numero(datos.ocupacion.libres)} libres</strong></p>
          </Metrica>
          <Metrica titulo="Reservas del día" icono="calendar" valor={numero(datos.reservasHoy.total)}><p><strong>{numero(datos.reservasHoy.pendientes)}</strong> por ingresar hoy</p><span>Incluye canceladas y no presentados.</span></Metrica>
          <Metrica titulo="No presentados" icono="users" valor={numero(datos.reservasHoy.noPresentados)}><p>Sin ingreso al vencer la tolerancia.</p><span>Sobre reservas que comienzan hoy.</span></Metrica>
          <Metrica titulo="Estadías excedidas" icono="clock" valor={numero(datos.operacion.permanenciasExcedidas)}><p>Vehículos adentro fuera de horario.</p><span>Contempla la gracia de egreso.</span></Metrica>
        </div>
        <div className={styles.capacityLine}><span><strong>{numero(datos.ocupacion.ocupadas)}</strong> vehículos en el predio</span><span><strong>{numero(datos.ocupacion.total)}</strong> cocheras totales</span><span><strong>{numero(datos.ocupacion.fueraServicio)}</strong> fuera de servicio</span><Link href="/dashboard-admin">Ver ocupación por cochera ↗</Link></div>
        {datos.ocupacion.ocupadasFueraServicio > 0 && <p className={styles.warning} role="note">Hay {numero(datos.ocupacion.ocupadasFueraServicio)} vehículos en cocheras fuera de servicio. Siguen contando como presentes hasta registrar su egreso.</p>}
        <div className={styles.topGrid}><Actividad horas={datos.actividadPorHora} operacion={datos.operacion} /><Sectores sectores={datos.sectores} /></div>
        <div className={styles.bottomGrid}><EstadoReservas reservas={datos.reservasHoy} /><ProximasLlegadas reservas={datos.proximasReservas} total={datos.operacion.proximosIngresos} fecha={datos.fecha} /></div>
        <p className={styles.footnote}>La ocupación se basa en ingresos sin egreso registrado, incluso de días anteriores. Los lugares libres indican disponibilidad física; pueden tener reservas asignadas.</p>
      </>}
    </main>
  );
}
