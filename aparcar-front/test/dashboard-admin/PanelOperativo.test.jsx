import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";

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
      if (url === "/api/v1/cocheras/disponibles") return Promise.resolve({ data: [] });
      return Promise.reject(new Error(`URL no mockeada: ${url}`));
    });
  });

  // Cocheras y reservas se movieron a sus propias páginas
  // (/dashboard-admin/cocheras y /dashboard-admin/reservas): el panel
  // principal ya no debe mostrar ni la grilla vieja ni el formulario de
  // reservas, solo el alta rápida de visitante.
  it("muestra unicamente el alta de visitante, no la grilla vieja ni el formulario de reservas", async () => {
    render(<PanelOperativo />);

    expect(await screen.findByText("Nuevo visitante")).toBeInTheDocument();
    expect(screen.queryByText("Estado de las cocheras")).not.toBeInTheDocument();
    expect(screen.queryByText("Nueva reserva")).not.toBeInTheDocument();
  });
});