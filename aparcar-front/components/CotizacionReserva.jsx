import { detalleCotizacion, formatearPrecio, TIPOS_TARIFA } from "@/utils/tarifas";

export default function CotizacionReserva({ cotizacion }) {
  const { datos, cargando, error, recargar } = cotizacion;
  return (
    <div className="rounded-xl border border-accent/25 bg-accent/5 p-4" aria-live="polite" aria-atomic="true">
      <p className="text-sm font-semibold text-ink">Precio a pagar</p>
      {cargando ? <p className="mt-1 text-sm text-ink/60">Calculando precio…</p> : error ? (
        <div>
          <p role="alert" className="mt-1 text-sm text-red-600 dark:text-red-300">{error}</p>
          <button type="button" onClick={recargar} className="mt-2 text-sm font-semibold text-link underline">Reintentar cotización</button>
        </div>
      ) : datos ? (
        <>
          <p className="mt-1 text-2xl font-extrabold text-ink">{formatearPrecio(datos.total)} <span className="text-xs font-medium text-ink/60">ARS</span></p>
          <p className="mt-1 text-xs text-ink/60">Tarifa {TIPOS_TARIFA.find((t) => t.tipo === datos.tipo)?.nombre ?? datos.tipo} · {detalleCotizacion(datos)}</p>
        </>
      ) : <p className="mt-1 text-sm text-ink/60">Elegí un vehículo y una franja válida para ver el precio.</p>}
    </div>
  );
}
