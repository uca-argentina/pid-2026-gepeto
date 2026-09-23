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

const PERFIL = {
  id: "v1",
  nombre: "Juan Perez",
  documento: "30111222",
  email: "juan@test.com",
};

function mockPerfil({ perfil = PERFIL, vehiculos = [] } = {}) {
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/visitantes/me") return Promise.resolve({ data: perfil });
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

    expect(screen.getByText("Cargando tus datos...")).toBeInTheDocument();
  });

  // Al unificar visitante y cuenta, el perfil ya no se "carga una sola vez":
  // viene con el alta. Si existe la cuenta, existen el nombre y el documento.
  it("no ofrece ningun formulario de autoregistro: el perfil viene con la cuenta", async () => {
    mockPerfil();
    render(<MiPerfilContent />);
    await screen.findByText("Juan Perez");

    expect(screen.queryByRole("button", { name: /guardar mis datos/i })).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("DNI / documento")).not.toBeInTheDocument();
  });

  it("muestra sus datos y sus vehiculos", async () => {
    mockPerfil({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });
    render(<MiPerfilContent />);

    expect(await screen.findByText("Juan Perez")).toBeInTheDocument();
    expect(screen.getByText(/Documento 30111222/)).toBeInTheDocument();
    expect(screen.getByText("ABC123")).toBeInTheDocument();
  });

  // El backend filtra los vehiculos por la cuenta autenticada, asi que el
  // frontend ya no tiene que decirle de quien son.
  it("pide sus vehiculos sin mandar visitanteId", async () => {
    mockPerfil();
    render(<MiPerfilContent />);
    await screen.findByText("Juan Perez");

    const llamadas = getMock.mock.calls.filter((c) => c[0] === "/api/v1/vehiculos");
    expect(llamadas.length).toBeGreaterThan(0);
    llamadas.forEach(([, config]) => expect(config?.params?.visitanteId).toBeUndefined());
  });

  it("si todavia no tiene ningun vehiculo, avisa que no cargo ninguno", async () => {
    mockPerfil({ vehiculos: [] });
    render(<MiPerfilContent />);

    expect(await screen.findByText("Todavía no cargaste ningún vehículo.")).toBeInTheDocument();
  });

  it("un error al cargar el perfil muestra un toast de error", async () => {
    getMock.mockRejectedValue({ response: { status: 500 } });
    render(<MiPerfilContent />);

    await waitFor(() => expect(toastErrorMock).toHaveBeenCalledWith("No se pudieron cargar tus datos."));
  });

  it("agregar un vehiculo con patente invalida muestra el error de formato", async () => {
    mockPerfil({ vehiculos: [] });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByText("Juan Perez");

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "123");
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    expect(await screen.findByText("Formato inválido para auto/carga (ej: ABC123 o AB123CD)")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("agregar un vehiculo con patente de auto para un tipo MOTO muestra el error especifico de moto", async () => {
    mockPerfil({ vehiculos: [] });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByText("Juan Perez");

    await user.selectOptions(screen.getByRole("combobox"), "MOTO");
    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    expect(await screen.findByText("Formato inválido para moto (ej: 123ABC o A123BCD)")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("agrega un vehiculo propio sin mandar visitanteId y refresca la lista", async () => {
    mockPerfil({ vehiculos: [] });
    postMock.mockResolvedValue({ data: {} });
    const onVehiculosCambiaron = vi.fn();
    const user = userEvent.setup();
    render(<MiPerfilContent onVehiculosCambiaron={onVehiculosCambiaron} />);
    await screen.findByText("Juan Perez");

    await user.type(screen.getByPlaceholderText("ABC123 / AB123CD"), "ABC123");
    await user.click(screen.getByRole("button", { name: /agregar/i }));

    await waitFor(() => expect(onVehiculosCambiaron).toHaveBeenCalled());
  });

  it("el boton 'Editar mis datos' precarga telefono y email actuales", async () => {
    mockPerfil({ perfil: { ...PERFIL, telefono: "111", email: "a@a.com" } });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /editar mis datos/i }));

    expect(screen.getByDisplayValue("111")).toBeInTheDocument();
    expect(screen.getByDisplayValue("a@a.com")).toBeInTheDocument();
  });

  it("guarda los cambios de telefono/email con PUT /api/v1/visitantes/me", async () => {
    const putMock = vi.fn().mockResolvedValue({
      data: { ...PERFIL, telefono: "222", email: "b@b.com" },
    });
    api.put = putMock;
    mockPerfil({ perfil: { ...PERFIL, telefono: "", email: "" } });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /editar mis datos/i }));
    await user.type(screen.getByLabelText("Teléfono"), "222");
    await user.type(screen.getByLabelText("Email"), "b@b.com");
    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    await waitFor(() =>
      expect(putMock).toHaveBeenCalledWith("/api/v1/visitantes/me", { telefono: "222", email: "b@b.com" })
    );
    expect(toastSuccessMock).toHaveBeenCalledWith("Tus datos se actualizaron correctamente");
  });

  // El email dejo de ser un dato de contacto opcional: es el identificador de
  // login, asi que vaciarlo dejaria a la cuenta sin forma de entrar.
  it("no deja vaciar el email, porque es con lo que se inicia sesion", async () => {
    const putMock = vi.fn();
    api.put = putMock;
    mockPerfil({ perfil: { ...PERFIL, email: "" } });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /editar mis datos/i }));
    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    expect(await screen.findByText("El email es obligatorio")).toBeInTheDocument();
    expect(putMock).not.toHaveBeenCalled();
  });

  it("edita un vehiculo existente con PUT /api/v1/vehiculos/{id}", async () => {
    const putMock = vi.fn().mockResolvedValue({ data: {} });
    api.put = putMock;
    mockPerfil({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });

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
    mockPerfil({ vehiculos: [{ id: "veh1", patente: "ABC123", tipo: "AUTO" }] });
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByText("ABC123");

    await user.click(screen.getByRole("button", { name: /eliminar/i }));

    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith("/api/v1/vehiculos/veh1"));
  });
});

