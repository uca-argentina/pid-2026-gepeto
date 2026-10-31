/**
 * Ayudas de los puntos. Todas las cuentas de verdad (cuánto descuento dan,
 * cuánto se suma) las hace el backend: acá solo se avisa antes de pedirlas.
 */

/** Lo que más suma una franja (la nocturna), para destacarla. Refleja FranjaPuntos del backend. */
export const PUNTOS_MAXIMOS_FRANJA = 20;

export const TIPO_MOVIMIENTO_ETIQUETA = {
  ACREDITACION_FRANJA: "Puntos por reserva",
  BONUS_RACHA: "Bonus por racha",
  CANJE: "Descuento",
  REINTEGRO: "Reintegro",
};

/**
 * Devuelve por qué no se pueden usar esos puntos, o null si se pueden pedir.
 * `maximo` es lo más que se puede usar en esta reserva: el menor entre el
 * saldo y el tope de descuento.
 */
export const validarPuntos = (texto, { saldo, maximo }) => {
  const valor = String(texto ?? "").trim();
  if (valor === "") return "Indicá cuántos puntos querés usar.";
  if (!/^-?\d+([.,]\d+)?$/.test(valor)) return "Escribí un número entero de puntos.";
  const numero = Number(valor.replace(",", "."));
  if (!Number.isInteger(numero)) return "Los puntos se usan enteros, sin decimales.";
  if (numero <= 0) return "Indicá una cantidad de puntos mayor a cero.";
  if (numero > saldo) return `No tenés puntos suficientes: tu saldo es de ${saldo}.`;
  if (numero > maximo) return `Podés usar hasta ${maximo} puntos en esta reserva.`;
  return null;
};

/** "10 %" o "12,5 %". */
export const formatearPorcentaje = (valor) =>
  `${new Intl.NumberFormat("es-AR", { maximumFractionDigits: 2 }).format(Number(valor ?? 0))} %`;

/** "+20" o "−100", con el signo menos tipográfico. */
export const formatearPuntos = (puntos) => (puntos > 0 ? `+${puntos}` : puntos < 0 ? `−${Math.abs(puntos)}` : "0");

export const plural = (cantidad, singular, pluralTexto) => `${cantidad} ${cantidad === 1 ? singular : pluralTexto}`;

/**
 * Lo que se le cuenta al visitante sobre los puntos de una reserva suya:
 * lo que usó, si se le devolvieron, y lo que sumó o va a sumar al ingresar.
 */
export const textosPuntosReserva = (reserva) => {
  const puntos = reserva?.puntos;
  if (!puntos) return [];
  const textos = [];
  if (puntos.puntosUsados > 0) {
    textos.push(`Usaste ${puntos.puntosUsados} puntos: ${formatearPorcentaje(puntos.descuentoPct)} de descuento.`);
    if (puntos.reintegrados) textos.push(`Te devolvimos los ${puntos.puntosUsados} puntos al cancelarla.`);
    else if (reserva.estado === "VENCIDA") textos.push("Venció sin ingreso: los puntos usados no se devuelven.");
  }
  if (puntos.puntosAcreditados > 0) textos.push(`Sumaste ${puntos.puntosAcreditados} puntos al ingresar.`);
  else if (puntos.puntosAGanar > 0 && reserva.estado === "CONFIRMADA") textos.push(`Suma ${puntos.puntosAGanar} puntos al ingresar.`);
  else if (puntos.motivoSinPuntos && ["CONFIRMADA", "EN_CURSO", "FINALIZADA"].includes(reserva.estado)) {
    textos.push(puntos.motivoSinPuntos);
  }
  return textos;
};
