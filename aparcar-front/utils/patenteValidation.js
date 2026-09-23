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