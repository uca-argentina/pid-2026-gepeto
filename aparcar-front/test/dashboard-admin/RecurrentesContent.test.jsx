import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get, post, put, del, error } = vi.hoisted(() => ({
  get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn(), error: vi.fn(),
}));
vi.mock("@/app/api", () => ({ default: { get, post, put, delete: del } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error } }));

import RecurrentesContent from "@/app/dashboard-admin/recurrentes/RecurrentesContent";

let vehiculos;

beforeEach(() => {
  vi.clearAllMocks();
  vehiculos = [];
  get.mockImplementation(async (url) => ({
    data: url === "/api/v1/vehiculos/mios" ? [...vehiculos] : [],
  }));
  post.mockImplementation(async (url, data) => {
    vehiculos = [{ id: "propio", ...data }];
    return { data: vehiculos[0] };
  });
  put.mockImplementation(async (url, data) => {
    vehiculos = [{ id: "propio", ...data }];
    return { data: vehiculos[0] };
  });
  del.mockImplementation(async () => { vehiculos = []; return {}; });
});

describe("Mis vehículos y reservas recurrentes del admin", () => {
  it("agrega, edita y elimina vehículos propios y actualiza el selector sin recargar la página", async () => {
    render(<RecurrentesContent />);
    const gestion = screen.getByRole("region", { name: "Mis vehículos" });
    await within(gestion).findByText("Todavía no cargaste ningún vehículo.");
    expect(screen.queryByRole("combobox", { name: "Patente" })).not.toBeInTheDocument();

    fireEvent.change(within(gestion).getByLabelText("Patente"), { target: { value: "abc-123" } });
    await userEvent.click(within(gestion).getByRole("button", { name: "Agregar" }));
    await screen.findByRole("option", { name: /ABC123/ });
    expect(post).toHaveBeenCalledWith("/api/v1/vehiculos/mios", { patente: "ABC123", tipo: "AUTO" });

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Patente" }), "ABC123");
    const fila = within(gestion).getByRole("img", { name: "Patente ABC123" }).closest("li");
    await userEvent.click(within(fila).getByRole("button", { name: "Editar" }));
    fireEvent.change(within(fila).getByLabelText("Patente"), { target: { value: "AB123CD" } });
    await userEvent.click(within(fila).getByRole("button", { name: "Guardar" }));
    await screen.findByRole("option", { name: /AB123CD/ });
    expect(put).toHaveBeenCalledWith("/api/v1/vehiculos/mios/propio", { patente: "AB123CD", tipo: "AUTO" });
    expect(screen.queryByRole("option", { name: /ABC123/ })).not.toBeInTheDocument();

    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
    await userEvent.click(within(gestion).getByRole("button", { name: "Eliminar" }));
    await within(gestion).findByText("Todavía no cargaste ningún vehículo.");
    await waitFor(() => expect(screen.queryByRole("combobox", { name: "Patente" })).not.toBeInTheDocument());
    expect(del).toHaveBeenCalledWith("/api/v1/vehiculos/mios/propio");
    expect(get).not.toHaveBeenCalledWith("/api/v1/vehiculos");
    expect(get).not.toHaveBeenCalledWith("/api/v1/visitantes");
    confirmar.mockRestore();
  });

  it("conserva un vehículo si el servidor impide borrarlo por sus reservas activas", async () => {
    vehiculos = [{ id: "propio", patente: "ABC123", tipo: "AUTO" }];
    del.mockRejectedValue({ response: { data: { message: "El vehículo tiene reservas activas" } } });
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<RecurrentesContent />);
    await userEvent.click(await screen.findByRole("button", { name: "Eliminar" }));
    await waitFor(() => expect(error).toHaveBeenCalledWith("El vehículo tiene reservas activas"));
    expect(screen.getByRole("option", { name: /ABC123/ })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Patente ABC123" })).toBeInTheDocument();
    confirmar.mockRestore();
  });
});
