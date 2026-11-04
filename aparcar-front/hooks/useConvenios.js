"use client";

import { useEffect, useMemo, useState } from "react";
import api from "@/app/api";

const VACIAS = [];

// La validación siempre la hace el servidor sobre el documento del visitante.
// Un cambio de visitante o documento descarta también las respuestas en curso.
export default function useConvenios({ visitanteId, documento, habilitado = true, guardar = false, revision } = {}) {
  const [catalogo, setCatalogo] = useState(null);
  const [estado, setEstado] = useState(null);
  const [intento, setIntento] = useState(0);
  const solicitud = useMemo(() => ({ visitanteId, documento, habilitado, revision, intento }),
    [visitanteId, documento, habilitado, revision, intento]);

  useEffect(() => {
    let vigente = true;
    api.get("/api/v1/convenios")
      .then(({ data }) => {
        if (vigente) setCatalogo({ datos: Array.isArray(data) ? data : [], intento });
      })
      .catch(() => {
        if (vigente) setCatalogo({ error: "No se pudieron cargar los convenios.", intento });
      });
    return () => { vigente = false; };
  }, [intento]);

  useEffect(() => {
    if (!habilitado) return;
    let vigente = true;
    // En el alta de un visitante todavía no hay una cuenta con credenciales guardadas.
    const consulta = documento !== undefined ? Promise.resolve({ data: [] })
      : api.get("/api/v1/convenios/credenciales", { params: visitanteId ? { visitanteId } : {} });
    consulta.then(({ data }) => {
      if (!vigente) return;
      const campos = Object.fromEntries((Array.isArray(data) ? data : []).map((credencial) => [
        credencial.convenioId, { texto: credencial.credencial, validada: true, guardada: true },
      ]));
      setEstado({ solicitud, campos });
    }).catch(() => {
      if (vigente) setEstado({ solicitud, campos: {}, error: "No se pudieron cargar las credenciales guardadas." });
    });
    return () => { vigente = false; };
  }, [solicitud, habilitado, visitanteId, documento]);

  const actual = estado?.solicitud === solicitud ? estado : null;
  const catalogoActual = catalogo?.intento === intento ? catalogo : null;
  const convenios = catalogoActual?.datos ?? VACIAS;
  const campos = actual?.campos ?? {};
  const credenciales = useMemo(() => convenios.flatMap(({ id }) => {
    const campo = actual?.campos[id];
    return campo?.validada ? [{ convenioId: id, credencial: campo.texto }] : [];
  }), [convenios, actual]);

  const actualizarCampo = (id, transformar) => setEstado((anterior) => anterior?.solicitud === solicitud
    ? { ...anterior, campos: { ...anterior.campos, [id]: transformar(anterior.campos[id] ?? {}) } }
    : anterior);
  const cambiar = (id, texto) => actualizarCampo(id, () => ({ texto, validada: false }));
  const verificar = async (id) => {
    const texto = campos[id]?.texto?.trim();
    if (!texto || campos[id]?.ocupada) return;
    const operacion = {};
    actualizarCampo(id, (campo) => ({ ...campo, ocupada: true, error: null, operacion }));
    try {
      const body = { convenioId: id, credencial: texto,
        ...(visitanteId ? { visitanteId } : {}), ...(documento !== undefined ? { documento } : {}) };
      const { data } = guardar ? await api.put("/api/v1/convenios/credenciales", body)
        : await api.post("/api/v1/convenios/verificar", body);
      actualizarCampo(id, (campo) => campo.operacion === operacion
        ? { texto: data.credencial, validada: true, guardada: guardar } : campo);
    } catch (error) {
      actualizarCampo(id, (campo) => campo.operacion === operacion
        ? { ...campo, ocupada: false, error: error.response?.data?.message || "No se pudo verificar la credencial." } : campo);
    }
  };
  const borrar = async (id) => {
    if (!campos[id]?.guardada) { cambiar(id, ""); return; }
    if (!guardar || campos[id]?.ocupada) return;
    const operacion = {};
    actualizarCampo(id, (campo) => ({ ...campo, ocupada: true, error: null, operacion }));
    try {
      await api.delete(`/api/v1/convenios/credenciales/${id}`);
      actualizarCampo(id, (campo) => campo.operacion === operacion ? { texto: "" } : campo);
    } catch (error) {
      actualizarCampo(id, (campo) => campo.operacion === operacion
        ? { ...campo, ocupada: false, error: error.response?.data?.message || "No se pudo eliminar la credencial." } : campo);
    }
  };

  const error = catalogoActual?.error || actual?.error;
  const lista = habilitado && Boolean(catalogoActual?.datos && actual && !error);
  // Si falla la carga, el servidor todavía puede recuperar las guardadas. El
  // personal debe conservar el titular de la reserva al pedir esa cotización.
  const cotizarConContexto = habilitado && (convenios.length > 0
    || (error && (Boolean(visitanteId) || documento !== undefined)));
  return { convenios, campos, credenciales, habilitado, error, lista,
    cargando: habilitado && (!catalogoActual || !actual),
    verificando: Object.values(campos).some((campo) => campo.ocupada),
    puedeVerificar: documento === undefined || Boolean(documento.trim()),
    cambiar, verificar, borrar, recargar: () => setIntento((valor) => valor + 1),
    contextoCotizacion: cotizarConContexto ? { visitanteId, documento, credencialesConvenio: credenciales } : undefined,
  };
}
