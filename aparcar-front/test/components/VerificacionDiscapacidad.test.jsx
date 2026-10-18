import { describe, it, expect, beforeEach, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock } }));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: VerificacionDiscapacidad } = await import("@/components/VerificacionDiscapacidad");

const estado = (extra = {}) => ({
  estado: "SIN_SOLICITUD", puedeUsarCocherasAccesibles: false, accesoProvisorioHasta: null,
  puedeEnviar: true, documentoMaxMb: 5, solicitud: null, ...extra,
});
const solicitud = (extra = {}) => ({
  id: "s1", estado: "EN_REVISION", fechaCreacion: "2026-10-09T13:00:00Z", resueltaAt: null, revocadaAt: null,
  motivo: null, venceEl: null, documentoDisponible: true, documentoNombre: "cud.pdf",
  documentoTipo: "application/pdf", documentoTamano: 100, ...extra,
});
const pdf = (nombre = "cud.pdf", tamano = 1024, tipo = "application/pdf") => {
  const archivo = new File(["%PDF-1.7"], nombre, { type: tipo });
  Object.defineProperty(archivo, "size", { value: tamano });
  return archivo;
};

const campo = () => screen.getByLabelText(/Subí tu certificado|Enviar un certificado nuevo/);

beforeEach(() => {
  vi.clearAllMocks();
  getMock.mockResolvedValue({ data: estado() });
});

