import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useAuthStore } from "../store/authStore";
import Home from "../app/page.jsx";

const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mockReplace,
  }),
}));

describe("Home (landing)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ isAuthenticated: false, user: null, isHydrated: true });
  });

  it("muestra la landing con el boton de iniciar sesion cuando no hay sesion", () => {
    render(<Home />);

    expect(screen.getByText("AparcAR")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /iniciar sesión/i }).length).toBeGreaterThan(0);
  });

  it("con rol ADMIN autenticado, redirige a /dashboard-admin", () => {
    useAuthStore.setState({ isAuthenticated: true, user: { roles: ["ADMIN"] }, isHydrated: true });

    render(<Home />);

    expect(mockReplace).toHaveBeenCalledWith("/dashboard-admin");
  });

  it("con rol USER autenticado, redirige a /dashboard-user", () => {
    useAuthStore.setState({ isAuthenticated: true, user: { roles: ["USER"] }, isHydrated: true });

    render(<Home />);

    expect(mockReplace).toHaveBeenCalledWith("/dashboard-user");
  });

  it("el menu hamburguesa se abre y cierra en mobile", async () => {
    const user = userEvent.setup();
    render(<Home />);

    const toggle = screen.getByLabelText(/abrir menú/i);
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
  });
});