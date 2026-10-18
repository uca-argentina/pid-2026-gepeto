import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));

const { default: ProfileSettings } = await import("@/components/ProfileSettings");

const PERFIL = {
  id: "v1",
  nombre: "Juan Perez",
  documento: "30111222",
  email: "juan@test.com",
};

const SIN_SOLICITUD = {
  estado: "SIN_SOLICITUD", puedeUsarCocherasAccesibles: false, accesoProvisorioHasta: null,
  puedeEnviar: true, documentoMaxMb: 5, solicitud: null,
};

function mockPerfil({ perfil = PERFIL, vehiculos = [], verificacion = SIN_SOLICITUD } = {}) {
  getMock.mockImplementation((url) => {
    if (url === "/api/v1/visitantes/me") return Promise.resolve({ data: perfil });
    if (url === "/api/v1/vehiculos") return Promise.resolve({ data: vehiculos });
    if (url === "/api/v1/verificaciones-discapacidad/mia") return Promise.resolve({ data: verificacion });
    return Promise.reject(new Error(`URL no mockeada: ${url}`));
  });
}

describe("ProfileSettings: datos", () => {
  beforeEach(() => { vi.clearAllMocks(); });
  it("precarga telefono y email actuales", async () => {
    mockPerfil({ perfil: { ...PERFIL, telefono: "111", email: "a@a.com" } });
    const user = userEvent.setup();
    render(<ProfileSettings />);
    await screen.findByText("Juan Perez");


    expect(screen.getByDisplayValue("111")).toBeInTheDocument();
    expect(screen.getByDisplayValue("a@a.com")).toBeInTheDocument();
  });

  it("guarda los cambios de telefono/email con PUT /api/v1/visitantes/me", async () => {
    const putMock = vi.fn().mockResolvedValue({
      data: { ...PERFIL, telefono: "222", email: "b@b.com" },
    });
    api.put = putMock;
    mockPerfil({ perfil: { ...PERFIL, telefono: "", email: "b@b.com" } });
    const user = userEvent.setup();
    render(<ProfileSettings />);
    await screen.findByText("Juan Perez");

    await user.type(screen.getByLabelText("Teléfono"), "222");

    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    await waitFor(() =>
      expect(putMock).toHaveBeenCalledWith("/api/v1/visitantes/me", {
        telefono: "222",
        email: "b@b.com",
      })
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
    render(<ProfileSettings />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    expect(await screen.findByText("El email es obligatorio")).toBeInTheDocument();
    expect(putMock).not.toHaveBeenCalled();
  });

});

describe("ProfileSettings: discapacidad", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Antes era un checkbox autodeclarado. Ahora el acceso lo da un admin al
  // verificar el certificado: el perfil no puede cambiarlo.
  it("ya no tiene el checkbox para autodeclararse", async () => {
    mockPerfil({ perfil: { ...PERFIL, tieneDiscapacidad: false } });
    render(<ProfileSettings />);
    await screen.findByText("Juan Perez");

    expect(screen.queryByLabelText("Soy una persona con discapacidad")).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("el PUT del perfil nunca manda tieneDiscapacidad", async () => {
    const putMock = vi.fn().mockResolvedValue({ data: { ...PERFIL, tieneDiscapacidad: true } });
    api.put = putMock;
    mockPerfil({ perfil: { ...PERFIL, tieneDiscapacidad: true } });
    const user = userEvent.setup();
    render(<ProfileSettings />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    await waitFor(() => expect(putMock).toHaveBeenCalled());
    expect(putMock.mock.calls[0][1]).not.toHaveProperty("tieneDiscapacidad");
  });

  it("muestra el bloque de cocheras accesibles con el estado de la verificación", async () => {
    mockPerfil();
    render(<ProfileSettings />);

    expect(await screen.findByRole("heading", { name: "Cocheras accesibles" })).toBeInTheDocument();
    expect(await screen.findByLabelText(/Subí tu certificado/)).toBeInTheDocument();
  });

  // El admin reserva para sí sus series recurrentes: también se verifica.
  it("el administrador también ve el bloque para verificarse", async () => {
    mockPerfil();
    render(<ProfileSettings admin />);

    expect(await screen.findByRole("heading", { name: "Cocheras accesibles" })).toBeInTheDocument();
  });

  it("muestra la insignia en sus datos cuando puede usar cocheras accesibles", async () => {
    mockPerfil({ perfil: { ...PERFIL, tieneDiscapacidad: true } });
    render(<ProfileSettings />);

    expect(await screen.findByText("Puede usar cocheras accesibles")).toBeInTheDocument();
  });

  it("no muestra la insignia cuando no puede", async () => {
    mockPerfil({ perfil: { ...PERFIL, tieneDiscapacidad: false } });
    render(<ProfileSettings />);
    await screen.findByText("Juan Perez");

    expect(screen.queryByText("Puede usar cocheras accesibles")).not.toBeInTheDocument();
  });
});

describe("ProfileSettings: cambiar contraseña", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function abrirFormulario() {
    mockPerfil();
    const user = userEvent.setup();
    render(<ProfileSettings />);
    await screen.findByText("Juan Perez");
    await user.click(screen.getByRole("button", { name: /cambiar contraseña/i }));
    return user;
  }

  it("el formulario esta oculto hasta tocar 'Cambiar contraseña'", async () => {
    mockPerfil();
    render(<ProfileSettings />);
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
