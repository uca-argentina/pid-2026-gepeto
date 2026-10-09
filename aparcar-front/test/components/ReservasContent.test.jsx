import { describe, it, expect, beforeEach, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock } }));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: ReservasContent } = await import("@/components/ReservasContent");

const visitante = (overrides = {}) => ({ id: "v1", nombre: "Juan Perez", documento: "1", ...overrides });
const vehiculo = (overrides = {}) => ({ id: "veh1", patente: "ABC123", tipo: "AUTO", visitanteId: "v1", ...overrides });
const cochera = (overrides = {}) => ({ id: "c1", numero: "A-01", sector: "Planta Baja", tipo: "AUTO", ...overrides });

describe("ReservasContent: trazabilidad", () => {
  const alta = {
    id: "m1", accion: "ALTA", fecha: "2026-10-09T15:30:00Z",
    actorId: "a1", actorNombre: "Ana Admin", actorEmail: "ana@test.com", actorRol: "ADMIN",
  };
  const reserva = {
    id: "r1", desde: "2026-10-10T10:00", hasta: "2026-10-10T11:00",
    visitante: visitante(), vehiculo: vehiculo(), cochera: cochera(), estado: "CANCELADA",
  };

  beforeEach(() => vi.clearAllMocks());

  it.each([
    ["USUARIO", "CANCELASTE ESTA RESERVA"],
    ["ADMINISTRACION", "CANCELADA POR ADMINISTRACIÓN"],
    ["DESHABILITACION", "DESHABILITADA POR ADMINISTRACIÓN"],
    [null, "CANCELADA"],
  ])("muestra al usuario el motivo %s sin exponer el historial administrativo", async (motivoCancelacion, mensaje) => {
    // Incluso si una respuesta trajera historial, el modo user no lo renderiza.
    mockData({ reservas: [{ ...reserva, motivoCancelacion, historial: [alta] }] });
    render(<ReservasContent modo="user" />);
    await userEvent.setup().click(await screen.findByRole("button", { name: /^Canceladas 1$/ }));
    expect(await screen.findByText(mensaje)).toBeInTheDocument();
    expect(screen.queryByText("Historial de la reserva")).not.toBeInTheDocument();
    expect(screen.queryByText(/Ana Admin/)).not.toBeInTheDocument();
    expect(screen.queryByText(/ana@test.com/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument();
  });

  it.each([
    ["CANCELACION", "USUARIO", "USER", "Cancelación de reserva", "CANCELADA POR USUARIO"],
    ["CANCELACION", "ADMINISTRACION", "ADMIN", "Cancelación de reserva", "CANCELADA POR ADMINISTRACIÓN"],
    ["DESHABILITACION", "DESHABILITACION", "ADMIN", "Deshabilitación por baja de cochera", "DESHABILITADA POR ADMINISTRACIÓN"],
  ])("admin ve al ocupante y los autores de alta y %s (%s)", async (accion, motivoCancelacion, actorRol, etiqueta, estado) => {
    const movimiento = {
      id: "m2", accion, actorRol, fecha: "2026-10-09T16:45:00Z",
      actorId: "a2", actorNombre: "Luis", actorEmail: "luis@test.com",
    };
    mockData({ reservas: [{ ...reserva, motivoCancelacion, historial: [alta, movimiento] }] });
    render(<ReservasContent modo="admin" />);
    await userEvent.setup().click(await screen.findByRole("button", { name: /^Canceladas/ }));
    expect(await screen.findByText("Juan Perez — ABC123")).toBeInTheDocument();
    expect(screen.getByText(estado)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByText("Historial de la reserva"));
    const historial = screen.getByRole("list", { name: "Movimientos de la reserva" });
    expect(within(historial).getAllByRole("listitem")).toHaveLength(2);
    expect(within(historial).getByText("Alta de reserva")).toBeInTheDocument();
    expect(within(historial).getByText(etiqueta)).toBeInTheDocument();
    expect(within(historial).getByText(/Ana Admin \(Administrador\).*ana@test.com/)).toBeInTheDocument();
    expect(within(historial).getByText(new RegExp(`Luis \\(${actorRol === "ADMIN" ? "Administrador" : "Usuario"}\\).*luis@test.com`))).toBeInTheDocument();
    const fechas = historial.querySelectorAll("time");
    expect(fechas[0]).toHaveAttribute("dateTime", alta.fecha);
    expect(fechas[0]).toHaveTextContent(/12:30/);
    expect(fechas[1]).toHaveTextContent(/13:45/);
  });

  it("las reservas anteriores informan la falta de autor sin atribuir el alta al ocupante", async () => {
    mockData({ reservas: [{ ...reserva, historial: [] }] });
    render(<ReservasContent modo="admin" />);
    await userEvent.setup().click(await screen.findByRole("button", { name: /^Canceladas/ }));
    await userEvent.setup().click(await screen.findByText("Historial de la reserva"));
    expect(screen.getByText("Alta sin autor registrado.")).toBeInTheDocument();
    expect(screen.getByText("Sin movimientos registrados.")).toBeInTheDocument();
  });

  it("actualiza el motivo visible luego de cancelar una reserva propia", async () => {
    mockData({ reservas: [{ ...reserva, estado: "CONFIRMADA" }] });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    postMock.mockImplementation(async () => {
      mockData({ reservas: [{ ...reserva, motivoCancelacion: "USUARIO" }] });
      return { data: {} };
    });
    render(<ReservasContent modo="user" />);
    await userEvent.setup().click(await screen.findByRole("button", { name: "Cancelar" }));
    expect(await screen.findByText("No hay reservas activas.")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Canceladas 1" }));
    expect(await screen.findByText("CANCELASTE ESTA RESERVA")).toBeInTheDocument();
    expect(postMock).toHaveBeenCalledWith("/api/v1/reservas/r1/cancelar");
    expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument();
    vi.restoreAllMocks();
  });
});

function mockData({ visitantes = [], vehiculos = [], reservas = [], disponibles = [] }) {
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/tarifas/cotizacion") return Promise.resolve({ data: { tipo: "AUTO", total: 1000, horas: 1 } });
    if (url === "/api/v1/visitantes") return Promise.resolve({ data: visitantes });
    if (url === "/api/v1/vehiculos") return Promise.resolve({ data: vehiculos });
    if (url === "/api/v1/reservas") return Promise.resolve({ data: reservas });
    if (url === "/api/v1/cocheras/disponibles") return Promise.resolve({ data: disponibles });
    return Promise.reject(new Error(`URL no mockeada: ${url}`));
  });
}

describe("ReservasContent en modo admin", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("muestra el mensaje de vacio cuando no hay reservas cargadas", async () => {
    mockData({});
    render(<ReservasContent modo="admin" />);

    expect(await screen.findByText("Todavía no hay reservas cargadas.")).toBeInTheDocument();
  });

  it("lista las reservas existentes con su estado", async () => {
    mockData({
      reservas: [
        {
          id: "r1",
          desde: "2026-01-01T10:00",
          hasta: "2026-01-01T12:00",
          estado: "CONFIRMADA",
          visitante: { nombre: "Juan Perez" },
          vehiculo: { patente: "ABC123" },
          cochera: { numero: "A-01", sector: "Planta Baja" },
        },
      ],
    });
    render(<ReservasContent modo="admin" />);

    expect(await screen.findByText("Juan Perez — ABC123")).toBeInTheDocument();
    expect(screen.getByText("CONFIRMADA")).toBeInTheDocument();
  });

  it("al escribir una patente registrada, muestra el nombre del visitante y el tipo de vehiculo", async () => {
    mockData({ visitantes: [visitante()], vehiculos: [vehiculo()] });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");

    expect(await screen.findByText("Juan Perez — AUTO")).toBeInTheDocument();
  });

  it("al escribir una patente que no existe, avisa que no se encontro ningun vehiculo", async () => {
    mockData({});
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ZZZ999");

    expect(
      await screen.findByText("No hay ningún vehículo registrado con esa patente.")
    ).toBeInTheDocument();
  });

  it("la cochera queda deshabilitada hasta encontrar un vehiculo por patente", async () => {
    mockData({});
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    expect(screen.getByLabelText("Cochera")).toBeDisabled();
  });

  it("al resolver un vehiculo (con la franja ya cargada por defecto), pide las cocheras disponibles de ese tipo", async () => {
    mockData({ visitantes: [visitante()], vehiculos: [vehiculo()], disponibles: [cochera()] });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");

    await waitFor(() =>
      expect(getMock).toHaveBeenCalledWith(
        "/api/v1/cocheras/disponibles",
        expect.objectContaining({ params: expect.objectContaining({ tipoVehiculo: "AUTO" }) })
      )
    );
    expect(await screen.findByRole("option", { name: /A-01/ })).toBeInTheDocument();
    expect(screen.getByLabelText("Cochera")).not.toBeDisabled();
  });

  // Regresion del bug real: si se carga un vehiculo nuevo arriba en la misma
  // pagina, esta lista ya se habia pedido antes de que existiera. El fix fue
  // re-pedirla al hacer foco en el campo de patente.
  it("el foco en el campo de patente vuelve a pedir vehiculos y visitantes", async () => {
    mockData({});
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    const llamadasIniciales = getMock.mock.calls.filter((c) => c[0] === "/api/v1/vehiculos").length;

    await user.click(screen.getByPlaceholderText("ABC123 / AB123CD"));

    await waitFor(() => {
      const llamadasLuegoDelFoco = getMock.mock.calls.filter((c) => c[0] === "/api/v1/vehiculos").length;
      expect(llamadasLuegoDelFoco).toBeGreaterThan(llamadasIniciales);
    });
  });

  it("confirmar la reserva envia el visitanteId/vehiculoId resueltos por patente, junto con cochera y franja", async () => {
    mockData({ visitantes: [visitante()], vehiculos: [vehiculo()], disponibles: [cochera()] });
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");
    await screen.findByRole("option", { name: /A-01/ });
    await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await user.click(screen.getByRole("button", { name: /confirmar reserva/i }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(
        "/api/v1/reservas",
        expect.objectContaining({
          visitanteId: "v1",
          vehiculoId: "veh1",
          cocheraId: "c1",
          desde: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/),
          hasta: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/),
        })
      )
    );
    expect(toastSuccessMock).toHaveBeenCalledWith("Reserva creada correctamente");
  });

  it("el boton de confirmar reserva esta deshabilitado hasta elegir una cochera", async () => {
    mockData({});
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    expect(screen.getByRole("button", { name: /confirmar reserva/i })).toBeDisabled();
  });
});

