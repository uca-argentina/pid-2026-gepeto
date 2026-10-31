"use client";

import { useEffect, useState } from "react";
import api from "@/app/api";
import DashboardIcon from "@/components/DashboardIcon";

const numero = (valor) => new Intl.NumberFormat("es-AR").format(valor ?? 0);

/**
 * El programa de puntos de un vistazo: lo emitido, lo canjeado y las rachas.
 * Va aparte del dashboard operativo: no cambia minuto a minuto, así que se
 * pide una vez al entrar.
 */
export default function ResumenPuntos() {
  const [resumen, setResumen] = useState(null);
  const [error, setError] = useState(false);
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    let vigente = true;
    const consultar = async () => {
      try {
        const { data } = await api.get("/api/v1/puntos/resumen");
        if (!vigente) return;
        setResumen(data);
        setError(false);
      } catch {
        if (vigente) setError(true);
      }
    };
    consultar();
    return () => { vigente = false; };
  }, [intento]);

  const cargar = () => setIntento((valor) => valor + 1);

  return (
    <section className="ui-card puntos-seccion puntos-admin" aria-labelledby="puntos-resumen-titulo">
      <div className="puntos-admin-encabezado">
        <div>
          <h2 id="puntos-resumen-titulo">Programa de puntos</h2>
          <p className="puntos-nota">Desde que empezó. Los canjes devueltos por cancelación no cuentan como canjeados.</p>
        </div>
        <DashboardIcon name="estrella" />
      </div>
      {error && !resumen ? (
        <div className="puntos-aviso" role="alert">
          <p>No se pudo cargar el resumen de puntos.</p>
          <button type="button" className="profile-secondary" onClick={cargar}>Reintentar</button>
        </div>
      ) : !resumen ? (
        <p role="status" className="puntos-nota">Cargando…</p>
      ) : (
        <dl className="puntos-admin-datos">
          <div><dt>Emitidos</dt><dd>{numero(resumen.emitidos)}</dd></div>
          <div><dt>Canjeados</dt><dd>{numero(resumen.canjeados)}</dd></div>
          <div><dt>En circulación</dt><dd>{numero(resumen.enCirculacion)}</dd></div>
          <div><dt>Usuarios con racha activa</dt><dd>{numero(resumen.usuariosConRacha)}</dd></div>
          <div><dt>Mejor racha</dt><dd>{numero(resumen.mejorRacha)} <span>{resumen.mejorRacha === 1 ? "día" : "días"}</span></dd></div>
        </dl>
      )}
    </section>
  );
}
