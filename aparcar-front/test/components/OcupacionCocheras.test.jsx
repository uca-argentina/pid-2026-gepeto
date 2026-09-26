import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock } }));
vi.mock("sonner", () => ({ toast: { error: toastErrorMock } }));

const { default: OcupacionCocheras } = await import("@/components/OcupacionCocheras");

/**
 * El panel de ocupación del admin.
 *
 * Caja negra sobre el componente: lo que importa es que agrupe por tipo y por
 * piso a partir de lo que devuelve el backend (sin listas escritas en el
 * código), que moverse de día cambie lo que se ve, y que el detalle muestre los
 * cuatro datos pedidos: patente, horario, modalidad y mail.
 */

// El día que ven los tests, para que no dependan de cuándo se corren.
const AHORA = new Date(2026, 8, 26, 10, 0);
const HOY = "2026-09-26";

const cochera = (numero, tipo = "AUTO", sector = "Planta Baja") => ({
  id: `c-${numero}`,
  numero,
  tipo,
  sector,
  estado: "HABILITADA",
});

const reserva = (overrides = {}) => ({
  id: "r1",
  desde: `${HOY}T08:00`,
  hasta: `${HOY}T12:00`,
  estado: "CONFIRMADA",
  modalidad: "FRANJA",
  visitante: { id: "v1", nombre: "Juan Perez", email: "juan@test.com" },
  vehiculo: { id: "veh1", patente: "ABC123", tipo: "AUTO" },
  cochera: cochera("A-01"),
  ...overrides,
});

function mockData({ cocheras = [], reservas = [] }) {
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/cocheras") return Promise.resolve({ data: cocheras });
    if (url === "/api/v1/reservas") return Promise.resolve({ data: reservas });
    return Promise.reject(new Error(`URL no mockeada: ${url}`));
  });
}

/** Los desplegables, que son los botones con contador "n/m". */
const grupos = () =>
  screen.getAllByRole("button").filter((b) => /\d+\/\d+/.test(b.textContent));

const grupo = (nombre) => screen.getByRole("button", { name: new RegExp(nombre, "i") });

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(AHORA);
});

afterEach(() => {
  vi.useRealTimers();
});

describe("OcupacionCocheras: los grupos", () => {
  it("arranca en el día de hoy", async () => {
    mockData({ cocheras: [cochera("A-01")] });
    render(<OcupacionCocheras />);

    expect(await screen.findByText(/sábado 26 de septiembre/i)).toBeInTheDocument();
    expect(screen.getByText("Hoy")).toBeInTheDocument();
  });

  // Los grupos salen de las cocheras que manda el backend. Esto es lo que hace
  // que sumar un piso no obligue a tocar el código.
  it("arma un desplegable por cada tipo que exista, sin lista fija", async () => {
    mockData({
      cocheras: [cochera("A-01", "AUTO"), cochera("M-01", "MOTO"), cochera("B-01", "BARCO")],
    });
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupos()).toHaveLength(3));
    expect(grupo("Auto")).toBeInTheDocument();
    expect(grupo("Moto")).toBeInTheDocument();
    // Un tipo sin etiqueta cargada se muestra crudo, no desaparece.
    expect(grupo("BARCO")).toBeInTheDocument();
  });

  it("la pestaña por piso agrupa los mismos datos por sector", async () => {
    mockData({
      cocheras: [
        cochera("A-01", "AUTO", "Planta Baja"),
        cochera("A-02", "AUTO", "Subsuelo"),
        cochera("M-01", "MOTO", "Subsuelo"),
      ],
    });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupos()).toHaveLength(2)); // AUTO y MOTO

    await user.click(screen.getByRole("tab", { name: /por piso/i }));

    await waitFor(() => expect(grupos()).toHaveLength(2)); // Planta Baja y Subsuelo
    expect(grupo("Planta Baja")).toBeInTheDocument();
    expect(grupo("Subsuelo")).toBeInTheDocument();
  });

  it("el contador dice cuántas están ocupadas sobre el total", async () => {
    const libres = [cochera("A-01"), cochera("A-02"), cochera("A-03")];
    mockData({ cocheras: libres, reservas: [reserva({ cochera: libres[0] })] });
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Auto")).toHaveTextContent("1/3"));
    expect(screen.getByText("1 de 3 cocheras ocupadas")).toBeInTheDocument();
  });

  // Se piden una sola vez: cambiar de pestaña o de día trabaja sobre lo que ya
  // esta en memoria, no vuelve a la red.
  it("no vuelve a pedir datos al cambiar de pestaña o de día", async () => {
    mockData({ cocheras: [cochera("A-01")] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(2));

    await user.click(screen.getByRole("tab", { name: /por piso/i }));
    await user.click(screen.getByRole("button", { name: /día siguiente/i }));

    expect(getMock).toHaveBeenCalledTimes(2);
  });
});

