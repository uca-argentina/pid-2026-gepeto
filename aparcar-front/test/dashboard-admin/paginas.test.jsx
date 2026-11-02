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
const { default: RecurrentesPage } = await import("@/app/dashboard-admin/recurrentes/page");
const { default: VisitantesPage } = await import("@/app/dashboard-admin/visitantes/page");
const { default: TarifasPage } = await import("@/app/dashboard-admin/tarifas/page");
const { default: ConveniosPage } = await import("@/app/dashboard-admin/convenios/page");
const { default: DashboardPage } = await import("@/app/dashboard-admin/page");
const { default: PerfilAdminPage } = await import("@/app/dashboard-admin/perfil/page");
const { default: PerfilUserPage } = await import("@/app/dashboard-user/perfil/page");

/** Un Server Component async se resuelve y después se renderiza su resultado. */
const renderPagina = async (Pagina) => render(await Pagina());

describe("Rutas de perfil", () => {
  it.each([
    [PerfilAdminPage, ["ADMIN", "EMPLEADO"], "/dashboard-admin"],
    [PerfilUserPage, ["USER"], "/dashboard-user"],
  ])(
    "protege la pantalla con su rol y permite volver al panel (%s, %s)", async (Pagina, roles, href) => {
      await renderPagina(Pagina);
      expect(requireAuthMock).toHaveBeenCalledWith(roles);
      expect(screen.getByRole("link", { name: /volver al panel/i })).toHaveAttribute("href", href);
    }
  );
});

describe("/dashboard-admin/convenios", () => {
  it("exige ADMIN y permite volver al panel", async () => {
    await renderPagina(ConveniosPage);
    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN"]);
    expect(screen.getByRole("heading", { name: "Gestionar convenios" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /volver al panel/i })).toHaveAttribute("href", "/dashboard-admin");
  });

  it("no renderiza contenido cuando falla la autorización", async () => {
    requireAuthMock.mockRejectedValueOnce(new Error("Sin permiso"));
    await expect(ConveniosPage()).rejects.toThrow("Sin permiso");
    expect(getMock).not.toHaveBeenCalled();
  });
});

describe("/dashboard-admin/tarifas", () => {
  it("exige ADMIN y permite volver al panel", async () => {
    await renderPagina(TarifasPage);
    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN"]);
    expect(screen.getByRole("heading", { name: "Gestionar tarifas" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /volver al panel/i })).toHaveAttribute("href", "/dashboard-admin");
  });

  it("el panel incorpora el acceso a Gestionar tarifas", async () => {
    await renderPagina(DashboardPage);
    expect(screen.getByRole("link", { name: "Gestionar tarifas" })).toHaveAttribute("href", "/dashboard-admin/tarifas");
  });
});

beforeEach(() => {
  vi.clearAllMocks();
  // requireAuth devuelve el usuario decodificado; las pantallas leen `roles`
  // para decidir qué accesos mostrar.
  requireAuthMock.mockResolvedValue({ roles: ["ADMIN"] });
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/cocheras/disponibles") return Promise.resolve({ data: [] });
    if (url === "/api/v1/cocheras") return Promise.resolve({ data: [] });
    return Promise.resolve({ data: [] });
  });
});

describe("/dashboard-admin/reservas", () => {
  it("la abren el admin y el empleado", async () => {
    await renderPagina(ReservasPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN", "EMPLEADO"]);
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

describe("/dashboard-admin/recurrentes", () => {
  it("exige ADMIN y muestra vehículos propios junto a la reserva recurrente", async () => {
    await renderPagina(RecurrentesPage);
    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN"]);
    expect(screen.getByRole("heading", { name: "Mis reservas recurrentes" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Mis vehículos" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Nueva reserva recurrente" })).toBeInTheDocument();
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos/mios"));
    expect(getMock).not.toHaveBeenCalledWith("/api/v1/visitantes");
    expect(screen.getByRole("link", { name: /volver al panel/i })).toHaveAttribute("href", "/dashboard-admin");
  });

  it("no renderiza contenido cuando falla la autorización", async () => {
    requireAuthMock.mockRejectedValueOnce(new Error("Sin permiso"));
    await expect(RecurrentesPage()).rejects.toThrow("Sin permiso");
    expect(getMock).not.toHaveBeenCalled();
  });

  it("el panel ofrece un acceso separado de las reservas de visitantes", async () => {
    await renderPagina(DashboardPage);
    expect(screen.getByRole("link", { name: "Mis reservas recurrentes" })).toHaveAttribute("href", "/dashboard-admin/recurrentes");
    expect(screen.getByRole("link", { name: "Ver reservas" })).toHaveAttribute("href", "/dashboard-admin/reservas");
  });
});

describe("/dashboard-admin/visitantes", () => {
  it("la abren el admin y el empleado", async () => {
    await renderPagina(VisitantesPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN", "EMPLEADO"]);
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

/**
 * El rol EMPLEADO comparte el panel con el administrador.
 *
 * La diferencia es qué ofrece el menú: el empleado opera (reservas, accesos,
 * alta de visitantes) pero no configura. Ocultar los accesos es comodidad —
 * cada pantalla valida su rol y el backend rechaza igual—, pero si el menú le
 * ofreciera algo que no puede usar, el error aparecería recién al hacer clic.
 */
describe("el panel según el rol", () => {
  /** Lo que solo tiene que ver un administrador. */
  const soloAdmin = ["Dashboard", "Gestionar cocheras", "Gestionar usuarios", "Gestionar tarifas", "Gestionar convenios"];
  /** Lo que forma parte de operar y ve también el empleado. */
  const operacion = ["Control de acceso", "Nuevo visitante", "Ver reservas"];

  it("el administrador ve todos los accesos", async () => {
    requireAuthMock.mockResolvedValue({ roles: ["ADMIN"] });

    await renderPagina(DashboardPage);

    for (const nombre of [...soloAdmin, ...operacion]) {
      expect(screen.getByRole("link", { name: nombre })).toBeInTheDocument();
    }
  });

  it("el empleado ve los accesos de operación", async () => {
    requireAuthMock.mockResolvedValue({ roles: ["EMPLEADO"] });

    await renderPagina(DashboardPage);

    for (const nombre of operacion) {
      expect(screen.getByRole("link", { name: nombre })).toBeInTheDocument();
    }
  });

  it("al empleado no se le ofrece nada de configuración", async () => {
    requireAuthMock.mockResolvedValue({ roles: ["EMPLEADO"] });

    await renderPagina(DashboardPage);

    for (const nombre of soloAdmin) {
      expect(screen.queryByRole("link", { name: nombre })).not.toBeInTheDocument();
    }
    // Las recurrentes también son del admin, y su enlace no tiene el prefijo
    // "Gestionar", así que se chequea aparte.
    expect(screen.queryByRole("link", { name: /recurrentes/i })).not.toBeInTheDocument();
  });

  it("la pantalla la pueden abrir los dos roles", async () => {
    requireAuthMock.mockResolvedValue({ roles: ["EMPLEADO"] });

    await renderPagina(DashboardPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["ADMIN", "EMPLEADO"]);
  });
});
