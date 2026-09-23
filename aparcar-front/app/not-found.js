import Link from "next/link";

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-bg px-4 text-center">
      <h1 className="text-4xl font-extrabold tracking-tight text-ink">Página no encontrada</h1>
      <p className="mt-4 text-ink/60">La ruta a la que intentaste acceder no existe.</p>
      <Link
        href="/"
        className="ui-primary mt-8 px-6 py-3 text-sm font-semibold transition-all"
      >
        Ir al inicio
      </Link>
    </div>
  );
}