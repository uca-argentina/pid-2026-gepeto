import { describe, expect, it } from "vitest";
import {
  formatearCodigo, normalizarCodigo, payloadQR, textoVentanaIngreso, tieneCodigoVigente,
} from "@/utils/codigoAcceso";

describe("normalizarCodigo", () => {
  it.each([
    ["ABCDE12345", "ABCDE12345"],
    ["ABCDE-12345", "ABCDE12345"],
    ["abcde-12345", "ABCDE12345"],
    ["  ab cde 123 45 ", "ABCDE12345"],
    ["APARCAR:ABCDE12345", "ABCDE12345"],
    ["aparcar:abcde-12345", "ABCDE12345"],
    // Equivalencias de Crockford: I y L se leen como 1, O como 0.
    ["ABCDE-I2345", "ABCDE12345"],
    ["abcde-l2345", "ABCDE12345"],
    ["ABCDE-O2345", "ABCDE02345"],
  ])("%s → %s", (entrada, esperado) => {
    expect(normalizarCodigo(entrada)).toBe(esperado);
  });

  it.each([
    "ABCDE1234", "ABCDE123456", "ABCDE-1234U", "ABCDE_12345", "APARCAR:", "OTRO:ABCDE12345",
    "https://ejemplo.com", "", null, undefined,
  ])("rechaza %s", (entrada) => {
    expect(normalizarCodigo(entrada)).toBeNull();
  });
});

describe("formato y QR", () => {
  it("muestra el código como XXXXX-XXXXX y deja intacto lo que no tiene 10 caracteres", () => {
    expect(formatearCodigo("ABCDE12345")).toBe("ABCDE-12345");
    expect(formatearCodigo("ABC")).toBe("ABC");
    expect(formatearCodigo(null)).toBe("");
  });

  it("el QR lleva el prefijo APARCAR: y solo el código, sin datos de la reserva", () => {
    expect(payloadQR("ABCDE12345")).toBe("APARCAR:ABCDE12345");
  });
});

describe("textoVentanaIngreso", () => {
  it("muestra la ventana de ingreso del mismo día con la hora local", () => {
    expect(textoVentanaIngreso({ ingresoDesde: "2026-10-10T14:45:00", ingresoHasta: "2026-10-10T15:30:00" }))
      .toBe("Ingresá entre 14:45 y 15:30");
  });

  it("nombra los días si la ventana cruza la medianoche", () => {
    expect(textoVentanaIngreso({ ingresoDesde: "2026-10-10T23:45:00", ingresoHasta: "2026-10-11T00:30:00" }))
      .toBe("Ingresá entre el 10/10 a las 23:45 y el 11/10 a las 00:30");
  });

  it("no inventa una ventana si el backend no la mandó", () => {
    expect(textoVentanaIngreso(null)).toBe("");
    expect(textoVentanaIngreso({ ingresoDesde: "2026-10-10T14:45:00" })).toBe("");
  });
});

describe("tieneCodigoVigente", () => {
  it.each([
    ["CONFIRMADA", true], ["EN_CURSO", true], ["FINALIZADA", false], ["VENCIDA", false], ["CANCELADA", false],
  ])("%s → %s", (estado, esperado) => {
    expect(tieneCodigoVigente({ estado, acceso: { codigo: "ABCDE12345" } })).toBe(esperado);
  });

  it("sin código no hay QR que mostrar", () => {
    expect(tieneCodigoVigente({ estado: "CONFIRMADA", acceso: { codigo: null } })).toBe(false);
    expect(tieneCodigoVigente({ estado: "CONFIRMADA" })).toBe(false);
    expect(tieneCodigoVigente(undefined)).toBe(false);
  });
});
