import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, putMock, deleteMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  putMock: vi.fn(),
  deleteMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock, put: putMock, delete: deleteMock } }));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: MiPerfilContent } = await import("@/app/dashboard-user/MiPerfilContent");

function mockVehiculos({ vehiculos = [] } = {}) {
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/vehiculos") return Promise.resolve({ data: vehiculos });
    return Promise.reject(new Error(`URL no mockeada: ${url}`));
  });
}

/** Espera a que termine la carga: el campo de patente del alta solo aparece después. */
const campoPatente = () => screen.findByLabelText("Patente");

describe("MiPerfilContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("muestra el estado de carga inicialmente", () => {
    getMock.mockReturnValue(new Promise(() => {}));
    render(<MiPerfilContent />);

    expect(screen.getByText("Cargando tus vehículos...")).toBeInTheDocument();
  });

  it("carga los vehículos sin consultar el perfil personal", async () => {
    mockVehiculos();
    render(<MiPerfilContent />);
    await campoPatente();

    expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos");
    expect(getMock.mock.calls.every(([url]) => url === "/api/v1/vehiculos")).toBe(true);
  });

  // Ahora es una sección dentro de "Mis datos": un único encabezado propio y
  // un ancla para que el acceso directo del panel caiga justo acá.
  it("es una sección 'Mis vehículos' con ancla, sin datos personales ni accesos al perfil", async () => {
    mockVehiculos({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });
    const { container } = render(<MiPerfilContent />);

    const seccion = await screen.findByRole("region", { name: "Mis vehículos" });
    expect(seccion).toHaveAttribute("id", "mis-vehiculos");
    expect(await screen.findByRole("img", { name: "Patente ABC123" })).toBeInTheDocument();
    expect(screen.getAllByRole("heading")).toHaveLength(1);
    expect(screen.queryByText(/documento|email|mi cuenta|mis datos/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(container.querySelector("#mis-vehiculos")).toBe(seccion);
  });

  // El backend filtra los vehiculos por la cuenta autenticada, asi que el
  // frontend ya no tiene que decirle de quien son.
  it("pide sus vehiculos sin mandar visitanteId", async () => {
    mockVehiculos();
    render(<MiPerfilContent />);
    await campoPatente();

    const llamadas = getMock.mock.calls.filter((c) => c[0] === "/api/v1/vehiculos");
    expect(llamadas.length).toBeGreaterThan(0);
    llamadas.forEach(([, config]) => expect(config?.params?.visitanteId).toBeUndefined());
  });

  it("si todavia no tiene ningun vehiculo, avisa que no cargo ninguno", async () => {
    mockVehiculos({ vehiculos: [] });
    render(<MiPerfilContent />);

    expect(await screen.findByText("Todavía no cargaste ningún vehículo.")).toBeInTheDocument();
  });

  it("un error al cargar vehículos muestra un aviso y no habilita el formulario", async () => {
    getMock.mockRejectedValue({ response: { status: 500 } });
    render(<MiPerfilContent />);

    expect(await screen.findByText("No se pudieron cargar tus vehículos.")).toBeInTheDocument();
    expect(toastErrorMock).toHaveBeenCalledWith("No se pudieron cargar tus vehículos.");
    expect(screen.queryByRole("button", { name: /agregar/i })).not.toBeInTheDocument();
  });

  describe("leyenda de formato de la patente", () => {
    it("con AUTO (por defecto) muestra el formato actual y el anterior de auto", async () => {
      mockVehiculos();
      render(<MiPerfilContent />);
      const patente = await campoPatente();

      expect(patente).toHaveAttribute("placeholder", "AB 123 CD / ABC 123");
      expect(patente).toHaveAccessibleDescription("Formato actual: AB 123 CD · anterior: ABC 123");
    });

    it("al elegir MOTO cambia al formato actual y anterior de moto", async () => {
      mockVehiculos();
      const user = userEvent.setup();
      render(<MiPerfilContent />);
      const patente = await campoPatente();

      await user.selectOptions(screen.getByLabelText("Tipo de vehículo"), "MOTO");

      expect(patente).toHaveAttribute("placeholder", "A 123 BCD / 123 ABC");
      expect(patente).toHaveAccessibleDescription("Formato actual: A 123 BCD · anterior: 123 ABC");
    });

    it.each(["AUTO", "CARGA"])("al volver a %s desde MOTO usa otra vez el formato de auto", async (tipo) => {
      mockVehiculos();
      const user = userEvent.setup();
      render(<MiPerfilContent />);
      const patente = await campoPatente();

      await user.selectOptions(screen.getByLabelText("Tipo de vehículo"), "MOTO");
      await user.selectOptions(screen.getByLabelText("Tipo de vehículo"), tipo);

      expect(patente).toHaveAttribute("placeholder", "AB 123 CD / ABC 123");
    });

    it("la edicion tambien sigue al tipo elegido", async () => {
      mockVehiculos({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });
      const user = userEvent.setup();
      render(<MiPerfilContent />);
      const fila = (await screen.findByRole("img", { name: "Patente ABC123" })).closest("li");

      await user.click(within(fila).getByRole("button", { name: /^editar$/i }));
      await user.selectOptions(within(fila).getByLabelText("Tipo de vehículo"), "MOTO");

      expect(within(fila).getByLabelText("Patente")).toHaveAttribute("placeholder", "A 123 BCD / 123 ABC");
    });
  });

  it("agregar un vehiculo con patente invalida muestra el error de formato coherente con la leyenda", async () => {
    mockVehiculos({ vehiculos: [] });
    const user = userEvent.setup();
    render(<MiPerfilContent />);

    await user.type(await campoPatente(), "123");
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Formato inválido para auto/carga. Usá AB 123 CD (actual) o ABC 123 (anterior)"
    );
    expect(postMock).not.toHaveBeenCalled();
  });

  it("agregar un vehiculo con patente de auto para un tipo MOTO muestra el error especifico de moto", async () => {
    mockVehiculos({ vehiculos: [] });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    const patente = await campoPatente();

    await user.selectOptions(screen.getByLabelText("Tipo de vehículo"), "MOTO");
    await user.type(patente, "ABC123");
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Formato inválido para moto. Usá A 123 BCD (actual) o 123 ABC (anterior)"
    );
    expect(patente).toBeInvalid();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("agrega un vehiculo propio sin mandar visitanteId y refresca la lista", async () => {
    mockVehiculos({ vehiculos: [] });
    postMock.mockResolvedValue({ data: {} });
    const onVehiculosCambiaron = vi.fn();
    const user = userEvent.setup();
    render(<MiPerfilContent onVehiculosCambiaron={onVehiculosCambiaron} />);

    await user.type(await campoPatente(), "ABC123");
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    await waitFor(() => expect(onVehiculosCambiaron).toHaveBeenCalled());
    expect(postMock).toHaveBeenCalledWith("/api/v1/vehiculos", { patente: "ABC123", tipo: "AUTO" });
  });

  // La leyenda muestra la patente separada en bloques: si se escribe así, se
  // manda compacta y en mayúsculas, que es lo que acepta el backend.
  it.each([
    ["ab 123 cd", "AUTO", "AB123CD"],
    ["abc-123", "AUTO", "ABC123"],
    ["a 123 bcd", "MOTO", "A123BCD"],
    ["123 abc", "MOTO", "123ABC"],
  ])("escribir '%s' (%s) envia la patente normalizada %s", async (escrita, tipo, enviada) => {
    mockVehiculos({ vehiculos: [] });
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    const patente = await campoPatente();

    await user.selectOptions(screen.getByLabelText("Tipo de vehículo"), tipo);
    await user.type(patente, escrita);
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith("/api/v1/vehiculos", { patente: enviada, tipo })
    );
  });

  it("muestra cada vehiculo como una patente grafica con su tipo", async () => {
    mockVehiculos({
      vehiculos: [
        { id: "veh1", patente: "AB123CD", tipo: "AUTO" },
        { id: "veh2", patente: "123ABC", tipo: "MOTO" },
      ],
    });
    render(<MiPerfilContent />);

    const auto = await screen.findByRole("img", { name: "Patente AB123CD" });
    const moto = screen.getByRole("img", { name: "Patente 123ABC" });
    expect(auto).toHaveAttribute("data-formato", "auto-mercosur");
    expect(moto).toHaveAttribute("data-formato", "moto-anterior");
    expect(within(auto.closest("li")).getByText("Auto")).toBeInTheDocument();
    expect(within(moto.closest("li")).getByText("Moto")).toBeInTheDocument();
  });

  it("edita un vehiculo existente con PUT /api/v1/vehiculos/{id}", async () => {
    putMock.mockResolvedValue({ data: {} });
    mockVehiculos({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });

    const user = userEvent.setup();
    render(<MiPerfilContent />);
    const fila = (await screen.findByRole("img", { name: "Patente ABC123" })).closest("li");

    await user.click(within(fila).getByRole("button", { name: /^editar$/i }));
    await user.click(within(fila).getByRole("button", { name: /^guardar$/i }));

    await waitFor(() =>
      expect(putMock).toHaveBeenCalledWith("/api/v1/vehiculos/veh1", { patente: "ABC123", tipo: "AUTO" })
    );
  });

  it("elimina un vehiculo con confirmacion", async () => {
    deleteMock.mockResolvedValue({});
    vi.spyOn(window, "confirm").mockReturnValue(true);
    mockVehiculos({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    const fila = (await screen.findByRole("img", { name: "Patente ABC123" })).closest("li");

    await user.click(within(fila).getByRole("button", { name: /eliminar/i }));

    expect(window.confirm).toHaveBeenCalledWith("¿Seguro que querés eliminar el vehículo ABC123?");
    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith("/api/v1/vehiculos/veh1"));
  });
});