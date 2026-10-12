import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";

/**
 * Las páginas del visitante (Server Components). Se prueba cómo quedaron
 * repartidas las secciones: el panel arranca por las reservas y los vehículos
 * se gestionan dentro de "Mis datos".
 */

const { getMock, requireAuthMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  requireAuthMock: vi.fn(),
}));

vi.mock("@/utils/serverAuth", () => ({ requireAuth: requireAuthMock }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...resto }) => (
    <a href={href} {...resto}>
      {children}
    </a>
  ),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));
vi.mock("@/app/api", () => ({ default: { get: getMock, post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const { default: DashboardUserPage } = await import("@/app/dashboard-user/page");
const { default: PerfilUserPage } = await import("@/app/dashboard-user/perfil/page");

const renderPagina = async (Pagina) => render(await Pagina());

/** true si `a` aparece antes que `b` en el documento. */
const estaAntes = (a, b) => Boolean(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);

beforeEach(() => {
  vi.clearAllMocks();
  requireAuthMock.mockResolvedValue(undefined);
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/visitantes/me") {
      return Promise.resolve({ data: { id: "v1", nombre: "Juan Perez", documento: "30111222", email: "juan@test.com" } });
    }
    if (url === "/api/v1/vehiculos") {
      return Promise.resolve({ data: [{ id: "veh1", patente: "AB123CD", tipo: "AUTO", visitanteId: "v1" }] });
    }
    return Promise.resolve({ data: [] });
  });
});

describe("/dashboard-user", () => {
  it("exige el rol USER", async () => {
    await renderPagina(DashboardUserPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["USER"]);
  });

  it("lo primero que muestra es la seccion para reservar", async () => {
    await renderPagina(DashboardUserPage);

    const primerTitulo = (await screen.findAllByRole("heading"))[0];
    expect(primerTitulo).toHaveTextContent("Nueva reserva");
    expect(estaAntes(primerTitulo, screen.getByRole("heading", { name: "Mis reservas" }))).toBe(true);
  });

  it("ya no gestiona vehiculos en el panel: deja un acceso directo a Mis datos", async () => {
    await renderPagina(DashboardUserPage);
    await screen.findByRole("heading", { name: "Nueva reserva" });

    expect(screen.queryByRole("region", { name: "Mis vehículos" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /agregar/i })).not.toBeInTheDocument();
    const nav = screen.getByRole("navigation", { name: "Mi cuenta" });
    expect(within(nav).getByRole("link", { name: "Mis vehículos" })).toHaveAttribute(
      "href",
      "/dashboard-user/perfil#mis-vehiculos"
    );
  });
});

describe("/dashboard-user/perfil", () => {
  it("incluye la gestion de vehiculos dentro de Mis datos", async () => {
    await renderPagina(PerfilUserPage);

    expect(await screen.findByRole("heading", { level: 1, name: "Mis datos" })).toBeInTheDocument();
    const vehiculos = await screen.findByRole("region", { name: "Mis vehículos" });
    expect(await within(vehiculos).findByRole("img", { name: "Patente AB123CD" })).toBeInTheDocument();
    expect(within(vehiculos).getByRole("button", { name: /agregar/i })).toBeInTheDocument();
  });

  it("los vehiculos van despues de los datos personales y antes de la seguridad", async () => {
    await renderPagina(PerfilUserPage);

    const vehiculos = await screen.findByRole("region", { name: "Mis vehículos" });
    const datos = screen.getByRole("heading", { name: "Datos personales" });
    const seguridad = screen.getByRole("heading", { name: "Seguridad" });
    expect(estaAntes(datos, vehiculos)).toBe(true);
    expect(estaAntes(vehiculos, seguridad)).toBe(true);
  });
});