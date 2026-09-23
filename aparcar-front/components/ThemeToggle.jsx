"use client";

import { useEffect } from "react";
import { useThemeStore } from "@/store/themeStore";

export default function ThemeToggle() {
  const { tema, isHydrated, hidratar, alternar } = useThemeStore();

  useEffect(() => {
    hidratar();
  }, [hidratar]);

  if (!isHydrated) {
    // Placeholder del mismo tamaño para evitar un salto de layout mientras
    // se lee localStorage.
    return <div className="h-11 w-11" aria-hidden="true" />;
  }

  return (
    <button
      type="button"
      onClick={alternar}
      aria-label={tema === "dark" ? "Cambiar a modo claro" : "Cambiar a modo oscuro"}
      className="theme-toggle flex h-11 w-11 items-center justify-center rounded-[10px] text-ink/70 transition-colors hover:bg-ink/5"
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className="h-[18px] w-[18px]">
        {tema === "dark" ? (
          <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></>
        ) : (
          <path d="M20.5 13.2A8.5 8.5 0 0 1 10.8 3.5a8.5 8.5 0 1 0 9.7 9.7Z" />
        )}
      </svg>
    </button>
  );
}
