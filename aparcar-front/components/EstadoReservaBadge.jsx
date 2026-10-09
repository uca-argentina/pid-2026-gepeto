export default function EstadoReservaBadge({ estado, motivoCancelacion, esAdmin = false }) {
  const isConfirmada = estado === "CONFIRMADA";
  const motivos = {
    USUARIO: esAdmin ? "CANCELADA POR USUARIO" : "CANCELASTE ESTA RESERVA",
    ADMINISTRACION: "CANCELADA POR ADMINISTRACIÓN",
    DESHABILITACION: "DESHABILITADA POR ADMINISTRACIÓN",
  };
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
        isConfirmada
          ? "bg-accent/10 text-link ring-1 ring-inset ring-accent/30"
          : "bg-ink/5 text-ink/50 ring-1 ring-inset ring-ink/10"
      }`}
    >
      {estado === "CANCELADA" ? motivos[motivoCancelacion] ?? estado : estado}
    </span>
  );
}
