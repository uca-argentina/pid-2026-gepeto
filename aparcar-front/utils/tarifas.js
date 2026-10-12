export const TIPOS_TARIFA = [
  { tipo: "AUTO", nombre: "Auto" },
  { tipo: "MOTO", nombre: "Moto" },
  { tipo: "ACCESIBLE", nombre: "Accesible" },
  { tipo: "CARGA", nombre: "Carga" },
];

export const PERIODOS_TARIFA = [
  { campo: "hora", nombre: "Hora", duracion: "60 minutos" },
  { campo: "fraccion", nombre: "Fracción", duracion: "15 minutos" },
  { campo: "mediaJornada", nombre: "Media jornada", duracion: "12 horas" },
  { campo: "jornadaCompleta", nombre: "Jornada completa", duracion: "24 horas" },
];

export function formatearPrecio(valor) {
  return new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(valor);
}

export function detalleCotizacion(cotizacion) {
  return [
    [cotizacion.jornadas, "jornada de 24 h", "jornadas de 24 h"],
    [cotizacion.mediasJornadas, "media jornada de 12 h", "medias jornadas de 12 h"],
    [cotizacion.horas, "hora", "horas"],
    [cotizacion.fracciones, "fracción de 15 min", "fracciones de 15 min"],
  ].filter(([cantidad]) => cantidad > 0)
    .map(([cantidad, singular, plural]) => `${cantidad} ${cantidad === 1 ? singular : plural}`)
    .join(" + ");
}
