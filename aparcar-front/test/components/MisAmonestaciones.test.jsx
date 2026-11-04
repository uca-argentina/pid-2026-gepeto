import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get: getMock } }));

const { default: MisAmonestaciones } = await import("@/components/MisAmonestaciones");
const { default: PuntosFaltas } = await import("@/components/PuntosFaltas");

const falta = (overrides = {}) => ({
  id: "a1",
  fecha: "2026-10-01T10:00:00",
  origen: "MANUAL",
  motivo: "Dejó el auto fuera de la cochera",
  patente: "ABC123",
  autorNombre: "Ana Admin",
  automatica: false,
  vigente: true,
  ...overrides,
});

const resumen = (overrides = {}) => ({
  vigentes: 1, maxFaltas: 3, mesesVentana: 3, bloqueado: false, bloqueadoHasta: null,
  historial: [falta()],
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
  getMock.mockResolvedValue({ data: resumen() });
});

/**
 * Lo que ve la persona sancionada en su propio perfil.
 *
 * Hay dos números distintos en juego —las faltas que cuentan hoy y todas las
 * que existieron— y la pantalla tiene que dejar claro cuál es cuál. Si se
 * confundieran, alguien contaría las del historial y no entendería por qué no
 * coinciden con las que lo pueden bloquear.
 */
describe("MisAmonestaciones", () => {
  it("muestra cuántas faltas cuentan hoy sobre el máximo permitido", async () => {
    render(<MisAmonestaciones />);

    expect(await screen.findByText(/en los últimos 3 meses/i)).toBeInTheDocument();
    expect(getMock).toHaveBeenCalledWith("/api/v1/amonestaciones/mias");
  });

  it("el historial es un desplegable y arranca cerrado", async () => {
    render(<MisAmonestaciones />);

    const resumenHistorial = await screen.findByText(/historial de incidencias/i);
    expect(resumenHistorial.closest("details")).not.toHaveAttribute("open");
  });

  it("el historial trae lo que escribió el personal, palabra por palabra", async () => {
    render(<MisAmonestaciones />);

    expect(await screen.findByText("Dejó el auto fuera de la cochera")).toBeInTheDocument();
  });

  it("una falta que ya no cuenta sigue en el historial y se marca como tal", async () => {
    getMock.mockResolvedValue({
      data: resumen({
        vigentes: 0,
        historial: [falta({ id: "vieja", vigente: false, motivo: "Algo de hace un año" })],
      }),
    });

    render(<MisAmonestaciones />);

    // El historial es permanente: la falta sigue ahí aunque haya salido de la
    // ventana de tres meses.
    expect(await screen.findByText("Algo de hace un año")).toBeInTheDocument();
    expect(screen.getByText(/ya no cuenta para el bloqueo/i)).toBeInTheDocument();
  });

  it("con la cuenta bloqueada avisa y dice hasta cuándo", async () => {
    getMock.mockResolvedValue({
      data: resumen({ vigentes: 4, bloqueado: true, bloqueadoHasta: "2026-12-01T10:00:00" }),
    });

    render(<MisAmonestaciones />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/inhabilitada para reservar/i);
  });

  it("sin faltas no inventa un historial vacío confuso", async () => {
    getMock.mockResolvedValue({ data: resumen({ vigentes: 0, historial: [] }) });

    render(<MisAmonestaciones />);

    expect(await screen.findByText(/ninguna incidencia registrada/i)).toBeInTheDocument();
  });
});

/**
 * Los puntitos.
 *
 * El dato real va en el texto y en el `aria-label`; el dibujo es decorativo.
 * Por eso se verifica el rótulo accesible y no los colores.
 */
describe("PuntosFaltas", () => {
  it("dibuja tantos puntos como el máximo configurado, no siempre tres", () => {
    const { container } = render(<PuntosFaltas cantidad={1} maximo={5} />);

    expect(container.querySelectorAll(".falta-punto")).toHaveLength(5);
  });

  it("marca en rojo solo los usados", () => {
    const { container } = render(<PuntosFaltas cantidad={2} maximo={4} />);

    expect(container.querySelectorAll(".falta-punto-usado")).toHaveLength(2);
  });

  it("agrega un punto extra cuando se pasó del máximo", () => {
    const { container } = render(<PuntosFaltas cantidad={4} maximo={3} />);

    // Sin esto, la cuarta falta sobre un máximo de tres se vería igual que la
    // tercera: justo el momento en que la cuenta se bloquea.
    expect(container.querySelectorAll(".falta-punto")).toHaveLength(4);
    expect(container.querySelectorAll(".falta-punto-excedido")).toHaveLength(1);
  });

  it("dice el estado en texto, para no depender de ver colores", () => {
    render(<PuntosFaltas cantidad={2} maximo={3} />);

    expect(screen.getByRole("img", { name: "2 de 3 faltas permitidas" })).toBeInTheDocument();
  });
});

/**
 * La ventana de conteo es configurable, así que el texto no puede decir
 * "3 meses" fijo: diría algo falso en cuanto el administrador la cambie.
 */
describe("ventana configurable", () => {
  it("usa la ventana que viene del backend, no un número escrito a mano", async () => {
    getMock.mockResolvedValue({ data: resumen({ mesesVentana: 6 }) });

    render(<MisAmonestaciones />);

    expect(await screen.findByText(/los últimos 6 meses/i)).toBeInTheDocument();
  });

  it("con una ventana de un mes lo dice en singular", async () => {
    getMock.mockResolvedValue({ data: resumen({ mesesVentana: 1 }) });

    render(<MisAmonestaciones />);

    expect(await screen.findByText(/el último mes/i)).toBeInTheDocument();
  });
});
