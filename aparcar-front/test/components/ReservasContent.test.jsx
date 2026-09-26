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

function mockData({ visitantes = [], vehiculos = [], reservas = [], disponibles = [] }) {
  getMock.mockImplementation((url) => {
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

    expect(
      await screen.findByText(/Cargá al menos un vehículo en "Mis datos"/)
    ).toBeInTheDocument();
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

  it("un horario fuera de bloque se baja al bloque en curso", async () => {
    mockData({ vehiculos: [vehiculo()] });
    render(<ReservasContent modo="admin" />);
    await waitFor(() => expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos"));

    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2099-01-01T18:23" } });

    expect(screen.getByLabelText("Hasta")).toHaveValue("2099-01-01T18:15");
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
      expect(getMock).toHaveBeenLastCalledWith(
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
    expect(await screen.findByText(/01\/01 de 10:00 a 12:00/)).toBeInTheDocument();
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

    expect(await screen.findByText(/01\/01 22:00 → 03\/01 08:00/)).toBeInTheDocument();
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
