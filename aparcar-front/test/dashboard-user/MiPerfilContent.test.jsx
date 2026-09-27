import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import api from "@/app/api";

const { getMock, postMock, putMock, deleteMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  putMock: vi.fn(),
  deleteMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock, put: putMock, delete: deleteMock} }));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: MiPerfilContent } = await import("@/app/dashboard-user/MiPerfilContent");

function mockVehiculos({ vehiculos = [] } = {}) {
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/vehiculos") return Promise.resolve({ data: vehiculos });
    return Promise.reject(new Error(`URL no mockeada: ${url}`));
  });
}

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
    await screen.findByRole("heading", { name: "Mis vehículos" });

    expect(getMock).toHaveBeenCalledWith("/api/v1/vehiculos");
    expect(getMock.mock.calls.every(([url]) => url === "/api/v1/vehiculos")).toBe(true);
  });

  it("muestra solo los vehículos, sin datos personales ni acceso duplicado al perfil", async () => {
    mockVehiculos({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });
    render(<MiPerfilContent />);

    expect(await screen.findByText("ABC123")).toBeInTheDocument();
    expect(screen.getAllByRole("heading")).toHaveLength(1);
    expect(screen.getByRole("heading", { name: "Mis vehículos" })).toBeInTheDocument();
    expect(screen.queryByText(/documento|email|mi cuenta|mis datos/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  // El backend filtra los vehiculos por la cuenta autenticada, asi que el
  // frontend ya no tiene que decirle de quien son.
  it("pide sus vehiculos sin mandar visitanteId", async () => {
    mockVehiculos();
    render(<MiPerfilContent />);
    await screen.findByRole("heading", { name: "Mis vehículos" });

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

  it("agregar un vehiculo con patente invalida muestra el error de formato", async () => {
    mockVehiculos({ vehiculos: [] });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByRole("heading", { name: "Mis vehículos" });

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "123");
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    expect(await screen.findByText("Formato inválido para auto/carga (ej: ABC123 o AB123CD)")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("agregar un vehiculo con patente de auto para un tipo MOTO muestra el error especifico de moto", async () => {
    mockVehiculos({ vehiculos: [] });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByRole("heading", { name: "Mis vehículos" });

    await user.selectOptions(screen.getByRole("combobox"), "MOTO");
    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    expect(await screen.findByText("Formato inválido para moto (ej: 123ABC o A123BCD)")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("agrega un vehiculo propio sin mandar visitanteId y refresca la lista", async () => {
    mockVehiculos({ vehiculos: [] });
    postMock.mockResolvedValue({ data: {} });
    const onVehiculosCambiaron = vi.fn();
    const user = userEvent.setup();
    render(<MiPerfilContent onVehiculosCambiaron={onVehiculosCambiaron} />);
    await screen.findByRole("heading", { name: "Mis vehículos" });

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    await waitFor(() => expect(onVehiculosCambiaron).toHaveBeenCalled());
  });

  it("edita un vehiculo existente con PUT /api/v1/vehiculos/{id}", async () => {
    const putMock = vi.fn().mockResolvedValue({ data: {} });
    api.put = putMock;
    mockVehiculos({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });

    const user = userEvent.setup();
    render(<MiPerfilContent />);
    const patente = await screen.findByText("ABC123");
    const fila = patente.closest("li");

    await user.click(within(fila).getByRole("button", { name: /^editar$/i }));
    await user.click(within(fila).getByRole("button", { name: /^guardar$/i }));

    await waitFor(() =>
      expect(putMock).toHaveBeenCalledWith("/api/v1/vehiculos/veh1", { patente: "ABC123", tipo: "AUTO" })
    );
  });

  it("elimina un vehiculo con confirmacion", async () => {
    const deleteMock = vi.fn().mockResolvedValue({});
    api.delete = deleteMock;
    vi.spyOn(window, "confirm").mockReturnValue(true);
    mockVehiculos({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByText("ABC123");

    await user.click(screen.getByRole("button", { name: /eliminar/i }));

    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith("/api/v1/vehiculos/veh1"));
  });
});
