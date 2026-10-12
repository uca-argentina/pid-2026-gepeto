export const DIAS_SEMANA = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];

/** Cuenta fechas locales sin desplazarlas al convertirlas a UTC. */
export function contarRepeticiones(desde, repetirHasta, diasSemana) {
  if (!desde || !repetirHasta || repetirHasta < desde.slice(0, 10)) return 0;
  const fecha = new Date(`${desde.slice(0, 10)}T12:00:00`);
  const fin = new Date(`${repetirHasta}T12:00:00`);
  let cantidad = 0;
  // El servidor admite un horizonte máximo de un año y 90 ocurrencias.
  for (let i = 0; i <= 366 && fecha <= fin; i += 1) {
    if (diasSemana.includes(fecha.getDay() || 7)) cantidad += 1;
    fecha.setDate(fecha.getDate() + 1);
  }
  return cantidad;
}

export function limiteRepeticion(desde) {
  if (!desde) return "";
  const fecha = new Date(`${desde.slice(0, 10)}T12:00:00`);
  const mes = fecha.getMonth();
  fecha.setFullYear(fecha.getFullYear() + 1);
  if (fecha.getMonth() !== mes) fecha.setDate(0);
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, "0")}-${String(fecha.getDate()).padStart(2, "0")}`;
}

export default function RepeticionReserva({ desde, diasSemana, onDias, repetirHasta, onHasta, cantidad }) {
  return (
    <fieldset className="space-y-3">
      <legend className="ui-label">Repetición semanal</legend>
      <div className="flex flex-wrap gap-x-4 gap-y-2" role="group" aria-label="Días de la semana">
        {DIAS_SEMANA.map((dia, i) => <label key={dia} className="flex cursor-pointer items-center gap-2 text-sm text-ink">
          <input type="checkbox" checked={diasSemana.includes(i + 1)} onChange={(e) => onDias(e.target.checked
            ? [...diasSemana, i + 1].sort((a, b) => a - b) : diasSemana.filter((valor) => valor !== i + 1))} className="h-4 w-4 rounded accent-accent" />
          {dia}
        </label>)}
      </div>
      <div>
        <label htmlFor="reserva-repetir-hasta" className="ui-label">Repetir hasta (inclusive)</label>
        <input id="reserva-repetir-hasta" type="date" value={repetirHasta} min={desde?.slice(0, 10)} max={limiteRepeticion(desde)} onChange={(e) => onHasta(e.target.value)} className="ui-input" required />
      </div>
      <p className="text-sm text-ink/60">
        Se repiten el horario, la duración y la cochera en los días elegidos, a partir de Desde.
        Se confirman todas juntas si hay disponibilidad en cada fecha. Máximo 90 reservas en un año.
        Cada reserva se puede cancelar por separado.
      </p>
      {cantidad > 0 && <p className={cantidad > 90 ? "text-sm text-red-500" : "text-sm font-semibold text-link"}>
        {cantidad} reservas{cantidad > 90 ? ": reducí los días o la fecha final." : ". El importe indicado corresponde a cada reserva."}
      </p>}
      {diasSemana.length === 0 && <p className="text-sm text-ink/60">Elegí al menos un día de la semana.</p>}
      {desde && diasSemana.length > 0 && !diasSemana.includes(new Date(`${desde.slice(0, 10)}T12:00:00`).getDay() || 7)
        && <p className="text-sm text-red-500">Los días elegidos deben incluir el día de la primera reserva (Desde).</p>}
      {repetirHasta && cantidad === 0 && <p className="text-sm text-red-500">No hay fechas de los días elegidos dentro de ese período.</p>}
    </fieldset>
  );
}
