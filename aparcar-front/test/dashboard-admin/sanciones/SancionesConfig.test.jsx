import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, putMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  putMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, put: putMock } }));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: SancionesConfig } = await import("@/app/dashboard-admin/sanciones/SancionesConfig");

const config = (overrides = {}) => ({
  maxFaltas: 3, diasBloqueo: 30, mesesVentana: 3, toleranciaIngresoMin: 15, toleranciaEgresoMin: 15,
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
  getMock.mockResolvedValue({ data: config() });
  putMock.mockResolvedValue({ data: config({ maxFaltas: 5 }) });
});

/**
 * Los números del régimen de faltas.
 *
 * Son configuración, no operación: cambiarlos altera cómo se comporta el
 * sistema con todo el mundo. Por eso lo que más importa probar es que los
 * valores que se ven sean los guardados y que lo que se manda sea lo que se
 * editó, sin conversiones raras de por medio.
 */
describe("SancionesConfig", () => {
  it("muestra los valores que están guardados", async () => {
    render(<SancionesConfig />);

    expect(await screen.findByLabelText(/faltas permitidas/i)).toHaveValue(3);
    expect(screen.getByLabelText(/duración del bloqueo/i)).toHaveValue(30);
    expect(screen.getByLabelText(/tolerancia de ingreso/i)).toHaveValue(15);
    expect(screen.getByLabelText(/tolerancia de egreso/i)).toHaveValue(15);
  });

  it("explica qué provoca cada número, no deja casilleros sueltos", async () => {
    render(<SancionesConfig />);

    expect(await screen.findByText(/al superar este número/i)).toBeInTheDocument();
    expect(screen.getByText(/la cuenta se restituye/i)).toBeInTheDocument();
  });

  it("guarda como número, no como texto", async () => {
    const usuario = userEvent.setup();
    render(<SancionesConfig />);
    const campo = await screen.findByLabelText(/faltas permitidas/i);

    await usuario.clear(campo);
    await usuario.type(campo, "5");
    await usuario.click(screen.getByRole("button", { name: /guardar/i }));

    await waitFor(() => expect(putMock).toHaveBeenCalled());
    // Si viajara como "5" el backend lo rechazaría por tipo. El input de un
    // formulario siempre entrega texto, así que la conversión tiene que estar.
    expect(putMock.mock.calls.at(-1)[1].maxFaltas).toBe(5);
  });

  it("al guardar bien, lo dice", async () => {
    const usuario = userEvent.setup();
    render(<SancionesConfig />);
    await screen.findByLabelText(/faltas permitidas/i);

    await usuario.click(screen.getByRole("button", { name: /guardar/i }));

    await waitFor(() => expect(toastSuccessMock).toHaveBeenCalled());
  });

  it("si el backend rechaza el valor, muestra su mensaje y no el genérico", async () => {
    const usuario = userEvent.setup();
    putMock.mockRejectedValue({
      response: { data: { message: "El máximo de faltas tiene que estar entre 1 y 50." } },
    });
    render(<SancionesConfig />);
    await screen.findByLabelText(/faltas permitidas/i);

    await usuario.click(screen.getByRole("button", { name: /guardar/i }));

    await waitFor(() => expect(toastErrorMock)
      .toHaveBeenCalledWith("El máximo de faltas tiene que estar entre 1 y 50."));
  });

  it("la ventana de conteo también se edita", async () => {
    render(<SancionesConfig />);

    expect(await screen.findByLabelText(/ventana de conteo/i)).toHaveValue(3);
  });

  it("avisa que alargar la ventana puede bloquear cuentas en el acto", async () => {
    render(<SancionesConfig />);

    // No es obvio: alargarla no aplica faltas nuevas, pero vuelve a traer al
    // conteo faltas que ya habían salido, y eso puede pasar el máximo.
    expect(await screen.findByText(/bloquear cuentas en el acto/i)).toBeInTheDocument();
  });

  it("aclara que el historial no depende de la ventana", async () => {
    render(<SancionesConfig />);

    expect(await screen.findByText(/historial de incidencias no se toca/i)).toBeInTheDocument();
  });

  it("si no se puede cargar, lo dice en vez de mostrar campos vacíos", async () => {
    getMock.mockRejectedValue(new Error("se cayó la red"));
    render(<SancionesConfig />);

    expect(await screen.findByText(/no se pudo cargar/i)).toBeInTheDocument();
  });
});
