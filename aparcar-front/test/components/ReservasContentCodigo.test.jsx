import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock } }));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));
vi.mock("qrcode", () => ({ default: { toString: vi.fn().mockResolvedValue("<svg></svg>") } }));

const { default: ReservasContent } = await import("@/components/ReservasContent");

const reservaCreada = {
  id: "r9", estado: "CONFIRMADA", desde: "2026-10-10T15:00", hasta: "2026-10-10T17:00",
  vehiculo: { patente: "ABC123", tipo: "AUTO" }, cochera: { numero: "A-01", sector: "PB" },
  acceso: { codigo: "ABCDE12345", ingresoDesde: "2026-10-10T14:45:00", ingresoHasta: "2026-10-10T15:30:00" },
};

function mockData({ reservas = [] } = {}) {
  getMock.mockImplementation((url, config) => {
    if (url === "/api/v1/tarifas/cotizacion") return Promise.resolve({ data: { tipo: config.params.tipo, total: 1000, horas: 1 } });
    if (url === "/api/v1/vehiculos") return Promise.resolve({ data: [{ id: "v1", patente: "ABC123", tipo: "AUTO", visitanteId: "u1" }] });
    if (url === "/api/v1/visitantes") return Promise.resolve({ data: [{ id: "u1", nombre: "Juan", tieneDiscapacidad: false }] });
    if (url === "/api/v1/reservas") return Promise.resolve({ data: reservas });
    if (url === "/api/v1/cocheras/disponibles") return Promise.resolve({ data: [{ id: "c1", tipo: "AUTO", numero: "A-01", sector: "PB" }] });
    return Promise.reject(new Error(`URL no mockeada: ${url}`));
  });
}

async function reservar(modo, user) {
  if (modo === "admin") await user.type(await screen.findByLabelText("Patente"), "ABC123");
  else {
    await screen.findByRole("option", { name: /ABC123/ });
    await user.selectOptions(screen.getByLabelText("Patente"), "ABC123");
  }
  await screen.findByRole("option", { name: /A-01/ });
  await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
  await screen.findByText(/\$\s*1\.000,00/);
  await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));
}

describe("ReservasContent: código de acceso", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockData();
    postMock.mockResolvedValue({ data: reservaCreada });
  });

  it("al reservar, el visitante ve enseguida el QR y el código de la reserva nueva", async () => {
    const user = userEvent.setup();
    render(<ReservasContent modo="user" />);

    await reservar("user", user);

    const dialogo = await screen.findByRole("dialog", { name: "Reserva confirmada: tu código de acceso" });
    expect(within(dialogo).getByLabelText("Código ABCDE-12345")).toBeInTheDocument();
    expect(dialogo).toHaveTextContent("Ingresá entre 14:45 y 15:30.");
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("el admin que reserva en nombre de otro no recibe el modal", async () => {
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);

    await reservar("admin", user);

    await waitFor(() => expect(toastSuccessMock).toHaveBeenCalledWith("Reserva creada correctamente"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("si el backend no devolvió código, no abre un modal vacío", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValue({ data: { ...reservaCreada, acceso: { codigo: null } } });
    render(<ReservasContent modo="user" />);

    await reservar("user", user);

    await waitFor(() => expect(toastSuccessMock).toHaveBeenCalled());
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("Ver QR en Mis reservas abre el código de esa reserva", async () => {
    const user = userEvent.setup();
    mockData({ reservas: [reservaCreada] });
    render(<ReservasContent modo="user" />);

    await user.click(await screen.findByRole("button", { name: "Ver QR" }));

    const dialogo = screen.getByRole("dialog", { name: "Tu código de acceso" });
    expect(within(dialogo).getByLabelText("Código ABCDE-12345")).toBeInTheDocument();
    await user.click(within(dialogo).getByRole("button", { name: "Cerrar" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    // El foco vuelve al botón que lo abrió.
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "Ver QR" }));
  });
});
