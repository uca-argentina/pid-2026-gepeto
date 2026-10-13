import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";

/** La página de Control de acceso, Server Component, y su acceso desde el panel. */

const { getMock, postMock, requireAuthMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
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
vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const { default: AccesosPage } = await import("@/app/dashboard-admin/accesos/page");
const { default: DashboardPage } = await import("@/app/dashboard-admin/page");

const renderPagina = async (Pagina) => render(await Pagina());

beforeEach(() => {
  vi.clearAllMocks();
  // requireAuth devuelve el usuario decodificado; las pantallas leen `roles`
  // para decidir qué accesos mostrar.
  requireAuthMock.mockResolvedValue({ roles: ["ADMIN"] });
  getMock.mockResolvedValue({ data: [] });
});

describe("/dashboard-admin/accesos", () => {
  it("la abren el admin y el empleado, muestra el control de acceso y permite volver al panel", async () => {
    await renderPagina(AccesosPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN", "EMPLEADO"]);
    expect(screen.getByRole("heading", { name: "Control de acceso" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /volver al panel/i })).toHaveAttribute("href", "/dashboard-admin");
  });

  it("el panel del admin incorpora el acceso a Control de acceso", async () => {
    await renderPagina(DashboardPage);

    expect(screen.getByRole("link", { name: "Control de acceso" })).toHaveAttribute("href", "/dashboard-admin/accesos");
  });
});