describe("ReservasContent: cancelar una reserva", () => {
  const RESERVA_CONFIRMADA = {
    id: "r1",
    desde: "2026-01-01T10:00",
    hasta: "2026-01-01T12:00",
    estado: "CONFIRMADA",
    visitante: { nombre: "Juan Perez" },
    vehiculo: { patente: "ABC123" },
    cochera: { numero: "A-01", sector: "Planta Baja" },
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("cancela con POST /api/v1/reservas/{id}/cancelar", async () => {
    mockData({ reservas: [RESERVA_CONFIRMADA] });
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await screen.findByText("Juan Perez — ABC123");

    await user.click(screen.getByRole("button", { name: /^cancelar$/i }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith("/api/v1/reservas/r1/cancelar")
    );
    expect(toastSuccessMock).toHaveBeenCalledWith("Reserva cancelada correctamente");
  });

  it("pide confirmacion y no hace nada si se cancela el dialogo", async () => {
    window.confirm.mockReturnValue(false);
    mockData({ reservas: [RESERVA_CONFIRMADA] });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await screen.findByText("Juan Perez — ABC123");

    await user.click(screen.getByRole("button", { name: /^cancelar$/i }));

    expect(postMock).not.toHaveBeenCalled();
  });

  // Cancelar libera la cochera, así que la cuadrícula de ocupación tiene que
  // enterarse igual que cuando se crea una reserva.
  it("avisa que la ocupacion cambio", async () => {
    mockData({ reservas: [RESERVA_CONFIRMADA] });
    postMock.mockResolvedValue({ data: {} });
    const onOcupacionCambiada = vi.fn();
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" onOcupacionCambiada={onOcupacionCambiada} />);
    await screen.findByText("Juan Perez — ABC123");

    await user.click(screen.getByRole("button", { name: /^cancelar$/i }));

    await waitFor(() => expect(onOcupacionCambiada).toHaveBeenCalled());
  });

  // Cancelar no borra la fila: queda con el badge CANCELADA, y ya no se puede
  // volver a cancelar.
  it("no ofrece cancelar una reserva que ya esta cancelada", async () => {
    mockData({ reservas: [{ ...RESERVA_CONFIRMADA, estado: "CANCELADA" }] });
    render(<ReservasContent modo="admin" />);
    await userEvent.setup().click(await screen.findByRole("button", { name: /^Canceladas/ }));

    expect(await screen.findByText("CANCELADA")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^cancelar$/i })).not.toBeInTheDocument();
  });

  it("el visitante tambien puede cancelar desde su dashboard", async () => {
    mockData({ vehiculos: [vehiculo()], reservas: [RESERVA_CONFIRMADA] });
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<ReservasContent modo="user" />);
    await screen.findByText("Mis reservas");

    await user.click(screen.getByRole("button", { name: /^cancelar$/i }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith("/api/v1/reservas/r1/cancelar")
    );
  });

  it("si el backend rechaza la cancelacion, muestra su mensaje", async () => {
    mockData({ reservas: [RESERVA_CONFIRMADA] });
    postMock.mockRejectedValue({
      response: { data: { message: "La reserva ya estaba cancelada." } },
    });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await screen.findByText("Juan Perez — ABC123");

    await user.click(screen.getByRole("button", { name: /^cancelar$/i }));

    await waitFor(() =>
      expect(toastErrorMock).toHaveBeenCalledWith("La reserva ya estaba cancelada.")
    );
  });
});

