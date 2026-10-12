import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { jsQRMock } = vi.hoisted(() => ({ jsQRMock: vi.fn() }));
vi.mock("jsqr", () => ({ default: jsQRMock }));

const { default: LectorQR, MENSAJES_CAMARA } = await import("@/components/LectorQR");

/**
 * El lector de QR con la cámara, con todo lo del navegador simulado:
 * getUserMedia, BarcodeDetector, el canvas y jsqr.
 */

const track = () => ({ stop: vi.fn() });
let tracks;
let getUserMedia;

const errorDeCamara = (name) => Object.assign(new Error(name), { name });

function conCamara() {
  tracks = [track()];
  getUserMedia = vi.fn().mockResolvedValue({ getTracks: () => tracks });
  Object.defineProperty(navigator, "mediaDevices", { value: { getUserMedia }, configurable: true });
}

function conBarcodeDetector(leido) {
  const detect = vi.fn().mockResolvedValue(leido ? [{ rawValue: leido }] : []);
  window.BarcodeDetector = vi.fn(function BarcodeDetector() {
    this.detect = detect;
  });
  window.BarcodeDetector.getSupportedFormats = vi.fn().mockResolvedValue(["qr_code", "ean_13"]);
  return detect;
}

beforeEach(() => {
  vi.clearAllMocks();
  conCamara();
  delete window.BarcodeDetector;
  Object.defineProperty(window, "isSecureContext", { value: true, configurable: true });
  vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue();
});

afterEach(() => {
  delete window.BarcodeDetector;
  vi.restoreAllMocks();
});

