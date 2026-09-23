import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import LogoutButton from "@/components/LogoutButton";
import { useAuthStore } from "@/store/authStore";

const { replaceMock } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

describe("LogoutButton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    document.cookie = "JWT=token-de-prueba; path=/;";
    useAuthStore.setState({
      isAuthenticated: true,
      user: { email: "usuario@aparcar.com", roles: ["USER"] },
    });
  });

  it("cierra la sesión y navega a /login", async () => {
    const user = userEvent.setup();
    render(<LogoutButton />);

    await user.click(screen.getByRole("button", { name: "Cerrar sesión" }));

    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().user).toBeNull();
    expect(document.cookie).not.toContain("JWT=");
    expect(replaceMock).toHaveBeenCalledWith("/login");
  });
});