describe("ReservasContent en modo visitante", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("muestra filtros y orden en Mis reservas sin paginar una lista vacía", async () => {
    mockData({ vehiculos: [vehiculo()], reservas: [] });
    render(<ReservasContent modo="user" />);
    expect(await screen.findByText("Todavía no tenés reservas.")).toBeInTheDocument();
    expect(screen.getByText("Mis reservas")).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Filtrar reservas por estado" })).toBeInTheDocument();
    expect(screen.getByLabelText("Ordenar por")).toHaveValue("INICIO_ASC");
    expect(screen.queryByRole("navigation", { name: "Páginas de reservas" })).not.toBeInTheDocument();
  });

  it("permite reintentar la carga de Mis reservas sin confundir un error con una lista vacía", async () => {
    mockData({});
    const cargarDatos = getMock.getMockImplementation();
    let fallo = true;
    getMock.mockImplementation((url) => {
      if (url === "/api/v1/reservas" && fallo) {
        fallo = false;
        return Promise.reject(new Error("Sin conexión"));
      }
      return cargarDatos(url);
    });
    render(<ReservasContent modo="user" />);
    expect(await screen.findByRole("alert")).toHaveTextContent("No se pudieron cargar las reservas.");
    expect(screen.queryByText("Todavía no tenés reservas.")).not.toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Reintentar" }));
    expect(await screen.findByText("Todavía no tenés reservas.")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("un rechazo al cancelar conserva la reserva propia y habilita un nuevo intento", async () => {
    mockData({ reservas: [{
      id: "r1", estado: "CONFIRMADA", desde: "2026-10-10T10:00", hasta: "2026-10-10T12:00",
      vehiculo: vehiculo(), cochera: cochera(),
    }] });
    postMock.mockRejectedValue({ response: { data: { message: "No se pudo cancelar la reserva." } } });
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
    try {
      render(<ReservasContent modo="user" />);
      const cancelar = await screen.findByRole("button", { name: "Cancelar" });
      await userEvent.setup().click(cancelar);
      await waitFor(() => expect(toastErrorMock).toHaveBeenCalledWith("No se pudo cancelar la reserva."));
      await waitFor(() => expect(cancelar).toBeEnabled());
      expect(screen.getByRole("button", { name: "Activas 1" })).toHaveAttribute("aria-pressed", "true");
      expect(screen.getByRole("img", { name: "Patente ABC123" })).toBeInTheDocument();
    } finally {
      confirmar.mockRestore();
    }
  });

  // Un visitante solo puede reservar a su nombre, asi que no tiene por que
  // saber que otros visitantes existen: la pantalla ni siquiera pide la lista.
  it("no pide el catalogo de visitantes", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="user" />);

    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));
    expect(getMock).not.toHaveBeenCalledWith("/api/v1/visitantes");
  });

  it("ofrece sus propias patentes en un desplegable en vez de un campo libre", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="user" />);

    expect(await screen.findByRole("option", { name: /ABC123 — AUTO/ })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("ABC123 / AB123CD")).not.toBeInTheDocument();
  });

  it("si todavia no cargo ningun vehiculo, explica que hace falta uno para reservar", async () => {
    mockData({ vehiculos: [] });
    render(<ReservasContent modo="user" />);

    const link = await screen.findByRole("link", { name: '"Mis datos"' });
    expect(link).toHaveAttribute("href", "/dashboard-user/perfil#mis-vehiculos");
    expect(link.closest("p")).toHaveTextContent('Cargá al menos un vehículo en "Mis datos" para poder reservar.');
  });

  it("al elegir un vehiculo muestra su patente grafica debajo del desplegable", async () => {
    mockData({ vehiculos: [vehiculo(), vehiculo({ id: "veh2", patente: "A123BCD", tipo: "MOTO" })] });
    const user = userEvent.setup();
    render(<ReservasContent modo="user" />);

    await screen.findByRole("option", { name: /A123BCD — MOTO/ });
    expect(screen.queryByRole("img", { name: /^Patente/ })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Patente"), "A123BCD");

    expect(screen.getByRole("img", { name: "Patente A123BCD" })).toHaveAttribute("data-formato", "moto-mercosur");
  });

  // El backend ignora el visitanteId que mande un USER, pero el frontend
  // tampoco lo manda: la reserva siempre va a nombre de quien esta logueado.
  it("no manda visitanteId al reservar", async () => {
    mockData({ vehiculos: [vehiculo()], disponibles: [cochera()] });
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<ReservasContent modo="user" />);

    await screen.findByRole("option", { name: /ABC123 — AUTO/ });
    await user.selectOptions(screen.getByLabelText("Patente"), "ABC123");
    await screen.findByRole("option", { name: /A-01/ });
    await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await user.click(screen.getByRole("button", { name: /confirmar reserva/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalled());

    const [, body] = postMock.mock.calls[0];
    expect(body.visitanteId).toBeUndefined();
    expect(body).toMatchObject({ vehiculoId: "veh1", cocheraId: "c1" });
  });

  it("lista solo la patente, sin el nombre del visitante", async () => {
    mockData({
      vehiculos: [vehiculo()],
      reservas: [
        {
          id: "r1",
          desde: "2026-01-01T10:00",
          hasta: "2026-01-01T12:00",
          estado: "CONFIRMADA",
          visitante: { nombre: "Juan Perez" },
          vehiculo: { patente: "ABC123" },
          cochera: { numero: "A-01", sector: "Planta Baja" },
        },
      ],
    });
    render(<ReservasContent modo="user" />);

    expect(await screen.findByText("Mis reservas")).toBeInTheDocument();
    expect(screen.queryByText("Juan Perez — ABC123")).not.toBeInTheDocument();
  });

  it("en Mis reservas la patente se ve como una chapa grafica", async () => {
    mockData({
      vehiculos: [vehiculo()],
      reservas: [
        {
          id: "r1",
          desde: "2026-01-01T10:00",
          hasta: "2026-01-01T12:00",
          estado: "CONFIRMADA",
          vehiculo: { patente: "ABC123", tipo: "AUTO" },
          cochera: { numero: "A-01", sector: "Planta Baja" },
        },
      ],
    });
    render(<ReservasContent modo="user" />);

    const lista = await screen.findByRole("list", { name: "Mis reservas" });
    const chapa = within(lista).getByRole("img", { name: "Patente ABC123" });
    expect(chapa).toHaveAttribute("data-formato", "auto-anterior");
    expect(chapa).toHaveClass("patente--sm");
  });
});

