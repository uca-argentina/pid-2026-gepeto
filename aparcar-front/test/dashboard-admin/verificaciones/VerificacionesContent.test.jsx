import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock } }));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: VerificacionesContent } = await import("@/app/dashboard-admin/verificaciones/VerificacionesContent");

const solicitud = (extra = {}) => ({
  id: "s1", estado: "EN_REVISION", fechaCreacion: "2026-10-09T13:00:00Z", resueltaAt: null, revocadaAt: null,
  motivo: null, venceEl: null, documentoDisponible: true, documentoNombre: "cud.pdf",
  documentoTipo: "application/pdf", documentoTamano: 100,
  solicitante: { id: "v1", nombre: "Juan Perez", email: "juan@test.com" }, ...extra,
});

let porEstado;
let confirmar;

beforeEach(() => {
  vi.clearAllMocks();
  porEstado = { EN_REVISION: [solicitud(), solicitud({ id: "s2", solicitante: { id: "v2", nombre: "Eva Lopez", email: "eva@test.com" } })] };
  getMock.mockImplementation((url, config) => {
    if (url === "/api/v1/verificaciones-discapacidad") return Promise.resolve({ data: porEstado[config.params.estado] ?? [] });
    if (url.endsWith("/documento")) return Promise.resolve({ data: new Blob(["%PDF"], { type: "application/pdf" }) });
    return Promise.reject(new Error(url));
  });
  URL.createObjectURL = vi.fn(() => "blob:certificado");
  URL.revokeObjectURL = vi.fn();
  confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
});

afterEach(() => vi.restoreAllMocks());

async function abrir(user, nombre = "Juan Perez") {
  render(<VerificacionesContent />);
  await user.click(await screen.findByRole("button", { name: new RegExp(nombre) }));
  return screen.getByRole("region", { name: "Detalle de la solicitud" });
}

