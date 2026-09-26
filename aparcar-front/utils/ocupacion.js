/**
 * Ocupación de cocheras vista por día.
 *
 * El panel del admin responde una pregunta distinta a la del formulario de
 * reserva: no "¿qué está libre para esta franja?" sino "¿qué hay ocupado este
 * día, y de quién es". Por eso agrupa por tipo o por piso en vez de listar.
 *
 * Los grupos se derivan de las cocheras que existen en la base, no de una lista
 * escrita acá: si mañana aparece un piso nuevo o un tipo nuevo de cochera, la
 * pantalla lo muestra sin que haya que tocar este archivo.
 */

import { MODALIDAD_ETIQUETA, formatearRango, hora } from "@/utils/franjaHoraria";

/**
 * Los estados que ocupan la cochera.
 *
 * FINALIZADA entra porque el admin puede moverse a días pasados, y ahí las
 * reservas ya vencieron: si solo se mirara CONFIRMADA, ayer aparecería vacío.
 * CANCELADA no ocupa nunca, que es justamente lo que significa cancelar.
 */
export const ESTADOS_QUE_OCUPAN = ["CONFIRMADA", "FINALIZADA"];

const dosDigitos = (n) => String(n).padStart(2, "0");

/** Un Date al día calendario "YYYY-MM-DD", en hora local. */
export const aDiaLocal = (date) =>
  `${date.getFullYear()}-${dosDigitos(date.getMonth() + 1)}-${dosDigitos(date.getDate())}`;

/** El día de hoy. Es el valor con el que arranca el panel. */
export const hoy = () => aDiaLocal(new Date());

/**
 * Corre un día "YYYY-MM-DD" la cantidad indicada.
 *
 * Se construye el Date con los tres números por separado y no parseando el
 * texto, porque `new Date("2026-09-26")` se interpreta como UTC y en UTC-3
 * devuelve el día anterior.
 */
export const sumarDias = (dia, cantidad) => {
  const [a, m, d] = dia.split("-").map(Number);
  return aDiaLocal(new Date(a, m - 1, d + cantidad));
};

const DIAS = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
const MESES = [
  "enero", "febrero", "marzo", "abril", "mayo", "junio",
  "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
];

/**
 * El día en palabras, para el encabezado.
 *
 * Se arma a mano por lo mismo que `formatearRango`: el locale es-AR cambia de
 * forma según el ICU que tenga instalado el entorno, y acá el texto tiene que
 * ser estable.
 */
export const diaEnPalabras = (dia) => {
  const [a, m, d] = dia.split("-").map(Number);
  const fecha = new Date(a, m - 1, d);
  return `${DIAS[fecha.getDay()]} ${d} de ${MESES[m - 1]}`;
};

/** Etiqueta relativa, cuando la hay: es más rápido de leer que la fecha. */
export const diaRelativo = (dia) => {
  const h = hoy();
  if (dia === h) return "Hoy";
  if (dia === sumarDias(h, 1)) return "Mañana";
  if (dia === sumarDias(h, -1)) return "Ayer";
  return null;
};

/**
 * Si la reserva toca el día.
 *
 * Es el mismo test de intersección de intervalos semiabiertos que usa el
 * backend para decidir si dos reservas se pisan, aplicado contra el día:
 * `desde < finDelDia AND hasta > inicioDelDia`. Que sea semiabierto es lo que
 * hace que una reserva que termina a las 00:00 no cuente en el día siguiente,
 * y que una jornada completa a caballo de la medianoche sí aparezca en los dos.
 */
export const ocupaElDia = (reserva, dia) => {
  if (!reserva?.desde || !reserva?.hasta) return false;
  const inicio = `${dia}T00:00`;
  const fin = `${sumarDias(dia, 1)}T00:00`;
  return reserva.desde < fin && reserva.hasta > inicio;
};

/**
 * Cómo se lee el horario de una reserva dentro de un día.
 *
 * Si empieza antes o termina después, se marca con una flecha en vez de
 * mostrar la hora de otro día, que es la forma corta de decir "viene de ayer"
 * o "sigue mañana" sin obligar a leer dos fechas.
 */
export const horarioEnElDia = (reserva, dia) => {
  const empiezaAntes = reserva.desde < `${dia}T00:00`;
  const terminaDespues = reserva.hasta > `${sumarDias(dia, 1)}T00:00`;
  const desde = empiezaAntes ? "←" : hora(new Date(reserva.desde));
  const hasta = terminaDespues ? "→" : hora(new Date(reserva.hasta));
  return {
    texto: `${desde} – ${hasta}`,
    cruzaElDia: empiezaAntes || terminaDespues,
    completo: formatearRango(reserva.desde, reserva.hasta),
  };
};

