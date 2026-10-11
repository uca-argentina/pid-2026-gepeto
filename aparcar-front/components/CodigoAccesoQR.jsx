"use client";

import { useEffect, useState } from "react";
import { formatearCodigo, payloadQR } from "@/utils/codigoAcceso";

/**
 * El QR del código de acceso, generado en el navegador.
 *
 * `qrcode` se importa recién acá y a demanda: solo lo descarga quien abre un
 * código, no todo el que entra al panel. Los colores son fijos (negro sobre
 * blanco, con margen blanco alrededor) y no siguen el tema: un QR invertido o
 * de poco contraste muchos lectores no lo leen.
 */
export default function CodigoAccesoQR({ codigo }) {
  const [estado, setEstado] = useState({ codigo: null, src: null, error: false });

  useEffect(() => {
    let vigente = true;
    import("qrcode")
      .then((modulo) => (modulo.default ?? modulo).toString(payloadQR(codigo), {
        type: "svg",
        errorCorrectionLevel: "M",
        margin: 4,
        color: { dark: "#000000", light: "#ffffff" },
      }))
      .then((svg) => {
        if (vigente) setEstado({ codigo, src: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`, error: false });
      })
      .catch(() => {
        if (vigente) setEstado({ codigo, src: null, error: true });
      });
    return () => {
      vigente = false;
    };
  }, [codigo]);

  // Mientras se genera el de un código nuevo, no se muestra el anterior.
  const actual = estado.codigo === codigo ? estado : { src: null, error: false };

  return (
    <div className="qr-acceso">
      {actual.src ? (
        // Es un data URI generado acá: next/image no aporta nada.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={actual.src} alt={`Código QR de acceso ${formatearCodigo(codigo)}`} width="240" height="240" />
      ) : actual.error ? (
        <p className="qr-acceso-error" role="alert">
          No se pudo dibujar el QR. Mostrá el código escrito de abajo: sirve igual.
        </p>
      ) : (
        <div className="qr-acceso-cargando animate-spin" role="status" aria-label="Generando el código QR" />
      )}
    </div>
  );
}
