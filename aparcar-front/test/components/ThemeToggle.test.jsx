import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ThemeToggle from "@/components/ThemeToggle";
import { useThemeStore } from "@/store/themeStore";

describe("ThemeToggle", () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.classList.remove("dark");
    useThemeStore.setState({ tema: "light", isHydrated: false });
  });

  it("se hidrata al montar y muestra el ícono de luna en modo claro", async () => {
    render(<ThemeToggle />);

    await waitFor(() => expect(screen.getByRole("button")).toBeInTheDocument());
    expect(screen.getByRole("button")).toHaveAccessibleName(/cambiar a modo oscuro/i);
  });

  it("al hacer click, alterna a oscuro y aplica la clase al <html>", async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);

    const boton = await screen.findByRole("button");
    await user.click(boton);

    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(screen.getByRole("button")).toHaveAccessibleName(/cambiar a modo claro/i);
  });
});