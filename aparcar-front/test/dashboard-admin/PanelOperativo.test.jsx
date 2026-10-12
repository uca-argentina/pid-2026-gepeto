import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";

const { getMock, postMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock } }));
vi.mock("sonner", () => ({
  toast: { success: toastSuccessMock, error: toastErrorMock },
}));

const { default: PanelOperativo } = await import("@/app/dashboard-admin/PanelOperativo");

describe("PanelOperativo (dashboard ADMIN)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getMock.mockImplementation((url) => {
      if (url === "/api/v1/cocheras") {
        return Promise.resolve({
          data: [
            { id: "c1", numero: "A-01", tipo: "AUTO", sector: "Planta Baja", estado: "HABILITADA" },
          ],
        });
      }
      if (url === "/api/v1/reservas") return Promise.resolve({ data: [] });
      return Promise.reject(new Error(`URL no mockeada: ${url}`));
    });
  });

  // Cada cosa tiene su pantalla: cocheras, reservas, usuarios y el alta de
  // visitante viven en /dashboard-admin/*. Con lo que abre el panel es con la
  // foto del predio, que es lo que se mira primero al llegar.
  it("abre con la ocupación de cocheras", async () => {
    render(<PanelOperativo />);

    expect(await screen.findByRole("tab", { name: /por tipo de cochera/i })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /por piso/i })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("0 de 1 cocheras ocupadas")).toBeInTheDocument());
  });

  // El alta de visitante pasó a su propia página, detrás del botón "Nuevo
  // visitante" del encabezado, así que acá ya no tiene que estar el formulario.
  it("no trae el alta de visitante ni el formulario de reservas", async () => {
    render(<PanelOperativo />);

    await screen.findByRole("tab", { name: /por tipo de cochera/i });
    expect(screen.queryByLabelText(/patente/i)).not.toBeInTheDocument();
    expect(screen.queryByText("Nueva reserva")).not.toBeInTheDocument();
    expect(screen.queryByText("Estado de las cocheras")).not.toBeInTheDocument();
  });
});
