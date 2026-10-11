import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { renderToString } from "react-dom/server";
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
    expect(trigger).not.toHaveAttribute("aria-controls");
    await user.click(trigger);
    expect(screen.getByRole("link", { name: /mis datos/i })).toHaveAttribute("href", href);
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    expect(document.getElementById(trigger.getAttribute("aria-controls"))).toContainElement(screen.getByRole("link", { name: /mis datos/i }));
  });

  it("hidrata el menú cerrado sin referencias a un panel inexistente", async () => {
    const errores = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      const container = document.createElement("div");
      container.innerHTML = renderToString(<AccountMenu />);
      document.body.appendChild(container);
      const trigger = container.querySelector("button");
      expect(trigger).toHaveAttribute("aria-expanded", "false");
      expect(trigger).not.toHaveAttribute("aria-controls");
      render(<AccountMenu />, { container, hydrate: true });
      const user = userEvent.setup();
      await user.click(trigger);
      const id = trigger.getAttribute("aria-controls");
      expect(id).toBeTruthy();
      expect(document.getElementById(id)).toContainElement(screen.getByRole("link", { name: /mis datos/i }));
      await user.keyboard("{Escape}");
      expect(trigger).not.toHaveAttribute("aria-controls");
      expect(document.getElementById(id)).not.toBeInTheDocument();
      expect(errores).not.toHaveBeenCalled();
    } finally {
      errores.mockRestore();
    }
  });

  it("mantiene ids propios para distintas instancias del menú", async () => {
    const user = userEvent.setup();
    render(<><AccountMenu /><AccountMenu visitor /></>);
    const [admin, visitante] = screen.getAllByRole("button", { name: "Mi cuenta" });
    await user.click(admin);
    const idAdmin = admin.getAttribute("aria-controls");
    await user.click(visitante);
    const idVisitante = visitante.getAttribute("aria-controls");
    expect(idAdmin).toBeTruthy();
    expect(idVisitante).toBeTruthy();
    expect(idVisitante).not.toBe(idAdmin);
    expect(document.getElementById(idVisitante)).toContainElement(screen.getByRole("link", { name: /mis datos/i }));
    expect(admin).not.toHaveAttribute("aria-controls");
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
