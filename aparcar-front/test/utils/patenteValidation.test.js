import { describe, it, expect } from "vitest";
import { formatoPatenteValido } from "@/utils/patenteValidation";

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
});