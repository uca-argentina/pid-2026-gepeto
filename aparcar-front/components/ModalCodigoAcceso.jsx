"use client";

import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { toast } from "sonner";
import CodigoAccesoQR from "@/components/CodigoAccesoQR";
import PatenteVisual from "@/components/PatenteVisual";
import { formatearRango, hora } from "@/utils/franjaHoraria";
import { formatearCodigo, textoVentanaIngreso } from "@/utils/codigoAcceso";

const ENFOCABLES = 'button:not([disabled]), [href], input:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * El código de acceso de una reserva, para mostrar en la entrada: el QR, el
 * código corto (por si no se puede escanear) y los datos de la reserva.
 *
 * Es un diálogo modal accesible: atrapa el foco, cierra con Escape o con el
 * fondo, y al cerrar devuelve el foco a quien lo abrió. Va por portal al
 * <body> porque el listado usa container queries, y un contenedor así se
 * vuelve el bloque contenedor de todo lo `position: fixed` que tenga adentro.
 */
export default function ModalCodigoAcceso({ reserva, onClose, titulo = "Tu código de acceso" }) {
  const id = useId();
  const dialogoRef = useRef(null);
  const cerrarRef = useRef(null);
  // Se lee en el cleanup; una ref evita re-suscribir el teclado en cada render.
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

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

  const codigo = reserva.acceso?.codigo;
  const codigoVisible = formatearCodigo(codigo);
  const enCurso = reserva.estado === "EN_CURSO";

  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(codigoVisible);
      toast.success("Código copiado");
    } catch {
      toast.error("No se pudo copiar el código. Anotalo a mano.");
    }
  };

  return createPortal(
    <div className="modal-fondo" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={dialogoRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-titulo`}
        aria-describedby={`${id}-ayuda`}
        className="modal-acceso ui-card"
      >
        <div className="modal-acceso-cabecera">
          <h2 id={`${id}-titulo`}>{titulo}</h2>
          <button ref={cerrarRef} type="button" className="modal-acceso-x" onClick={onClose} aria-label="Cerrar ventana">
            ×
          </button>
        </div>
        <p id={`${id}-ayuda`} className="modal-acceso-ayuda">
          Mostralo en la entrada del estacionamiento. Es el mismo código para entrar y para salir.
        </p>

        {codigo ? (
          <>
            <CodigoAccesoQR codigo={codigo} />

            <div className="modal-acceso-codigo">
              <span className="modal-acceso-codigo-etiqueta">Código</span>
              <output className="modal-acceso-codigo-valor" aria-label={`Código ${codigoVisible}`}>
                {codigoVisible}
              </output>
              <button type="button" className="ui-primary modal-acceso-copiar" onClick={copiar}>
                Copiar
              </button>
            </div>

            <p className="modal-acceso-ventana" role="note">
              {enCurso
                ? `Ingresaste a las ${reserva.acceso?.ingresoAt ? hora(new Date(reserva.acceso.ingresoAt)) : "—"}. Mostrá este mismo código para salir.`
                : `${textoVentanaIngreso(reserva.acceso)}.`}
            </p>
          </>
        ) : (
          <p className="modal-acceso-ventana" role="note">Esta reserva no tiene un código de acceso vigente.</p>
        )}

        <dl className="modal-acceso-datos">
          <div>
            <dt>Vehículo</dt>
            <dd><PatenteVisual patente={reserva.vehiculo?.patente} tipo={reserva.vehiculo?.tipo} tamano="sm" /></dd>
          </div>
          <div>
            <dt>Cochera</dt>
            <dd>{reserva.cochera?.numero ?? "—"} · {reserva.cochera?.sector ?? "Sin sector"}</dd>
          </div>
          <div>
            <dt>Horario</dt>
            <dd>{formatearRango(reserva.desde, reserva.hasta) || "—"}</dd>
          </div>
        </dl>

        <button type="button" className="modal-acceso-cerrar" onClick={onClose}>
          Cerrar
        </button>
      </div>
    </div>,
    document.body
  );
}
