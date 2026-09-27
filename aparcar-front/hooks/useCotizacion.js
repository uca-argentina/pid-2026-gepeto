"use client";

import { useEffect, useMemo, useState } from "react";
import api from "@/app/api";

// Compartido por reservas ADMIN/USER y el alta operativa. El precio viene del servidor.
export default function useCotizacion(tipo, desde, hasta) {
  const [respuesta, setRespuesta] = useState(null);
  const [intento, setIntento] = useState(0);
  const habilitada = Boolean(tipo && desde && hasta && hasta > desde);
  const solicitud = useMemo(() => ({ tipo, desde, hasta, intento }), [tipo, desde, hasta, intento]);

  useEffect(() => {
    if (!habilitada) return;
    let vigente = true;
    api.get("/api/v1/tarifas/cotizacion", { params: { tipo, desde, hasta } })
      .then(({ data }) => {
        if (!Number.isFinite(Number(data?.total)) || data?.total == null) {
          throw new Error("Cotización inválida");
        }
        if (vigente) setRespuesta({ solicitud, datos: data });
      })
      .catch((error) => {
        if (vigente) setRespuesta({ solicitud, error: error.response?.data?.message || "No se pudo calcular el precio. Reintentá antes de reservar." });
      });
    return () => { vigente = false; };
  }, [tipo, desde, hasta, habilitada, solicitud]);

  // Al cambiar los parámetros, el importe anterior deja de ser utilizable en ese mismo render.
  const actual = habilitada && respuesta?.solicitud === solicitud ? respuesta : null;
  return {
    datos: actual?.datos,
    error: actual?.error,
    cargando: habilitada && !actual,
    lista: Boolean(actual?.datos),
    recargar: () => setIntento((valor) => valor + 1),
  };
}
