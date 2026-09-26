import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";

/**
 * Las páginas del dashboard ADMIN, que son Server Components.
 *
 * Se prueban dos cosas que no se ven desde los componentes: que cada una pida
 * el rol ADMIN antes de renderizar nada — es la única defensa de la ruta en el
 * lado del servidor — y que arme el contenido que le corresponde después de que
 * las secciones se repartieron en pantallas distintas.
 */

const { getMock, postMock, requireAuthMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  requireAuthMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/utils/serverAuth", () => ({ requireAuth: requireAuthMock }));
// Fuera del router de Next, <Link> revienta y LogoutButton no puede navegar:
// acá lo que se prueba es el contenido de la página, no la navegación.
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
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: toastErrorMock },
}));

const { default: ReservasPage } = await import("@/app/dashboard-admin/reservas/page");
const { default: VisitantesPage } = await import("@/app/dashboard-admin/visitantes/page");

/** Un Server Component async se resuelve y después se renderiza su resultado. */
const renderPagina = async (Pagina) => render(await Pagina());

beforeEach(() => {
  vi.clearAllMocks();
  requireAuthMock.mockResolvedValue(undefined);
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/cocheras/disponibles") return Promise.resolve({ data: [] });
    if (url === "/api/v1/cocheras") return Promise.resolve({ data: [] });
    return Promise.resolve({ data: [] });
  });
});

describe("/dashboard-admin/reservas", () => {
  it("exige el rol ADMIN", async () => {
    await renderPagina(ReservasPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN"]);
  });

  // La ocupación se mudó a la pantalla de entrada: tenerla también acá era
  // leer lo mismo dos veces.
  it("trae el alta y el listado, sin el panel de ocupación", async () => {
    const { container } = await renderPagina(ReservasPage);

    expect(await screen.findByText("Nueva reserva")).toBeInTheDocument();
    expect(screen.getByText("Todas las reservas")).toBeInTheDocument();
    expect(container.querySelector(".ocupacion")).toBeNull();
  });

  it("los muestra en dos columnas", async () => {
    const { container } = await renderPagina(ReservasPage);

    await waitFor(() => expect(container.querySelector(".reservas-columnas")).not.toBeNull());
  });
});

describe("/dashboard-admin/visitantes", () => {
  it("exige el rol ADMIN", async () => {
    await renderPagina(VisitantesPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN"]);
  });

  // El alta dejó de ser la pantalla de entrada y pasó acá, detrás del botón
  // "Nuevo visitante" del encabezado.
  it("trae el alta de visitante", async () => {
    await renderPagina(VisitantesPage);

    expect(await screen.findByText("Nuevo visitante")).toBeInTheDocument();
    expect(screen.getByLabelText("Desde")).toBeInTheDocument();
  });

  it("deja volver al panel", async () => {
    await renderPagina(VisitantesPage);

    const volver = screen.getByRole("link", { name: /volver al panel/i });
    expect(volver).toHaveAttribute("href", "/dashboard-admin");
  });
});
