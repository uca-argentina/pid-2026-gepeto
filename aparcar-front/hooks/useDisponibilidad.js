"use client";

import { useEffect, useMemo, useState } from "react";
import api from "@/app/api";
import { toast } from "sonner";
import { redondearAlBloqueLocal } from "@/utils/franjaHoraria";

const VACIAS = [];

/** Distingue una consulta vacía de una pendiente o fallida, incluso al cambiar la franja. */
export default function useDisponibilidad(tipoVehiculo, desde, hasta, propia = false) {
  const [respuesta, setRespuesta] = useState(null);
  const [intento, setIntento] = useState(0);
  const habilitada = Boolean(tipoVehiculo && desde && hasta && hasta > desde);
  const solicitud = useMemo(() => ({ tipoVehiculo, desde, hasta, propia, intento }), [tipoVehiculo, desde, hasta, propia, intento]);

  useEffect(() => {
    if (!habilitada) return;
    let vigente = true;
    api.get("/api/v1/cocheras/disponibles", { params: {
      tipoVehiculo, desde: redondearAlBloqueLocal(desde), hasta: redondearAlBloqueLocal(hasta),
      ...(propia ? { propia: true } : {}),
    } }).then(({ data }) => {
      if (vigente) setRespuesta({ solicitud, cocheras: data });
    }).catch(() => {
      if (vigente) {
        const error = "No se pudieron cargar las cocheras disponibles.";
        setRespuesta({ solicitud, error });
        toast.error(error);
      }
    });
    return () => { vigente = false; };
  }, [tipoVehiculo, desde, hasta, propia, habilitada, solicitud]);

  const actual = habilitada && respuesta?.solicitud === solicitud ? respuesta : null;
  return {
    cocheras: actual?.cocheras ?? VACIAS,
    lista: Boolean(actual?.cocheras),
    cargando: habilitada && !actual,
    error: actual?.error,
    solicitud,
    recargar: () => setIntento((valor) => valor + 1),
  };
}