describe("OcupacionCocheras: el detalle", () => {
  it("muestra patente, horario, modalidad y mail de cada ocupación", async () => {
    const c = cochera("A-01");
    mockData({
      cocheras: [c],
      reservas: [reserva({ cochera: c, modalidad: "MEDIA_JORNADA" })],
    });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Auto")).toBeInTheDocument());
    await user.click(grupo("Auto"));

    const item = (await screen.findByText("ABC123")).closest("li");
    expect(within(item).getByText("A-01")).toBeInTheDocument();
    expect(within(item).getByText("08:00 – 12:00")).toBeInTheDocument();
    expect(within(item).getByText("Media jornada")).toBeInTheDocument();
    expect(within(item).getByText("juan@test.com")).toBeInTheDocument();
  });

  it("hasta que no se elige un grupo, invita a elegir uno", async () => {
    mockData({ cocheras: [cochera("A-01")] });
    render(<OcupacionCocheras />);

    expect(await screen.findByText(/elegí un tipo de cochera/i)).toBeInTheDocument();
  });

  it("un grupo sin nada ocupado lo dice en vez de quedar vacío", async () => {
    mockData({ cocheras: [cochera("M-01", "MOTO")] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Moto")).toBeInTheDocument());
    await user.click(grupo("Moto"));

    expect(await screen.findByText(/no hay nada ocupado en moto/i)).toBeInTheDocument();
  });

  it("volver a tocar el mismo grupo lo cierra", async () => {
    const c = cochera("A-01");
    mockData({ cocheras: [c], reservas: [reserva({ cochera: c })] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Auto")).toBeInTheDocument());
    await user.click(grupo("Auto"));
    expect(await screen.findByText("ABC123")).toBeInTheDocument();

    await user.click(grupo("Auto"));
    expect(screen.queryByText("ABC123")).not.toBeInTheDocument();
  });
});

describe("OcupacionCocheras: moverse entre días", () => {
  it("el día siguiente muestra sus propias reservas", async () => {
    const c = cochera("A-01");
    mockData({
      cocheras: [c],
      reservas: [
        reserva({ id: "r1", cochera: c, vehiculo: { patente: "HOY111" } }),
        reserva({
          id: "r2",
          cochera: c,
          desde: "2026-09-27T08:00",
          hasta: "2026-09-27T12:00",
          vehiculo: { patente: "MAN222" },
        }),
      ],
    });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Auto")).toBeInTheDocument());
    await user.click(grupo("Auto"));
    expect(await screen.findByText("HOY111")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /día siguiente/i }));

    expect(await screen.findByText("MAN222")).toBeInTheDocument();
    expect(screen.queryByText("HOY111")).not.toBeInTheDocument();
    expect(screen.getByText("Mañana")).toBeInTheDocument();
  });

  it("el día anterior sigue mostrando lo que ya terminó", async () => {
    const c = cochera("A-01");
    mockData({
      cocheras: [c],
      reservas: [
        reserva({
          cochera: c,
          desde: "2026-09-25T08:00",
          hasta: "2026-09-25T12:00",
          estado: "FINALIZADA",
          vehiculo: { patente: "AYE333" },
        }),
      ],
    });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Auto")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /día anterior/i }));
    await user.click(grupo("Auto"));

    expect(await screen.findByText("AYE333")).toBeInTheDocument();
    expect(screen.getByText("Ayer")).toBeInTheDocument();
  });

  // Una jornada completa a caballo de la medianoche ocupa los dos días, y en
  // cada uno se muestra solo el tramo que cae ahí.
  it("una reserva que cruza la medianoche aparece en los dos días", async () => {
    const c = cochera("A-01");
    mockData({
      cocheras: [c],
      reservas: [
        reserva({
          cochera: c,
          desde: `${HOY}T20:00`,
          hasta: "2026-09-27T20:00",
          modalidad: "JORNADA_COMPLETA",
        }),
      ],
    });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Auto")).toBeInTheDocument());
    await user.click(grupo("Auto"));
    // Hoy arranca a las 20 y sigue: el fin se marca con flecha.
    expect(await screen.findByText("20:00 – →")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /día siguiente/i }));
    // Mañana viene de antes y termina a las 20.
    expect(await screen.findByText("← – 20:00")).toBeInTheDocument();
  });

  it("el botón Hoy vuelve al día actual y desaparece cuando ya estás ahí", async () => {
    mockData({ cocheras: [cochera("A-01")] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(screen.getByText("Hoy")).toBeInTheDocument());
    // Estando en hoy no hay a dónde volver, así que no hay botón.
    expect(screen.queryByRole("button", { name: "Hoy" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /día siguiente/i }));
    await user.click(await screen.findByRole("button", { name: "Hoy" }));

    expect(await screen.findByText(/sábado 26 de septiembre/i)).toBeInTheDocument();
  });
});

describe("OcupacionCocheras: casos de borde", () => {
  it("una reserva cancelada no ocupa", async () => {
    const c = cochera("A-01");
    mockData({ cocheras: [c], reservas: [reserva({ cochera: c, estado: "CANCELADA" })] });
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Auto")).toHaveTextContent("0/1"));
  });

  it("sin cocheras cargadas lo dice", async () => {
    mockData({ cocheras: [], reservas: [] });
    render(<OcupacionCocheras />);

    expect(await screen.findByText(/todavía no hay cocheras cargadas/i)).toBeInTheDocument();
  });

  it("si falla la carga avisa y no rompe", async () => {
    getMock.mockRejectedValue(new Error("sin red"));
    render(<OcupacionCocheras />);

    await waitFor(() => expect(toastErrorMock).toHaveBeenCalledWith(
      "No se pudo cargar la ocupación de cocheras."
    ));
    expect(screen.getByRole("tab", { name: /por tipo/i })).toBeInTheDocument();
  });
});

describe("OcupacionCocheras: refresco", () => {
  // El panel vive justo arriba del alta de reserva. Sin esto mentiría apenas
  // se reserva: la cochera recién ocupada seguiría figurando libre.
  it("vuelve a pedir los datos cuando cambia refreshKey", async () => {
    mockData({ cocheras: [cochera("A-01")] });
    const { rerender } = render(<OcupacionCocheras refreshKey={0} />);

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(2));

    rerender(<OcupacionCocheras refreshKey={1} />);

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(4));
  });
});
