import { describe, it, expect, beforeEach, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock } }));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: VisitantesContent } = await import("@/app/dashboard-admin/VisitantesContent");

const cochera = (overrides = {}) => ({
  id: "c1",
  numero: "A-01",
  sector: "Planta Baja",
  tipo: "AUTO",
  ...overrides,
});

function mockCocheras(disponibles = []) {
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/cocheras/disponibles") return Promise.resolve({ data: disponibles });
    return Promise.reject(new Error(`URL no mockeada: ${url}`));
  });
}

async function completarFormulario(user, overrides = {}) {
  const valores = {
    nombre: "Juan Perez",
    documento: "30111222",
    email: "juan@test.com",
    patente: "ABC123",
    ...overrides,
  };
  if (valores.nombre) await user.type(screen.getByPlaceholderText("Nombre completo"), valores.nombre);
  if (valores.documento) await user.type(screen.getByPlaceholderText("DNI / documento"), valores.documento);
  if (valores.email) await user.type(screen.getByPlaceholderText("Con esto inicia sesión"), valores.email);
  if (valores.patente) await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), valores.patente);
}

describe("VisitantesContent (alta de visitante con reserva)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("consulta disponibilidad y reserva para la franja elegida, limpiando la cochera anterior", async () => {
    mockCocheras([cochera()]);
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<VisitantesContent />);
    await completarFormulario(user);
    await user.selectOptions(screen.getByLabelText("Cochera"), "c1");

    const futura = new Date();
    futura.setDate(futura.getDate() + 7);
    const fecha = futura.toISOString().split("T")[0];
    fireEvent.change(screen.getByLabelText("Fecha"), { target: { value: fecha } });

    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith(
      "/api/v1/cocheras/disponibles", { params: { fecha, tipoVehiculo: "AUTO" } }
    ));
    expect(screen.getByLabelText("Cochera")).toHaveValue("");
    await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));
    await waitFor(() => expect(postMock).toHaveBeenCalledWith(
      "/api/v1/visitantes/alta", expect.objectContaining({ fecha, cocheraId: "c1" })
    ));
  });

  it("ignora respuestas de disponibilidad de una fecha anterior y deshabilita la cochera sin fecha", async () => {
    let resolverAnterior;
    getMock.mockImplementationOnce(() => new Promise((resolve) => { resolverAnterior = resolve; }))
      .mockResolvedValue({ data: [cochera({ id: "c2", numero: "A-02" })] });
    render(<VisitantesContent />);
    fireEvent.change(screen.getByLabelText("Fecha"), { target: { value: "2099-01-01" } });
    await screen.findByRole("option", { name: /A-02/ });
    await act(async () => resolverAnterior({ data: [cochera()] }));
    expect(screen.queryByRole("option", { name: /A-01/ })).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Fecha"), { target: { value: "" } });
    expect(screen.getByLabelText("Cochera")).toBeDisabled();
    expect(screen.queryByRole("option", { name: /A-02/ })).not.toBeInTheDocument();
  });

  it("muestra errores si se envia el formulario vacio", async () => {
    mockCocheras();
    const user = userEvent.setup();
    render(<VisitantesContent />);

    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));

    expect(await screen.findByText("El nombre es obligatorio")).toBeInTheDocument();
    expect(screen.getByText("El documento es obligatorio")).toBeInTheDocument();
    expect(screen.getByText("El email es obligatorio")).toBeInTheDocument();
    expect(screen.getByText("La patente es obligatoria")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  // El email dejo de ser opcional al unificar visitante y cuenta: es el
  // identificador de login, asi que sin el no hay cuenta que crear.
  it("exige el email porque es con lo que el visitante inicia sesion", async () => {
    mockCocheras([cochera()]);
    const user = userEvent.setup();
    render(<VisitantesContent />);

    await completarFormulario(user, { email: "" });
    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));

    expect(await screen.findByText("El email es obligatorio")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("rechaza una patente con formato invalido", async () => {
    mockCocheras();
    const user = userEvent.setup();
    render(<VisitantesContent />);

    await completarFormulario(user, { patente: "123" });
    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));

    expect(await screen.findByText("Formato inválido para auto/carga (ej: ABC123 o AB123CD)")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("rechaza una patente de moto cuando el tipo elegido sigue siendo AUTO", async () => {
    mockCocheras();
    const user = userEvent.setup();
    render(<VisitantesContent />);

    await completarFormulario(user, { patente: "123ABC" });
    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));

    expect(await screen.findByText("Formato inválido para auto/carga (ej: ABC123 o AB123CD)")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("pide las cocheras disponibles de hoy para el tipo de vehiculo elegido", async () => {
    mockCocheras([cochera()]);
    const user = userEvent.setup();
    render(<VisitantesContent />);

    await completarFormulario(user);
    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));

    expect(await screen.findByText("Selecciona una cochera")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  // El punto del cambio: antes esto eran dos POST sueltos (visitante, despues
  // vehiculo) y si el segundo fallaba quedaba un visitante fantasma. Ahora es
  // una sola llamada que el backend resuelve en una transaccion.
  it("manda cuenta, vehiculo y cochera en una sola llamada", async () => {
    mockCocheras([cochera()]);
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<VisitantesContent />);

    await completarFormulario(user);
    await screen.findByRole("option", { name: /A-01/ });
    await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));

    const [url, body] = postMock.mock.calls[0];
    expect(url).toBe("/api/v1/visitantes/alta");
    expect(body).toMatchObject({
      nombre: "Juan Perez",
      documento: "30111222",
      email: "juan@test.com",
      patente: "ABC123",
      tipoVehiculo: "AUTO",
      cocheraId: "c1",
    });
  });

  it("avisa que la contraseña inicial es el documento", async () => {
    mockCocheras([cochera()]);
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<VisitantesContent />);

    await completarFormulario(user);
    await screen.findByRole("option", { name: /A-01/ });
    await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));

    await waitFor(() =>
      expect(toastSuccessMock).toHaveBeenCalledWith(expect.stringContaining("30111222"))
    );
  });

  it("avisa al padre para que refresque ocupacion y reservas", async () => {
    mockCocheras([cochera()]);
    postMock.mockResolvedValue({ data: {} });
    const onAltaCreada = vi.fn();
    const user = userEvent.setup();
    render(<VisitantesContent onAltaCreada={onAltaCreada} />);

    await completarFormulario(user);
    await screen.findByRole("option", { name: /A-01/ });
    await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));

    await waitFor(() => expect(onAltaCreada).toHaveBeenCalled());
  });

  it("si el alta falla (ej. documento duplicado), muestra el error del backend", async () => {
    mockCocheras([cochera()]);
    postMock.mockRejectedValue({
      response: { data: { message: "Ya existe un visitante con ese documento." } },
    });
    const user = userEvent.setup();
    render(<VisitantesContent />);

    await completarFormulario(user);
    await screen.findByRole("option", { name: /A-01/ });
    await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await user.click(screen.getByRole("button", { name: /dar de alta y reservar/i }));

    await waitFor(() =>
      expect(toastErrorMock).toHaveBeenCalledWith("Ya existe un visitante con ese documento.")
    );
    expect(toastSuccessMock).not.toHaveBeenCalled();
  });
});
