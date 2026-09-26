import { describe, it, expect, vi, afterEach } from "vitest";

import {
  ESTADOS_QUE_OCUPAN,
  agruparOcupacion,
  aDiaLocal,
  diaEnPalabras,
  diaRelativo,
  etiquetaDeGrupo,
  horarioEnElDia,
  hoy,
  ocupaElDia,
  sumarDias,
} from "@/utils/ocupacion";

/**
 * La lógica del panel de ocupación, aislada de React.
 *
 * Caja blanca: lo que se ataca son los bordes de "¿esta reserva toca este
 * día?", que es donde vive todo. Un `<=` de más y una reserva que termina a
 * medianoche se contaría dos veces.
 */

const cochera = (numero, tipo = "AUTO", sector = "Planta Baja") => ({
  id: `c-${numero}`,
  numero,
  tipo,
  sector,
});

const reserva = (extra = {}) => ({
  id: "r1",
  desde: "2026-09-26T08:00",
  hasta: "2026-09-26T12:00",
  estado: "CONFIRMADA",
  modalidad: "FRANJA",
  visitante: { email: "juan@test.com" },
  vehiculo: { patente: "ABC123" },
  cochera: cochera("A-01"),
  ...extra,
});

afterEach(() => {
  vi.useRealTimers();
});

describe("sumarDias", () => {
  // Se construye el Date con los numeros por separado justamente por esto:
  // `new Date("2026-09-26")` se lee como UTC y en UTC-3 da el dia anterior.
  it("no se corre de dia por la zona horaria", () => {
    expect(sumarDias("2026-09-26", 0)).toBe("2026-09-26");
    expect(sumarDias("2026-09-26", 1)).toBe("2026-09-27");
    expect(sumarDias("2026-09-26", -1)).toBe("2026-09-25");
  });

  it("cruza fin de mes y fin de año", () => {
    expect(sumarDias("2026-09-30", 1)).toBe("2026-10-01");
    expect(sumarDias("2026-12-31", 1)).toBe("2027-01-01");
    expect(sumarDias("2026-03-01", -1)).toBe("2026-02-28");
  });

  // 2028 es bisiesto: el 29 de febrero existe.
  it("respeta los años bisiestos", () => {
    expect(sumarDias("2028-02-28", 1)).toBe("2028-02-29");
  });
});

describe("ocupaElDia", () => {
  const DIA = "2026-09-26";

  it("una reserva dentro del día lo ocupa", () => {
    expect(ocupaElDia(reserva(), DIA)).toBe(true);
  });

  it("una reserva de otro día no lo ocupa", () => {
    expect(ocupaElDia(reserva({ desde: "2026-09-27T08:00", hasta: "2026-09-27T12:00" }), DIA))
      .toBe(false);
  });

  // El caso que justifica el intervalo semiabierto: si el test fuera `>=`, una
  // reserva que termina a medianoche apareceria tambien en el dia siguiente.
  it("una reserva que termina a medianoche no ocupa el día siguiente", () => {
    const r = reserva({ desde: "2026-09-26T20:00", hasta: "2026-09-27T00:00" });
    expect(ocupaElDia(r, "2026-09-26")).toBe(true);
    expect(ocupaElDia(r, "2026-09-27")).toBe(false);
  });

  // Y el simetrico: arrancar exactamente a medianoche sí ocupa ese día.
  it("una reserva que arranca a medianoche ocupa ese día y no el anterior", () => {
    const r = reserva({ desde: "2026-09-27T00:00", hasta: "2026-09-27T06:00" });
    expect(ocupaElDia(r, "2026-09-26")).toBe(false);
    expect(ocupaElDia(r, "2026-09-27")).toBe(true);
  });

  // Una jornada completa a caballo de la medianoche tiene que verse los dos
  // dias: en los dos hay un auto ocupando la cochera.
  it("una reserva a caballo de la medianoche ocupa los dos días", () => {
    const r = reserva({ desde: "2026-09-26T20:00", hasta: "2026-09-27T20:00" });
    expect(ocupaElDia(r, "2026-09-26")).toBe(true);
    expect(ocupaElDia(r, "2026-09-27")).toBe(true);
  });

  it("una reserva de varios días ocupa también los del medio", () => {
    const r = reserva({ desde: "2026-09-25T10:00", hasta: "2026-09-28T10:00" });
    expect(ocupaElDia(r, "2026-09-26")).toBe(true);
    expect(ocupaElDia(r, "2026-09-27")).toBe(true);
  });

  it("una reserva sin franja no rompe", () => {
    expect(ocupaElDia({ id: "x" }, DIA)).toBe(false);
    expect(ocupaElDia(null, DIA)).toBe(false);
  });
});

