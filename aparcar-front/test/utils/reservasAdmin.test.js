import { describe, expect, it } from "vitest";
import { seleccionarReservasAdmin, formatearFechaRegistro } from "@/utils/reservasAdmin";
import { formatearRango } from "@/utils/franjaHoraria";

const reservas = [
  { id: "r1", estado: "CONFIRMADA", desde: "2026-10-12T10:00:00", fechaCreacion: "2026-10-01T10:00:00Z" },
  { id: "r2", estado: "CONFIRMADA", desde: "2026-10-10T10:00:00", fechaCreacion: "2026-10-05T10:00:00Z" },
  { id: "r3", estado: "CANCELADA", desde: "2026-10-11T10:00:00", fechaCreacion: "2026-10-03T10:00:00Z" },
  { id: "r4", estado: "CANCELADA", motivoCancelacion: "DESHABILITACION", desde: "2026-10-09T10:00:00", fechaCreacion: "2026-10-04T10:00:00Z" },
  { id: "r5", estado: "FINALIZADA", desde: "2026-10-08T10:00:00", fechaCreacion: "2026-10-02T10:00:00Z" },
];
const ids = (datos, filtro, orden) => seleccionarReservasAdmin(datos, filtro, orden).visibles.map((r) => r.id);

describe("seleccionarReservasAdmin", () => {
  it.each([
    ["CONFIRMADA", ["r2", "r1"]], ["FINALIZADA", ["r5"]],
    ["CANCELADA", ["r4", "r3"]], ["TODAS", ["r5", "r4", "r2", "r3", "r1"]],
  ])("filtra %s sin alterar los contadores del conjunto completo", (filtro, esperado) => {
    const seleccion = seleccionarReservasAdmin(reservas, filtro, "INICIO_ASC");
    expect(seleccion.visibles.map((r) => r.id)).toEqual(esperado);
    expect(seleccion.cantidades).toEqual({ TODAS: 5, CONFIRMADA: 2, FINALIZADA: 1, CANCELADA: 2 });
  });

  it.each([
    ["INICIO_ASC", ["r5", "r4", "r2", "r3", "r1"]],
    ["INICIO_DESC", ["r1", "r3", "r2", "r4", "r5"]],
    ["ALTA_ASC", ["r1", "r5", "r3", "r4", "r2"]],
    ["ALTA_DESC", ["r2", "r4", "r3", "r5", "r1"]],
  ])("ordena por %s sin confundir el inicio con el alta", (orden, esperado) => {
    expect(ids(reservas, "TODAS", orden)).toEqual(esperado);
  });

  it("no modifica la lista ni los objetos recibidos de la API", () => {
    const datos = Object.freeze(reservas.map((r) => Object.freeze({ ...r })));
    seleccionarReservasAdmin(datos, "TODAS", "ALTA_DESC");
    expect(datos.map((r) => r.id)).toEqual(["r1", "r2", "r3", "r4", "r5"]);
  });

  it.each(["INICIO_ASC", "INICIO_DESC", "ALTA_ASC", "ALTA_DESC"])("%s deja fechas ausentes e inválidas al final", (orden) => {
    const datos = [
      { id: "r1", desde: null, fechaCreacion: null },
      { id: "r2", desde: "invalida", fechaCreacion: "invalida" },
      { id: "r3", desde: "2026-01-01T10:00", fechaCreacion: "2026-01-01T13:00Z" },
    ];
    expect(ids(datos, "TODAS", orden)).toEqual(["r3", "r1", "r2"]);
  });

  it("desempata por alta descendente e ID para mantener las páginas estables", () => {
    const datos = [
      { ...reservas[0], id: "z" }, { ...reservas[0], id: "a" },
      { ...reservas[0], id: "b", fechaCreacion: "2026-10-06T10:00:00Z" },
    ];
    expect(ids(datos, "TODAS", "INICIO_ASC")).toEqual(["b", "a", "z"]);
    expect(ids([...datos].reverse(), "TODAS", "INICIO_ASC")).toEqual(["b", "a", "z"]);
  });

  it("compara los instantes de alta aunque usen offsets diferentes", () => {
    expect(ids([
      { id: "r1", fechaCreacion: "2026-10-09T12:00:00Z" },
      { id: "r2", fechaCreacion: "2026-10-09T10:00:00-03:00" },
    ], "TODAS", "ALTA_DESC")).toEqual(["r2", "r1"]);
  });

  it("tolera una lista vacía y conserva estados desconocidos en Todas", () => {
    expect(seleccionarReservasAdmin([], "TODAS", "INICIO_ASC").visibles).toEqual([]);
    expect(ids([{ id: "r1", estado: "OTRO" }], "TODAS", "INICIO_ASC")).toEqual(["r1"]);
  });
});

it("las fechas de auditoría usan horario argentino de 24 horas y toleran datos históricos ausentes", () => {
  expect(formatearFechaRegistro("2026-10-09T16:45:00Z")).toMatch(/13:45/);
  expect(formatearFechaRegistro(null)).toBe("Sin fecha registrada");
  expect(formatearFechaRegistro("invalida")).toBe("Sin fecha registrada");
});

it("incluye el año en el listado admin y conserva el formato original por defecto", () => {
  expect(formatearRango("2026-01-01T10:00", "2026-01-01T12:00")).toBe("01/01 de 10:00 a 12:00");
  expect(formatearRango("2026-01-01T10:00", "2026-01-01T12:00", { incluirAnio: true }))
    .toBe("01/01/2026 de 10:00 a 12:00");
  expect(formatearRango("2026-01-01T10:00", "2027-01-01T12:00", { incluirAnio: true }))
    .toBe("01/01/2026 10:00 → 01/01/2027 12:00");
});
