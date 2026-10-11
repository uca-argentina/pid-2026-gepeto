import { describe, expect, it } from "vitest";
import { agruparOcupacion, hastaEfectivo, minutosExcedidos, ocupaElDia } from "@/utils/ocupacion";

const DIA = "2026-09-26";
const AHORA = new Date(2026, 8, 26, 13, 40);
const cochera = { id: "c1", numero: "A-01", tipo: "AUTO", sector: "PB", estado: "HABILITADA" };
const reserva = (extra = {}) => ({
  id: "r1", desde: `${DIA}T10:00`, hasta: `${DIA}T12:00`, estado: "EN_CURSO", modalidad: "FRANJA",
  vehiculo: { patente: "ABC123" }, visitante: { email: "juan@test.com" }, cochera, ...extra,
});

describe("vehículos adentro (EN_CURSO)", () => {
  it("un vehículo que se pasó de hora ocupa hasta ahora, no hasta su horario", () => {
    expect(hastaEfectivo(reserva(), AHORA)).toBe(`${DIA}T13:40`);
    expect(minutosExcedidos(reserva(), AHORA)).toBe(100);
  });

  it("dentro de su horario ocupa lo reservado y no está excedido", () => {
    const enHorario = reserva({ hasta: `${DIA}T15:00` });
    expect(hastaEfectivo(enHorario, AHORA)).toBe(`${DIA}T15:00`);
    expect(minutosExcedidos(enHorario, AHORA)).toBe(0);
  });

  it("solo EN_CURSO se extiende: una CONFIRMADA o FINALIZADA ocupa su franja", () => {
    expect(hastaEfectivo(reserva({ estado: "CONFIRMADA" }), AHORA)).toBe(`${DIA}T12:00`);
    expect(minutosExcedidos(reserva({ estado: "FINALIZADA" }), AHORA)).toBe(0);
  });

  it("un vehículo de ayer que sigue adentro ocupa también hoy", () => {
    const deAyer = reserva({ desde: "2026-09-25T20:00", hasta: "2026-09-25T23:00" });
    expect(ocupaElDia(deAyer, DIA, AHORA)).toBe(true);
    expect(ocupaElDia({ ...deAyer, estado: "FINALIZADA" }, DIA, AHORA)).toBe(false);
  });

  it("marca la cochera como ocupada con vehículo adentro y excedida", () => {
    const [grupo] = agruparOcupacion([cochera], [reserva()], DIA, "tipo", AHORA);

    expect(grupo.ocupadas).toBe(1);
    expect(grupo.cocheras[0]).toMatchObject({ ocupada: true, vehiculoAdentro: true, excedida: true });
    expect(grupo.cocheras[0].reservas[0]).toMatchObject({ adentro: true, minutosExcedidos: 100, cancelable: false });
  });

  it("una reserva VENCIDA (nunca llegó) no ocupa", () => {
    const [grupo] = agruparOcupacion([cochera], [reserva({ estado: "VENCIDA" })], DIA, "tipo", AHORA);

    expect(grupo.ocupadas).toBe(0);
    expect(grupo.cocheras[0].reservas).toEqual([]);
  });
});