describe("agruparOcupacion", () => {
  const DIA = "2026-09-26";

  // Los grupos salen de las cocheras y no de las reservas: un tipo sin nada
  // reservado igual aparece, porque "está todo libre" también es informacion.
  it("un grupo sin reservas aparece igual, con su cochera libre", () => {
    const grupos = agruparOcupacion([cochera("M-01", "MOTO")], [], DIA, "tipo");

    expect(grupos).toHaveLength(1);
    expect(grupos[0]).toMatchObject({ clave: "MOTO", total: 1, ocupadas: 0 });
    // La cochera figura igual: el panel muestra todo el predio, no solo lo
    // reservado.
    expect(grupos[0].cocheras).toHaveLength(1);
    expect(grupos[0].cocheras[0]).toMatchObject({ numero: "M-01", ocupada: false });
    expect(grupos[0].cocheras[0].reservas).toEqual([]);
  });

  // Las cocheras se ordenan como se buscan en el predio, no alfabeticamente:
  // con localeCompare a secas A-10 quedaria antes que A-2.
  it("ordena las cocheras por numero de forma natural", () => {
    const grupos = agruparOcupacion(
      [cochera("A-10"), cochera("A-2"), cochera("A-1")],
      [],
      DIA,
      "tipo"
    );

    expect(grupos[0].cocheras.map((c) => c.numero)).toEqual(["A-1", "A-2", "A-10"]);
  });

  it("cuenta aparte las cocheras fuera de servicio", () => {
    const grupos = agruparOcupacion(
      [cochera("A-01"), { ...cochera("A-02"), estado: "DESHABILITADA" }],
      [],
      DIA,
      "tipo"
    );

    expect(grupos[0]).toMatchObject({ total: 2, ocupadas: 0, deshabilitadas: 1 });
  });

  it("agrupa por tipo y por piso sobre los mismos datos", () => {
    const cocheras = [
      cochera("A-01", "AUTO", "Planta Baja"),
      cochera("M-01", "MOTO", "Planta Baja"),
      cochera("A-02", "AUTO", "Subsuelo"),
    ];
    const reservas = [reserva({ cochera: cocheras[0] })];

    const porTipo = agruparOcupacion(cocheras, reservas, DIA, "tipo");
    expect(porTipo.map((g) => [g.clave, g.total, g.ocupadas])).toEqual([
      ["AUTO", 2, 1],
      ["MOTO", 1, 0],
    ]);

    const porPiso = agruparOcupacion(cocheras, reservas, DIA, "sector");
    expect(porPiso.map((g) => [g.clave, g.total, g.ocupadas])).toEqual([
      ["Planta Baja", 2, 1],
      ["Subsuelo", 1, 0],
    ]);
  });

  // Un tipo o un piso que no estaba previsto aparece solo: es lo que hace que
  // la pantalla no haya que tocarla cuando se suma un piso nuevo.
  it("un tipo o piso nuevo aparece sin tocar nada", () => {
    const grupos = agruparOcupacion(
      [cochera("B-01", "BARCO", "Dársena Norte")],
      [],
      DIA,
      "tipo"
    );

    expect(grupos.map((g) => g.clave)).toEqual(["BARCO"]);
    // Sin etiqueta cargada se muestra la clave cruda, no un hueco.
    expect(etiquetaDeGrupo("BARCO", "tipo")).toBe("BARCO");
    expect(etiquetaDeGrupo("Dársena Norte", "sector")).toBe("Dársena Norte");
  });

  it("una reserva cancelada no ocupa", () => {
    const c = cochera("A-01");
    const grupos = agruparOcupacion(
      [c],
      [reserva({ cochera: c, estado: "CANCELADA" })],
      DIA,
      "tipo"
    );

    expect(grupos[0].ocupadas).toBe(0);
  });

  // FINALIZADA sí ocupa: sin eso, moverse a un día pasado mostraría todo vacío.
  it("una reserva finalizada sigue contando en su día", () => {
    const c = cochera("A-01");
    const grupos = agruparOcupacion(
      [c],
      [reserva({ cochera: c, estado: "FINALIZADA" })],
      DIA,
      "tipo"
    );

    expect(grupos[0].ocupadas).toBe(1);
    expect(ESTADOS_QUE_OCUPAN).toContain("FINALIZADA");
  });

  // El contador cuenta cocheras, no reservas: dos turnos en la misma cochera
  // siguen siendo una sola cochera ocupada, pero las dos reservas se listan.
  it("dos turnos en la misma cochera son una cochera ocupada y dos reservas", () => {
    const c = cochera("A-01");
    const grupos = agruparOcupacion(
      [c],
      [
        reserva({ id: "r1", cochera: c, desde: "2026-09-26T08:00", hasta: "2026-09-26T12:00" }),
        reserva({ id: "r2", cochera: c, desde: "2026-09-26T14:00", hasta: "2026-09-26T18:00" }),
      ],
      DIA,
      "tipo"
    );

    // Una cochera ocupada, pero con sus dos turnos colgados.
    expect(grupos[0].ocupadas).toBe(1);
    expect(grupos[0].cocheras).toHaveLength(1);
    expect(grupos[0].cocheras[0].reservas).toHaveLength(2);
  });

  it("cada ocupación trae los cuatro datos que el admin necesita", () => {
    const c = cochera("A-01");
    const grupos = agruparOcupacion(
      [c],
      [reserva({ cochera: c, modalidad: "MEDIA_JORNADA" })],
      DIA,
      "tipo"
    );

    const [libre] = grupos[0].cocheras;
    expect(libre.numero).toBe("A-01");
    expect(libre.reservas[0]).toMatchObject({
      patente: "ABC123",
      email: "juan@test.com",
      modalidad: "Media jornada",
      // Una reserva confirmada se puede cancelar desde el panel.
      cancelable: true,
    });
    expect(libre.reservas[0].horario.texto).toBe("08:00 – 12:00");
  });

  // Una reserva que ya vencio se sigue mostrando, pero no se cancela: el boton
  // no tendria que hacer.
  it("una reserva finalizada no es cancelable", () => {
    const c = cochera("A-01");
    const grupos = agruparOcupacion(
      [c],
      [reserva({ cochera: c, estado: "FINALIZADA" })],
      DIA,
      "tipo"
    );

    expect(grupos[0].cocheras[0].reservas[0].cancelable).toBe(false);
  });

  // Si la cochera se borró del catálogo, la reserva igual se muestra: la
  // ocupación existió y esconderla daría un panel que miente.
  it("una reserva de una cochera que ya no está en el catálogo se muestra igual", () => {
    const grupos = agruparOcupacion([], [reserva()], DIA, "tipo");

    expect(grupos).toHaveLength(1);
    expect(grupos[0]).toMatchObject({ clave: "AUTO", total: 1, ocupadas: 1 });
    expect(grupos[0].cocheras[0].numero).toBe("A-01");
  });

  it("aguanta listas vacías o ausentes", () => {
    expect(agruparOcupacion(null, null, DIA, "tipo")).toEqual([]);
    expect(agruparOcupacion([], [], DIA, "tipo")).toEqual([]);
  });
});

