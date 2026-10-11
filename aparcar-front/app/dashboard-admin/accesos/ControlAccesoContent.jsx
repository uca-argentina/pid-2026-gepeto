"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import api from "@/app/api";
import LectorQR from "@/components/LectorQR";
import PatenteVisual from "@/components/PatenteVisual";
import { formatearRango, hora } from "@/utils/franjaHoraria";
import { formatearCodigo, normalizarCodigo, rangoVentanaIngreso } from "@/utils/codigoAcceso";

const PESTANAS = [
  { clave: "escanear", etiqueta: "Escanear QR" },
  { clave: "escribir", etiqueta: "Escribir código" },
];

const ACCION_ETIQUETA = { INGRESO: "Ingreso", EGRESO: "Egreso" };

const MOTIVO_TITULO = {
  ANTICIPADO: "Todavía no puede ingresar",
  VENCIDA: "Reserva vencida",
  CANCELADA: "Reserva cancelada",
  YA_UTILIZADA: "Código ya utilizado",
  PERMANENCIA_MINIMA: "Ingreso recién registrado",
  INGRESO_YA_REGISTRADO: "El ingreso ya estaba registrado",
  SIN_INGRESO: "No tiene ingreso registrado",
  COCHERA_OCUPADA: "La cochera sigue ocupada",
  VEHICULO_EN_CURSO: "El vehículo ya tiene un ingreso registrado",
};

const mensajeDeError = (err, porDefecto) => {
  if (err.response?.status === 409) {
    return "Otro operador registró este código al mismo tiempo. Validalo de nuevo para ver cómo quedó.";
  }
  return err.response?.data?.message || porDefecto;
};

/**
 * Control de acceso en la puerta: el operador escanea el QR del visitante o
 * tipea su código corto, ve de quién es la reserva y qué corresponde (ingreso
 * o egreso), y lo confirma con un botón.
 *
 * Escanear y tipear terminan en la misma consulta. El backend revalida todo al
 * registrar: esta pantalla solo muestra lo que él decide.
 */