describe("VerificacionesContent: cola y detalle", () => {
  it("lista las pendientes, más viejas primero según el backend, con datos mínimos", async () => {
    render(<VerificacionesContent />);

    const lista = await screen.findByRole("list", { name: "Solicitudes de verificación" });
    const items = within(lista).getAllByRole("button");
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent("Juan Perez");
    expect(items[0]).toHaveTextContent("juan@test.com");
    expect(getMock).toHaveBeenCalledWith("/api/v1/verificaciones-discapacidad", { params: { estado: "EN_REVISION" } });
  });

  it("filtra por estado", async () => {
    const user = userEvent.setup();
    porEstado.VERIFICADA = [solicitud({ id: "s9", estado: "VERIFICADA", solicitante: { nombre: "Ana Ruiz", email: "ana@test.com" } })];
    render(<VerificacionesContent />);
    await screen.findByRole("button", { name: /Juan Perez/ });

    await user.click(screen.getByRole("button", { name: "Verificada" }));

    expect(await screen.findByRole("button", { name: /Ana Ruiz/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Juan Perez/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Verificada" })).toHaveAttribute("aria-pressed", "true");
  });

  it("sin pendientes lo dice", async () => {
    porEstado.EN_REVISION = [];
    render(<VerificacionesContent />);

    expect(await screen.findByText("No hay solicitudes pendientes.")).toBeInTheDocument();
  });

  it("muestra el PDF obtenido con la sesión, desde memoria y sin URL pública", async () => {
    const user = userEvent.setup();
    const detalle = await abrir(user);

    const visor = await within(detalle).findByTitle("Certificado de Juan Perez");
    expect(visor).toHaveAttribute("src", "blob:certificado");
    expect(getMock).toHaveBeenCalledWith("/api/v1/verificaciones-discapacidad/s1/documento", { responseType: "blob" });
    expect(detalle).toHaveTextContent("juan@test.com");
  });

  it("una imagen se muestra como imagen", async () => {
    const user = userEvent.setup();
    porEstado.EN_REVISION = [solicitud({ documentoTipo: "image/png", documentoNombre: "cud.png" })];
    const detalle = await abrir(user);

    expect(await within(detalle).findByRole("img", { name: "Certificado de Juan Perez" })).toHaveAttribute("src", "blob:certificado");
  });

  it("si el certificado ya se eliminó, lo explica y conserva la decisión", async () => {
    const user = userEvent.setup();
    porEstado.EN_REVISION = [solicitud({ documentoDisponible: false })];
    const detalle = await abrir(user);

    expect(detalle).toHaveTextContent("se eliminó por la política de retención");
    expect(getMock).not.toHaveBeenCalledWith(expect.stringMatching(/documento$/), expect.anything());
  });
});

describe("VerificacionesContent: resolver", () => {
  it("aprueba con vencimiento opcional tras confirmar, y refresca la cola y el contador", async () => {
    const user = userEvent.setup();
    const evento = vi.fn();
    window.addEventListener("aparcar:verificaciones-actualizadas", evento);
    postMock.mockResolvedValue({ data: solicitud({ estado: "VERIFICADA" }) });
    const detalle = await abrir(user);

    await user.type(within(detalle).getByLabelText("Vence el (opcional)"), "2027-03-31");
    await user.click(within(detalle).getByRole("button", { name: "Aprobar" }));

    expect(confirmar).toHaveBeenCalled();
    expect(postMock).toHaveBeenCalledWith("/api/v1/verificaciones-discapacidad/s1/aprobar", { venceEl: "2027-03-31" });
    await waitFor(() => expect(toastSuccessMock).toHaveBeenCalledWith("Verificación aprobada"));
    expect(evento).toHaveBeenCalled();
    window.removeEventListener("aparcar:verificaciones-actualizadas", evento);
  });

  it("aprobar sin vencimiento manda un cuerpo vacío", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValue({ data: solicitud({ estado: "VERIFICADA" }) });
    const detalle = await abrir(user);

    await user.click(within(detalle).getByRole("button", { name: "Aprobar" }));

    expect(postMock).toHaveBeenCalledWith("/api/v1/verificaciones-discapacidad/s1/aprobar", {});
  });

  it("si no confirma, no hace nada", async () => {
    const user = userEvent.setup();
    confirmar.mockReturnValue(false);
    const detalle = await abrir(user);

    await user.click(within(detalle).getByRole("button", { name: "Aprobar" }));

    expect(postMock).not.toHaveBeenCalled();
  });

  it("rechazar exige el motivo antes de confirmar", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValue({ data: solicitud({ estado: "RECHAZADA" }) });
    const detalle = await abrir(user);

    await user.click(within(detalle).getByRole("button", { name: "Rechazar" }));
    expect(within(detalle).getByRole("alert")).toHaveTextContent("El motivo es obligatorio");
    expect(confirmar).not.toHaveBeenCalled();
    expect(postMock).not.toHaveBeenCalled();

    await user.type(within(detalle).getByLabelText("Motivo del rechazo"), "  No se lee el número.  ");
    await user.click(within(detalle).getByRole("button", { name: "Rechazar" }));

    expect(postMock).toHaveBeenCalledWith("/api/v1/verificaciones-discapacidad/s1/rechazar", { motivo: "No se lee el número." });
  });

  it("un doble clic manda una sola decisión", async () => {
    const user = userEvent.setup();
    let resolver;
    postMock.mockReturnValue(new Promise((resolve) => { resolver = resolve; }));
    const detalle = await abrir(user);

    await user.dblClick(within(detalle).getByRole("button", { name: "Aprobar" }));

    expect(postMock).toHaveBeenCalledTimes(1);
    expect(within(detalle).getByRole("button", { name: "Aprobando…" })).toBeDisabled();
    resolver({ data: solicitud({ estado: "VERIFICADA" }) });
    await waitFor(() => expect(toastSuccessMock).toHaveBeenCalled());
  });

  it("si otro admin ya la resolvió (409), lo avisa y recarga la cola", async () => {
    const user = userEvent.setup();
    postMock.mockRejectedValue({ response: { status: 409, data: { message: "Otro administrador ya resolvió esta solicitud." } } });
    const detalle = await abrir(user);
    porEstado.EN_REVISION = [porEstado.EN_REVISION[1]];

    await user.click(within(detalle).getByRole("button", { name: "Aprobar" }));

    await waitFor(() => expect(toastErrorMock).toHaveBeenCalledWith("Otro administrador ya resolvió esta solicitud."));
    await waitFor(() => expect(screen.queryByRole("button", { name: /Juan Perez/ })).not.toBeInTheDocument());
    expect(screen.getByText("Elegí una solicitud para ver el certificado.")).toBeInTheDocument();
  });

  it("otro error del backend se muestra sin cerrar el detalle", async () => {
    const user = userEvent.setup();
    postMock.mockRejectedValue({ response: { status: 400, data: { message: "La fecha de vencimiento no puede estar en el pasado." } } });
    const detalle = await abrir(user);

    await user.click(within(detalle).getByRole("button", { name: "Aprobar" }));

    await waitFor(() => expect(toastErrorMock).toHaveBeenCalledWith("La fecha de vencimiento no puede estar en el pasado."));
    expect(within(detalle).getByRole("button", { name: "Aprobar" })).toBeEnabled();
  });

  it("revoca una verificación vigente con motivo obligatorio", async () => {
    const user = userEvent.setup();
    porEstado.VERIFICADA = [solicitud({ estado: "VERIFICADA", venceEl: "2027-03-31" })];
    postMock.mockResolvedValue({ data: solicitud({ estado: "REVOCADA" }) });
    render(<VerificacionesContent />);
    await screen.findByRole("button", { name: /Juan Perez/ });
    await user.click(screen.getByRole("button", { name: "Verificada" }));
    await user.click(await screen.findByRole("button", { name: /Juan Perez/ }));
    const detalle = screen.getByRole("region", { name: "Detalle de la solicitud" });
    expect(detalle).toHaveTextContent("31/03/2027");
    expect(within(detalle).queryByRole("button", { name: "Aprobar" })).not.toBeInTheDocument();

    await user.type(within(detalle).getByLabelText("Motivo de la revocación"), "Certificado adulterado.");
    await user.click(within(detalle).getByRole("button", { name: "Revocar verificación" }));

    expect(confirmar.mock.calls[0][0]).toMatch(/Sus reservas ya hechas no se cancelan/);
    expect(postMock).toHaveBeenCalledWith("/api/v1/verificaciones-discapacidad/s1/revocar", { motivo: "Certificado adulterado." });
  });

  it("una solicitud ya resuelta no ofrece acciones", async () => {
    const user = userEvent.setup();
    porEstado.RECHAZADA = [solicitud({ estado: "RECHAZADA", motivo: "Ilegible", resueltaAt: "2026-10-10T13:00:00Z", resueltaPor: "Ana Admin (ana@test.com)" })];
    render(<VerificacionesContent />);
    await screen.findByRole("button", { name: /Juan Perez/ });
    await user.click(screen.getByRole("button", { name: "Rechazada" }));
    await user.click(await screen.findByRole("button", { name: /Juan Perez/ }));
    const detalle = screen.getByRole("region", { name: "Detalle de la solicitud" });

    expect(detalle).toHaveTextContent("Ilegible");
    expect(detalle).toHaveTextContent("Ana Admin (ana@test.com)");
    expect(within(detalle).queryByRole("button", { name: /Aprobar|Rechazar|Revocar/ })).not.toBeInTheDocument();
  });
});