describe("ReservasContent: franja horaria", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // "La reserva debe marcar por default la hora y dia del momento": el campo
  // no arranca vacío ni en medianoche.
  // El arranque por defecto cae en el bloque de 15 en curso, redondeando
  // hacia abajo: si son las 14:07 arranca 14:00, para no dejar sin cubrir los
  // minutos en los que el auto ya está estacionado.
  it("arranca en el bloque de 15 en curso y propone una hora de duracion", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    const desde = screen.getByLabelText("Desde").value;
    const hasta = screen.getByLabelText("Hasta").value;

    expect(Number(desde.slice(14, 16))).toBe(Math.floor(new Date().getMinutes() / 15) * 15);
    expect(new Date(hasta) - new Date(desde)).toBe(60 * 60 * 1000);
  });

  // El paso de 15 min lo ofrece el navegador, pero no impide escribir a mano:
  // por eso el valor se baja al bloque igual.
  it("los campos declaran el paso de 15 minutos", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    expect(screen.getByLabelText("Desde")).toHaveAttribute("step", "900");
    expect(screen.getByLabelText("Hasta")).toHaveAttribute("step", "900");
  });

  // Escribir los minutos pasa por estados intermedios: tipear el "3" de ":30"
  // deja ":03" un instante. Si el valor se acomodara en cada tecla, ese ":03"
  // se volveria ":00" y no habria forma de escribir ningun minuto.
  it("deja escribir cualquier minuto mientras se tipea", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2099-01-01T18:23" } });

    expect(screen.getByLabelText("Hasta")).toHaveValue("2099-01-01T18:23");
  });

  // Al salir se va al bloque MAS CERCANO, no al anterior: quien escribe 18:23
  // esta mas cerca de querer 18:30 que 18:15.
  it("al salir del campo redondea al bloque de 15 mas cercano", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    const hasta = screen.getByLabelText("Hasta");
    fireEvent.change(hasta, { target: { value: "2099-01-01T18:23" } });
    fireEvent.blur(hasta);

    await waitFor(() => expect(hasta).toHaveValue("2099-01-01T18:30"));
  });

  it("redondea para abajo cuando el minuto esta mas cerca del bloque anterior", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    const hasta = screen.getByLabelText("Hasta");
    fireEvent.change(hasta, { target: { value: "2099-01-01T18:07" } });
    fireEvent.blur(hasta);

    await waitFor(() => expect(hasta).toHaveValue("2099-01-01T18:00"));
  });

  // El redondeo puede llevar a la hora siguiente, y hasta al dia siguiente.
  it("redondear cerca de medianoche pasa al dia siguiente", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    const hasta = screen.getByLabelText("Hasta");
    fireEvent.change(hasta, { target: { value: "2099-01-01T23:53" } });
    fireEvent.blur(hasta);

    await waitFor(() => expect(hasta).toHaveValue("2099-01-02T00:00"));
  });

  it("el inicio se acomoda con la misma regla", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    const desde = screen.getByLabelText("Desde");
    fireEvent.change(desde, { target: { value: "2099-01-01T08:38" } });
    fireEvent.blur(desde);

    await waitFor(() => expect(desde).toHaveValue("2099-01-01T08:45"));
  });

  // La disponibilidad se pregunta por el rango completo, no por el día.
  it("pide las cocheras libres mandando desde y hasta", async () => {
    mockData({ visitantes: [visitante()], vehiculos: [vehiculo()], disponibles: [cochera()] });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");

    await waitFor(() =>
      expect(getMock).toHaveBeenCalledWith(
        "/api/v1/cocheras/disponibles",
        expect.objectContaining({
          params: expect.objectContaining({
            tipoVehiculo: "AUTO",
            desde: expect.any(String),
            hasta: expect.any(String),
          }),
        })
      )
    );
  });

  // Se puede reservar el tiempo que se desee: cambiar el "hasta" a otro día
  // tiene que volver a consultar disponibilidad con ese rango.
  // Mover la franja tiene que volver a preguntar qué cocheras quedan libres:
  // el rango cambió.
  it("cambiar la franja vuelve a consultar disponibilidad", async () => {
    mockData({ visitantes: [visitante()], vehiculos: [vehiculo()], disponibles: [cochera()] });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));
    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");
    await screen.findByRole("option", { name: /A-01/ });

    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2099-03-10T18:00" } });

    await waitFor(() =>
      expect(getMock).toHaveBeenCalledWith(
        "/api/v1/cocheras/disponibles",
        expect.objectContaining({ params: expect.objectContaining({ hasta: "2099-03-10T18:00" }) })
      )
    );
  });

  // Se avisa al instante: si se esperara al submit, el error visible sería el
  // de la cochera (deshabilitada por la franja inválida) y el problema real
  // quedaría escondido.
  it("avisa al instante si la franja queda invertida", async () => {
    mockData({ visitantes: [visitante()], vehiculos: [vehiculo()], disponibles: [cochera()] });
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2099-01-01T12:00" } });
    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2099-01-01T08:00" } });

    // waitFor y no findByText: el efecto de disponibilidad vuelve a renderizar
    // y findByText devolvería el nodo viejo, ya desprendido del documento.
    await waitFor(() =>
      expect(screen.getByText("El fin tiene que ser posterior al inicio")).toBeInTheDocument()
    );
    expect(screen.getByLabelText("Cochera")).toBeDisabled();
  });

  it("muestra la franja de cada reserva en el listado, no una fecha suelta", async () => {
    mockData({
      reservas: [
        {
          id: "r1",
          desde: "2026-01-01T10:00",
          hasta: "2026-01-01T12:00",
          estado: "CONFIRMADA",
          visitante: { nombre: "Juan Perez" },
          vehiculo: { patente: "ABC123" },
          cochera: { numero: "A-01", sector: "Planta Baja" },
        },
      ],
    });
    render(<ReservasContent modo="admin" />);

    // Mismo día: se nombra una sola vez y se muestran las dos horas.
    expect(await screen.findByText(/01\/01\/2026 de 10:00 a 12:00/)).toBeInTheDocument();
  });

  it("muestra los dos dias cuando la franja cruza la medianoche", async () => {
    mockData({
      reservas: [
        {
          id: "r1",
          desde: "2026-01-01T22:00",
          hasta: "2026-01-03T08:00",
          estado: "CONFIRMADA",
          visitante: { nombre: "Juan Perez" },
          vehiculo: { patente: "ABC123" },
          cochera: { numero: "A-01", sector: "Planta Baja" },
        },
      ],
    });
    render(<ReservasContent modo="admin" />);

    expect(await screen.findByText(/01\/01\/2026 22:00 → 03\/01\/2026 08:00/)).toBeInTheDocument();
  });
});