export default function ControlAccesoContent() {
  const id = useId();
  const inputRef = useRef(null);
  const [modo, setModo] = useState("escanear");
  const [texto, setTexto] = useState("");
  const [consultando, setConsultando] = useState(false);
  const consultandoRef = useRef(false);
  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState(null);
  const [registrando, setRegistrando] = useState(false);
  // Un ref y no solo estado: dos clics en el mismo cuadro llegan antes de
  // que el botón se re-renderice deshabilitado.
  const registrandoRef = useRef(false);
  const [registro, setRegistro] = useState(null);
  const [movimientos, setMovimientos] = useState([]);
  const [errorMovimientos, setErrorMovimientos] = useState(false);
  // Cuántos códigos se leyeron con la cámara: a partir del primero, el lector
  // se vuelve a prender solo para el siguiente vehículo.
  const [lecturas, setLecturas] = useState(0);

  const cargarMovimientos = useCallback(async () => {
    try {
      const res = await api.get("/api/v1/accesos/movimientos");
      setMovimientos(res.data);
      setErrorMovimientos(false);
    } catch {
      setErrorMovimientos(true);
    }
  }, []);

  useEffect(() => {
    cargarMovimientos();
  }, [cargarMovimientos]);

  const consultar = async (entrada, origen) => {
    // Una lectura tardía o un segundo envío no pueden reemplazar la reserva
    // mientras se está validando o registrando su movimiento.
    if (consultandoRef.current || registrandoRef.current) return;
    const codigo = normalizarCodigo(entrada);
    setResultado(null);
    setRegistro(null);
    if (!codigo) {
      setError(origen === "qr"
        ? "Ese QR no es un código de AparcAR."
        : "El código tiene 10 letras o números, por ejemplo ABCDE-12345.");
      return;
    }

    setError(null);
    consultandoRef.current = true;
    setConsultando(true);
    try {
      const res = await api.post("/api/v1/accesos/consulta", { codigo });
      setResultado(res.data);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo validar el código. Revisá la conexión y volvé a intentar."));
    } finally {
      consultandoRef.current = false;
      setConsultando(false);
    }
  };

  /** Deja la pantalla lista para el siguiente vehículo. */
  const siguiente = () => {
    setResultado(null);
    setTexto("");
    if (modo === "escribir") inputRef.current?.focus();
    else setLecturas((n) => n + 1);
  };

  const registrar = async () => {
    if (consultandoRef.current || registrandoRef.current || !resultado?.valido || !resultado.accion) return;
    registrandoRef.current = true;
    setRegistrando(true);
    try {
      const res = await api.post("/api/v1/accesos/registro", {
        codigo: normalizarCodigo(resultado.codigo),
        accion: resultado.accion,
      });
      setRegistro(res.data);
      setError(null);
      siguiente();
      cargarMovimientos();
    } catch (err) {
      setResultado(null);
      setError(mensajeDeError(err, "No se pudo registrar. Volvé a intentar."));
    } finally {
      registrandoRef.current = false;
      setRegistrando(false);
    }
  };

  const descartar = () => {
    setError(null);
    siguiente();
  };

  const reserva = resultado?.reserva;
  const ocupado = consultando || registrando;

  return (
    <div className="acceso">
      <div className="acceso-encabezado">
        <h1 className="text-3xl font-extrabold tracking-tight text-ink">Control de acceso</h1>
        <p>Escaneá el QR del visitante o escribí su código para registrar el ingreso o la salida.</p>
      </div>

      <div className="acceso-columnas">
        <section className="ui-card acceso-lector" aria-label="Validar un código">
          <div className="acceso-tabs" role="tablist" aria-label="Cómo ingresar el código">
            {PESTANAS.map(({ clave, etiqueta }) => (
              <button
                key={clave}
                type="button"
                role="tab"
                id={`${id}-tab-${clave}`}
                aria-selected={modo === clave}
                aria-controls={`${id}-panel`}
                disabled={ocupado}
                className={`acceso-tab ${modo === clave ? "is-activa" : ""}`}
                onClick={() => {
                  setModo(clave);
                  setError(null);
                }}
              >
                {etiqueta}
              </button>
            ))}
          </div>

          <div id={`${id}-panel`} role="tabpanel" aria-labelledby={`${id}-tab-${modo}`} className="acceso-panel">
            {modo === "escanear" ? (
              <LectorQR
                key={lecturas}
                autoIniciar={lecturas > 0}
                disabled={ocupado}
                onLectura={(leido) => consultar(leido, "qr")}
                onUsarCodigo={() => setModo("escribir")}
              />
            ) : (
              <form
                className="acceso-form"
                onSubmit={(e) => {
                  e.preventDefault();
                  consultar(texto, "texto");
                }}
              >
                <label className="ui-label" htmlFor={`${id}-codigo`}>Código de acceso</label>
                <input
                  ref={inputRef}
                  id={`${id}-codigo`}
                  className="ui-input acceso-input"
                  value={texto}
                  readOnly={ocupado}
                  onChange={(e) => {
                    setTexto(e.target.value.toUpperCase());
                    setResultado(null);
                  }}
                  placeholder="ABCDE-12345"
                  autoComplete="off"
                  autoCapitalize="characters"
                  spellCheck={false}
                  autoFocus
                />
                <p className="acceso-ayuda">Con o sin guion; mayúsculas y espacios no importan.</p>
                <button type="submit" className="ui-primary acceso-boton" disabled={ocupado || !texto.trim()}>
                  {consultando ? "Validando…" : "Validar"}
                </button>
              </form>
            )}
          </div>
        </section>

        <section className="acceso-resultado" aria-label="Resultado" aria-live="polite">
          {consultando && <p className="acceso-cargando" role="status">Validando el código…</p>}

          {registro && (
            <div className={`acceso-aviso ${registro.excedido ? "is-advertencia" : "is-ok"}`} role="status">
              <p className="acceso-aviso-titulo">
                {registro.yaRegistrado
                  ? `${ACCION_ETIQUETA[registro.accion]} ya registrado`
                  : `${ACCION_ETIQUETA[registro.accion]} registrado`}
              </p>
              <p>
                {registro.reserva?.patente} · Cochera {registro.reserva?.cocheraNumero} · {hora(new Date(registro.fecha))}
              </p>
              <p>{registro.mensaje}</p>
            </div>
          )}

          {error && (
            <div className="acceso-aviso is-error" role="alert">
              <p className="acceso-aviso-titulo">No se puede usar el código</p>
              <p>{error}</p>
            </div>
          )}

          {resultado && (
            <article
              className={`ui-card acceso-tarjeta ${resultado.valido ? "is-valida" : "is-invalida"}`}
              aria-labelledby={`${id}-resultado`}
            >
              <p className="acceso-tarjeta-codigo">{formatearCodigo(normalizarCodigo(resultado.codigo))}</p>
              <h2 id={`${id}-resultado`} className="acceso-tarjeta-titulo">
                {resultado.valido
                  ? `Corresponde: ${ACCION_ETIQUETA[resultado.accion].toUpperCase()}`
                  : MOTIVO_TITULO[resultado.motivo] ?? "No se puede usar el código"}
              </h2>
              {!resultado.valido && <p className="acceso-tarjeta-motivo" role="alert">{resultado.mensaje}</p>}

              {reserva && (
                <dl className="acceso-datos">
                  <div>
                    <dt>Visitante</dt>
                    <dd>{reserva.visitanteNombre}</dd>
                  </div>
                  <div>
                    <dt>Vehículo</dt>
                    <dd><PatenteVisual patente={reserva.patente} tipo={reserva.tipoVehiculo} tamano="sm" /></dd>
                  </div>
                  <div>
                    <dt>Cochera</dt>
                    <dd>{reserva.cocheraNumero} · {reserva.cocheraSector}</dd>
                  </div>
                  <div>
                    <dt>Horario</dt>
                    <dd>{formatearRango(reserva.desde, reserva.hasta)}</dd>
                  </div>
                  {reserva.estado === "CONFIRMADA" && (
                    <div className="acceso-datos-ancho">
                      <dt>Ventana de ingreso</dt>
                      <dd>Entre {rangoVentanaIngreso(reserva)}</dd>
                    </div>
                  )}
                  {reserva.ingresoAt && (
                    <div className="acceso-datos-ancho">
                      <dt>Ingresó</dt>
                      <dd>{hora(new Date(reserva.ingresoAt))}</dd>
                    </div>
                  )}
                </dl>
              )}

              {resultado.valido && reserva?.minutosExceso != null && (
                <p className="acceso-exceso">Se pasó {reserva.minutosExceso} minutos de su horario.</p>
              )}

              <div className="acceso-acciones">
                {resultado.valido && (
                  <button type="button" className="ui-primary acceso-boton" onClick={registrar} disabled={registrando}>
                    {registrando
                      ? "Registrando…"
                      : resultado.accion === "INGRESO" ? "Registrar ingreso" : "Registrar egreso"}
                  </button>
                )}
                <button type="button" className="acceso-boton-secundario" onClick={descartar} disabled={registrando}>
                  {resultado.valido ? "Cancelar" : "Validar otro código"}
                </button>
              </div>
            </article>
          )}

          {!consultando && !resultado && !error && !registro && (
            <p className="acceso-vacio">Todavía no se validó ningún código.</p>
          )}
        </section>
      </div>

      <section className="ui-card acceso-movimientos" aria-labelledby={`${id}-movimientos`}>
        <div className="acceso-movimientos-cabecera">
          <h2 id={`${id}-movimientos`}>Movimientos de hoy</h2>
          <button type="button" className="acceso-boton-secundario" onClick={cargarMovimientos}>Actualizar</button>
        </div>
        {errorMovimientos ? (
          <p className="acceso-vacio" role="alert">No se pudieron cargar los movimientos.</p>
        ) : movimientos.length === 0 ? (
          <p className="acceso-vacio">Todavía no hubo ingresos ni egresos hoy.</p>
        ) : (
          <ul className="acceso-movimientos-lista" aria-label="Ingresos y egresos de hoy">
            {movimientos.map((m) => (
              <li key={m.id}>
                <time dateTime={m.fecha}>{hora(new Date(m.fecha))}</time>
                <span className={`acceso-movimiento-accion is-${m.accion.toLowerCase()}`}>{ACCION_ETIQUETA[m.accion]}</span>
                <PatenteVisual patente={m.patente} tipo={m.tipoVehiculo} tamano="sm" />
                <span className="acceso-movimiento-datos">
                  Cochera {m.cocheraNumero} · {m.visitanteNombre}
                  {m.minutosExceso != null && <strong> · excedido {m.minutosExceso} min</strong>}
                </span>
                <span className="acceso-movimiento-operador">{m.operador}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
