export default function AvisoListaEspera({ id, registro, tipoCochera }) {
  return (
    <div className="mt-3 rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-ink">
      <p className="font-semibold">No hay cocheras disponibles para esa franja.</p>
      <p className="mt-1 text-ink/70">
        Podés sumarte a la lista de espera. La reserva quedará pendiente, sin cochera ni código
        de acceso. Si se libera una cochera de tipo {tipoCochera?.toLowerCase()} mientras la franja siga vigente, se confirmará
        automáticamente y el titular recibirá una notificación en su cuenta.
      </p>
      <label htmlFor={id} className="mt-3 flex cursor-pointer items-center gap-2">
        <input id={id} type="checkbox" {...registro} className="h-4 w-4 rounded border-ink/20 accent-accent" />
        Acepto ingresar a la lista de espera
      </label>
    </div>
  );
}
