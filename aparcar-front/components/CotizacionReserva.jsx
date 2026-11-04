import { detalleCotizacion, formatearPrecio, TIPOS_TARIFA } from "@/utils/tarifas";
import { formatearPorcentaje, PUNTOS_MAXIMOS_FRANJA } from "@/utils/puntos";

// `mostrarPuntos`: solo cuando reserva el propio visitante; al reservar para
// otro, lo que sumaría es de esa otra cuenta.
export default function CotizacionReserva({ cotizacion, mostrarPuntos = false, puntosSolicitados = false }) {
  const { datos, cargando, error, recargar } = cotizacion;
  const conDescuento = datos?.puntosAplicados > 0;
  const convenio = datos?.convenio;
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
          {(conDescuento || convenio) && (
            <dl className="cotizacion-descuento">
              <div><dt>Subtotal</dt><dd>{formatearPrecio(datos.subtotal)}</dd></div>
              {convenio && <div>
                <dt>Convenio {convenio.nombre} ({formatearPorcentaje(convenio.descuentoPct)})</dt>
                <dd>−{formatearPrecio(convenio.descuentoMonto)}</dd>
              </div>}
              {conDescuento && <div>
                <dt>Descuento por {datos.puntosAplicados} puntos ({formatearPorcentaje(datos.descuentoPct)})</dt>
                <dd>−{formatearPrecio(datos.descuento)}</dd>
              </div>}
            </dl>
          )}
          {convenio && puntosSolicitados && <p className="mt-2 text-xs text-ink/60">
            El convenio ofrece un descuento igual o mayor. Conservás tus puntos.
          </p>}
          <p className="mt-1 text-2xl font-extrabold text-ink">{formatearPrecio(datos.total)} <span className="text-xs font-medium text-ink/60">ARS</span></p>
          <p className="mt-1 text-xs text-ink/60">Tarifa {TIPOS_TARIFA.find((t) => t.tipo === datos.tipo)?.nombre ?? datos.tipo} · {detalleCotizacion(datos)}</p>
          {mostrarPuntos && datos.puntosAGanar > 0 && (
            <p className={`cotizacion-ganas ${datos.puntosAGanar >= PUNTOS_MAXIMOS_FRANJA ? "is-destacado" : ""}`}>
              Ganás <strong>{datos.puntosAGanar} puntos</strong> al ingresar
              {datos.puntosAGanar >= PUNTOS_MAXIMOS_FRANJA && <span> · es la franja que más suma</span>}
            </p>
          )}
          {mostrarPuntos && datos.puntosAGanar === 0 && datos.motivoSinPuntos && (
            <p className="cotizacion-ganas">{datos.motivoSinPuntos}</p>
          )}
        </>
      ) : <p className="mt-1 text-sm text-ink/60">Elegí un vehículo y una franja válida para ver el precio.</p>}
    </div>
  );
}
