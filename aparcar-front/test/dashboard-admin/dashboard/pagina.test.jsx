import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const { requireAuthMock } = vi.hoisted(() => ({ requireAuthMock: vi.fn() }));
vi.mock("@/utils/serverAuth", () => ({ requireAuth: requireAuthMock }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...props }) => <a href={href} {...props}>{children}</a>,
}));
vi.mock("@/components/DashboardHeader", () => ({ default: ({ children }) => <header>{children}</header> }));
vi.mock("@/components/ParkingHeading", () => ({ default: () => <h1>Mi estacionamiento</h1> }));
vi.mock("@/app/dashboard-admin/PanelOperativo", () => ({ default: () => <p>Ocupación operativa existente</p> }));
vi.mock("@/app/dashboard-admin/dashboard/DashboardContent", () => ({ default: () => <main><h1>Dashboard</h1><p>Indicadores del estacionamiento</p></main> }));
vi.mock("@/app/dashboard-admin/dashboard/ResumenPuntos", () => ({ default: () => <section aria-label="Programa de puntos" /> }));

const { default: DashboardPage } = await import("@/app/dashboard-admin/dashboard/page");
const { default: PanelPage } = await import("@/app/dashboard-admin/page");

beforeEach(() => {
  vi.clearAllMocks();
  // requireAuth devuelve el usuario decodificado; las pantallas leen `roles`
  // para decidir qué accesos mostrar.
  requireAuthMock.mockResolvedValue({ roles: ["ADMIN"] });
});

describe("Ruta del Dashboard administrativo", () => {
  it("exige ADMIN antes de mostrar indicadores y ofrece regresar al panel", async () => {
    render(await DashboardPage());

    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN"]);
    expect(screen.getByRole("heading", { name: "Dashboard", level: 1 })).toBeInTheDocument();
    expect(screen.getByText("Indicadores del estacionamiento")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /volver al panel/i })).toHaveAttribute("href", "/dashboard-admin");
    expect(screen.getByRole("region", { name: "Programa de puntos" })).toBeInTheDocument();
  });

  it("no renderiza datos si la protección del servidor rechaza la sesión", async () => {
    const rechazo = new Error("NEXT_REDIRECT");
    requireAuthMock.mockRejectedValueOnce(rechazo);

    await expect(DashboardPage()).rejects.toBe(rechazo);
    expect(screen.queryByText("Indicadores del estacionamiento")).not.toBeInTheDocument();
  });

  it("agrega Dashboard a la navegación y conserva el panel operativo existente", async () => {
    render(await PanelPage());

    expect(screen.getByRole("link", { name: "Dashboard", exact: true })).toHaveAttribute("href", "/dashboard-admin/dashboard");
    expect(screen.getByText("Ocupación operativa existente")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Control de acceso" })).toHaveAttribute("href", "/dashboard-admin/accesos");
  });
});
