"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import api from "@/app/api";
import DashboardIcon from "@/components/DashboardIcon";

/** Lo dispara la pantalla de verificaciones al resolver una, para no esperar al próximo sondeo. */
export const EVENTO_VERIFICACIONES = "aparcar:verificaciones-actualizadas";
const INTERVALO_MS = 60000;

/**
 * El acceso a "Verificaciones" en la navegación del admin, con la cantidad de
 * solicitudes pendientes. Se actualiza al entrar, cada minuto, al volver a la
 * pestaña y cuando se resuelve una. Es el aviso a los administradores dentro
 * de la app; el mail es un extra.
 */
export default function ContadorVerificaciones() {
  const [cantidad, setCantidad] = useState(null);

  useEffect(() => {
    let vigente = true;
    const consultar = async () => {
      try {
        const { data } = await api.get("/api/v1/verificaciones-discapacidad/pendientes/cantidad");
        if (vigente) setCantidad(data.cantidad);
      } catch {
        // El contador es un aviso: si falla, la próxima consulta reintenta.
      }
    };
    consultar();
    const intervalo = window.setInterval(consultar, INTERVALO_MS);
    window.addEventListener("focus", consultar);
    window.addEventListener(EVENTO_VERIFICACIONES, consultar);
    return () => {
      vigente = false;
      window.clearInterval(intervalo);
      window.removeEventListener("focus", consultar);
      window.removeEventListener(EVENTO_VERIFICACIONES, consultar);
    };
  }, []);

  const pendientes = cantidad > 0;
  return (
    <Link
      href="/dashboard-admin/verificaciones"
      className="dashboard-nav-link"
      aria-label={pendientes ? `Verificaciones, ${cantidad} pendientes` : "Verificaciones"}
    >
      <DashboardIcon name="certificado" />
      <span>Verificaciones</span>
      {pendientes && <span className="nav-contador" aria-hidden="true">{cantidad > 99 ? "99+" : cantidad}</span>}
    </Link>
  );
}
