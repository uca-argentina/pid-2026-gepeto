"use client";

import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { toast } from "sonner";

import api from "@/app/api";
import PatenteVisual from "@/components/PatenteVisual";
import { formatearRango } from "@/utils/franjaHoraria";

const ENFOCABLES = 'button:not([disabled]), [href], input:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Aplicar una falta sobre la persona dueña de una reserva.
 *
 * Son dos pasos a propósito, y el orden importa:
 *
 *   1. Confirmar. Una falta tiene consecuencias —acumuladas bloquean la
 *      cuenta— y el botón vive pegado al de cancelar en una lista larga. Sin
 *      una confirmación, un clic errado sanciona a alguien.
 *   2. Escribir la causa, viendo cuántas faltas ya tiene. Ese número es lo que
 *      permite decidir con criterio: no es lo mismo la primera que la que va a
 *      dejar la cuenta inhabilitada, y el aviso lo dice explícitamente.
 *
 * Es un diálogo modal accesible: atrapa el foco, cierra con Escape o con el
 * fondo, y al cerrar devuelve el foco a quien lo abrió. Va por portal al
 * <body> por lo mismo que ModalCodigoAcceso: el listado usa container queries
 * y eso rompe el posicionamiento fijo de adentro.
 */
export default function ModalAmonestacion({ reserva, onClose, onAplicada }) {
  const id = useId();
  const dialogoRef = useRef(null);
  const cerrarRef = useRef(null);
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  const [paso, setPaso] = useState("confirmar");
  const [motivo, setMotivo] = useState("");
  const [resumen, setResumen] = useState(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    const anterior = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    cerrarRef.current?.focus();

    const alTeclear = (e) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onCloseRef.current?.();
        return;
      }
      if (e.key !== "Tab" || !dialogoRef.current) return;
      const enfocables = [...dialogoRef.current.querySelectorAll(ENFOCABLES)];
      if (enfocables.length === 0) return;
      const primero = enfocables[0];
      const ultimo = enfocables[enfocables.length - 1];
      const dentro = dialogoRef.current.contains(document.activeElement);
      if (e.shiftKey && (document.activeElement === primero || !dentro)) {
        e.preventDefault();
        ultimo.focus();
      } else if (!e.shiftKey && (document.activeElement === ultimo || !dentro)) {
        e.preventDefault();
        primero.focus();
      }
    };

    document.addEventListener("keydown", alTeclear);
    return () => {
      document.removeEventListener("keydown", alTeclear);
      document.body.style.overflow = overflow;
      anterior?.focus?.();
    };
  }, []);

  const visitanteId = reserva?.visitante?.id;

  /**
   * Al confirmar se piden las faltas que ya tiene, para mostrarlas en el paso
   * siguiente. Si la consulta falla igual se avanza: no poder mostrar el
   * contador no es razón para impedir que se cargue la falta.
   */
  const confirmar = async () => {
    setPaso("motivo");
    if (!visitanteId) return;
    try {
      const { data } = await api.get(`/api/v1/amonestaciones/visitante/${visitanteId}`);
      setResumen(data);
    } catch {
      setResumen(null);
    }
  };

  const aplicar = async () => {
    if (!motivo.trim() || enviando) return;
    setEnviando(true);
    try {
      await api.post("/api/v1/amonestaciones", {
        reservaId: reserva.id,
        motivo: motivo.trim(),
      });
      toast.success("Falta aplicada.");
      onAplicada?.();
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message ?? "No se pudo aplicar la falta.");
      setEnviando(false);
    }
  };

  const nombre = reserva?.visitante?.nombre ?? "esta persona";
  const patente = reserva?.vehiculo?.patente;
  // Con esta falta pasaría el máximo, así que la cuenta queda inhabilitada.
  const bloquearia = resumen && resumen.vigentes + 1 > resumen.maxFaltas;

  return createPortal(
    <div className="modal-fondo" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={dialogoRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-titulo`}
        className="modal-acceso ui-card"
      >
        <div className="modal-acceso-cabecera">
          <h2 id={`${id}-titulo`}>
            {paso === "confirmar" ? "Aplicar una falta" : "Causa de la amonestación"}
          </h2>
          <button ref={cerrarRef} type="button" className="modal-acceso-x"
            onClick={onClose} aria-label="Cerrar ventana">
            ×
          </button>
        </div>

        {paso === "confirmar" ? (
          <>
            <p className="modal-acceso-ayuda">
              ¿Seguro que querés aplicar una falta sobre este vehículo?
            </p>

            <div className="amonestacion-contexto">
              {patente && <PatenteVisual patente={patente} tipo={reserva?.vehiculo?.tipo} />}
              <p><strong>{nombre}</strong></p>
              {reserva?.desde && reserva?.hasta && (
                <p className="amonestacion-franja">{formatearRango(reserva.desde, reserva.hasta)}</p>
              )}
            </div>

            <div className="amonestacion-acciones">
              <button type="button" className="ui-secondary" onClick={onClose}>
                Cancelar
              </button>
              <button type="button" className="ui-primary" onClick={confirmar}>
                Aceptar
              </button>
            </div>
          </>
        ) : (
          <>
            <div className="amonestacion-contador" aria-live="polite">
              {resumen ? (
                <>
                  <p>
                    <strong>{nombre}</strong> tiene{" "}
                    <strong>{resumen.vigentes}</strong>{" "}
                    {resumen.vigentes === 1 ? "falta" : "faltas"} en{" "}
                    {resumen.mesesVentana === 1
                      ? "el último mes"
                      : `los últimos ${resumen.mesesVentana ?? 3} meses`}
                    , sobre un máximo de {resumen.maxFaltas}.
                  </p>
                  {bloquearia && (
                    <p className="amonestacion-aviso" role="alert">
                      Con esta falta se supera el máximo y la cuenta queda inhabilitada.
                    </p>
                  )}
                </>
              ) : (
                <p>No se pudo consultar cuántas faltas tiene.</p>
              )}
            </div>

            <label className="amonestacion-campo" htmlFor={`${id}-motivo`}>
              <span>Causa de la amonestación</span>
              <textarea
                id={`${id}-motivo`}
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                maxLength={500}
                rows={4}
                placeholder="Qué pasó. Queda en el historial de la persona, de forma permanente."
              />
            </label>

            <div className="amonestacion-acciones">
              <button type="button" className="ui-secondary" onClick={onClose}>
                Cancelar
              </button>
              <button type="button" className="ui-primary" onClick={aplicar}
                disabled={!motivo.trim() || enviando}>
                {enviando ? "Aplicando…" : "Aplicar falta"}
              </button>
            </div>
          </>
        )}
      </div>
    </div>,
    document.body
  );
}
