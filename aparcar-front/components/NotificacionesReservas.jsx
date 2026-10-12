"use client";

import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import api from "@/app/api";
import { formatearRango } from "@/utils/franjaHoraria";
import { tieneCodigoVigente } from "@/utils/codigoAcceso";
import EstadoReservaBadge from "@/components/EstadoReservaBadge";

/** Avisos persistidos en el servidor: sobreviven a salir de la cuenta o cambiar de dispositivo. */
export default function NotificacionesReservas({ onActualizadas, onVerCodigo }) {
  const [notificaciones, setNotificaciones] = useState([]);
  const [leyendo, setLeyendo] = useState(null);
  const callback = useRef(onActualizadas);
  const leidas = useRef(new Set());
  useEffect(() => { callback.current = onActualizadas; }, [onActualizadas]);

  useEffect(() => {
    let vigente = true;
    let consultando = false;
    let anteriores = new Set();
    const consultar = async () => {
      if (consultando) return;
      consultando = true;
      try {
        const { data } = await api.get("/api/v1/reservas/notificaciones");
        if (!vigente) return;
        const pendientes = data.filter((reserva) => !leidas.current.has(reserva.id));
        setNotificaciones(pendientes);
        if (pendientes.some((reserva) => !anteriores.has(reserva.id))) callback.current?.();
        anteriores = new Set(pendientes.map((reserva) => reserva.id));
      } catch {
        // La próxima consulta reintenta; un corte de conexión conserva los avisos ya recibidos.
      } finally {
        consultando = false;
      }
    };
    consultar();
    const intervalo = window.setInterval(consultar, 30000);
    window.addEventListener("focus", consultar);
    return () => {
      vigente = false;
      window.clearInterval(intervalo);
      window.removeEventListener("focus", consultar);
    };
  }, []);

  const marcarLeida = async (reserva) => {
    if (leyendo) return;
    setLeyendo(reserva.id);
    try {
      await api.post(`/api/v1/reservas/${reserva.id}/notificacion/leida`);
      leidas.current.add(reserva.id);
      setNotificaciones((actuales) => actuales.filter((actual) => actual.id !== reserva.id));
    } catch {
      toast.error("No se pudo marcar la notificación como leída. Reintentá.");
    } finally {
      setLeyendo(null);
    }
  };

  if (notificaciones.length === 0) return null;
  return (
    <section className="ui-card p-5 space-y-4" aria-label="Notificaciones de reservas" aria-live="polite">
      <h2 className="text-lg font-bold text-ink">Se confirmó tu reserva en lista de espera</h2>
      {notificaciones.map((reserva) => (
        <div key={reserva.id} className="space-y-2 text-sm text-ink">
          <p><strong>{reserva.vehiculo?.patente}</strong> · {formatearRango(reserva.desde, reserva.hasta, { incluirAnio: true })}</p>
          <p>Se asignó la cochera {reserva.cochera?.numero}. Consultá el estado actual en tus reservas.</p>
          <p>Estado actual: <EstadoReservaBadge estado={reserva.estado} motivoCancelacion={reserva.motivoCancelacion} /></p>
          <div className="flex flex-wrap gap-4">
            {onVerCodigo && tieneCodigoVigente(reserva) && <button type="button" className="font-semibold text-link hover:underline" onClick={() => onVerCodigo(reserva)}>Ver código de acceso</button>}
            <button type="button" className="font-semibold text-link hover:underline disabled:opacity-50" disabled={leyendo !== null} onClick={() => marcarLeida(reserva)}>
              {leyendo === reserva.id ? "Guardando…" : "Marcar como leída"}
            </button>
          </div>
        </div>
      ))}
    </section>
  );
}