describe("VerificacionDiscapacidad: estados", () => {
  it("sin solicitud invita a subir el certificado y explica la privacidad", async () => {
    render(<VerificacionDiscapacidad />);

    expect(await screen.findByText(/enviá tu certificado para poder reservar cocheras accesibles/)).toBeInTheDocument();
    expect(campo()).toHaveAttribute("type", "file");
    expect(screen.getByText(/Solo lo ven los administradores/)).toBeInTheDocument();
    expect(screen.getByText(/no un diagnóstico/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar certificado" })).toBeDisabled();
  });

  it("en revisión lo dice, no deja subir otro y permite retirarla", async () => {
    getMock.mockResolvedValue({ data: estado({ estado: "EN_REVISION", puedeEnviar: false, solicitud: solicitud() }) });
    render(<VerificacionDiscapacidad />);

    await screen.findByText("En revisión");
    expect(screen.getByRole("status")).toHaveTextContent("09/10/2026");
    expect(screen.queryByRole("button", { name: "Enviar certificado" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retirar mi solicitud" })).toBeInTheDocument();
  });

  it("verificada muestra hasta cuándo vale", async () => {
    getMock.mockResolvedValue({ data: estado({
      estado: "VERIFICADA", puedeEnviar: false, puedeUsarCocherasAccesibles: true,
      solicitud: solicitud({ estado: "VERIFICADA", venceEl: "2027-03-31" }),
    }) });
    render(<VerificacionDiscapacidad />);

    await screen.findByText("Verificada");
    expect(screen.getByRole("status")).toHaveTextContent("hasta el 31/03/2027");
    expect(screen.queryByRole("button", { name: "Enviar certificado" })).not.toBeInTheDocument();
  });

  it("rechazada muestra el motivo destacado y deja reenviar", async () => {
    getMock.mockResolvedValue({ data: estado({
      estado: "RECHAZADA", solicitud: solicitud({ estado: "RECHAZADA", motivo: "No se lee el número del CUD." }),
    }) });
    render(<VerificacionDiscapacidad />);

    const aviso = await screen.findByRole("alert");
    expect(aviso).toHaveTextContent("Tu certificado fue rechazado");
    expect(aviso).toHaveTextContent("Motivo: No se lee el número del CUD.");
    expect(screen.getByLabelText(/Enviar un certificado nuevo/)).toBeInTheDocument();
  });

  it.each([
    ["REVOCADA", "Tu verificación fue revocada"],
    ["VENCIDA", "Tu verificación venció"],
  ])("%s lo avisa y deja enviar uno nuevo", async (valor, texto) => {
    getMock.mockResolvedValue({ data: estado({ estado: valor, solicitud: solicitud({ estado: valor, motivo: "x" }) }) });
    render(<VerificacionDiscapacidad />);

    expect(await screen.findByRole("alert")).toHaveTextContent(texto);
    expect(screen.getByRole("button", { name: "Enviar certificado" })).toBeInTheDocument();
  });

  it("una cuenta migrada ve el plazo para enviar el certificado sin perder el acceso", async () => {
    getMock.mockResolvedValue({ data: estado({
      puedeUsarCocherasAccesibles: true, accesoProvisorioHasta: "2026-11-09T13:00:00Z",
    }) });
    render(<VerificacionDiscapacidad />);

    await screen.findByText("Tenés que enviar tu certificado");
    const aviso = screen.getByRole("status");
    expect(aviso).toHaveTextContent("09/11/2026");
  });

  it("si no se pudo cargar, lo dice y permite reintentar", async () => {
    const user = userEvent.setup();
    getMock.mockRejectedValueOnce(new Error("red")).mockResolvedValueOnce({ data: estado() });
    render(<VerificacionDiscapacidad />);

    expect(await screen.findByRole("alert")).toHaveTextContent("No se pudo cargar");
    await user.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(await screen.findByLabelText(/Subí tu certificado/)).toBeInTheDocument();
  });
});

describe("VerificacionDiscapacidad: subir", () => {
  it("valida el tipo en el cliente antes de subir", async () => {
    const user = userEvent.setup({ applyAccept: false });
    render(<VerificacionDiscapacidad />);
    await screen.findByLabelText(/Subí tu certificado/);

    await user.upload(campo(), new File(["x"], "virus.exe", { type: "application/x-msdownload" }));

    expect(screen.getByRole("alert")).toHaveTextContent("tiene que ser un PDF, JPG o PNG");
    expect(screen.getByRole("button", { name: "Enviar certificado" })).toBeDisabled();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("valida el tamaño máximo que informa el backend", async () => {
    const user = userEvent.setup();
    render(<VerificacionDiscapacidad />);
    await screen.findByLabelText(/Subí tu certificado/);

    await user.upload(campo(), pdf("cud.pdf", 5 * 1024 * 1024 + 1));

    expect(screen.getByRole("alert")).toHaveTextContent("no puede superar los 5 MB");
  });

  it("muestra el nombre elegido y envía el archivo como multipart", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValue({ data: estado({ estado: "EN_REVISION", puedeEnviar: false, solicitud: solicitud() }) });
    render(<VerificacionDiscapacidad />);
    await screen.findByLabelText(/Subí tu certificado/);

    await user.upload(campo(), pdf("Mi CUD.pdf"));
    expect(screen.getByText("Mi CUD.pdf")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Enviar certificado" }));

    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));
    const [url, datos, config] = postMock.mock.calls[0];
    expect(url).toBe("/api/v1/verificaciones-discapacidad");
    expect(datos).toBeInstanceOf(FormData);
    expect(datos.get("archivo").name).toBe("Mi CUD.pdf");
    expect(config.headers["Content-Type"]).toBe("multipart/form-data");
    expect(await screen.findByRole("status")).toHaveTextContent("En revisión");
    expect(toastSuccessMock).toHaveBeenCalled();
  });

  it("acepta un archivo arrastrado y soltado", async () => {
    render(<VerificacionDiscapacidad />);
    const zona = (await screen.findByText("Subí tu certificado")).closest("label");

    fireEvent.drop(zona, { dataTransfer: { files: [pdf("soltado.png", 2048, "image/png")] } });

    expect(screen.getByText("soltado.png")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar certificado" })).toBeEnabled();
  });

  it.each([
    [409, "Ya tenés una solicitud en revisión. Esperá la respuesta antes de enviar otra."],
    [415, "El contenido del archivo no corresponde a un PDF. Subí el certificado original."],
    [413, "El certificado no puede superar los 5 MB."],
  ])("muestra el error %i del backend", async (status, message) => {
    const user = userEvent.setup();
    postMock.mockRejectedValue({ response: { status, data: { code: status, message } } });
    render(<VerificacionDiscapacidad />);
    await screen.findByLabelText(/Subí tu certificado/);

    await user.upload(campo(), pdf());
    await user.click(screen.getByRole("button", { name: "Enviar certificado" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByRole("button", { name: "Enviar certificado" })).toBeInTheDocument();
  });

  it("no envía dos veces con un doble clic", async () => {
    const user = userEvent.setup();
    let resolver;
    postMock.mockReturnValue(new Promise((resolve) => { resolver = resolve; }));
    render(<VerificacionDiscapacidad />);
    await screen.findByLabelText(/Subí tu certificado/);
    await user.upload(campo(), pdf());

    await user.dblClick(screen.getByRole("button", { name: "Enviar certificado" }));

    expect(postMock).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Enviando…" })).toBeDisabled();
    resolver({ data: estado({ estado: "EN_REVISION", puedeEnviar: false, solicitud: solicitud() }) });
    expect(await screen.findByText("En revisión")).toBeInTheDocument();
  });

  it("retirar la solicitud vuelve a dejar subir", async () => {
    const user = userEvent.setup();
    getMock.mockResolvedValue({ data: estado({ estado: "EN_REVISION", puedeEnviar: false, solicitud: solicitud() }) });
    postMock.mockResolvedValue({ data: estado() });
    render(<VerificacionDiscapacidad />);

    await user.click(await screen.findByRole("button", { name: "Retirar mi solicitud" }));

    expect(postMock).toHaveBeenCalledWith("/api/v1/verificaciones-discapacidad/mia/cancelar");
    expect(await screen.findByLabelText(/Subí tu certificado/)).toBeInTheDocument();
  });
});
