/**
 * Código corto de acceso de una reserva: el mismo valor que lleva el QR.
 *
 * Son 10 caracteres base32 al estilo Crockford (sin I, L, O ni U) que se
 * muestran como `XXXXX-XXXXX`. El QR lleva `APARCAR:` delante; el lector lo
 * ignora. Escanear y tipear terminan en la misma consulta al backend, que es
 * quien decide si el código vale: esto solo prepara lo que se manda.
 */

import { hora } from "@/utils/franjaHoraria";

export const PREFIJO_QR = "APARCAR:";
export const LONGITUD_CODIGO = 10;
const ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
// Equivalencias de Crockford: lo que se confunde al leer o dictar.
const EQUIVALENCIAS = { I: "1", L: "1", O: "0" };

/**
 * Lleva lo escaneado o tipeado a la forma guardada: sin el prefijo del QR, en
 * mayúsculas, sin espacios ni guiones. Devuelve null si no quedan 10
 * caracteres válidos.
 */
export const normalizarCodigo = (entrada) => {
  if (typeof entrada !== "string") return null;
  let texto = entrada.trim().toUpperCase();
  if (texto.startsWith(PREFIJO_QR)) texto = texto.slice(PREFIJO_QR.length);

  let codigo = "";
  for (const c of texto) {
    if (c === "-" || /\s/.test(c)) continue;
    const equivalente = EQUIVALENCIAS[c] ?? c;
    if (!ALFABETO.includes(equivalente)) return null;
    codigo += equivalente;
  }
  return codigo.length === LONGITUD_CODIGO ? codigo : null;
};

/** `ABCDE12345` → `ABCDE-12345`. */
export const formatearCodigo = (codigo) =>
  codigo && codigo.length === LONGITUD_CODIGO ? `${codigo.slice(0, 5)}-${codigo.slice(5)}` : codigo ?? "";

/** Lo que va adentro del QR. */
export const payloadQR = (codigo) => `${PREFIJO_QR}${codigo}`;

/**
 * La ventana de ingreso como rango: "14:45 y 15:30". Si cruza de día, se
 * nombran las fechas: "el 10/10 a las 23:45 y el 11/10 a las 00:30".
 */
export const rangoVentanaIngreso = (acceso) => {
  if (!acceso?.ingresoDesde || !acceso?.ingresoHasta) return "";
  const desde = new Date(acceso.ingresoDesde);
  const hasta = new Date(acceso.ingresoHasta);
  const fecha = (d) => `${d.getDate()}/${d.getMonth() + 1}`;
  return desde.toDateString() === hasta.toDateString()
    ? `${hora(desde)} y ${hora(hasta)}`
    : `el ${fecha(desde)} a las ${hora(desde)} y el ${fecha(hasta)} a las ${hora(hasta)}`;
};

/** Lo que lee el visitante: "Ingresá entre 14:45 y 15:30". */
export const textoVentanaIngreso = (acceso) => {
  const rango = rangoVentanaIngreso(acceso);
  return rango ? `Ingresá entre ${rango}` : "";
};

/** Las reservas que todavía tienen un código que sirve. */
export const tieneCodigoVigente = (reserva) =>
  Boolean(reserva?.acceso?.codigo) && ["CONFIRMADA", "EN_CURSO"].includes(reserva.estado);
