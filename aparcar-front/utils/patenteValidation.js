// Formatos de patente vigentes en Argentina. Auto y Carga comparten el mismo
// esquema (mismo supuesto que ya usa el backend); Moto usa uno distinto e
// incompatible.
const AUTO_ANTERIOR = /^[A-Za-z]{3}[0-9]{3}$/;
const AUTO_MERCOSUR = /^[A-Za-z]{2}[0-9]{3}[A-Za-z]{2}$/;
const MOTO_ANTERIOR = /^[0-9]{3}[A-Za-z]{3}$/;
const MOTO_MERCOSUR = /^[A-Za-z][0-9]{3}[A-Za-z]{3}$/;

export function formatoPatenteValido(patente, tipo) {
  if (!patente || !tipo) return true; // otros checks (required, enum) ya lo marcan
  const p = patente.trim().toUpperCase();

  if (tipo === "MOTO") {
    return MOTO_ANTERIOR.test(p) || MOTO_MERCOSUR.test(p);
  }

  return AUTO_ANTERIOR.test(p) || AUTO_MERCOSUR.test(p);
}

export const MENSAJE_FORMATO_INVALIDO = {
  AUTO: "Formato inválido para auto/carga (ej: ABC123 o AB123CD)",
  CARGA: "Formato inválido para auto/carga (ej: ABC123 o AB123CD)",
  MOTO: "Formato inválido para moto (ej: 123ABC o A123BCD)",
};

/**
 * Saca espacios y guiones y pasa a mayúsculas. La ayuda del alta muestra las
 * patentes separadas en bloques ("AB 123 CD"), como en la chapa real, así que
 * es esperable que se escriban así; el backend solo pasa a mayúsculas y
 * compara contra el formato compacto, por eso se normaliza antes de enviar.
 */
export function normalizarPatente(patente) {
  return (patente ?? "").replace(/[\s-]+/g, "").toUpperCase();
}

/**
 * Qué diseño de chapa le corresponde a una patente. Los cuatro formatos son
 * excluyentes entre sí, así que alcanza con la patente; devuelve null si no
 * coincide con ninguno.
 */
export function detectarFormatoPatente(patente) {
  const p = normalizarPatente(patente);
  if (AUTO_MERCOSUR.test(p)) return "AUTO_MERCOSUR";
  if (AUTO_ANTERIOR.test(p)) return "AUTO_ANTERIOR";
  if (MOTO_MERCOSUR.test(p)) return "MOTO_MERCOSUR";
  if (MOTO_ANTERIOR.test(p)) return "MOTO_ANTERIOR";
  return null;
}

/**
 * Ejemplos para la leyenda del alta, según el tipo de vehículo. Auto y Carga
 * comparten formato; cualquier tipo que no sea MOTO usa el de auto.
 */
export function ejemplosPatente(tipo) {
  return tipo === "MOTO"
    ? { actual: "A 123 BCD", anterior: "123 ABC" }
    : { actual: "AB 123 CD", anterior: "ABC 123" };
}

/** Mensaje de formato inválido coherente con la leyenda de ejemplosPatente. */
export function mensajeFormatoInvalido(tipo) {
  const { actual, anterior } = ejemplosPatente(tipo);
  const etiqueta = tipo === "MOTO" ? "moto" : "auto/carga";
  return `Formato inválido para ${etiqueta}. Usá ${actual} (actual) o ${anterior} (anterior)`;
}