describe("ReservasContent: modalidad en el listado", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const reservaCon = (modalidad, extra = {}) => ({
    id: "r1",
    desde: "2026-01-01T10:00",
    hasta: "2026-01-01T12:00",
    estado: "CONFIRMADA",
    modalidad,
    visitante: { nombre: "Juan Perez" },
    vehiculo: { patente: "ABC123" },
    cochera: { numero: "A-01", sector: "Planta Baja" },
    ...extra,
  });

  // La modalidad la decide el backend a partir de la duración: el listado la
  // muestra, no la vuelve a deducir. Si la dedujera por su cuenta, backend y
  // pantalla podrían discrepar al cambiar un umbral.
  // Se consulta el badge del listado y no el texto suelto: "Media jornada" es
  // también el nombre de uno de los atajos del formulario.
  const badges = () =>
    [...document.querySelectorAll(".franja-modalidad-badge")].map((n) => n.textContent);

  it("muestra la modalidad que manda el backend", async () => {
    mockData({ reservas: [reservaCon("MEDIA_JORNADA")] });
    render(<ReservasContent modo="admin" />);
    await screen.findByText("Juan Perez — ABC123");

    expect(badges()).toEqual(["Media jornada"]);
  });

  it("traduce las tres modalidades", async () => {
    mockData({
      reservas: [
        reservaCon("FRANJA"),
        reservaCon("MEDIA_JORNADA", { id: "r2" }),
        reservaCon("JORNADA_COMPLETA", { id: "r3" }),
      ],
    });
    render(<ReservasContent modo="admin" />);
    await screen.findAllByText("Juan Perez — ABC123");

    expect(badges()).toEqual(["Por franja horaria", "Media jornada", "Jornada completa"]);
  });

  it("una reserva sin modalidad no rompe el listado", async () => {
    mockData({ reservas: [reservaCon(undefined)] });
    render(<ReservasContent modo="admin" />);

    expect(await screen.findByText("Juan Perez — ABC123")).toBeInTheDocument();
    // La chapa grafica es solo del visitante: el listado del admin no cambia.
    expect(screen.queryByRole("img", { name: /^Patente/ })).not.toBeInTheDocument();
  });
});

