import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock } }));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: ModalAmonestacion } = await import("@/components/ModalAmonestacion");

const reserva = {
  id: "r1",
  desde: "2026-11-01T08:00",
  hasta: "2026-11-01T12:00",
  visitante: { id: "v1", nombre: "Juan Perez" },
  vehiculo: { id: "veh1", patente: "ABC123", tipo: "AUTO" },
};

const resumen = (overrides = {}) => ({
  vigentes: 1, maxFaltas: 3, mesesVentana: 3, bloqueado: false, bloqueadoHasta: null, historial: [],
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
  getMock.mockResolvedValue({ data: resumen() });
  postMock.mockResolvedValue({ data: {} });
});

/**
 * Aplicar una falta desde una reserva.
 *
 * Lo que se prueba es la guarda: el botón vive pegado al de cancelar en una
 * lista larga, y una falta tiene consecuencias reales. Que haya que confirmar
 * antes de ver siquiera el campo de texto es la razón de ser del componente.
 */
describe("ModalAmonestacion", () => {
  it("arranca pidiendo confirmación, sin campo para escribir todavía", () => {
    render(<ModalAmonestacion reserva={reserva} onClose={vi.fn()} />);

    expect(screen.getByText(/seguro que querés aplicar una falta/i)).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("no aplica nada hasta que se confirma y se escribe la causa", async () => {
    const usuario = userEvent.setup();
    render(<ModalAmonestacion reserva={reserva} onClose={vi.fn()} />);

    await usuario.click(screen.getByRole("button", { name: /aceptar/i }));

    expect(postMock).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox")).toBeInTheDocument();
  });

  it("muestra cuántas faltas tiene ya, para poder decidir con criterio", async () => {
    const usuario = userEvent.setup();
    getMock.mockResolvedValue({ data: resumen({ vigentes: 2 }) });
    render(<ModalAmonestacion reserva={reserva} onClose={vi.fn()} />);

    await usuario.click(screen.getByRole("button", { name: /aceptar/i }));

    expect(await screen.findByText(/sobre un máximo de 3/i)).toBeInTheDocument();
    expect(getMock).toHaveBeenCalledWith("/api/v1/amonestaciones/visitante/v1");
  });

  it("avisa explícitamente cuando esta falta va a bloquear la cuenta", async () => {
    const usuario = userEvent.setup();
    getMock.mockResolvedValue({ data: resumen({ vigentes: 3 }) });
    render(<ModalAmonestacion reserva={reserva} onClose={vi.fn()} />);

    await usuario.click(screen.getByRole("button", { name: /aceptar/i }));

    // Con 3 de 3, la que se está por aplicar supera el máximo.
    expect(await screen.findByRole("alert")).toHaveTextContent(/queda inhabilitada/i);
  });

  it("no avisa de bloqueo cuando todavía queda margen", async () => {
    const usuario = userEvent.setup();
    getMock.mockResolvedValue({ data: resumen({ vigentes: 1 }) });
    render(<ModalAmonestacion reserva={reserva} onClose={vi.fn()} />);

    await usuario.click(screen.getByRole("button", { name: /aceptar/i }));
    await screen.findByText(/sobre un máximo de 3/i);

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("el botón de aplicar está deshabilitado mientras no haya causa escrita", async () => {
    const usuario = userEvent.setup();
    render(<ModalAmonestacion reserva={reserva} onClose={vi.fn()} />);

    await usuario.click(screen.getByRole("button", { name: /aceptar/i }));

    expect(screen.getByRole("button", { name: /aplicar falta/i })).toBeDisabled();
  });

  it("aplica la falta con la causa escrita y avisa a quien lo abrió", async () => {
    const usuario = userEvent.setup();
    const onClose = vi.fn();
    const onAplicada = vi.fn();
    render(<ModalAmonestacion reserva={reserva} onClose={onClose} onAplicada={onAplicada} />);

    await usuario.click(screen.getByRole("button", { name: /aceptar/i }));
    await usuario.type(screen.getByRole("textbox"), "Dejó el auto fuera de la cochera");
    await usuario.click(screen.getByRole("button", { name: /aplicar falta/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith("/api/v1/amonestaciones", {
      reservaId: "r1",
      motivo: "Dejó el auto fuera de la cochera",
    }));
    expect(onAplicada).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it("si no se pueden consultar las faltas, igual deja cargar la causa", async () => {
    const usuario = userEvent.setup();
    getMock.mockRejectedValue(new Error("se cayó la red"));
    render(<ModalAmonestacion reserva={reserva} onClose={vi.fn()} />);

    await usuario.click(screen.getByRole("button", { name: /aceptar/i }));

    // No poder mostrar el contador es un dato de menos, no una razón para
    // impedir que se registre lo que pasó.
    expect(await screen.findByText(/no se pudo consultar/i)).toBeInTheDocument();
    expect(screen.getByRole("textbox")).toBeInTheDocument();
  });
});
