import { describe, it, expect, beforeEach } from "vitest";
import { useAuthStore } from "@/store/authStore";

// Un JWT con el mismo shape que emite el back (header.payload.signature, sin
// validar la firma acá porque jwt-decode no la valida — solo decodifica).
function fakeJwt(payload) {
  const encode = (obj) => btoa(JSON.stringify(obj)).replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode(payload)}.firma-invalida`;
}

describe("authStore", () => {
  beforeEach(() => {
    document.cookie = "JWT=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
    useAuthStore.setState({ isAuthenticated: false, user: null, isHydrated: false });
  });

  it("setAuth decodifica las authorities del JWT (string separado por comas) a un array de roles", () => {
    const token = fakeJwt({ email: "mateo@test.com", authorities: "USER,ADMIN" });

    useAuthStore.getState().setAuth(token);

    const { isAuthenticated, user } = useAuthStore.getState();
    expect(isAuthenticated).toBe(true);
    expect(user.roles).toEqual(["USER", "ADMIN"]);
    expect(user.email).toBe("mateo@test.com");
  });

  it("setAuth guarda el token en una cookie JWT", () => {
    const token = fakeJwt({ email: "mateo@test.com", authorities: "USER" });

    useAuthStore.getState().setAuth(token);

    expect(document.cookie).toContain(`JWT=${token}`);
  });

  it("setAuth con un solo rol devuelve un array de un elemento (no un string suelto)", () => {
    const token = fakeJwt({ email: "user@test.com", authorities: "USER" });

    useAuthStore.getState().setAuth(token);

    expect(useAuthStore.getState().user.roles).toEqual(["USER"]);
  });

  it("setAuth con un token invalido deja isAuthenticated en false y no revienta", () => {
    useAuthStore.getState().setAuth("esto-no-es-un-jwt");

    const { isAuthenticated, user } = useAuthStore.getState();
    expect(isAuthenticated).toBe(false);
    expect(user).toBeNull();
  });

  it("logout borra la cookie JWT y limpia el estado", () => {
    const token = fakeJwt({ email: "mateo@test.com", authorities: "USER" });
    useAuthStore.getState().setAuth(token);

    useAuthStore.getState().logout();

    const { isAuthenticated, user } = useAuthStore.getState();
    expect(isAuthenticated).toBe(false);
    expect(user).toBeNull();
    expect(document.cookie).not.toContain("JWT=");
  });

  it("checkAuth restaura la sesion desde la cookie si el token todavia no expiro", () => {
    const futuro = Math.floor(Date.now() / 1000) + 3600;
    const token = fakeJwt({ email: "mateo@test.com", authorities: "ADMIN", exp: futuro });
    document.cookie = `JWT=${token}; path=/;`;

    useAuthStore.getState().checkAuth();

    const { isAuthenticated, user, isHydrated } = useAuthStore.getState();
    expect(isAuthenticated).toBe(true);
    expect(user.roles).toEqual(["ADMIN"]);
    expect(isHydrated).toBe(true);
  });

  it("checkAuth cierra la sesion si el token de la cookie ya expiro", () => {
    const pasado = Math.floor(Date.now() / 1000) - 3600;
    const token = fakeJwt({ email: "mateo@test.com", authorities: "USER", exp: pasado });
    document.cookie = `JWT=${token}; path=/;`;

    useAuthStore.getState().checkAuth();

    const { isAuthenticated, user } = useAuthStore.getState();
    expect(isAuthenticated).toBe(false);
    expect(user).toBeNull();
    expect(document.cookie).not.toContain("JWT=");
  });

  it("checkAuth sin cookie deja la sesion como no autenticada pero hidratada", () => {
    useAuthStore.getState().checkAuth();

    const { isAuthenticated, isHydrated } = useAuthStore.getState();
    expect(isAuthenticated).toBe(false);
    expect(isHydrated).toBe(true);
  });
});
