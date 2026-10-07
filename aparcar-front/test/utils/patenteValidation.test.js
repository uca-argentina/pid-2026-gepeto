import { describe, it, expect } from "vitest";
import {
  formatoPatenteValido,
  normalizarPatente,
  detectarFormatoPatente,
  ejemplosPatente,
  mensajeFormatoInvalido,
} from "@/utils/patenteValidation";

describe("formatoPatenteValido", () => {
  it.each(["ABC123", "AB123CD"])("acepta %s para AUTO", (patente) => {
    expect(formatoPatenteValido(patente, "AUTO")).toBe(true);
  });

  it.each(["ABC123", "AB123CD"])("acepta %s para CARGA", (patente) => {
    expect(formatoPatenteValido(patente, "CARGA")).toBe(true);
  });

  it.each(["123ABC", "A123BCD"])("acepta %s para MOTO", (patente) => {
    expect(formatoPatenteValido(patente, "MOTO")).toBe(true);
  });

  it("rechaza una patente con formato de auto para una MOTO", () => {
    expect(formatoPatenteValido("ABC123", "MOTO")).toBe(false);
  });

  it("rechaza una patente con formato de moto para un AUTO", () => {
    expect(formatoPatenteValido("123ABC", "AUTO")).toBe(false);
  });

  it("es case-insensitive", () => {
    expect(formatoPatenteValido("abc123", "AUTO")).toBe(true);
    expect(formatoPatenteValido("123abc", "MOTO")).toBe(true);
  });

  // Lo usa también el alta de visitantes del admin, que manda la patente tal
  // cual: no tiene que empezar a aceptar espacios por su cuenta.
  it("sigue rechazando la patente con espacios (normalizar es responsabilidad de quien la envia)", () => {
    expect(formatoPatenteValido("AB 123 CD", "AUTO")).toBe(false);
  });
});

describe("normalizarPatente", () => {
  it.each([
    ["ab 123 cd", "AB123CD"],
    ["  ABC-123 ", "ABC123"],
    ["a 123 bcd", "A123BCD"],
    ["", ""],
    [undefined, ""],
  ])("'%s' -> '%s'", (entrada, salida) => {
    expect(normalizarPatente(entrada)).toBe(salida);
  });
});

describe("detectarFormatoPatente", () => {
  it.each([
    ["AB123CD", "AUTO_MERCOSUR"],
    ["ABC123", "AUTO_ANTERIOR"],
    ["A123BCD", "MOTO_MERCOSUR"],
    ["123ABC", "MOTO_ANTERIOR"],
    ["ab 123 cd", "AUTO_MERCOSUR"],
    ["XYZ", null],
    ["", null],
  ])("%s -> %s", (patente, formato) => {
    expect(detectarFormatoPatente(patente)).toBe(formato);
  });
});

describe("ejemplosPatente y mensajeFormatoInvalido", () => {
  it("MOTO usa los formatos de moto", () => {
    expect(ejemplosPatente("MOTO")).toEqual({ actual: "A 123 BCD", anterior: "123 ABC" });
    expect(mensajeFormatoInvalido("MOTO")).toBe("Formato inválido para moto. Usá A 123 BCD (actual) o 123 ABC (anterior)");
  });

  it.each(["AUTO", "CARGA", undefined])("cualquier otro tipo (%s) usa los formatos de auto", (tipo) => {
    expect(ejemplosPatente(tipo)).toEqual({ actual: "AB 123 CD", anterior: "ABC 123" });
    expect(mensajeFormatoInvalido(tipo)).toBe("Formato inválido para auto/carga. Usá AB 123 CD (actual) o ABC 123 (anterior)");
  });

  // Los ejemplos de la leyenda tienen que ser patentes que el validador acepta.
  it.each(["AUTO", "CARGA", "MOTO"])("los ejemplos de %s son validos para ese tipo", (tipo) => {
    const { actual, anterior } = ejemplosPatente(tipo);
    expect(formatoPatenteValido(normalizarPatente(actual), tipo)).toBe(true);
    expect(formatoPatenteValido(normalizarPatente(anterior), tipo)).toBe(true);
  });
});