import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MisConvenios from "@/components/MisConvenios";

const { get, put, borrar } = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), borrar: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get, put, delete: borrar } }));

const convenio = { id: 1, nombre: "UCA", formatoCredencial: "UCA-XXX", descuentoPct: 15 };
const credencial = { convenioId: 1, nombre: "UCA", formatoCredencial: "UCA-XXX", descuentoPct: 15, credencial: "UCA-123" };

beforeEach(() => {
  vi.resetAllMocks();
  get.mockImplementation((url) => Promise.resolve({ data: url === "/api/v1/convenios" ? [convenio] : [] }));
  put.mockResolvedValue({ data: credencial });
  borrar.mockResolvedValue({});
});

describe("Mis convenios", () => {
  it("verifica y guarda una credencial propia y permite eliminarla", async () => {
    const user = userEvent.setup();
    render(<MisConvenios documento="30123123" />);
    expect(screen.getByRole("region", { name: "Mis convenios" })).toHaveAttribute("id", "mis-convenios");
    fireEvent.change(await screen.findByLabelText("Credencial UCA"), { target: { value: "UCA-123" } });
    await user.click(screen.getByRole("button", { name: "Verificar y guardar" }));
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/convenios/credenciales", { convenioId: 1, credencial: "UCA-123" }));
    expect(await screen.findByText("Credencial validada y guardada en Mis datos.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Eliminar" }));
    await waitFor(() => expect(borrar).toHaveBeenCalledWith("/api/v1/convenios/credenciales/1"));
    expect(screen.getByLabelText("Credencial UCA")).toHaveValue("");
    expect(screen.getByRole("button", { name: "Verificar y guardar" })).toBeDisabled();
  });

  it("muestra el rechazo de validación sin marcar la credencial como guardada", async () => {
    put.mockRejectedValue({ response: { data: { message: "La credencial no coincide con el documento." } } });
    const user = userEvent.setup();
    render(<MisConvenios documento="30123123" />);
    fireEvent.change(await screen.findByLabelText("Credencial UCA"), { target: { value: "UCA-456" } });
    await user.click(screen.getByRole("button", { name: "Verificar y guardar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("La credencial no coincide con el documento.");
    expect(screen.queryByRole("button", { name: "Eliminar" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Credencial UCA")).toHaveValue("UCA-456");
  });

  it("precarga lo guardado y vuelve a validarlo al cambiar el documento del perfil", async () => {
    get.mockImplementation((url) => Promise.resolve({ data: url === "/api/v1/convenios" ? [convenio] : [credencial] }));
    const { rerender } = render(<MisConvenios documento="30123123" />);
    expect(await screen.findByLabelText("Credencial UCA")).toHaveValue("UCA-123");
    expect(screen.getByLabelText("Credencial UCA")).toHaveAttribute("readonly");
    get.mockImplementation((url) => Promise.resolve({ data: url === "/api/v1/convenios" ? [convenio] : [] }));
    rerender(<MisConvenios documento="30123456" />);
    await waitFor(() => expect(screen.getByLabelText("Credencial UCA")).toHaveValue(""));
    expect(screen.queryByText("Credencial validada y guardada en Mis datos.")).not.toBeInTheDocument();
    expect(get.mock.calls.filter(([url]) => url === "/api/v1/convenios/credenciales")).toHaveLength(2);
  });
});
