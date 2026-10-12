"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/** Cada cuánto se analiza un cuadro del video. */
const INTERVALO_MS = 200;

export const MENSAJES_CAMARA = {
  INSEGURO:
    "La cámara solo funciona en una conexión segura (HTTPS) o en localhost. Usá el código escrito.",
  SIN_SOPORTE: "Este navegador no permite usar la cámara. Usá el código escrito.",
  PERMISO: "No diste permiso para usar la cámara. Habilitalo en el navegador o usá el código escrito.",
  SIN_CAMARA: "No se encontró ninguna cámara en este dispositivo. Usá el código escrito.",
  OCUPADA: "La cámara está siendo usada por otra aplicación. Cerrala o usá el código escrito.",
  OTRO: "No se pudo iniciar la cámara. Usá el código escrito.",
};

/** Por qué no se puede usar la cámara antes de pedirla, o null si se puede intentar. */
export const problemaDeEntorno = () => {
  if (typeof window === "undefined") return null;
  if (window.isSecureContext === false) return MENSAJES_CAMARA.INSEGURO;
  if (!navigator.mediaDevices?.getUserMedia) return MENSAJES_CAMARA.SIN_SOPORTE;
  return null;
};

const mensajeDeError = (error) => {
  switch (error?.name) {
    case "NotAllowedError":
    case "SecurityError":
      return MENSAJES_CAMARA.PERMISO;
    case "NotFoundError":
    case "OverconstrainedError":
      return MENSAJES_CAMARA.SIN_CAMARA;
    case "NotReadableError":
      return MENSAJES_CAMARA.OCUPADA;
    default:
      return MENSAJES_CAMARA.OTRO;
  }
};

/**
 * Arma la función que busca un QR en un cuadro del video.
 *
 * Primero `BarcodeDetector`, si el navegador lo ofrece y soporta QR.
 * Donde no existe o falla al decodificar, `jsqr`: JavaScript puro, sin
 * dependencias nativas, que se descarga recién acá y solo en ese caso.
 */
export async function crearDetector(canvas) {
  if (typeof window !== "undefined" && "BarcodeDetector" in window) {
    try {
      const formatos = (await window.BarcodeDetector.getSupportedFormats?.()) ?? ["qr_code"];
      if (formatos.includes("qr_code")) {
        const detector = new window.BarcodeDetector({ formats: ["qr_code"] });
        let alternativo;
        return async (video) => {
          if (!alternativo) {
            try {
              return (await detector.detect(video))[0]?.rawValue ?? null;
            } catch {
              // Algunos navegadores exponen la API pero no pueden leer video.
              alternativo = await crearDetectorJsQR(canvas);
            }
          }
          return alternativo(video);
        };
      }
    } catch {
      // Si el nativo falla, se sigue con jsqr.
    }
  }

  return crearDetectorJsQR(canvas);
}

async function crearDetectorJsQR(canvas) {
  const { default: jsQR } = await import("jsqr");
  return async (video) => {
    const ancho = video.videoWidth;
    const alto = video.videoHeight;
    if (!ancho || !alto) return null;
    canvas.width = ancho;
    canvas.height = alto;
    const contexto = canvas.getContext("2d", { willReadFrequently: true });
    if (!contexto) return null;
    contexto.drawImage(video, 0, 0, ancho, alto);
    const imagen = contexto.getImageData(0, 0, ancho, alto);
    return jsQR(imagen.data, ancho, alto, { inversionAttempts: "dontInvert" })?.data ?? null;
  };
}

/**
 * Lector de QR con la cámara.
 *
 * La cámara se pide recién cuando el operador la activa (o, con `autoIniciar`,
 * cuando ya la había activado antes y vuelve para el siguiente vehículo), se
 * prefiere la trasera, y se libera al leer un código, al detenerla y al salir
 * de la pantalla. Lo leído se entrega tal cual: validarlo es cosa del backend.
 */
