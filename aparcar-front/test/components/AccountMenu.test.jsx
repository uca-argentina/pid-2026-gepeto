import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AccountMenu from "@/components/AccountMenu";
import { useAuthStore } from "@/store/authStore";

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

beforeEach(() => vi.clearAllMocks());
describe("Mi cuenta", () => {
  it.each([[true, "/dashboard-user/perfil"], [false, "/dashboard-admin/perfil"]])("muestra el perfil correspondiente a visitor=%s", async (visitor, href) => {
    const user = userEvent.setup();
    render(<AccountMenu visitor={visitor} />);
    const trigger = screen.getByRole("button", { name: "Mi cuenta" });
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    await user.click(trigger);
    expect(screen.getByRole("link", { name: /mis datos/i })).toHaveAttribute("href", href);
    expect(trigger).toHaveAttribute("aria-expanded", "true");
  });

  it("abre con teclado y cierra con Escape devolviendo el foco", async () => {
    const user = userEvent.setup();
    render(<AccountMenu />);
    await user.tab();
    await user.keyboard("{Enter}");
    await user.tab();
    expect(screen.getByRole("link", { name: /mis datos/i })).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mi cuenta" })).toHaveFocus();
  });

  it("cierra al hacer clic fuera y al salir con Tab", async () => {
    const user = userEvent.setup();
    render(<><AccountMenu /><button>Otro control</button></>);
    const trigger = screen.getByRole("button", { name: "Mi cuenta" });
    await user.click(trigger);
    await user.click(screen.getByRole("button", { name: "Otro control" }));
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    await user.click(trigger);
    await user.tab();
    await user.tab();
    await user.tab();
    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  it("reutiliza el cierre de sesión, borrando la cookie y el estado", async () => {
    document.cookie = "JWT=token; path=/";
    useAuthStore.setState({ isAuthenticated: true, user: { roles: ["ADMIN"] } });
    const user = userEvent.setup();
    render(<AccountMenu />);
    await user.click(screen.getByRole("button", { name: "Mi cuenta" }));
    await user.click(screen.getByRole("button", { name: "Cerrar sesión" }));
    expect(document.cookie).not.toContain("JWT=");
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(replace).toHaveBeenCalledWith("/login");
  });
});