describe("LectorQR", () => {
  it("no pide la cámara hasta que el operador la activa, y prefiere la trasera", async () => {
    const user = userEvent.setup();
    conBarcodeDetector(null);
    render(<LectorQR onLectura={vi.fn()} />);

    expect(getUserMedia).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Activar cámara" }));

    expect(getUserMedia).toHaveBeenCalledWith({ video: { facingMode: { ideal: "environment" } }, audio: false });
    expect(await screen.findByText("Apuntá la cámara al código QR del visitante.")).toBeInTheDocument();
  });

  it("con BarcodeDetector lee el QR, entrega el texto y apaga la cámara", async () => {
    const user = userEvent.setup();
    const onLectura = vi.fn();
    const detect = conBarcodeDetector("APARCAR:ABCDE12345");
    render(<LectorQR onLectura={onLectura} />);

    await user.click(screen.getByRole("button", { name: "Activar cámara" }));

    await waitFor(() => expect(onLectura).toHaveBeenCalledWith("APARCAR:ABCDE12345"));
    expect(window.BarcodeDetector).toHaveBeenCalledWith({ formats: ["qr_code"] });
    expect(detect).toHaveBeenCalled();
    expect(tracks[0].stop).toHaveBeenCalled();
    expect(jsQRMock).not.toHaveBeenCalled();
  });

  it("sin BarcodeDetector usa jsqr sobre un cuadro del video", async () => {
    const user = userEvent.setup();
    const onLectura = vi.fn();
    vi.spyOn(HTMLVideoElement.prototype, "videoWidth", "get").mockReturnValue(640);
    vi.spyOn(HTMLVideoElement.prototype, "videoHeight", "get").mockReturnValue(480);
    const contexto = { drawImage: vi.fn(), getImageData: vi.fn(() => ({ data: new Uint8ClampedArray(4) })) };
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(contexto);
    jsQRMock.mockReturnValue({ data: "APARCAR:FGHJK67890" });
    render(<LectorQR onLectura={onLectura} />);

    await user.click(screen.getByRole("button", { name: "Activar cámara" }));

    await waitFor(() => expect(onLectura).toHaveBeenCalledWith("APARCAR:FGHJK67890"));
    expect(jsQRMock).toHaveBeenCalledWith(expect.any(Uint8ClampedArray), 640, 480, expect.anything());
    expect(contexto.drawImage).toHaveBeenCalled();
    expect(tracks[0].stop).toHaveBeenCalled();
  });

  it("usa jsqr si el detector nativo existe pero falla al leer el video", async () => {
    const user = userEvent.setup();
    const onLectura = vi.fn();
    conBarcodeDetector(null).mockRejectedValue(new Error("Video no soportado"));
    vi.spyOn(HTMLVideoElement.prototype, "videoWidth", "get").mockReturnValue(640);
    vi.spyOn(HTMLVideoElement.prototype, "videoHeight", "get").mockReturnValue(480);
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
      drawImage: vi.fn(),
      getImageData: vi.fn(() => ({ data: new Uint8ClampedArray(4) })),
    });
    jsQRMock.mockReturnValue({ data: "APARCAR:ABCDE12345" });
    render(<LectorQR onLectura={onLectura} />);

    await user.click(screen.getByRole("button", { name: "Activar cámara" }));

    await waitFor(() => expect(onLectura).toHaveBeenCalledWith("APARCAR:ABCDE12345"));
    expect(tracks[0].stop).toHaveBeenCalled();
  });

  it("no activa la cámara mientras la validación está ocupada", async () => {
    const user = userEvent.setup();
    render(<LectorQR onLectura={vi.fn()} autoIniciar disabled />);

    const activar = screen.getByRole("button", { name: "Activar cámara" });
    expect(activar).toBeDisabled();
    await user.click(activar);

    expect(getUserMedia).not.toHaveBeenCalled();
  });

  it("al deshabilitar apaga la cámara e ignora una lectura que llega tarde", async () => {
    const user = userEvent.setup();
    const onLectura = vi.fn();
    let resolver;
    conBarcodeDetector(null).mockReturnValue(new Promise((resolve) => { resolver = resolve; }));
    const { rerender } = render(<LectorQR onLectura={onLectura} />);
    await user.click(screen.getByRole("button", { name: "Activar cámara" }));
    await screen.findByText("Apuntá la cámara al código QR del visitante.");

    rerender(<LectorQR onLectura={onLectura} disabled />);
    await act(async () => { resolver([{ rawValue: "APARCAR:ABCDE12345" }]); });

    expect(tracks[0].stop).toHaveBeenCalled();
    expect(onLectura).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Activar cámara" })).toBeDisabled();

    rerender(<LectorQR onLectura={onLectura} />);

    expect(screen.getByRole("button", { name: "Activar cámara" })).toBeEnabled();
    expect(getUserMedia).toHaveBeenCalledTimes(1);
  });

  it("libera el permiso de cámara concedido después de deshabilitar el lector", async () => {
    const user = userEvent.setup();
    let conceder;
    getUserMedia.mockReturnValue(new Promise((resolve) => { conceder = resolve; }));
    const { rerender } = render(<LectorQR onLectura={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Activar cámara" }));

    rerender(<LectorQR onLectura={vi.fn()} disabled />);
    await act(async () => { conceder({ getTracks: () => tracks }); });

    expect(tracks[0].stop).toHaveBeenCalled();
    expect(HTMLMediaElement.prototype.play).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Activar cámara" })).toBeDisabled();
  });

  it("si el operador no da permiso, lo explica y ofrece el código escrito", async () => {
    const user = userEvent.setup();
    const onUsarCodigo = vi.fn();
    getUserMedia.mockRejectedValue(errorDeCamara("NotAllowedError"));
    render(<LectorQR onLectura={vi.fn()} onUsarCodigo={onUsarCodigo} />);

    await user.click(screen.getByRole("button", { name: "Activar cámara" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(MENSAJES_CAMARA.PERMISO);
    await user.click(screen.getByRole("button", { name: "Escribir el código" }));
    expect(onUsarCodigo).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Reintentar con la cámara" })).toBeInTheDocument();
  });

  it("si no hay cámara, lo dice", async () => {
    const user = userEvent.setup();
    getUserMedia.mockRejectedValue(errorDeCamara("NotFoundError"));
    render(<LectorQR onLectura={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Activar cámara" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(MENSAJES_CAMARA.SIN_CAMARA);
  });

  it("en HTTP sin localhost avisa de entrada que la cámara no va a funcionar, sin pedirla", () => {
    Object.defineProperty(window, "isSecureContext", { value: false, configurable: true });
    render(<LectorQR onLectura={vi.fn()} onUsarCodigo={vi.fn()} />);

    expect(screen.getByRole("alert")).toHaveTextContent(MENSAJES_CAMARA.INSEGURO);
    expect(screen.queryByRole("button", { name: /cámara/ })).not.toBeInTheDocument();
    expect(getUserMedia).not.toHaveBeenCalled();
  });

  it("libera la cámara al salir de la pantalla", async () => {
    const user = userEvent.setup();
    conBarcodeDetector(null);
    const { unmount } = render(<LectorQR onLectura={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Activar cámara" }));
    await screen.findByText("Apuntá la cámara al código QR del visitante.");
    expect(tracks[0].stop).not.toHaveBeenCalled();

    unmount();

    expect(tracks[0].stop).toHaveBeenCalled();
  });

  it("si se sale mientras el navegador pide permiso, apaga la cámara apenas llega", async () => {
    const user = userEvent.setup();
    let conceder;
    getUserMedia.mockReturnValue(new Promise((resolve) => { conceder = resolve; }));
    const { unmount } = render(<LectorQR onLectura={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Activar cámara" }));

    unmount();
    conceder({ getTracks: () => tracks });

    await waitFor(() => expect(tracks[0].stop).toHaveBeenCalled());
  });

  it("Detener cámara la apaga sin salir de la pantalla", async () => {
    const user = userEvent.setup();
    conBarcodeDetector(null);
    render(<LectorQR onLectura={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Activar cámara" }));
    await screen.findByRole("button", { name: "Detener cámara" });

    await user.click(screen.getByRole("button", { name: "Detener cámara" }));

    expect(tracks[0].stop).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Activar cámara" })).toBeInTheDocument();
  });

  it("con autoIniciar vuelve a prender la cámara sola para el siguiente vehículo", async () => {
    conBarcodeDetector(null);
    render(<LectorQR onLectura={vi.fn()} autoIniciar />);

    await waitFor(() => expect(getUserMedia).toHaveBeenCalledTimes(1));
  });
});