describe("MiPerfilContent: cambiar contraseña", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function abrirFormulario() {
    mockPerfil();
    const user = userEvent.setup();
    render(<MiPerfilContent />);
    await screen.findByText("Juan Perez");
    await user.click(screen.getByRole("button", { name: /cambiar contraseña/i }));
    return user;
  }

  it("el formulario esta oculto hasta tocar 'Cambiar contraseña'", async () => {
    mockPerfil();
    render(<MiPerfilContent />);
    await screen.findByText("Juan Perez");

    expect(screen.queryByLabelText("Contraseña actual")).not.toBeInTheDocument();
  });

  it("manda la actual y la nueva a PUT /api/v1/visitantes/me/password", async () => {
    const putMock = vi.fn().mockResolvedValue({ data: {} });
    api.put = putMock;
    const user = await abrirFormulario();

    await user.type(screen.getByLabelText("Contraseña actual"), "30111222");
    await user.type(screen.getByLabelText("Contraseña nueva"), "claveNueva1");
    await user.type(screen.getByLabelText("Repetir contraseña nueva"), "claveNueva1");
    await user.click(screen.getByRole("button", { name: /guardar contraseña/i }));

    await waitFor(() =>
      expect(putMock).toHaveBeenCalledWith("/api/v1/visitantes/me/password", {
        passwordActual: "30111222",
        passwordNueva: "claveNueva1",
      })
    );
    expect(toastSuccessMock).toHaveBeenCalledWith("Tu contraseña se cambió correctamente");
  });

  // Pedir la actual es lo que evita que alguien con la sesión abierta deje al
  // dueño afuera de su cuenta.
  it("exige la contraseña actual", async () => {
    const putMock = vi.fn();
    api.put = putMock;
    const user = await abrirFormulario();

    await user.type(screen.getByLabelText("Contraseña nueva"), "claveNueva1");
    await user.type(screen.getByLabelText("Repetir contraseña nueva"), "claveNueva1");
    await user.click(screen.getByRole("button", { name: /guardar contraseña/i }));

    expect(await screen.findByText("Ingresá tu contraseña actual")).toBeInTheDocument();
    expect(putMock).not.toHaveBeenCalled();
  });

  it("rechaza una contraseña nueva de menos de 8 caracteres", async () => {
    const putMock = vi.fn();
    api.put = putMock;
    const user = await abrirFormulario();

    await user.type(screen.getByLabelText("Contraseña actual"), "30111222");
    await user.type(screen.getByLabelText("Contraseña nueva"), "corta");
    await user.type(screen.getByLabelText("Repetir contraseña nueva"), "corta");
    await user.click(screen.getByRole("button", { name: /guardar contraseña/i }));

    expect(
      await screen.findByText("La contraseña nueva debe tener al menos 8 caracteres")
    ).toBeInTheDocument();
    expect(putMock).not.toHaveBeenCalled();
  });

  it("avisa si la repeticion no coincide", async () => {
    const putMock = vi.fn();
    api.put = putMock;
    const user = await abrirFormulario();

    await user.type(screen.getByLabelText("Contraseña actual"), "30111222");
    await user.type(screen.getByLabelText("Contraseña nueva"), "claveNueva1");
    await user.type(screen.getByLabelText("Repetir contraseña nueva"), "otraDistinta1");
    await user.click(screen.getByRole("button", { name: /guardar contraseña/i }));

    expect(await screen.findByText("Las contraseñas no coinciden")).toBeInTheDocument();
    expect(putMock).not.toHaveBeenCalled();
  });

  it("si el backend rechaza el cambio, muestra su mensaje", async () => {
    api.put = vi.fn().mockRejectedValue({
      response: { data: { message: "La contraseña actual no es correcta." } },
    });
    const user = await abrirFormulario();

    await user.type(screen.getByLabelText("Contraseña actual"), "equivocada");
    await user.type(screen.getByLabelText("Contraseña nueva"), "claveNueva1");
    await user.type(screen.getByLabelText("Repetir contraseña nueva"), "claveNueva1");
    await user.click(screen.getByRole("button", { name: /guardar contraseña/i }));

    await waitFor(() =>
      expect(toastErrorMock).toHaveBeenCalledWith("La contraseña actual no es correcta.")
    );
  });
});
