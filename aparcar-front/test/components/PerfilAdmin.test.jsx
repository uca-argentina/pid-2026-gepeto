import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ProfileSettings from "@/components/ProfileSettings";
import ParkingHeading from "@/components/ParkingHeading";
import { useAuthStore } from "@/store/authStore";

const { get, put, replace, error, success } = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), replace: vi.fn(), error: vi.fn(), success: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get, put } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("sonner", () => ({ toast: { error, success } }));
const perfil = { nombre: "Ana Pérez", documento: "30111222", email: "admin@test.com", telefono: "111", tieneDiscapacidad: false, nombreEstacionamiento: "Parking Larrea" };
beforeEach(() => {
  vi.resetAllMocks();
  get.mockResolvedValue({ data: perfil });
  put.mockImplementation((url, data) => Promise.resolve({ data: { ...perfil, ...data } }));
});

describe("Perfil del administrador", () => {
  it("guarda documento, teléfono y nombre normalizados sin consultar vehículos", async () => {
    const user = userEvent.setup();
    render(<ProfileSettings admin />);
    const documento = await screen.findByLabelText("Documento");
    await user.clear(documento);
    await user.type(documento, " 30444555 ");
    await user.clear(screen.getByLabelText("Nombre del estacionamiento"));
    await user.type(screen.getByLabelText("Nombre del estacionamiento"), " Parking Centro ");
    await user.clear(screen.getByLabelText("Teléfono"));
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/visitantes/me", {
      email: perfil.email, documento: "30444555", telefono: "", nombreEstacionamiento: "Parking Centro", tieneDiscapacidad: false,
    }));
    expect(get.mock.calls.every(([url]) => url === "/api/v1/visitantes/me")).toBe(true);
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("un error conserva la edición y permite descartar los cambios", async () => {
    put.mockRejectedValue({ response: { data: { message: "Ya existe un visitante con ese documento." } } });
    const user = userEvent.setup();
    render(<ProfileSettings admin />);
    const documento = await screen.findByLabelText("Documento");
    await user.clear(documento);
    await user.type(documento, "999");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
    await waitFor(() => expect(error).toHaveBeenCalledWith("Ya existe un visitante con ese documento."));
    expect(documento).toHaveValue("999");
    await user.click(screen.getByRole("button", { name: "Descartar cambios" }));
    expect(documento).toHaveValue(perfil.documento);
  });

  it("bloquea los controles durante el guardado y evita envíos dobles", async () => {
    let resolve;
    put.mockReturnValue(new Promise((r) => { resolve = r; }));
    const user = userEvent.setup();
    render(<ProfileSettings admin />);
    await screen.findByLabelText("Documento");
    await user.dblClick(screen.getByRole("button", { name: "Guardar cambios" }));
    expect(put).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("Documento")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Descartar cambios" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Cambiar contraseña" })).toBeDisabled();
    resolve({ data: perfil });
    await waitFor(() => expect(screen.getByLabelText("Documento")).toBeEnabled());
  });

  it("al cambiar el email termina la sesión e invita a entrar con el nuevo", async () => {
    document.cookie = "JWT=anterior; path=/";
    useAuthStore.setState({ isAuthenticated: true, user: { roles: ["ADMIN"] } });
    const user = userEvent.setup();
    render(<ProfileSettings admin />);
    const email = await screen.findByLabelText("Email");
    await user.clear(email);
    await user.type(email, "nuevo@test.com");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
    expect(document.cookie).not.toContain("JWT=");
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(success).toHaveBeenCalledWith("Tus datos se guardaron. Iniciá sesión con tu nuevo email.");
  });

  it("reintenta una carga fallida sin dejar un formulario vacío", async () => {
    get.mockRejectedValueOnce(new Error("red"));
    const user = userEvent.setup();
    render(<ProfileSettings admin />);
    await user.click(await screen.findByRole("button", { name: "Reintentar" }));
    expect(await screen.findByLabelText("Nombre del estacionamiento")).toHaveValue("Parking Larrea");
  });

  it("el usuario conserva sus permisos y no envía campos exclusivos del admin", async () => {
    const user = userEvent.setup();
    render(<ProfileSettings />);
    await screen.findByLabelText("Email");
    expect(screen.queryByLabelText("Documento")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Nombre del estacionamiento")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/visitantes/me", {
      email: perfil.email, telefono: "111", tieneDiscapacidad: false,
    }));
  });
});

describe("Título del estacionamiento", () => {
  it("muestra únicamente el nombre persistido como título", async () => {
    const { container } = render(<ParkingHeading />);
    expect(await screen.findByRole("heading", { name: "Parking Larrea" })).toBeInTheDocument();
    expect(container.textContent).toBe("Parking Larrea");
    expect(container.querySelector("svg")).toBeNull();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
  it("muestra solo el título predeterminado si aún no se configuró el nombre", async () => {
    get.mockResolvedValue({ data: { ...perfil, nombreEstacionamiento: null } });
    const { container } = render(<ParkingHeading />);
    expect(await screen.findByRole("heading", { name: "Mi estacionamiento" })).toBeInTheDocument();
    expect(container.textContent).toBe("Mi estacionamiento");
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