/** Una reserva, como se muestra dentro de la cochera que ocupa. */
const aOcupacion = (reserva, dia) => ({
  id: reserva.id,
  patente: reserva.vehiculo?.patente ?? "—",
  email: reserva.visitante?.email ?? "—",
  modalidad: MODALIDAD_ETIQUETA[reserva.modalidad] ?? reserva.modalidad ?? "—",
  horario: horarioEnElDia(reserva, dia),
  // Solo una reserva vigente se puede cancelar; una que ya terminó, no.
  cancelable: reserva.estado === "CONFIRMADA",
});

/**
 * Compara números de cochera de forma natural: A-2 antes que A-10.
 *
 * Con `localeCompare` a secas el orden sería alfabético y A-10 quedaría antes
 * que A-2, que es justo el orden en el que nadie busca una cochera.
 */
const porNumero = (a, b) =>
  a.numero.localeCompare(b.numero, "es", { numeric: true, sensitivity: "base" });

/**
 * Arma los grupos que se muestran como menús desplegables.
 *
 * `clave` es el campo por el que se agrupa: `tipo` para la pestaña por tipo de
 * cochera, `sector` para la de pisos. Los grupos salen de las cocheras y no de
 * las reservas, para que un tipo o un piso sin nada reservado igual aparezca
 * (que también es información: está todo libre).
 *
 * La unidad de la lista es **la cochera**, no la reserva: el panel muestra todo
 * el predio y cada cochera lleva colgadas las reservas que la ocupan ese día,
 * que pueden ser ninguna, una o varias (dos turnos en el mismo día).
 *
 * El contador, por eso, cuenta cocheras y no reservas: dos turnos en la misma
 * cochera siguen siendo una sola cochera ocupada.
 */
export const agruparOcupacion = (cocheras, reservas, dia, clave) => {
  const delDia = (reservas ?? []).filter(
    (r) => ESTADOS_QUE_OCUPAN.includes(r.estado) && ocupaElDia(r, dia)
  );

  const grupos = new Map();
  const deGrupo = (valor) => {
    if (!grupos.has(valor)) grupos.set(valor, { clave: valor, cocheras: new Map() });
    return grupos.get(valor).cocheras;
  };

  for (const c of cocheras ?? []) {
    const valor = c[clave];
    if (!valor) continue;
    deGrupo(valor).set(c.id, {
      id: c.id,
      numero: c.numero,
      sector: c.sector,
      tipo: c.tipo,
      estado: c.estado,
      reservas: [],
    });
  }

  for (const r of delDia) {
    const c = r.cochera;
    const valor = c?.[clave];
    if (!valor) continue;
    const enGrupo = deGrupo(valor);
    // Una reserva de una cochera que ya no está en el catálogo (borrada, por
    // ejemplo) igual se muestra: la ocupación existió. Se arma la cochera con
    // lo que trae la propia reserva.
    if (!enGrupo.has(c.id)) {
      enGrupo.set(c.id, {
        id: c.id,
        numero: c.numero,
        sector: c.sector,
        tipo: c.tipo,
        estado: c.estado,
        reservas: [],
      });
    }
    enGrupo.get(c.id).reservas.push(aOcupacion(r, dia));
  }

  return [...grupos.values()]
    .map(({ clave: valor, cocheras: mapa }) => {
      const lista = [...mapa.values()]
        .map((c) => ({
          ...c,
          // Por hora de entrada: es el orden en el que van llegando.
          reservas: [...c.reservas].sort((x, y) =>
            x.horario.texto.localeCompare(y.horario.texto)
          ),
          ocupada: c.reservas.length > 0,
        }))
        .sort(porNumero);

      return {
        clave: valor,
        cocheras: lista,
        total: lista.length,
        ocupadas: lista.filter((c) => c.ocupada).length,
        deshabilitadas: lista.filter((c) => c.estado === "DESHABILITADA").length,
      };
    })
    .sort((a, b) => a.clave.localeCompare(b.clave));
};

/** Los tipos de cochera en texto legible, sin depender de un listado fijo. */
export const TIPO_ETIQUETA = {
  AUTO: "Auto",
  MOTO: "Moto",
  CARGA: "Carga",
  ACCESIBLE: "Accesible",
};

/**
 * Cómo mostrar la clave de un grupo.
 *
 * Los tipos vienen del backend en mayúsculas y se traducen; si aparece uno que
 * no está en la tabla, se muestra tal cual en vez de quedar vacío, así un tipo
 * nuevo funciona igual aunque nadie le haya puesto etiqueta todavía. Los
 * sectores son texto libre cargado por el admin, así que ya vienen legibles.
 */
export const etiquetaDeGrupo = (clave, agrupadoPor) =>
  agrupadoPor === "tipo" ? TIPO_ETIQUETA[clave] ?? clave : clave;
