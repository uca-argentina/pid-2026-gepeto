// `estados` es lo que entra en cada filtro: "Activas" incluye las reservas con
// el vehículo adentro (EN_CURSO), que siguen ocupando la cochera.
export const FILTROS_RESERVAS = [
  { clave: "CONFIRMADA", etiqueta: "Activas", vacio: "No hay reservas activas.", estados: ["CONFIRMADA", "EN_CURSO"] },
  { clave: "FINALIZADA", etiqueta: "Finalizadas", vacio: "No hay reservas finalizadas.", estados: ["FINALIZADA"] },
  { clave: "VENCIDA", etiqueta: "Vencidas", vacio: "No hay reservas vencidas.", estados: ["VENCIDA"] },
  { clave: "CANCELADA", etiqueta: "Canceladas", vacio: "No hay reservas canceladas ni deshabilitadas.", estados: ["CANCELADA"] },
  { clave: "TODAS", etiqueta: "Todas", vacio: "Todavía no hay reservas cargadas." },
];

const FILTRO_DE_ESTADO = Object.fromEntries(
  FILTROS_RESERVAS.flatMap(({ clave, estados = [] }) => estados.map((estado) => [estado, clave]))
);

export const ORDENES_RESERVAS = [
  { clave: "INICIO_ASC", etiqueta: "Inicio: orden cronológico", campo: "desde", direccion: 1 },
  { clave: "INICIO_DESC", etiqueta: "Inicio: orden inverso", campo: "desde", direccion: -1 },
  { clave: "ALTA_DESC", etiqueta: "Alta: más recientes primero", campo: "fechaCreacion", direccion: -1 },
  { clave: "ALTA_ASC", etiqueta: "Alta: más antiguas primero", campo: "fechaCreacion", direccion: 1 },
];

const fechaValida = (valor) => {
  const fecha = valor ? new Date(valor).getTime() : NaN;
  return Number.isFinite(fecha) ? fecha : null;
};

// Una fecha histórica ausente queda al final en ambos sentidos, sin inventarla.
const compararFechas = (a, b, direccion) => {
  const primera = fechaValida(a);
  const segunda = fechaValida(b);
  if (primera === null) return segunda === null ? 0 : 1;
  if (segunda === null) return -1;
  return (primera - segunda) * direccion;
};

/** Solo presentación: el estado y los vencimientos siguen siendo responsabilidad del backend. */
export const seleccionarReservas = (reservas, filtro, orden) => {
  const cantidades = { TODAS: reservas.length, CONFIRMADA: 0, FINALIZADA: 0, VENCIDA: 0, CANCELADA: 0 };
  for (const reserva of reservas) {
    const clave = FILTRO_DE_ESTADO[reserva.estado];
    if (clave) cantidades[clave] += 1;
  }
  const criterio = ORDENES_RESERVAS.find((opcion) => opcion.clave === orden) ?? ORDENES_RESERVAS[0];
  const visibles = reservas
    .filter((reserva) => filtro === "TODAS" || FILTRO_DE_ESTADO[reserva.estado] === filtro)
    .sort((a, b) =>
      compararFechas(a[criterio.campo], b[criterio.campo], criterio.direccion)
      || compararFechas(a.fechaCreacion, b.fechaCreacion, -1)
      || String(a.id).localeCompare(String(b.id))
    );
  return { visibles, cantidades };
};

const formatoRegistro = new Intl.DateTimeFormat("es-AR", {
  dateStyle: "short", timeStyle: "short", hour12: false,
  timeZone: "America/Argentina/Buenos_Aires",
});

export const formatearFechaRegistro = (fecha) => {
  const valor = fechaValida(fecha);
  return valor === null ? "Sin fecha registrada" : formatoRegistro.format(valor);
};
