import { describe, expect, it } from "vitest";
import { fechaCorta, validarCertificado } from "@/utils/verificacionDiscapacidad";

const archivo = (name, type, size = 10) => ({ name, type, size });

describe("validarCertificado", () => {
  it.each([
    ["cud.pdf", "application/pdf"], ["cud.PNG", "image/png"], ["cud.jpg", "image/jpeg"], ["cud.jpeg", "image/jpeg"],
    // Algunos navegadores no informan el tipo: se decide por la extension y el backend revisa el contenido.
    ["cud.pdf", ""],
  ])("acepta %s (%s)", (name, type) => {
    expect(validarCertificado(archivo(name, type), 5)).toBeNull();
  });

  it.each([
    ["cud.gif", "image/gif"], ["cud.exe", "application/pdf"], ["cud", "application/pdf"], ["cud.pdf", "text/html"],
  ])("rechaza %s (%s)", (name, type) => {
    expect(validarCertificado(archivo(name, type), 5)).toMatch(/PDF, JPG o PNG/);
  });

  it("rechaza vacío y lo que supera el máximo, en el borde exacto", () => {
    expect(validarCertificado(archivo("c.pdf", "application/pdf", 0), 5)).toMatch(/vacío/);
    expect(validarCertificado(archivo("c.pdf", "application/pdf", 5 * 1024 * 1024), 5)).toBeNull();
    expect(validarCertificado(archivo("c.pdf", "application/pdf", 5 * 1024 * 1024 + 1), 5)).toMatch(/5 MB/);
    expect(validarCertificado(null, 5)).toMatch(/Elegí/);
  });
});

describe("fechaCorta", () => {
  it("lee un día sin hora como fecha local, no como UTC", () => {
    expect(fechaCorta("2027-03-01")).toBe("01/03/2027");
  });

  it("formatea un instante y tolera vacíos o inválidos", () => {
    expect(fechaCorta("2026-10-09T13:00:00Z")).toBe("09/10/2026");
    expect(fechaCorta(null)).toBe("");
    expect(fechaCorta("no")).toBe("");
  });
});