describe("ReservasContent: atajos de jornada", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // El atajo tiene que llegar hasta el campo: no alcanza con que el botón
  // calcule bien si después el formulario no toma el valor.
  it("media jornada deja el campo Hasta doce horas despues del inicio", async () => {
    mockData({ vehiculos: [vehiculo()] });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2099-01-01T08:00" } });
    await user.click(screen.getByRole("button", { name: /media jornada/i }));

    expect(screen.getByLabelText("Hasta")).toHaveValue("2099-01-01T20:00");
  });

  it("jornada completa lleva el fin al dia siguiente", async () => {
    mockData({ vehiculos: [vehiculo()] });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2099-01-01T08:00" } });
    await user.click(screen.getByRole("button", { name: /jornada completa/i }));

    expect(screen.getByLabelText("Hasta")).toHaveValue("2099-01-02T08:00");
  });

  // Cambiar la franja con un atajo también cambia qué cocheras sirven.
  it("usar un atajo vuelve a consultar disponibilidad", async () => {
    mockData({ visitantes: [visitante()], vehiculos: [vehiculo()], disponibles: [cochera()] });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));
    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");
    await screen.findByRole("option", { name: /A-01/ });

    const previas = getMock.mock.calls.filter((c) => c[0] === "/api/v1/cocheras/disponibles").length;
    await user.click(screen.getByRole("button", { name: /jornada completa/i }));

    await waitFor(() => {
      const ahora = getMock.mock.calls.filter((c) => c[0] === "/api/v1/cocheras/disponibles");
      expect(ahora.length).toBeGreaterThan(previas);
    });
  });
});

