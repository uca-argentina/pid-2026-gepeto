"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import { toast } from "sonner";
import api from "@/app/api";
import DashboardIcon from "@/components/DashboardIcon";
import {
  TIPOS_CERTIFICADO, fechaCorta, validarCertificado,
} from "@/utils/verificacionDiscapacidad";

const URL_MIA = "/api/v1/verificaciones-discapacidad/mia";

/**
 * "Cocheras accesibles" en Mis datos: el estado de la verificación del
 * certificado de discapacidad y lo que se puede hacer en cada caso.
 *
 * El acceso ya no se autodeclara: lo da un administrador después de mirar el
 * certificado. Acá el visitante lo sube, ve si está en revisión, el motivo si
 * se rechazó, o hasta cuándo vale si está verificado.
 */
export default function VerificacionDiscapacidad() {
  const id = useId();
  const inputRef = useRef(null);
  const [estado, setEstado] = useState(null);
  const [error, setError] = useState(false);
  const [archivo, setArchivo] = useState(null);
  const [errorArchivo, setErrorArchivo] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [cancelando, setCancelando] = useState(false);
  const [arrastrando, setArrastrando] = useState(false);

  const cargar = useCallback(async () => {
    try {
      const { data } = await api.get(URL_MIA);
      setEstado(data);
      setError(false);
    } catch {
      setError(true);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const elegir = (elegido) => {
    if (!elegido) return;
    const problema = validarCertificado(elegido, estado?.documentoMaxMb ?? 5);
    setErrorArchivo(problema);
    setArchivo(problema ? null : elegido);
  };

  const enviar = async (e) => {
    e.preventDefault();
    if (!archivo || enviando) return;
    setEnviando(true);
    setErrorArchivo(null);
    try {
      const datos = new FormData();
      datos.append("archivo", archivo);
      // El JSON es el formato por defecto de la API: acá va el archivo.
      const { data } = await api.post("/api/v1/verificaciones-discapacidad", datos, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      setEstado(data);
      setArchivo(null);
      if (inputRef.current) inputRef.current.value = "";
      toast.success("Enviamos tu certificado. Te avisamos cuando lo revisen.");
    } catch (err) {
      setErrorArchivo(err.response?.data?.message || "No se pudo enviar el certificado. Volvé a intentar.");
    } finally {
      setEnviando(false);
    }
  };

  const cancelar = async () => {
    if (cancelando) return;
    setCancelando(true);
    try {
      const { data } = await api.post(`${URL_MIA}/cancelar`);
      setEstado(data);
      toast.success("Retiraste tu solicitud.");
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo cancelar la solicitud.");
    } finally {
      setCancelando(false);
    }
  };

  const solicitud = estado?.solicitud;

  return (
    <section className="ui-card profile-form verificacion" aria-labelledby={`${id}-titulo`}>
      <div className="profile-section-heading">
        <span className="profile-section-icon"><DashboardIcon name="shield" /></span>
        <div>
          <h2 id={`${id}-titulo`}>Cocheras accesibles</h2>
          <p>Para reservarlas, verificamos tu certificado de discapacidad.</p>
        </div>
      </div>

      {error && !estado ? (
        <div className="verificacion-aviso is-error" role="alert">
          <p>No se pudo cargar el estado de tu verificación.</p>
          <button type="button" className="profile-secondary" onClick={cargar}>Reintentar</button>
        </div>
      ) : !estado ? (
        <p role="status" className="text-sm text-ink/60">Cargando…</p>
      ) : (
        <>
          <EstadoActual estado={estado} />

          {estado.estado === "EN_REVISION" && (
            <button type="button" className="profile-secondary" onClick={cancelar} disabled={cancelando}>
              {cancelando ? "Cancelando…" : "Retirar mi solicitud"}
            </button>
          )}

          {estado.puedeEnviar && (
            <form className="verificacion-form" onSubmit={enviar} noValidate>
              <label
                htmlFor={`${id}-archivo`}
                className={`verificacion-zona ${arrastrando ? "is-arrastrando" : ""}`}
                onDragOver={(e) => { e.preventDefault(); setArrastrando(true); }}
                onDragLeave={() => setArrastrando(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setArrastrando(false);
                  elegir(e.dataTransfer?.files?.[0]);
                }}
              >
                <span className="verificacion-zona-titulo">
                  {solicitud && ["RECHAZADA", "REVOCADA", "VENCIDA"].includes(estado.estado)
                    ? "Enviar un certificado nuevo"
                    : "Subí tu certificado"}
                </span>
                <span className="verificacion-zona-ayuda">
                  Arrastralo acá o tocá para elegirlo. PDF, JPG o PNG, hasta {estado.documentoMaxMb} MB.
                </span>
                {archivo && <span className="verificacion-zona-archivo">{archivo.name}</span>}
              </label>
              <input
                ref={inputRef}
                id={`${id}-archivo`}
                type="file"
                className="sr-only"
                accept={TIPOS_CERTIFICADO.accept}
                aria-describedby={`${id}-privacidad${errorArchivo ? ` ${id}-error` : ""}`}
                onChange={(e) => elegir(e.target.files?.[0])}
              />
              {errorArchivo && (
                <p id={`${id}-error`} role="alert" className="verificacion-error">{errorArchivo}</p>
              )}
              <button type="submit" className="ui-primary px-5 py-2.5 text-sm disabled:opacity-50" disabled={!archivo || enviando}>
                {enviando ? "Enviando…" : "Enviar certificado"}
              </button>
            </form>
          )}

          <p id={`${id}-privacidad`} className="verificacion-privacidad">
            Pedimos el certificado (por ejemplo, el CUD), no un diagnóstico. Solo lo ven los administradores
            para verificarlo, y se elimina pasado un tiempo de la decisión: guardamos solo el resultado.
          </p>
        </>
      )}
    </section>
  );
}

/** El estado en palabras, con su aviso accesible. */
function EstadoActual({ estado }) {
  const solicitud = estado.solicitud;
  switch (estado.estado) {
    case "EN_REVISION":
      return (
        <div className="verificacion-aviso is-revision" role="status">
          <p className="verificacion-aviso-titulo">En revisión</p>
          <p>Enviaste tu certificado el {fechaCorta(solicitud?.fechaCreacion)}. Un administrador lo va a revisar.</p>
          {estado.puedeUsarCocherasAccesibles && estado.accesoProvisorioHasta && (
            <p>Mientras tanto conservás el acceso a las cocheras accesibles.</p>
          )}
        </div>
      );
    case "VERIFICADA":
      return (
        <div className="verificacion-aviso is-ok" role="status">
          <p className="verificacion-aviso-titulo">Verificada</p>
          <p>
            Podés reservar cocheras accesibles
            {solicitud?.venceEl ? ` hasta el ${fechaCorta(solicitud.venceEl)}, cuando vence tu certificado.` : "."}
          </p>
        </div>
      );
    case "RECHAZADA":
    case "REVOCADA":
      return (
        <div className="verificacion-aviso is-error" role="alert">
          <p className="verificacion-aviso-titulo">
            {estado.estado === "RECHAZADA" ? "Tu certificado fue rechazado" : "Tu verificación fue revocada"}
          </p>
          {solicitud?.motivo && <p>Motivo: {solicitud.motivo}</p>}
          <p>Podés enviar un certificado nuevo.</p>
        </div>
      );
    case "VENCIDA":
      return (
        <div className="verificacion-aviso is-advertencia" role="alert">
          <p className="verificacion-aviso-titulo">Tu verificación venció</p>
          <p>Para seguir reservando cocheras accesibles, enviá un certificado vigente.</p>
        </div>
      );
    default:
      return estado.accesoProvisorioHasta && estado.puedeUsarCocherasAccesibles ? (
        <div className="verificacion-aviso is-advertencia" role="status">
          <p className="verificacion-aviso-titulo">Tenés que enviar tu certificado</p>
          <p>
            Tenías declarada una discapacidad. Conservás el acceso a las cocheras accesibles hasta el{" "}
            {fechaCorta(estado.accesoProvisorioHasta)}: enviá tu certificado antes para no perderlo.
          </p>
        </div>
      ) : (
        <p className="verificacion-sin-solicitud">
          Si tenés una discapacidad, enviá tu certificado para poder reservar cocheras accesibles.
        </p>
      );
  }
}
