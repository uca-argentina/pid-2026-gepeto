import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const { getMock, requireAuthMock } = vi.hoisted(() => ({ getMock: vi.fn(), requireAuthMock: vi.fn() }));

vi.mock("@/utils/serverAuth", () => ({ requireAuth: requireAuthMock }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...resto }) => <a href={href} {...resto}>{children}</a>,
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn(), push: vi.fn() }) }));
vi.mock("@/app/api", () => ({ default: { get: getMock, post: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const { default: VerificacionesPage } = await import("@/app/dashboard-admin/verificaciones/page");
const { default: DashboardPage } = await import("@/app/dashboard-admin/page");

const renderPagina = async (Pagina) => render(await Pagina());

beforeEach(() => {
  vi.clearAllMocks();
  getMock.mockImplementation((url) => Promise.resolve({ data: url.endsWith("/cantidad") ? { cantidad: 2 } : [] }));
});

describe("/dashboard-admin/verificaciones", () => {
  it("exige ADMIN (no EMPLEADO: son datos de salud) y permite volver al panel", async () => {
    requireAuthMock.mockResolvedValue({ roles: ["ADMIN"] });
    await renderPagina(VerificacionesPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN"]);
    expect(screen.getByRole("heading", { name: "Verificaciones de discapacidad" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /volver al panel/i })).toHaveAttribute("href", "/dashboard-admin");
  });

  it("el panel del admin incorpora el acceso con el contador de pendientes", async () => {
    requireAuthMock.mockResolvedValue({ roles: ["ADMIN"] });
    await renderPagina(DashboardPage);

    expect(await screen.findByRole("link", { name: "Verificaciones, 2 pendientes" }))
      .toHaveAttribute("href", "/dashboard-admin/verificaciones");
  });

  it("el empleado no ve el acceso", async () => {
    requireAuthMock.mockResolvedValue({ roles: ["EMPLEADO"] });
    await renderPagina(DashboardPage);

    expect(screen.queryByRole("link", { name: /Verificaciones/ })).not.toBeInTheDocument();
  });
});
