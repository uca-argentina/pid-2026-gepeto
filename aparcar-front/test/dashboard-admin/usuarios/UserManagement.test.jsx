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

vi.mock("@/app/api", () => ({
  default: { get: getMock, post: postMock, put: putMock, delete: deleteMock },
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: UserManagement } = await import("@/app/dashboard-admin/usuarios/UserManagement");

const usuario = (overrides = {}) => ({
  id: "1",
  nombre: "Juan Perez",
  documento: "30111222",
  email: "juan@aparcar.com",
  telefono: "",
  authorities: ["USER"],
  isActive: true,
  ...overrides,
});

describe("UserManagement", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getMock.mockResolvedValue({ data: [] });
  });

  it("muestra la navegación de regreso al panel", () => {
    render(<UserManagement />);

    expect(screen.getByRole("link", { name: "← Volver al panel" }))
      .toHaveAttribute("href", "/dashboard-admin");
  });

  it("carga y lista los usuarios existentes", async () => {
    getMock.mockResolvedValue({ data: [usuario()] });
    render(<UserManagement />);

    expect(await screen.findByText("Juan Perez")).toBeInTheDocument();
    expect(screen.getByText("juan@aparcar.com")).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Estado" })).not.toBeInTheDocument();
    expect(screen.queryByText("Activo")).not.toBeInTheDocument();
  });

  it("conserva el boton Activar para usuarios inactivos sin mostrar la columna Estado", async () => {
    getMock.mockResolvedValue({ data: [usuario({ isActive: false })] });
    render(<UserManagement />);

    await screen.findByText("Juan Perez");
    expect(screen.queryByText("Inactivo")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /activar/i })).toBeInTheDocument();
  });

  it("un usuario activo no muestra el boton Activar", async () => {
    getMock.mockResolvedValue({ data: [usuario({ isActive: true })] });
    render(<UserManagement />);

    await screen.findByText("Juan Perez");
    expect(screen.queryByRole("button", { name: /^activar$/i })).not.toBeInTheDocument();
  });

  it("activar un usuario llama a POST /users/activate con su email", async () => {
    getMock.mockResolvedValue({ data: [usuario({ isActive: false })] });
    postMock.mockResolvedValue({});
    const user = userEvent.setup();
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /activar/i }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith("/users/activate", { email: "juan@aparcar.com" })
    );
    expect(toastSuccessMock).toHaveBeenCalledWith("Usuario activado correctamente");
  });

  it("muestra errores de validacion al crear un usuario con datos invalidos", async () => {
    const user = userEvent.setup();
    render(<UserManagement />);

    await user.type(screen.getByPlaceholderText("Mínimo 8 caracteres"), "123");
    await user.click(screen.getByRole("button", { name: /crear usuario/i }));

    expect(await screen.findByText("El nombre es obligatorio")).toBeInTheDocument();
    expect(screen.getByText("Ingresá un correo válido")).toBeInTheDocument();
    expect(screen.getByText("La contraseña debe tener al menos 8 caracteres")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("crea un usuario con datos validos", async () => {
    postMock.mockResolvedValue({ data: usuario() });
    const user = userEvent.setup();
    render(<UserManagement />);

    await user.type(screen.getByPlaceholderText("Nombre completo"), "Juan Perez");
    await user.type(screen.getByPlaceholderText("DNI / documento"), "30111222");
    await user.type(screen.getByPlaceholderText("usuario@aparcar.com"), "juan@aparcar.com");
    await user.type(screen.getByPlaceholderText("Mínimo 8 caracteres"), "password123");
    await user.click(screen.getByRole("button", { name: /crear usuario/i }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(
        "/register",
        expect.objectContaining({
          nombre: "Juan Perez",
          documento: "30111222",
          email: "juan@aparcar.com",
          password: "password123",
        })
      )
    );
    expect(toastSuccessMock).toHaveBeenCalledWith("Usuario creado correctamente");
  });

  // El documento identifica al visitante, y ahora toda cuenta es un visitante:
  // por eso el alta administrativa tambien lo exige.
  it("no deja crear una cuenta sin documento", async () => {
    const user = userEvent.setup();
    render(<UserManagement />);

    await user.type(screen.getByPlaceholderText("Nombre completo"), "Juan Perez");
    await user.type(screen.getByPlaceholderText("usuario@aparcar.com"), "juan@aparcar.com");
    await user.type(screen.getByPlaceholderText("Mínimo 8 caracteres"), "password123");
    await user.click(screen.getByRole("button", { name: /crear usuario/i }));

    expect(await screen.findByText("El documento es obligatorio")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("al editar, precarga nombre, telefono y los roles marcados", async () => {
    getMock.mockResolvedValue({ data: [usuario({ telefono: "12345678", authorities: ["USER", "ADMIN"] })] });
    const user = userEvent.setup();
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /^editar$/i }));

    const editForm = screen.getByRole("button", { name: /guardar cambios/i }).closest("form");
    expect(within(editForm).getByDisplayValue("Juan Perez")).toBeInTheDocument();
    expect(within(editForm).getByDisplayValue("12345678")).toBeInTheDocument();
    expect(within(editForm).getByRole("checkbox", { name: "USER" })).toBeChecked();
    expect(within(editForm).getByRole("checkbox", { name: "ADMIN" })).toBeChecked();
  });

  // El administrador designa el rol de empleado desde esta misma pantalla: es
  // la única forma de crear uno, porque nadie puede asignárselo a sí mismo.
  it("ofrece EMPLEADO entre los roles asignables", async () => {
    getMock.mockResolvedValue({ data: [usuario({ authorities: ["USER"] })] });
    const user = userEvent.setup();
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /^editar$/i }));

    const editForm = screen.getByRole("button", { name: /guardar cambios/i }).closest("form");
    expect(within(editForm).getByRole("checkbox", { name: "EMPLEADO" })).toBeInTheDocument();
  });

  it("guarda el rol EMPLEADO cuando el administrador lo marca", async () => {
    getMock.mockResolvedValue({ data: [usuario({ authorities: ["USER"] })] });
    putMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /^editar$/i }));
    const editForm = screen.getByRole("button", { name: /guardar cambios/i }).closest("form");
    await user.click(within(editForm).getByRole("checkbox", { name: "EMPLEADO" }));
    await user.click(within(editForm).getByRole("button", { name: /guardar cambios/i }));

    await waitFor(() => expect(putMock).toHaveBeenCalled());
    const enviado = putMock.mock.calls.at(-1)[1];
    expect(enviado.authorities).toContain("EMPLEADO");
  });

  it("editar sin ningun rol marcado muestra el error de validacion", async () => {
    getMock.mockResolvedValue({ data: [usuario({ authorities: ["USER"] })] });
    const user = userEvent.setup();
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /^editar$/i }));
    const editForm = screen.getByRole("button", { name: /guardar cambios/i }).closest("form");
    await user.click(within(editForm).getByRole("checkbox", { name: "USER" }));
    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    expect(await screen.findByText("El usuario debe tener al menos un rol")).toBeInTheDocument();
    expect(putMock).not.toHaveBeenCalled();
  });

  it("guarda los cambios de edicion con PUT /api/v1/usuarios/{id}", async () => {
    getMock.mockResolvedValue({ data: [usuario()] });
    putMock.mockResolvedValue({ data: usuario() });
    const user = userEvent.setup();
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /^editar$/i }));
    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    await waitFor(() =>
      expect(putMock).toHaveBeenCalledWith(
        "/api/v1/usuarios/1",
        expect.objectContaining({ nombre: "Juan Perez", documento: "30111222", authorities: ["USER"] })
      )
    );
  });

  it("eliminar pide confirmacion y llama a DELETE /users con el email", async () => {
    getMock.mockResolvedValue({ data: [usuario()] });
    deleteMock.mockResolvedValue({});
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /eliminar/i }));

    expect(window.confirm).toHaveBeenCalledWith("¿Seguro que querés eliminar a Juan Perez?");
    await waitFor(() =>
      expect(deleteMock).toHaveBeenCalledWith("/users", { data: { email: "juan@aparcar.com" } })
    );
  });

  it("eliminar cancelado no llama a DELETE", async () => {
    getMock.mockResolvedValue({ data: [usuario()] });
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /eliminar/i }));

    expect(deleteMock).not.toHaveBeenCalled();
  });
});

