"use client";

import { useEffect, useMemo, useState } from "react";
import api from "@/app/api";

// Compartido por reservas ADMIN/USER y el alta operativa. El precio viene del servidor.
// `canje` ({ puntos } o { todos: true }) pide el precio con descuento por puntos.
export default function useCotizacion(tipo, desde, hasta, canje = null, convenios) {
  const [respuesta, setRespuesta] = useState(null);
  const [intento, setIntento] = useState(0);
  const habilitada = Boolean(tipo && desde && hasta && hasta > desde);
  const puntos = canje?.todos ? undefined : canje?.puntos;
  const usarTodos = canje?.todos ? true : undefined;
  // El contenido estable evita recotizar por una nueva referencia del formulario.
  const contextoConvenios = convenios === undefined ? undefined : JSON.stringify(convenios);
  const solicitud = useMemo(() => ({ tipo, desde, hasta, puntos, usarTodos, intento, contextoConvenios }),
    [tipo, desde, hasta, puntos, usarTodos, intento, contextoConvenios]);

  useEffect(() => {
    if (!habilitada) return;
    let vigente = true;
    const params = { tipo, desde, hasta };
    if (puntos != null) params.puntos = puntos;
    if (usarTodos) params.usarTodos = true;
    const consulta = contextoConvenios === undefined ? api.get("/api/v1/tarifas/cotizacion", { params })
      : api.post("/api/v1/tarifas/cotizacion", { ...params, ...JSON.parse(contextoConvenios) });
    consulta
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
  }, [tipo, desde, hasta, puntos, usarTodos, habilitada, solicitud, contextoConvenios]);

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
