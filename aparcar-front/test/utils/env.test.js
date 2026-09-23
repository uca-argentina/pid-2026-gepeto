import { describe, it, expect, afterEach, vi } from "vitest";

// getEnv lee `window.__ENV` en runtime, o cae al valor inlineado en build
// time (PUBLIC_ENV). Reimportamos el módulo en cada test para poder
// simular ambos escenarios sin que el estado de un test contamine al otro.
describe("getEnv", () => {
  afterEach(() => {
    delete window.__ENV;
    vi.resetModules();
  });

  it("devuelve el valor de window.__ENV cuando está presente (runtime)", async () => {
    window.__ENV = { NEXT_PUBLIC_API_BASE_URL: "https://runtime.example.com" };
    const { getEnv } = await import("@/utils/env");

    expect(getEnv("NEXT_PUBLIC_API_BASE_URL")).toBe("https://runtime.example.com");
  });

  it("ignora window.__ENV si la clave pedida no está definida ahí", async () => {
    window.__ENV = {};
    const { getEnv } = await import("@/utils/env");

    // No debe explotar ni devolver undefined desde un objeto vacío de forma rara.
    expect(getEnv("NEXT_PUBLIC_API_BASE_URL")).toBeUndefined();
  });

  it("no revienta si window.__ENV no existe", async () => {
    const { getEnv } = await import("@/utils/env");

    expect(() => getEnv("NEXT_PUBLIC_API_BASE_URL")).not.toThrow();
  });
});