/**
 * La columna de faltas del panel de cuentas.
 *
 * Es donde el administrador ve de un vistazo quién está cerca del límite y
 * quién ya quedó bloqueado. Lo que se prueba es que el botón de restituir solo
 * exista cuando hay algo que levantar: ofrecerlo siempre invita a apretarlo
 * sobre cuentas que no están bloqueadas.
 */
describe("faltas en el panel de cuentas", () => {
  it("dibuja los puntitos de cada cuenta", async () => {
    getMock.mockResolvedValue({
      data: [usuario({ faltasVigentes: 2, maxFaltas: 3 })],
    });
    const { container } = render(<UserManagement />);
    await screen.findByText("Juan Perez");

    expect(container.querySelectorAll(".falta-punto")).toHaveLength(3);
    expect(container.querySelectorAll(".falta-punto-usado")).toHaveLength(2);
  });

  it("sin bloqueo no ofrece restituir", async () => {
    getMock.mockResolvedValue({
      data: [usuario({ faltasVigentes: 1, maxFaltas: 3, bloqueadoHasta: null })],
    });
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    expect(screen.queryByRole("button", { name: /restituir/i })).not.toBeInTheDocument();
  });

  it("con la cuenta bloqueada muestra hasta cuándo y ofrece restituir", async () => {
    getMock.mockResolvedValue({
      data: [usuario({ faltasVigentes: 4, maxFaltas: 3, bloqueadoHasta: "2026-12-01T10:00:00" })],
    });
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    expect(screen.getByText(/bloqueada hasta/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /restituir/i })).toBeInTheDocument();
  });

  it("restituir llama al endpoint de esa cuenta y recarga el listado", async () => {
    getMock.mockResolvedValue({
      data: [usuario({ id: "u-7", faltasVigentes: 4, maxFaltas: 3, bloqueadoHasta: "2026-12-01T10:00:00" })],
    });
    postMock.mockResolvedValue({ data: {} });
    const user = userEvent.setup();
    render(<UserManagement />);
    await screen.findByText("Juan Perez");

    await user.click(screen.getByRole("button", { name: /restituir/i }));

    await waitFor(() => expect(postMock)
      .toHaveBeenCalledWith("/api/v1/amonestaciones/visitante/u-7/restituir"));
    expect(toastSuccessMock).toHaveBeenCalled();
  });
});
