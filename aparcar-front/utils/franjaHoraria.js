/**
 * Helpers para la franja horaria de una reserva.
 *
 * Las reservas se toman en bloques de 15 minutos: tanto el formulario del
 * visitante como el alta del admin trabajan sobre esa grilla.
 */

/** Tamaño del bloque. Todo lo demás se deriva de acá. */
export const PASO_MINUTOS = 15;

/**
 * Convierte un Date al formato que espera datetime-local: "YYYY-MM-DDTHH:mm".
 *
 * El input trabaja en hora local, pero toISOString() devuelve UTC. Sin restar
 * el offset, alguien en Argentina (UTC-3) vería el campo tres horas adelantado.
 */
export const aInputLocal = (date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
};

/** Baja un Date al bloque de 15 en el que cae (14:07 -> 14:00). */
export const alBloque = (date) => {
  const d = new Date(date);
  d.setSeconds(0, 0);
  d.setMinutes(Math.floor(d.getMinutes() / PASO_MINUTOS) * PASO_MINUTOS);
  return d;
};

/**
 * Baja un valor "YYYY-MM-DDTHH:mm" al bloque de 15 minutos en el que cae.
 *
 * El atributo `step` del input ofrece los bloques y marca como inválido
 * cualquier horario intermedio, pero no impide escribirlo a mano: esto es lo
 * que garantiza que lo que sale del formulario siempre caiga en un bloque.
 */
export const alBloqueLocal = (valor) => {
  if (!valor) return valor;
  const [dia, horaTexto = ""] = valor.split("T");
  const [hh, mm] = horaTexto.split(":");
  if (hh === undefined || mm === undefined) return valor;
  const bloque = Math.floor(Number(mm) / PASO_MINUTOS) * PASO_MINUTOS;
  if (Number.isNaN(bloque)) return valor;
  return `${dia}T${hh.padStart(2, "0")}:${String(bloque).padStart(2, "0")}`;
};

/**
 * El bloque en curso: dónde arranca una reserva nueva.
 *
 * Se redondea hacia abajo, no hacia arriba, justamente para que cubra "ahora":
 * si son las 14:07 y arrancara en 14:15, los ocho minutos en los que el auto
 * ya está estacionado quedarían sin reserva.
 */
export const bloqueActual = () => alBloque(new Date());

/** Minutos entre dos horarios. */
export const minutosEntre = (desde, hasta) =>
  Math.round((new Date(hasta) - new Date(desde)) / 60000);

// ---------------------------------------------------------------- modalidad
//
// El sistema reconoce tres formas de reservar. La clasificación la hace el
// backend (`ModalidadReserva.java`) a partir de la duración y viaja en la
// respuesta; acá solo se traduce a texto para mostrarla. Si la dedujera
// también el frontend, al cambiar un umbral los dos lados discreparían.

export const MODALIDADES = {
  FRANJA: { clave: "FRANJA", etiqueta: "Por franja horaria" },
  MEDIA_JORNADA: { clave: "MEDIA_JORNADA", etiqueta: "Media jornada" },
  JORNADA_COMPLETA: { clave: "JORNADA_COMPLETA", etiqueta: "Jornada completa" },
};

/** Traduce la clave que manda el backend a texto para mostrar. */
export const MODALIDAD_ETIQUETA = Object.fromEntries(
  Object.values(MODALIDADES).map((m) => [m.clave, m.etiqueta])
);

const dosDigitos = (n) => String(n).padStart(2, "0");
const dia = (x) => `${dosDigitos(x.getDate())}/${dosDigitos(x.getMonth() + 1)}`;
export const hora = (x) => `${dosDigitos(x.getHours())}:${dosDigitos(x.getMinutes())}`;

/**
 * Muestra la franja de forma legible. Si empieza y termina el mismo día,
 * alcanza con nombrarlo una vez.
 *
 * Se arma a mano en vez de con toLocaleString porque el locale es-AR devuelve
 * reloj de 12 horas ("10:00 a. m."), que para horarios de cochera es peor de
 * leer, y además el formato varía según el ICU que tenga instalado el entorno.
 */
export const formatearRango = (desde, hasta) => {
  if (!desde || !hasta) return "";
  const d = new Date(desde);
  const h = new Date(hasta);
  return dia(d) === dia(h)
    ? `${dia(d)} de ${hora(d)} a ${hora(h)}`
    : `${dia(d)} ${hora(d)} → ${dia(h)} ${hora(h)}`;
};

/** Valor por defecto: el bloque en curso, por una hora. */
export const franjaPorDefecto = () => {
  const desde = bloqueActual();
  const hasta = new Date(desde.getTime() + 60 * 60 * 1000);
  return { desde: aInputLocal(desde), hasta: aInputLocal(hasta) };
};
