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
const { default: ReservasUserPage } = await import("@/app/dashboard-user/reservas/page");
const { default: PuntosUserPage } = await import("@/app/dashboard-user/puntos/page");

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
    if (url === "/api/v1/amonestaciones/mias") {
      return Promise.resolve({
        data: { vigentes: 0, maxFaltas: 3, mesesVentana: 3, bloqueado: false, bloqueadoHasta: null, historial: [] },
      });
    }
    return Promise.resolve({ data: [] });
  });
});

describe("/dashboard-user", () => {
  it("exige el rol USER", async () => {
    await renderPagina(DashboardUserPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["USER"]);
  });

  it("el panel es para reservar: el listado ya no cuelga debajo del formulario", async () => {
    await renderPagina(DashboardUserPage);

    const primerTitulo = (await screen.findAllByRole("heading"))[0];
    expect(primerTitulo).toHaveTextContent("Nueva reserva");
    // El listado se mudó a /dashboard-user/reservas. Si volviera a aparecer
    // acá, se repetiría en dos pantallas y habría que bajar para verlo, que es
    // justo lo que se quiso sacar.
    expect(screen.queryByRole("heading", { name: "Mis reservas" })).not.toBeInTheDocument();
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
/**
 * El atajo a las reservas.
 *
 * El listado queda debajo del formulario de alta, así que sin un acceso en el
 * encabezado hay que desplazarse hasta el final para ver las reservas propias.
 */
describe("atajo a Mis reservas", () => {
  it("el encabezado lleva a la pantalla del listado", async () => {
    await renderPagina(DashboardUserPage);

    const enlace = screen.getByRole("link", { name: /mis reservas/i });

    expect(enlace).toHaveAttribute("href", "/dashboard-user/reservas");
  });

  it("esa pantalla muestra el listado y vuelve al panel", async () => {
    await renderPagina(ReservasUserPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["USER"]);
    expect(await screen.findByRole("heading", { name: "Mis reservas" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /volver al panel/i }))
      .toHaveAttribute("href", "/dashboard-user");
  });

  it("esa pantalla no repite el formulario de alta", async () => {
    await renderPagina(ReservasUserPage);
    await screen.findByRole("heading", { name: "Mis reservas" });

    expect(screen.queryByRole("heading", { name: "Nueva reserva" })).not.toBeInTheDocument();
  });

  it("queda antes que el atajo a vehículos, por ser el contenido de esta pantalla", async () => {
    await renderPagina(DashboardUserPage);

    const reservas = screen.getByRole("link", { name: /mis reservas/i });
    const vehiculos = screen.getByRole("link", { name: /mis vehículos/i });

    expect(estaAntes(reservas, vehiculos)).toBe(true);
  });
});

describe("/dashboard-user/puntos", () => {
  it("el encabezado del panel lleva a Mis puntos", async () => {
    await renderPagina(DashboardUserPage);

    const nav = screen.getByRole("navigation", { name: "Mi cuenta" });
    expect(within(nav).getByRole("link", { name: "Mis puntos" })).toHaveAttribute("href", "/dashboard-user/puntos");
  });

  it("exige el rol USER, muestra la billetera y vuelve al panel", async () => {
    getMock.mockImplementation((url) => Promise.resolve({ data: url.endsWith("/billetera") ? {
      saldo: 40, rachaActual: 2, mejorRacha: 3, diasParaBonus: 5, rachaDias: 7, rachaBonus: 10, descuentoMaxPct: 50,
      puntosPorPorcentaje: 10, maxPuntosCanjeables: 500, duracionMinimaMin: 60, maxAcreditacionesDia: 2, franjas: [],
    } : [] }));
    await renderPagina(PuntosUserPage);

    expect(requireAuthMock).toHaveBeenCalledWith(["USER"]);
    expect(screen.getByRole("heading", { level: 1, name: "Mis puntos" })).toBeInTheDocument();
    expect(await screen.findByRole("region", { name: "Tu saldo" })).toHaveTextContent("40 puntos");
    expect(screen.getByRole("link", { name: /volver al panel/i })).toHaveAttribute("href", "/dashboard-user");
  });
});
