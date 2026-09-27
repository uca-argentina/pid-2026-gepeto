import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { get, put, success } = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), success: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get, put } }));
vi.mock("sonner", () => ({ toast: { success } }));
import TarifasManagement from "@/app/dashboard-admin/tarifas/TarifasManagement";

const tarifas = () => ["AUTO", "MOTO", "ACCESIBLE", "CARGA"].map((tipo) => ({
  tipo, hora: 1000, fraccion: 300, mediaJornada: 9000, jornadaCompleta: 16000, version: 0,
}));
const campoHora = () => screen.getByLabelText("Hora · Auto");

beforeEach(() => {
  vi.resetAllMocks();
  get.mockResolvedValue({ data: tarifas() });
  put.mockImplementation(async (dataUrl, body) => ({ data: body.tarifas }));
});

describe("Gestionar tarifas", () => {
  it("carga las cuatro categorías y sus 16 importes sin habilitar guardado innecesario", async () => {
    render(<TarifasManagement />);
    expect(screen.getByText("Cargando tarifas…")).toBeInTheDocument();
    expect(await screen.findAllByRole("textbox")).toHaveLength(16);
    expect(campoHora()).toHaveValue("1000");
    expect(screen.getByLabelText("Fracción · Accesible")).toHaveValue("300");
    expect(screen.getByRole("button", { name: "Guardar tarifas" })).toBeDisabled();
  });

  it("guarda los 16 precios juntos, acepta coma decimal y conserva la versión", async () => {
    const user = userEvent.setup();
    render(<TarifasManagement />);
    await screen.findAllByRole("textbox");
    fireEvent.change(campoHora(), { target: { value: "1234,56" } });
    await user.click(screen.getByRole("button", { name: "Guardar tarifas" }));
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/tarifas", {
      tarifas: tarifas().map((t) => ({ ...t, hora: t.tipo === "AUTO" ? "1234.56" : "1000",
        fraccion: "300", mediaJornada: "9000", jornadaCompleta: "16000" })),
    }));
    expect(success).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Guardar tarifas" })).toBeDisabled();
  });

  it.each(["", "-1", "1.001", "100000000", "abc"])("rechaza el importe inválido %s sin enviar", async (valor) => {
    render(<TarifasManagement />);
    await screen.findAllByRole("textbox");
    fireEvent.change(campoHora(), { target: { value: valor } });
    fireEvent.submit(campoHora().closest("form"));
    expect(await screen.findByRole("alert")).toHaveTextContent("Ingresá un importe");
    expect(put).not.toHaveBeenCalled();
  });

  it("acepta cero como precio explícito", async () => {
    render(<TarifasManagement />);
    await screen.findAllByRole("textbox");
    fireEvent.change(campoHora(), { target: { value: "0" } });
    fireEvent.submit(campoHora().closest("form"));
    await waitFor(() => expect(put).toHaveBeenCalled());
    expect(put.mock.calls[0][1].tarifas[0].hora).toBe("0");
  });

  it("deja vacíos los precios sin configurar y exige completar todos", async () => {
    get.mockResolvedValue({ data: [] });
    render(<TarifasManagement />);
    await screen.findAllByRole("textbox");
    expect(campoHora()).toHaveValue("");
    fireEvent.change(campoHora(), { target: { value: "1000" } });
    fireEvent.submit(campoHora().closest("form"));
    expect(await screen.findAllByRole("alert")).toHaveLength(15);
    expect(put).not.toHaveBeenCalled();
  });

  it("permite reintentar una carga fallida", async () => {
    get.mockRejectedValueOnce(new Error("offline"));
    const user = userEvent.setup();
    render(<TarifasManagement />);
    expect(await screen.findByRole("alert")).toHaveTextContent("No se pudieron cargar");
    await user.click(screen.getByRole("button", { name: "Reintentar carga" }));
    expect(await screen.findAllByRole("textbox")).toHaveLength(16);
  });

  it("conserva los cambios si el servidor rechaza el guardado", async () => {
    put.mockRejectedValue({ response: { data: { message: "Las tarifas cambiaron. Recargá los precios antes de guardar." } } });
    render(<TarifasManagement />);
    await screen.findAllByRole("textbox");
    fireEvent.change(campoHora(), { target: { value: "2000" } });
    fireEvent.submit(campoHora().closest("form"));
    expect(await screen.findByRole("alert")).toHaveTextContent("Las tarifas cambiaron");
    expect(campoHora()).toHaveValue("2000");
    expect(success).not.toHaveBeenCalled();
  });

  it("bloquea los campos y el envío mientras guarda", async () => {
    put.mockReturnValue(new Promise(() => {}));
    render(<TarifasManagement />);
    await screen.findAllByRole("textbox");
    fireEvent.change(campoHora(), { target: { value: "2000" } });
    fireEvent.submit(campoHora().closest("form"));
    expect(await screen.findByRole("button", { name: "Guardando…" })).toBeDisabled();
    expect(campoHora()).toBeDisabled();
    expect(screen.getByRole("button", { name: "Recargar precios" })).toBeDisabled();
  });

  it("recarga precios solo si se acepta descartar los cambios", async () => {
    const confirmar = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    const user = userEvent.setup();
    render(<TarifasManagement />);
    await screen.findAllByRole("textbox");
    fireEvent.change(campoHora(), { target: { value: "2000" } });
    await user.click(screen.getByRole("button", { name: "Recargar precios" }));
    expect(get).toHaveBeenCalledTimes(1);
    expect(campoHora()).toHaveValue("2000");
    await user.click(screen.getByRole("button", { name: "Recargar precios" }));
    await waitFor(() => expect(campoHora()).toHaveValue("1000"));
    confirmar.mockRestore();
  });
});
