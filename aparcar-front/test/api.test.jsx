import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import MockAdapter from "axios-mock-adapter";

vi.mock("@/utils/env", () => ({
  getEnv: vi.fn(() => "http://mock-api.test"),
}));

// Importamos api despues de mockear getEnv, para que el interceptor de
// request use el mock (el mock de vi.mock se hoistea antes que este import
// de todas formas, pero lo dejamos explicito para que quede claro el orden).
const { default: api } = await import("@/app/api");

describe("api (interceptors de axios)", () => {
  let mock;

  beforeEach(() => {
    mock = new MockAdapter(api);
  });

  afterEach(() => {
    mock.restore();
    document.cookie = "JWT=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
    delete window.__location_stub_installed;
  });

  it("setea el baseURL usando getEnv", async () => {
    mock.onGet("/algo").reply((config) => {
      expect(config.baseURL).toBe("http://mock-api.test");
      return [200, {}];
    });

    await api.get("/algo");
  });

  it("agrega el Bearer token de la cookie JWT cuando no hay Authorization explicito", async () => {
    document.cookie = "JWT=el-token-guardado; path=/;";

    mock.onGet("/algo").reply((config) => {
      expect(config.headers.Authorization).toBe("Bearer el-token-guardado");
      return [200, {}];
    });

    await api.get("/algo");
  });

  it("no agrega Authorization si no hay cookie JWT", async () => {
    mock.onGet("/algo").reply((config) => {
      expect(config.headers.Authorization).toBeUndefined();
      return [200, {}];
    });

    await api.get("/algo");
  });

  // Regresion: este es el bug real que causaba que el login pidiera el
  // usuario y contraseña dos veces. El interceptor pisaba el Authorization
  // "Basic ..." armado a mano por login/page.jsx con un "Bearer <token
  // viejo>" si ya habia una cookie JWT de una sesion anterior.
  it("NO pisa un Authorization ya seteado a mano (ej. Basic Auth de /login), aunque haya una cookie JWT vieja", async () => {
    document.cookie = "JWT=token-viejo-de-otra-sesion; path=/;";

    mock.onPost("/login").reply((config) => {
      expect(config.headers.Authorization).toBe("Basic bWF0ZW86MTIzNDU2");
      return [200, {}];
    });

    await api.post(
      "/login",
      {},
      { headers: { Authorization: "Basic bWF0ZW86MTIzNDU2" } }
    );
  });

  it("ante un 401, borra la cookie JWT y redirige a /login", async () => {
    document.cookie = "JWT=token-expirado; path=/;";
    Object.defineProperty(window, "location", {
      writable: true,
      value: { href: "" },
    });

    mock.onGet("/algo-protegido").reply(401, { message: "No autorizado" });

    await expect(api.get("/algo-protegido")).rejects.toBeTruthy();

    expect(document.cookie).not.toContain("JWT=token-expirado");
    expect(window.location.href).toBe("/login");
  });

  it("ante un error que no es 401, no toca la cookie ni redirige", async () => {
    document.cookie = "JWT=token-valido; path=/;";
    Object.defineProperty(window, "location", {
      writable: true,
      value: { href: "" },
    });

    mock.onGet("/algo").reply(500, { message: "Error interno" });

    await expect(api.get("/algo")).rejects.toBeTruthy();

    expect(document.cookie).toContain("JWT=token-valido");
    expect(window.location.href).toBe("");
  });
});
