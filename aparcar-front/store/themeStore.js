import { create } from "zustand";

const STORAGE_KEY = "aparcar-theme";

function aplicarClase(tema) {
  if (typeof document === "undefined") return;
  document.documentElement.classList.toggle("dark", tema === "dark");
}

export const useThemeStore = create((set, get) => ({
  tema: "light",
  isHydrated: false,

  hidratar: () => {
    if (typeof window === "undefined") return;
    const guardado = window.localStorage.getItem(STORAGE_KEY);
    const tema = guardado === "dark" ? "dark" : "light";
    aplicarClase(tema);
    set({ tema, isHydrated: true });
  },

  alternar: () => {
    const nuevo = get().tema === "dark" ? "light" : "dark";
    aplicarClase(nuevo);
    window.localStorage.setItem(STORAGE_KEY, nuevo);
    set({ tema: nuevo });
  },
}));