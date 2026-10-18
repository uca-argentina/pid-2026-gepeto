"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import { toast } from "sonner";
import api from "@/app/api";
import { EVENTO_VERIFICACIONES } from "@/components/ContadorVerificaciones";
import { ESTADO_VERIFICACION_ETIQUETA, fechaCorta } from "@/utils/verificacionDiscapacidad";
import { formatearFechaRegistro } from "@/utils/reservasListado";

const BASE = "/api/v1/verificaciones-discapacidad";
const FILTROS = ["EN_REVISION", "VERIFICADA", "RECHAZADA", "REVOCADA", "VENCIDA", "CANCELADA"];

const mensajeDeError = (err, porDefecto) => err.response?.data?.message || porDefecto;

/**
 * Cola de verificaciones de discapacidad para el administrador.
 *
 * El certificado se pide al endpoint autenticado y se muestra desde la memoria
 * del navegador (un object URL): nunca hay una dirección pública del archivo.
 * La pantalla no muestra ni pide datos médicos; el admin mira el documento y
 * decide.
 */
export default function VerificacionesContent() {
  const id = useId();
  const [filtro, setFiltro] = useState("EN_REVISION");
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(false);
  const [seleccionada, setSeleccionada] = useState(null);

  const cargar = useCallback(async (estado) => {
    setCargando(true);
    try {
      const { data } = await api.get(BASE, { params: { estado } });
      setSolicitudes(data);
      setError(false);
      return data;
    } catch {
      setError(true);
      return null;
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    setSeleccionada(null);
    cargar(filtro);
  }, [filtro, cargar]);

  /** Después de resolver: refresca la cola y el contador de la navegación. */
  const actualizada = async (solicitud) => {
    window.dispatchEvent(new Event(EVENTO_VERIFICACIONES));
    const data = await cargar(filtro);
    setSeleccionada(data?.find((s) => s.id === solicitud?.id) ?? null);
  };

  return (
    <div className="verificaciones">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight text-ink">Verificaciones de discapacidad</h1>
        <p className="mt-2 text-sm text-ink/60">
          Revisá el certificado y aprobá o rechazá la solicitud. Solo con una verificación aprobada la persona puede
          reservar cocheras accesibles.
        </p>
      </div>

      <div className="verificaciones-filtros" role="group" aria-label="Filtrar por estado">
        {FILTROS.map((estado) => (
          <button key={estado} type="button" aria-pressed={filtro === estado} onClick={() => setFiltro(estado)}>
            {ESTADO_VERIFICACION_ETIQUETA[estado]}
          </button>
        ))}
      </div>

      <div className="verificaciones-columnas">
        <section className="ui-card verificaciones-lista" aria-labelledby={`${id}-cola`}>
          <h2 id={`${id}-cola`} className="sr-only">Solicitudes</h2>
          {cargando ? (
            <p role="status" className="verificaciones-vacio">Cargando solicitudes…</p>
          ) : error ? (
            <div role="alert" className="verificaciones-vacio">
              <p>No se pudieron cargar las solicitudes.</p>
              <button type="button" className="profile-secondary" onClick={() => cargar(filtro)}>Reintentar</button>
            </div>
          ) : solicitudes.length === 0 ? (
            <p className="verificaciones-vacio">
              {filtro === "EN_REVISION" ? "No hay solicitudes pendientes." : "No hay solicitudes en este estado."}
            </p>
          ) : (
            <ul aria-label="Solicitudes de verificación">
              {solicitudes.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    className={`verificaciones-item ${seleccionada?.id === s.id ? "is-activa" : ""}`}
                    aria-pressed={seleccionada?.id === s.id}
                    onClick={() => setSeleccionada(s)}
                  >
                    <span className="verificaciones-item-nombre">{s.solicitante?.nombre}</span>
                    <span className="verificaciones-item-email">{s.solicitante?.email}</span>
                    <span className="verificaciones-item-fecha">Enviada {formatearFechaRegistro(s.fechaCreacion)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="ui-card verificaciones-detalle" aria-live="polite" aria-label="Detalle de la solicitud">
          {seleccionada ? (
            <Detalle key={seleccionada.id} solicitud={seleccionada} onActualizada={actualizada} />
          ) : (
            <p className="verificaciones-vacio">Elegí una solicitud para ver el certificado.</p>
          )}
        </section>
      </div>
    </div>
  );
}

function Detalle({ solicitud, onActualizada }) {
  const id = useId();
  const [vence, setVence] = useState("");
  const [motivo, setMotivo] = useState("");
  const [errorMotivo, setErrorMotivo] = useState(null);
  const [resolviendo, setResolviendo] = useState(null);
  // Un ref y no solo estado: dos clics seguidos llegan antes de que el botón se deshabilite.
  const ocupado = useRef(false);
  const enRevision = solicitud.estado === "EN_REVISION";
  const vigente = solicitud.estado === "VERIFICADA";

  const resolver = async (accion, cuerpo, pregunta, exito) => {
    if (ocupado.current) return;
    if (!window.confirm(pregunta)) return;
    ocupado.current = true;
    setResolviendo(accion);
    try {
      const { data } = await api.post(`${BASE}/${solicitud.id}/${accion}`, cuerpo);
      toast.success(exito);
      await onActualizada(data);
    } catch (err) {
      if (err.response?.status === 409) {
        // Alguien más la resolvió: se muestra cómo quedó.
        toast.error(mensajeDeError(err, "Otro administrador ya resolvió esta solicitud."));
        await onActualizada(solicitud);
      } else {
        toast.error(mensajeDeError(err, "No se pudo guardar la decisión. Volvé a intentar."));
      }
    } finally {
      ocupado.current = false;
      setResolviendo(null);
    }
  };

  const aprobar = () => resolver(
    "aprobar",
    vence ? { venceEl: vence } : {},
    `¿Aprobar la verificación de ${solicitud.solicitante?.nombre}? Va a poder reservar cocheras accesibles.`,
    "Verificación aprobada",
  );

  const conMotivo = (accion, pregunta, exito) => {
    if (!motivo.trim()) {
      setErrorMotivo("El motivo es obligatorio: la persona lo va a leer.");
      return;
    }
    setErrorMotivo(null);
    resolver(accion, { motivo: motivo.trim() }, pregunta, exito);
  };

  return (
    <div className="verificaciones-ficha">
      <div>
        <p className="eyebrow">{ESTADO_VERIFICACION_ETIQUETA[solicitud.estado]}</p>
        <h2 className="text-lg font-bold text-ink">{solicitud.solicitante?.nombre}</h2>
        <dl className="verificaciones-datos">
          <div><dt>Email</dt><dd>{solicitud.solicitante?.email}</dd></div>
          <div><dt>Enviada</dt><dd>{formatearFechaRegistro(solicitud.fechaCreacion)}</dd></div>
          {solicitud.resueltaAt && (
            <div><dt>Resuelta</dt><dd>{formatearFechaRegistro(solicitud.resueltaAt)}{solicitud.resueltaPor ? ` · ${solicitud.resueltaPor}` : ""}</dd></div>
          )}
          {solicitud.revocadaAt && (
            <div><dt>Revocada</dt><dd>{formatearFechaRegistro(solicitud.revocadaAt)}{solicitud.revocadaPor ? ` · ${solicitud.revocadaPor}` : ""}</dd></div>
          )}
          {solicitud.venceEl && <div><dt>Vence</dt><dd>{fechaCorta(solicitud.venceEl)}</dd></div>}
          {solicitud.motivo && <div className="verificaciones-datos-ancho"><dt>Motivo</dt><dd>{solicitud.motivo}</dd></div>}
        </dl>
      </div>

      <Documento solicitud={solicitud} />

      {(enRevision || vigente) && (
        <div className="verificaciones-acciones">
          {enRevision && (
            <div className="verificaciones-aprobar">
              <label className="ui-label" htmlFor={`${id}-vence`}>Vence el (opcional)</label>
              <input id={`${id}-vence`} type="date" className="ui-input" value={vence}
                onChange={(e) => setVence(e.target.value)} />
              <button type="button" className="ui-primary verificaciones-boton" onClick={aprobar} disabled={resolviendo !== null}>
                {resolviendo === "aprobar" ? "Aprobando…" : "Aprobar"}
              </button>
            </div>
          )}
          <div className="verificaciones-motivo">
            <label className="ui-label" htmlFor={`${id}-motivo`}>
              {enRevision ? "Motivo del rechazo" : "Motivo de la revocación"}
            </label>
            <textarea id={`${id}-motivo`} className="ui-input" rows={3} maxLength={500} value={motivo}
              aria-invalid={Boolean(errorMotivo)} aria-describedby={errorMotivo ? `${id}-motivo-error` : `${id}-motivo-ayuda`}
              onChange={(e) => setMotivo(e.target.value)} />
            <p id={`${id}-motivo-ayuda`} className="text-xs text-ink/50">La persona lo va a ver en su perfil. No incluyas datos médicos.</p>
            {errorMotivo && <p id={`${id}-motivo-error`} role="alert" className="text-sm text-red-600 dark:text-red-300">{errorMotivo}</p>}
            {enRevision ? (
              <button type="button" className="verificaciones-boton is-peligro" disabled={resolviendo !== null}
                onClick={() => conMotivo("rechazar", `¿Rechazar la solicitud de ${solicitud.solicitante?.nombre}?`, "Solicitud rechazada")}>
                {resolviendo === "rechazar" ? "Rechazando…" : "Rechazar"}
              </button>
            ) : (
              <button type="button" className="verificaciones-boton is-peligro" disabled={resolviendo !== null}
                onClick={() => conMotivo("revocar",
                  `¿Revocar la verificación de ${solicitud.solicitante?.nombre}? Va a dejar de poder reservar cocheras accesibles. Sus reservas ya hechas no se cancelan.`,
                  "Verificación revocada")}>
                {resolviendo === "revocar" ? "Revocando…" : "Revocar verificación"}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/** El certificado, pedido con la sesión del admin y mostrado desde la memoria del navegador. */
function Documento({ solicitud }) {
  const [url, setUrl] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!solicitud.documentoDisponible) return undefined;
    let vigente = true;
    let creada = null;
    api.get(`${BASE}/${solicitud.id}/documento`, { responseType: "blob" })
      .then(({ data }) => {
        if (!vigente) return;
        creada = URL.createObjectURL(data);
        setUrl(creada);
      })
      .catch((err) => {
        if (vigente) setError(err.response?.status === 404 ? "El certificado ya no está disponible." : "No se pudo cargar el certificado.");
      });
    return () => {
      vigente = false;
      if (creada) URL.revokeObjectURL(creada);
    };
  }, [solicitud.id, solicitud.documentoDisponible]);

  if (!solicitud.documentoDisponible) {
    return <p className="verificaciones-documento-vacio">El certificado se eliminó por la política de retención. Se conserva la decisión.</p>;
  }
  if (error) return <p role="alert" className="verificaciones-documento-vacio">{error}</p>;
  if (!url) return <p role="status" className="verificaciones-documento-vacio">Cargando certificado…</p>;

  const titulo = `Certificado de ${solicitud.solicitante?.nombre}`;
  return (
    <div className="verificaciones-documento">
      {solicitud.documentoTipo === "application/pdf" ? (
        <iframe src={url} title={titulo} />
      ) : (
        // Un object URL local: next/image no aplica.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={titulo} />
      )}
      <a href={url} target="_blank" rel="noopener noreferrer" className="text-sm font-semibold text-link hover:underline">
        Abrir en una pestaña nueva
      </a>
    </div>
  );
}
