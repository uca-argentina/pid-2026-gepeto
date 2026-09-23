import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { post, replace, success, error } = vi.hoisted(() => ({
  post: vi.fn(), replace: vi.fn(), success: vi.fn(), error: vi.fn(),
}));
vi.mock("@/app/api", () => ({ default: { post } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("sonner", () => ({ toast: { success, error } }));

import RegisterPage from "@/app/register/page";

function fill(values = {}) {
  const data = { nombre: " Ana Pérez ", documento: " 30111222 ", email: "Ana@Ejemplo.com", telefono: "", password: "Contraseña123🔑", confirmPassword: "Contraseña123🔑", ...values };
  for (const [name, value] of Object.entries(data)) {
    fireEvent.change(document.getElementById(name), { target: { value } });
  }
}

describe("Registro de visitantes", () => {
  beforeEach(() => vi.resetAllMocks());

  it("crea solo la cuenta con datos normalizados y permite iniciar sesión", async () => {
    post.mockResolvedValue({ status: 201 });
    render(<RegisterPage />);
    fill();
    fireEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
    expect(post).toHaveBeenCalledExactlyOnceWith("/register", {
      nombre: "Ana Pérez", documento: "30111222", email: "ana@ejemplo.com",
      telefono: undefined, password: "Contraseña123🔑",
    });
    expect(success).toHaveBeenCalled();
    expect(screen.getByRole("link", { name: "Iniciá sesión" })).toHaveAttribute("href", "/login");
  });

  it.each([
    [{ nombre: "   " }, "Ingresá tu nombre completo"],
    [{ documento: "   " }, "Ingresá tu DNI o documento"],
    [{ email: "invalido" }, "Ingresá un correo válido"],
    [{ password: "123" }, "La contraseña debe tener al menos 8 caracteres"],
    [{ password: "        ", confirmPassword: "        " }, "La contraseña no puede contener solo espacios"],
    [{ password: "á".repeat(37) }, "La contraseña es demasiado larga (máximo 72 bytes UTF-8)"],
    [{ confirmPassword: "otra-clave" }, "Las contraseñas no coinciden"],
  ])("rechaza datos inválidos: %j", async (data, message) => {
    render(<RegisterPage />);
    fill(data);
    fireEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));
    expect(await screen.findByText(message)).toBeVisible();
    expect(post).not.toHaveBeenCalled();
  });

  it.each([
    [400, "Ya existe una cuenta asociada a ese email."],
    [400, "Ya existe un visitante con ese documento."],
    [429, "Demasiados intentos. Esperá un minuto antes de volver a intentar."],
    [undefined, "No pudimos crear tu cuenta. Volvé a intentar."],
  ])("muestra el error %s sin redirigir ni perder datos", async (status, message) => {
    post.mockRejectedValue(status ? { response: { status, data: { message } } } : new Error("Network Error"));
    render(<RegisterPage />);
    fill();
    fireEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(error).toHaveBeenCalledWith(message);
    expect(replace).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Correo electrónico")).toHaveValue("Ana@Ejemplo.com");
    expect(screen.getByRole("button", { name: "Crear cuenta" })).toBeEnabled();
  });

  it("bloquea envíos repetidos mientras se crea la cuenta", async () => {
    let resolve;
    post.mockReturnValue(new Promise((done) => { resolve = done; }));
    render(<RegisterPage />);
    fill();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Crear cuenta" }));
    const button = await screen.findByRole("button", { name: "Creando tu cuenta..." });
    expect(button).toBeDisabled();
    expect(screen.getByLabelText("Nombre completo")).toBeDisabled();
    await user.click(button);
    expect(post).toHaveBeenCalledTimes(1);
    resolve({ status: 201 });
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });

  it("permite mostrar y ocultar las contraseñas", async () => {
    render(<RegisterPage />);
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Mostrar contraseñas" }));
    expect(screen.getByLabelText("Contraseña", { exact: true })).toHaveAttribute("type", "text");
    await user.click(screen.getByRole("button", { name: "Ocultar contraseñas" }));
    expect(screen.getByLabelText("Contraseña", { exact: true })).toHaveAttribute("type", "password");
  });
});