describe("horarioEnElDia", () => {
  // Mostrar "02/09 20:00" dentro del renglón del día 26 obliga a leer dos
  // fechas; la flecha dice "viene de antes" de un vistazo.
  it("marca con flechas lo que viene de antes o sigue después", () => {
    const r = reserva({ desde: "2026-09-25T20:00", hasta: "2026-09-27T08:00" });
    const h = horarioEnElDia(r, "2026-09-26");

    expect(h.texto).toBe("← – →");
    expect(h.cruzaElDia).toBe(true);
    // La franja completa queda disponible para el title del renglón.
    expect(h.completo).toContain("25/09");
  });

  it("una reserva contenida en el día muestra las dos horas", () => {
    const h = horarioEnElDia(reserva(), "2026-09-26");

    expect(h.texto).toBe("08:00 – 12:00");
    expect(h.cruzaElDia).toBe(false);
  });
});

describe("el día que se está mirando", () => {
  it("hoy sale del reloj local, no de UTC", () => {
    // 23:30 local: en UTC ya es el día siguiente, y aun así "hoy" es el 26.
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 26, 23, 30));

    expect(hoy()).toBe("2026-09-26");
    expect(aDiaLocal(new Date(2026, 8, 26, 23, 30))).toBe("2026-09-26");
  });

  it("nombra hoy, ayer y mañana, y nada más", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 26, 10, 0));

    expect(diaRelativo("2026-09-26")).toBe("Hoy");
    expect(diaRelativo("2026-09-27")).toBe("Mañana");
    expect(diaRelativo("2026-09-25")).toBe("Ayer");
    expect(diaRelativo("2026-09-28")).toBeNull();
  });

  it("escribe el día en castellano", () => {
    expect(diaEnPalabras("2026-09-26")).toBe("sábado 26 de septiembre");
    expect(diaEnPalabras("2027-01-01")).toBe("viernes 1 de enero");
  });
});