export default function LectorQR({ onLectura, onUsarCodigo, autoIniciar = false, disabled = false }) {
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const streamRef = useRef(null);
  const temporizadorRef = useRef(null);
  // Cada pedido de cámara tiene su número. Detener, salir de la pantalla o
  // volver a activar lo invalidan: si la cámara llega tarde (el navegador
  // estaba pidiendo permiso), se libera en vez de quedar prendida.
  const intentoRef = useRef(0);
  const [estado, setEstado] = useState("inactivo");
  const [error, setError] = useState(null);

  const detener = useCallback(() => {
    intentoRef.current += 1;
    clearTimeout(temporizadorRef.current);
    temporizadorRef.current = null;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
  }, []);

  const activar = useCallback(async () => {
    if (disabled) return;
    const problema = problemaDeEntorno();
    if (problema) {
      setError(problema);
      setEstado("error");
      return;
    }

    detener();
    const intento = intentoRef.current;
    const vigente = () => intento === intentoRef.current;
    setError(null);
    setEstado("iniciando");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" } },
        audio: false,
      });
      if (!vigente()) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      streamRef.current = stream;
      const video = videoRef.current;
      video.srcObject = stream;
      await video.play?.();
      if (!vigente()) return;
      const detectar = await crearDetector(canvasRef.current);
      if (!vigente()) return;
      setEstado("escaneando");

      const analizar = async () => {
        if (!vigente()) return;
        let texto = null;
        try {
          texto = await detectar(video);
        } catch {
          // Un cuadro que no se pudo analizar no corta el escaneo.
        }
        if (!vigente()) return;
        if (texto) {
          detener();
          setEstado("inactivo");
          onLectura(texto);
          return;
        }
        temporizadorRef.current = setTimeout(analizar, INTERVALO_MS);
      };
      analizar();
    } catch (e) {
      if (!vigente()) return;
      detener();
      setError(mensajeDeError(e));
      setEstado("error");
    }
  }, [detener, onLectura, disabled]);

  // Avisa de entrada si la cámara no va a funcionar, sin esperar al clic.
  useEffect(() => {
    const problema = problemaDeEntorno();
    if (problema) {
      setError(problema);
      setEstado("error");
    } else if (autoIniciar) {
      activar();
    }
    // Solo al montar: autoIniciar vale para esta instancia.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Validar o registrar pausa también una cámara ya activa o con permiso
  // pendiente: una lectura tardía no debe consumir el siguiente código.
  useEffect(() => {
    if (disabled) {
      detener();
      setEstado((actual) => actual === "error" ? actual : "inactivo");
    }
  }, [disabled, detener]);

  // Al salir de la pantalla, la cámara se apaga.
  useEffect(() => detener, [detener]);

  const escaneando = estado === "escaneando" || estado === "iniciando";

  return (
    <div className="lector-qr">
      <div className={`lector-qr-visor ${escaneando ? "is-activo" : ""}`}>
        {/* El video va siempre en el DOM: la cámara se le conecta al activarla. */}
        <video ref={videoRef} muted playsInline aria-label="Vista de la cámara" />
        <canvas ref={canvasRef} hidden />
        {escaneando && <span className="lector-qr-marco" aria-hidden="true" />}
        {!escaneando && (
          <p className="lector-qr-reposo">
            {estado === "error" ? "Cámara no disponible" : "La cámara está apagada"}
          </p>
        )}
      </div>

      <p className="lector-qr-estado" role="status">
        {estado === "iniciando"
          ? "Iniciando la cámara…"
          : estado === "escaneando"
            ? "Apuntá la cámara al código QR del visitante."
            : ""}
      </p>

      {error && (
        <div className="lector-qr-error" role="alert">
          <p>{error}</p>
          {onUsarCodigo && (
            <button type="button" className="lector-qr-boton" onClick={onUsarCodigo} disabled={disabled}>
              Escribir el código
            </button>
          )}
        </div>
      )}

      {escaneando ? (
        <button
          type="button"
          className="lector-qr-boton"
          onClick={() => {
            detener();
            setEstado("inactivo");
          }}
        >
          Detener cámara
        </button>
      ) : (
        estado !== "error" || error !== MENSAJES_CAMARA.INSEGURO ? (
          <button type="button" className="ui-primary lector-qr-activar" onClick={activar} disabled={disabled}>
            {estado === "error" ? "Reintentar con la cámara" : "Activar cámara"}
          </button>
        ) : null
      )}
    </div>
  );
}