describe("ReservasContent: el layout en dos columnas", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // El panel de reservas del admin muestra el alta a la izquierda y el listado
  // a la derecha; el dashboard del visitante los sigue viendo apilados, porque
  // convive con el resto de su panel.
  it("por defecto los apila, como venía", async () => {
    mockData({ vehiculos: [vehiculo()] });
    const { container } = render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    expect(container.querySelector(".reservas-columnas")).toBeNull();
  });

  it("en columnas agrupa el alta y el listado en dos bloques", async () => {
    mockData({ vehiculos: [vehiculo()] });
    const { container } = render(<ReservasContent modo="admin" layout="columnas" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    const grilla = container.querySelector(".reservas-columnas");
    expect(grilla).not.toBeNull();
    // Exactamente dos: si fueran tres, el CSS de dos columnas dejaría uno suelto
    // abajo en vez de la grilla que se espera.
    expect(grilla.children).toHaveLength(2);
  });

  // El orden del DOM es el que manda cuando las columnas se apilan en pantalla
  // angosta, y también el que recorre un lector de pantalla: el alta va primero
  // porque es a lo que se entra.
  it("el alta va antes que el listado en el DOM", async () => {
    mockData({ vehiculos: [vehiculo()] });
    const { container } = render(<ReservasContent modo="admin" layout="columnas" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    const [primero, segundo] = container.querySelector(".reservas-columnas").children;
    expect(primero).toHaveTextContent("Nueva reserva");
    expect(segundo).toHaveTextContent("Todas las reservas");
  });

  // Lo que importa del cambio de layout es que no haya cambiado nada más: el
  // alta tiene que seguir reservando igual.
  it("en columnas se reserva igual que apilado", async () => {
    mockData({
      visitantes: [visitante()],
      vehiculos: [vehiculo()],
      disponibles: [cochera()],
      reservas: [],
    });
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" layout="columnas" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");
    await screen.findByRole("option", { name: /A-01/ });
    await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await user.click(screen.getByRole("button", { name: /confirmar reserva/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith(
      "/api/v1/reservas",
      expect.objectContaining({ vehiculoId: "veh1", cocheraId: "c1" })
    ));
  });

  it("el listado sigue mostrándose en columnas", async () => {
    mockData({
      vehiculos: [vehiculo()],
      reservas: [{
        id: "r1",
        desde: "2026-01-01T10:00",
        hasta: "2026-01-01T12:00",
        estado: "CONFIRMADA",
        modalidad: "FRANJA",
        visitante: { nombre: "Juan Perez" },
        vehiculo: { patente: "ABC123" },
        cochera: { numero: "A-01", sector: "Planta Baja" },
      }],
    });
    render(<ReservasContent modo="admin" layout="columnas" />);

    expect(await screen.findByText("Juan Perez — ABC123")).toBeInTheDocument();
  });
});

describe("ReservasContent: cocheras accesibles", () => {
  const accesible = () => cochera({ id: "c9", numero: "A-09", tipo: "ACCESIBLE" });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function resolverPatenteAdmin() {
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/visitantes"));
    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");
    await screen.findByRole("option", { name: /A-01/ });
    return user;
  }

  // El admin reserva en nombre de otros, así que /disponibles le trae todo:
  // la pantalla filtra según el perfil del dueño del vehículo.
  it("admin: oculta las ACCESIBLE si el dueño no declaró discapacidad y lo explica", async () => {
    mockData({
      visitantes: [visitante({ tieneDiscapacidad: false })],
      vehiculos: [vehiculo()],
      disponibles: [cochera(), accesible()],
    });
    await resolverPatenteAdmin();

    expect(screen.queryByRole("option", { name: /A-09/ })).not.toBeInTheDocument();
    expect(
      screen.getByText(/No se muestran las cocheras accesibles: Juan Perez no tiene declarada discapacidad/)
    ).toBeInTheDocument();
  });

  it("admin: ofrece las ACCESIBLE si el dueño declaró discapacidad", async () => {
    mockData({
      visitantes: [visitante({ tieneDiscapacidad: true })],
      vehiculos: [vehiculo()],
      disponibles: [cochera(), accesible()],
    });
    await resolverPatenteAdmin();

    expect(screen.getByRole("option", { name: /A-09/ })).toBeInTheDocument();
    expect(screen.queryByText(/No se muestran las cocheras accesibles/)).not.toBeInTheDocument();
  });

  // Si el backend no manda el dato, no se esconde nada: el rechazo del backend
  // sigue siendo la última palabra.
  it("admin: si el visitante no trae el campo, no oculta nada", async () => {
    mockData({
      visitantes: [visitante()],
      vehiculos: [vehiculo()],
      disponibles: [cochera(), accesible()],
    });
    await resolverPatenteAdmin();

    expect(screen.getByRole("option", { name: /A-09/ })).toBeInTheDocument();
  });

  it("admin: si solo quedaban ACCESIBLE, avisa que no hay cocheras para esa persona", async () => {
    mockData({
      visitantes: [visitante({ tieneDiscapacidad: false })],
      vehiculos: [vehiculo()],
      disponibles: [accesible()],
    });
    const user = userEvent.setup();
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/visitantes"));
    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");

    expect(await screen.findByText("No hay cocheras libres durante toda esa franja.")).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /A-09/ })).not.toBeInTheDocument();
  });

  // Para el visitante el filtro lo hace el backend según su perfil: lo que
  // venga en /disponibles se muestra tal cual.
  it("visitante: muestra lo que devuelve el backend sin filtrar de nuevo", async () => {
    mockData({ vehiculos: [vehiculo()], disponibles: [cochera(), accesible()] });
    const user = userEvent.setup();
    render(<ReservasContent modo="user" />);

    await screen.findByRole("option", { name: /ABC123 — AUTO/ });
    await user.selectOptions(screen.getByLabelText("Patente"), "ABC123");

    expect(await screen.findByRole("option", { name: /A-09/ })).toBeInTheDocument();
  });

  it("si el backend rechaza la reserva de una ACCESIBLE, muestra su mensaje tal cual", async () => {
    const mensaje =
      "La cochera A-09 es de uso exclusivo para personas con discapacidad, y Juan Perez no lo tiene declarado en su perfil.";
    mockData({ vehiculos: [vehiculo()], disponibles: [accesible()] });
    postMock.mockRejectedValue({ response: { status: 400, data: { message: mensaje } } });
    const user = userEvent.setup();
    render(<ReservasContent modo="user" />);

    await screen.findByRole("option", { name: /ABC123 — AUTO/ });
    await user.selectOptions(screen.getByLabelText("Patente"), "ABC123");
    await screen.findByRole("option", { name: /A-09/ });
    await user.selectOptions(screen.getByLabelText("Cochera"), "c9");
    await user.click(screen.getByRole("button", { name: /confirmar reserva/i }));

    await waitFor(() => expect(toastErrorMock).toHaveBeenCalledWith(mensaje));
  });
});
