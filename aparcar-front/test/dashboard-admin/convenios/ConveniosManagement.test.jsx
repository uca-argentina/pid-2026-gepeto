import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ConveniosManagement from "@/app/dashboard-admin/convenios/ConveniosManagement";

const { get, post, put, borrar, success, error } = vi.hoisted(() => ({
  get: vi.fn(), post: vi.fn(), put: vi.fn(), borrar: vi.fn(), success: vi.fn(), error: vi.fn(),
}));
vi.mock("@/app/api", () => ({ default: { get, post, put, delete: borrar } }));
vi.mock("sonner", () => ({ toast: { success, error } }));

const uca = { id: 1, nombre: "UCA", formatoCredencial: "UCA-XXX", descuentoPct: 15 };
const nacion = { id: 2, nombre: "Club Nación", formatoCredencial: "NACION-XXX", descuentoPct: 10 };

beforeEach(() => {
  vi.resetAllMocks();
  get.mockResolvedValue({ data: [uca, nacion] });
  post.mockImplementation(async (_url, body) => ({ data: { ...body, id: 3 } }));
  put.mockImplementation(async (_url, body) => ({ data: { ...body, id: 1 } }));
  borrar.mockResolvedValue({});
});

const cargar = async () => {
  render(<ConveniosManagement />);
  await screen.findByLabelText("Nombre del convenio");
};

const completar = ({ nombre = " Club nuevo ", formato = " nuevo-xxx ", porcentaje = "12,5" } = {}) => {
  fireEvent.change(screen.getByLabelText("Nombre del convenio"), { target: { value: nombre } });
  fireEvent.change(screen.getByLabelText("Formato de credencial"), { target: { value: formato } });
  fireEvent.change(screen.getByLabelText("Descuento (%)"), { target: { value: porcentaje } });
  fireEvent.submit(screen.getByLabelText("Nombre del convenio").closest("form"));
};

describe("Gestionar convenios", () => {
  it("carga los convenios existentes y permite crear uno normalizado", async () => {
    await cargar();
    expect(screen.getByRole("heading", { name: "UCA" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Club Nación" })).toBeInTheDocument();
    completar();
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/convenios", {
      nombre: "Club nuevo", formatoCredencial: "NUEVO-XXX", descuentoPct: 12.5,
    }));
    expect(await screen.findByRole("heading", { name: "Club nuevo" })).toBeInTheDocument();
    expect(screen.getByLabelText("Nombre del convenio")).toHaveValue("");
  });

  it("edita el convenio elegido y actualiza su descuento en pantalla", async () => {
    const user = userEvent.setup();
    await cargar();
    await user.click(screen.getByRole("button", { name: "Editar UCA" }));
    expect(screen.getByLabelText("Formato de credencial")).toHaveValue("UCA-XXX");
    fireEvent.change(screen.getByLabelText("Descuento (%)"), { target: { value: "25" } });
    await user.click(screen.getByRole("button", { name: "Guardar convenio" }));
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/convenios/1", {
      nombre: "UCA", formatoCredencial: "UCA-XXX", descuentoPct: 25,
    }));
    expect(await screen.findByText("25% de descuento")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Agregar convenio" })).toBeInTheDocument();
  });

  it.each(["UCA", "XXX-UCA", "UCA-XXX-XXX", "UCA-XXXX", "UCA ### XXX", "XXX"])("rechaza el formato inválido %s", async (formato) => {
    await cargar();
    completar({ formato });
    expect(await screen.findByRole("alert")).toHaveTextContent("terminá con XXX");
    expect(post).not.toHaveBeenCalled();
  });

  it.each(["0", "-1", "101", "5.555", ""]) ("rechaza el descuento inválido %s", async (porcentaje) => {
    await cargar();
    completar({ porcentaje });
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it("elimina con confirmación y conserva el resto de los convenios", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    await cargar();
    await user.click(screen.getByRole("button", { name: "Eliminar UCA" }));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("Las reservas ya creadas conservarán su descuento"));
    await waitFor(() => expect(borrar).toHaveBeenCalledWith("/api/v1/convenios/1"));
    expect(screen.queryByRole("heading", { name: "UCA" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Club Nación" })).toBeInTheDocument();
  });

  it("cancelar la eliminación no envía cambios", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    await cargar();
    await user.click(screen.getByRole("button", { name: "Eliminar UCA" }));
    expect(borrar).not.toHaveBeenCalled();
  });

  it("conserva el formulario ante un error y evita operaciones simultáneas", async () => {
    let rechazar;
    post.mockReturnValue(new Promise((_resolve, reject) => { rechazar = reject; }));
    const user = userEvent.setup();
    await cargar();
    completar();
    await waitFor(() => expect(screen.getByLabelText("Nombre del convenio")).toBeDisabled());
    expect(screen.getByRole("button", { name: "Eliminar UCA" })).toBeDisabled();
    rechazar({ response: { data: { message: "Ya existe un convenio con ese nombre." } } });
    expect(await screen.findByRole("alert")).toHaveTextContent("Ya existe un convenio con ese nombre.");
    expect(screen.getByLabelText("Nombre del convenio")).toHaveValue(" Club nuevo ");
    post.mockResolvedValue({ data: { id: 3, nombre: "Club nuevo", formatoCredencial: "NUEVO-XXX", descuentoPct: 12.5 } });
    await user.click(screen.getByRole("button", { name: "Agregar convenio" }));
    expect(await screen.findByRole("heading", { name: "Club nuevo" })).toBeInTheDocument();
  });

  it("una eliminación fallida conserva la tarjeta y muestra el error", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    borrar.mockRejectedValue(new Error("red"));
    const user = userEvent.setup();
    await cargar();
    await user.click(screen.getByRole("button", { name: "Eliminar UCA" }));
    await waitFor(() => expect(error).toHaveBeenCalledWith("No se pudo eliminar el convenio."));
    expect(within(screen.getByRole("heading", { name: "UCA" }).closest("li")).getByRole("button", { name: "Eliminar UCA" })).toBeEnabled();
  });

  it("permite reintentar una carga fallida sin mostrar un formulario vacío", async () => {
    get.mockRejectedValueOnce(new Error("red"));
    const user = userEvent.setup();
    render(<ConveniosManagement />);
    const retry = await screen.findByRole("button", { name: "Reintentar carga" });
    expect(screen.queryByLabelText("Nombre del convenio")).not.toBeInTheDocument();
    await user.click(retry);
    expect(await screen.findByRole("heading", { name: "UCA" })).toBeInTheDocument();
  });
});
