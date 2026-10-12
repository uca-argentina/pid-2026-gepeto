import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, putMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  putMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({
  default: { get: getMock, post: postMock, put: putMock },
}));
vi.mock("sonner", () => ({
  toast: { success: toastSuccessMock, error: toastErrorMock },
}));

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

    const fila = (await screen.findByText("A-01")).closest(".ocupacion-cochera");
    expect(within(fila).getByText("ABC123")).toBeInTheDocument();
    expect(within(fila).getByText("08:00 – 12:00")).toBeInTheDocument();
    expect(within(fila).getByText("Media jornada")).toBeInTheDocument();
    expect(within(fila).getByText("juan@test.com")).toBeInTheDocument();
  });

  // Lo que cambia respecto de la primera version: el panel es del predio
  // entero, asi que una cochera sin reservas figura igual, marcada como libre.
  it("las cocheras libres también figuran", async () => {
    const ocupada = cochera("A-01");
    mockData({
      cocheras: [ocupada, cochera("A-02"), cochera("A-03")],
      reservas: [reserva({ cochera: ocupada })],
    });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Auto")).toBeInTheDocument());
    await user.click(grupo("Auto"));

    expect(await screen.findByText("A-02")).toBeInTheDocument();
    expect(screen.getByText("A-03")).toBeInTheDocument();
    expect(screen.getAllByText("Libre")).toHaveLength(2);
  });

  it("el filtro deja solo las reservadas y se puede volver atrás", async () => {
    const ocupada = cochera("A-01");
    mockData({
      cocheras: [ocupada, cochera("A-02")],
      reservas: [reserva({ cochera: ocupada })],
    });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Auto")).toBeInTheDocument());
    await user.click(grupo("Auto"));
    expect(await screen.findByText("A-02")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /solo reservadas/i }));

    expect(screen.queryByText("A-02")).not.toBeInTheDocument();
    expect(screen.getByText("A-01")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /solo reservadas/i }));
    expect(await screen.findByText("A-02")).toBeInTheDocument();
  });

  it("hasta que no se elige un grupo, invita a elegir uno", async () => {
    mockData({ cocheras: [cochera("A-01")] });
    render(<OcupacionCocheras />);

    expect(await screen.findByText(/elegí un tipo de cochera/i)).toBeInTheDocument();
  });

  it("un grupo sin nada ocupado muestra sus cocheras como libres", async () => {
    mockData({ cocheras: [cochera("M-01", "MOTO")] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Moto")).toBeInTheDocument());
    await user.click(grupo("Moto"));

    expect(await screen.findByText("M-01")).toBeInTheDocument();
    expect(screen.getByText("Libre")).toBeInTheDocument();
  });

  it("con el filtro puesto y nada reservado, lo dice", async () => {
    mockData({ cocheras: [cochera("M-01", "MOTO")] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);

    await waitFor(() => expect(grupo("Moto")).toBeInTheDocument());
    await user.click(grupo("Moto"));
    await user.click(screen.getByRole("button", { name: /solo reservadas/i }));

    expect(await screen.findByText(/no hay ninguna cochera reservada/i)).toBeInTheDocument();
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

describe("OcupacionCocheras: acciones sobre la cochera", () => {
  const confirmando = (respuesta) =>
    vi.spyOn(window, "confirm").mockReturnValue(respuesta);

  const abrirAuto = async (user) => {
    await waitFor(() => expect(grupo("Auto")).toBeInTheDocument());
    await user.click(grupo("Auto"));
    await screen.findByText("A-01");
  };

  it("deshabilita una cochera mandando la cochera entera, no solo el estado", async () => {
    confirmando(true);
    putMock.mockResolvedValue({ data: {} });
    mockData({ cocheras: [cochera("A-01")] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);
    await abrirAuto(user);

    await user.click(screen.getByRole("button", { name: "Deshabilitar" }));

    // El PUT del backend reemplaza la cochera completa: mandar solo el estado
    // le borraria el numero, el sector y el tipo.
    await waitFor(() => expect(putMock).toHaveBeenCalledWith("/api/v1/cocheras/c-A-01", {
      numero: "A-01",
      sector: "Planta Baja",
      tipo: "AUTO",
      estado: "DESHABILITADA",
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith("Cochera A-01 deshabilitada");
  });

  it("una cochera fuera de servicio se vuelve a habilitar desde el mismo lugar", async () => {
    confirmando(true);
    putMock.mockResolvedValue({ data: {} });
    mockData({ cocheras: [{ ...cochera("A-01"), estado: "DESHABILITADA" }] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);
    await abrirAuto(user);

    expect(screen.getByText("Fuera de servicio")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Habilitar" }));

    await waitFor(() => expect(putMock).toHaveBeenCalledWith(
      "/api/v1/cocheras/c-A-01",
      expect.objectContaining({ estado: "HABILITADA" })
    ));
  });

  // El aviso dice cuantas reservas se van a cancelar porque el panel ya las
  // tiene: no hace falta advertir en abstracto.
  it("al deshabilitar avisa cuántas reservas se van a cancelar", async () => {
    const confirm = confirmando(false);
    const c = cochera("A-01");
    mockData({ cocheras: [c], reservas: [reserva({ cochera: c })] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);
    await abrirAuto(user);

    await user.click(screen.getByRole("button", { name: "Deshabilitar" }));

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining("1 reserva confirmada"));
    // Si se dice que no, no se toca nada.
    expect(putMock).not.toHaveBeenCalled();
  });

  it("sin reservas no amenaza con cancelar nada", async () => {
    const confirm = confirmando(false);
    mockData({ cocheras: [cochera("A-01")] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);
    await abrirAuto(user);

    await user.click(screen.getByRole("button", { name: "Deshabilitar" }));

    expect(confirm).toHaveBeenCalledWith(expect.not.stringContaining("cancelar"));
  });

  it("si el backend rechaza el cambio lo dice y no miente", async () => {
    confirmando(true);
    putMock.mockRejectedValue({ response: { data: { message: "No se puede" } } });
    mockData({ cocheras: [cochera("A-01")] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);
    await abrirAuto(user);

    await user.click(screen.getByRole("button", { name: "Deshabilitar" }));

    await waitFor(() => expect(toastErrorMock).toHaveBeenCalledWith("No se puede"));
    expect(screen.getByRole("button", { name: "Deshabilitar" })).toBeEnabled();
  });
});

describe("OcupacionCocheras: cancelar una reserva", () => {
  const abrirAuto = async (user) => {
    await waitFor(() => expect(grupo("Auto")).toBeInTheDocument());
    await user.click(grupo("Auto"));
    await screen.findByText("ABC123");
  };

  it("cancela y vuelve a pedir los datos", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    postMock.mockResolvedValue({ data: {} });
    const c = cochera("A-01");
    mockData({ cocheras: [c], reservas: [reserva({ cochera: c })] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);
    await abrirAuto(user);
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(2));

    await user.click(screen.getByRole("button", { name: "Cancelar" }));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith("/api/v1/reservas/r1/cancelar"));
    // El panel no puede quedar mostrando la reserva que se acaba de cancelar.
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(4));
  });

  it("si se dice que no, no cancela", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const c = cochera("A-01");
    mockData({ cocheras: [c], reservas: [reserva({ cochera: c })] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);
    await abrirAuto(user);

    await user.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(postMock).not.toHaveBeenCalled();
  });

  // Una reserva ya vencida se sigue viendo, pero cancelarla no significaria
  // nada: el boton no esta.
  it("una reserva finalizada no ofrece cancelar", async () => {
    const c = cochera("A-01");
    mockData({
      cocheras: [c],
      reservas: [reserva({ cochera: c, estado: "FINALIZADA" })],
    });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);
    await abrirAuto(user);

    expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument();
  });

  it("el confirm nombra la patente y la cochera, para no cancelar la que no era", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    const c = cochera("A-01");
    mockData({ cocheras: [c], reservas: [reserva({ cochera: c })] });
    const user = userEvent.setup();
    render(<OcupacionCocheras />);
    await abrirAuto(user);

    await user.click(screen.getByRole("button", { name: "Cancelar" }));

    const texto = confirm.mock.calls[0][0];
    expect(texto).toContain("ABC123");
    expect(texto).toContain("A-01");
  });
});
