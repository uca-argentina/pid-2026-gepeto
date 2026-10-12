import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: vi.fn(), put: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const { default: OcupacionCocheras } = await import("@/components/OcupacionCocheras");

const AHORA = new Date(2026, 8, 26, 13, 30);
const HOY = "2026-09-26";
const cochera = (numero) => ({ id: `c-${numero}`, numero, tipo: "AUTO", sector: "Planta Baja", estado: "HABILITADA" });
const reserva = (id, numero, extra) => ({
  id, desde: `${HOY}T10:00`, hasta: `${HOY}T15:00`, estado: "CONFIRMADA", modalidad: "FRANJA",
  visitante: { email: `${id}@test.com` }, vehiculo: { patente: `PAT${id}` }, cochera: cochera(numero), ...extra,
});

function mockData(reservas) {
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/cocheras") return Promise.resolve({ data: [cochera("A-01"), cochera("A-02"), cochera("A-03")] });
    if (url === "/api/v1/reservas") return Promise.resolve({ data: reservas });
    return Promise.reject(new Error(url));
  });
}

describe("OcupacionCocheras con vehículos adentro", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(AHORA);
  });

  afterEach(() => vi.useRealTimers());

  it("muestra EN_CURSO como ocupada con el vehículo adentro, y avisa si se pasó de hora", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    mockData([
      reserva("1", "A-01", { estado: "EN_CURSO" }),
      reserva("2", "A-02", { estado: "EN_CURSO", hasta: `${HOY}T12:00` }),
      reserva("3", "A-03", { estado: "VENCIDA" }),
    ]);
    render(<OcupacionCocheras />);

    expect(await screen.findByText("2 de 3 cocheras ocupadas")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Auto/ }));

    const items = screen.getAllByRole("listitem").filter((li) => li.classList.contains("ocupacion-cochera"));
    const a01 = items.find((li) => within(li).queryByText("A-01"));
    const a02 = items.find((li) => within(li).queryByText("A-02"));
    const a03 = items.find((li) => within(li).queryByText("A-03"));
    expect(within(a01).getAllByText("Vehículo adentro")).not.toHaveLength(0);
    expect(within(a02).getByText("Vehículo adentro · se pasó de hora")).toBeInTheDocument();
    expect(within(a02).getByText("Vehículo adentro · se pasó 90 min")).toBeInTheDocument();
    expect(within(a03).getByText("Libre")).toBeInTheDocument();
  });

  it("una reserva con el vehículo adentro no se puede cancelar desde el panel", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    mockData([reserva("1", "A-01", { estado: "EN_CURSO" })]);
    render(<OcupacionCocheras />);

    await screen.findByText("1 de 3 cocheras ocupadas");
    await user.click(screen.getByRole("button", { name: /Auto/ }));

    expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument();
  });
});
