import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { pushMock, postMock, setAuthMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  postMock: vi.fn(),
  setAuthMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock("@/app/api", () => ({
  default: { post: postMock },
}));

vi.mock("@/store/authStore", () => ({
  useAuthStore: (selector) => selector({ setAuth: setAuthMock }),
}));

vi.mock("sonner", () => ({
  toast: { success: toastSuccessMock, error: toastErrorMock },
}));

const { default: LoginPage } = await import("@/app/login/page");

// JWT con el mismo shape que emite el back: claim "authorities" como string
// separado por comas.
function fakeJwt(authorities) {
  const encode = (obj) => btoa(JSON.stringify(obj)).replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode({ email: "mateo@test.com", authorities })}.firma`;
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("muestra los campos de email y contraseña y el boton de ingresar", () => {
    render(<LoginPage />);

    expect(screen.getByPlaceholderText("Correo electrónico")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Contraseña")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /ingresar/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Registrate" })).toHaveAttribute("href", "/register");
  });

  it("muestra errores de validacion y no llama a la API si el email esta vacio", async () => {
    // No se prueba con un string invalido tipo "no-es-un-email": el input
    // es type="email", y jsdom (como un navegador real) bloquea el submit
    // por validacion nativa HTML5 antes de que React llegue a correr Zod.
    // Un campo vacio sí pasa esa validación nativa (no tiene "required").
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText("Contraseña"), "123456");
    await user.click(screen.getByRole("button", { name: /ingresar/i }));

    expect(await screen.findByText("Ingresa un correo válido")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("muestra error de validacion si la contraseña tiene menos de 6 caracteres", async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText("Correo electrónico"), "mateo@test.com");
    await user.type(screen.getByPlaceholderText("Contraseña"), "123");
    await user.click(screen.getByRole("button", { name: /ingresar/i }));

    expect(await screen.findByText("La contraseña debe tener al menos 6 caracteres")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("envia el login con Basic Auth (email:password en base64) y no como body JSON", async () => {
    postMock.mockResolvedValue({ headers: { authorization: `Bearer ${fakeJwt("USER")}` } });
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText("Correo electrónico"), "mateo@test.com");
    await user.type(screen.getByPlaceholderText("Contraseña"), "secreto1");
    await user.click(screen.getByRole("button", { name: /ingresar/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));
    const [url, body, config] = postMock.mock.calls[0];
    expect(url).toBe("/login");
    expect(body).toEqual({});
    expect(config.headers.Authorization).toBe(`Basic ${btoa("mateo@test.com:secreto1")}`);
  });

  it("con rol ADMIN en el JWT, redirige a /dashboard-admin", async () => {
    postMock.mockResolvedValue({ headers: { authorization: `Bearer ${fakeJwt("USER,ADMIN")}` } });
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText("Correo electrónico"), "mateo@test.com");
    await user.type(screen.getByPlaceholderText("Contraseña"), "secreto1");
    await user.click(screen.getByRole("button", { name: /ingresar/i }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/dashboard-admin"));
    expect(setAuthMock).toHaveBeenCalled();
    expect(toastSuccessMock).toHaveBeenCalled();
  });

  it("codifica las contraseñas Unicode en UTF-8 para HTTP Basic", async () => {
    postMock.mockResolvedValue({ headers: { authorization: `Bearer ${fakeJwt("USER")}` } });
    const user = userEvent.setup();
    render(<LoginPage />);
    await user.type(screen.getByPlaceholderText("Correo electrónico"), "ana@test.com");
    await user.type(screen.getByPlaceholderText("Contraseña"), "Contraseña🔑");
    await user.click(screen.getByRole("button", { name: /ingresar/i }));
    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));
    expect(postMock.mock.calls[0][2].headers.Authorization)
      .toBe(`Basic ${Buffer.from("ana@test.com:Contraseña🔑", "utf8").toString("base64")}`);
  });

  it("con solo rol USER en el JWT, redirige a /dashboard-user", async () => {
    postMock.mockResolvedValue({ headers: { authorization: `Bearer ${fakeJwt("USER")}` } });
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText("Correo electrónico"), "visitante@test.com");
    await user.type(screen.getByPlaceholderText("Contraseña"), "secreto1");
    await user.click(screen.getByRole("button", { name: /ingresar/i }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/dashboard-user"));
  });

  it("si la respuesta no trae header Authorization, muestra error y no redirige", async () => {
    postMock.mockResolvedValue({ headers: {} });
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText("Correo electrónico"), "mateo@test.com");
    await user.type(screen.getByPlaceholderText("Contraseña"), "secreto1");
    await user.click(screen.getByRole("button", { name: /ingresar/i }));

    await waitFor(() =>
      expect(toastErrorMock).toHaveBeenCalledWith("El servidor no devolvió un token de sesión.")
    );
    expect(pushMock).not.toHaveBeenCalled();
    expect(setAuthMock).not.toHaveBeenCalled();
  });

  it("ante un 401 del backend, muestra 'Email o contraseña incorrectos.'", async () => {
    postMock.mockRejectedValue({ response: { status: 401 } });
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText("Correo electrónico"), "mateo@test.com");
    await user.type(screen.getByPlaceholderText("Contraseña"), "secreto1");
    await user.click(screen.getByRole("button", { name: /ingresar/i }));

    await waitFor(() =>
      expect(toastErrorMock).toHaveBeenCalledWith("Email o contraseña incorrectos.")
    );
  });

  it("ante otro error del backend, muestra el mensaje que devuelve el servidor", async () => {
    postMock.mockRejectedValue({ response: { status: 429, data: { message: "Demasiados intentos." } } });
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByPlaceholderText("Correo electrónico"), "mateo@test.com");
    await user.type(screen.getByPlaceholderText("Contraseña"), "secreto1");
    await user.click(screen.getByRole("button", { name: /ingresar/i }));

    await waitFor(() => expect(toastErrorMock).toHaveBeenCalledWith("Demasiados intentos."));
  });
});
