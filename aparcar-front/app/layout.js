import Script from "next/script";

import { Geist, Geist_Mono } from "next/font/google";
import { Toaster } from "sonner";
import ThemeToggle from "@/components/ThemeToggle";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata = {
  title: "AparcAR",
  description: "Sistema de reserva de cocheras — Proyecto Integral de Desarrollo",
};

export default function RootLayout({ children }) {
  return (
    <html lang="es">
      <body className={`${geistSans.variable} ${geistMono.variable} antialiased`}>
        {/*
          Define window.__ENV antes de que hidrate React, que es lo que lee
          utils/env.js para saber la URL del backend.

          Lo genera scripts/entrypoint.sh al arrancar el contenedor. Gracias a
          eso la misma imagen sirve en cualquier entorno: para apuntar a otro
          backend se cambia una variable y se reinicia, sin recompilar.
        */}
        <Script src="/env-config.js" strategy="beforeInteractive" />
        <div className="theme-switcher">
          <ThemeToggle />
        </div>
        {children}
        <Toaster position="top-right" richColors theme="dark" />
      </body>
    </html>
  );
}
