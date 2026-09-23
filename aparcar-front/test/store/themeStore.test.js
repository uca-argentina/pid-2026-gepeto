import { describe, it, expect, beforeEach } from "vitest";
import { useThemeStore } from "@/store/themeStore";

describe("themeStore", () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.classList.remove("dark");
    useThemeStore.setState({ tema: "light", isHydrated: false });
  });

  it("hidratar arranca en light si no hay nada guardado", () => {
    useThemeStore.getState().hidratar();

    const { tema, isHydrated } = useThemeStore.getState();
    expect(tema).toBe("light");
    expect(isHydrated).toBe(true);
    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });

  it("hidratar restaura el tema guardado en localStorage", () => {
    window.localStorage.setItem("aparcar-theme", "dark");

    useThemeStore.getState().hidratar();

    expect(useThemeStore.getState().tema).toBe("dark");
    expect(document.documentElement.classList.contains("dark")).toBe(true);
  });

  it("alternar cambia de light a dark y persiste la preferencia", () => {
    useThemeStore.getState().hidratar();

    useThemeStore.getState().alternar();

    expect(useThemeStore.getState().tema).toBe("dark");
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(window.localStorage.getItem("aparcar-theme")).toBe("dark");
  });

  it("alternar dos veces vuelve a light", () => {
    useThemeStore.getState().hidratar();

    useThemeStore.getState().alternar();
    useThemeStore.getState().alternar();

    expect(useThemeStore.getState().tema).toBe("light");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
    expect(window.localStorage.getItem("aparcar-theme")).toBe("light");
  });
});