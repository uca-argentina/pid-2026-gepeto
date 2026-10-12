import { describe, it, expect } from "vitest";

import { alBloqueLocal, redondearAlBloqueLocal } from "@/utils/franjaHoraria";

/**
 * Cómo se lleva un horario a la grilla de 15 minutos.
 *
 * Hay dos reglas distintas y conviene no confundirlas: lo que escribe una
 * persona se redondea al bloque **más cercano**, y el arranque por defecto de
 * una reserva nueva se trunca **hacia abajo**, para cubrir los minutos que ya
 * pasaron.
 */

describe("redondearAlBloqueLocal", () => {
  it("va al bloque más cercano, para arriba o para abajo", () => {
    expect(redondearAlBloqueLocal("2026-09-29T14:07")).toBe("2026-09-29T14:00");
    expect(redondearAlBloqueLocal("2026-09-29T14:08")).toBe("2026-09-29T14:15");
    expect(redondearAlBloqueLocal("2026-09-29T14:23")).toBe("2026-09-29T14:30");
    expect(redondearAlBloqueLocal("2026-09-29T14:38")).toBe("2026-09-29T14:45");
  });

  it("un horario que ya cae en la grilla no se mueve", () => {
    for (const m of ["00", "15", "30", "45"]) {
      expect(redondearAlBloqueLocal(`2026-09-29T14:${m}`)).toBe(`2026-09-29T14:${m}`);
    }
  });

  // Acá está la diferencia con truncar: el redondeo puede empujar el horario a
  // la hora siguiente, y por eso se resuelve sobre un Date y no sobre el texto.
  it("pasa a la hora siguiente cuando corresponde", () => {
    expect(redondearAlBloqueLocal("2026-09-29T14:53")).toBe("2026-09-29T15:00");
  });

  it("cruza al día siguiente cerca de medianoche", () => {
    expect(redondearAlBloqueLocal("2026-09-29T23:53")).toBe("2026-09-30T00:00");
  });

  it("cruza el año sin romperse", () => {
    expect(redondearAlBloqueLocal("2026-12-31T23:53")).toBe("2027-01-01T00:00");
  });

  it("un valor vacío o inválido se devuelve tal cual", () => {
    expect(redondearAlBloqueLocal("")).toBe("");
    expect(redondearAlBloqueLocal(null)).toBe(null);
    expect(redondearAlBloqueLocal("cualquier cosa")).toBe("cualquier cosa");
  });
});

describe("alBloqueLocal", () => {
  // El arranque por defecto trunca a propósito: si son las 14:07 la reserva
  // empieza 14:00 y no 14:15, para que los minutos en los que el auto ya está
  // estacionado queden cubiertos.
  it("trunca hacia abajo, a diferencia del redondeo", () => {
    expect(alBloqueLocal("2026-09-29T14:53")).toBe("2026-09-29T14:45");
    expect(redondearAlBloqueLocal("2026-09-29T14:53")).toBe("2026-09-29T15:00");
  });

  it("nunca adelanta el horario", () => {
    for (const m of ["01", "14", "16", "29", "31", "44", "46", "59"]) {
      const truncado = alBloqueLocal(`2026-09-29T10:${m}`);
      expect(truncado <= `2026-09-29T10:${m}`).toBe(true);
    }
  });
});
