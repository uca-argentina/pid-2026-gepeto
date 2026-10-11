const NEUTRO = "bg-ink/5 text-ink/50 ring-1 ring-inset ring-ink/10";

// Los colores van con su variante oscura: el verde y el ámbar claros de modo
// claro no alcanzan contraste sobre la superficie oscura.
const ESTILOS = {
  CONFIRMADA: "bg-accent/10 text-link ring-1 ring-inset ring-accent/30",
  EN_CURSO: "bg-emerald-500/10 text-emerald-800 ring-1 ring-inset ring-emerald-600/30 dark:text-emerald-300 dark:ring-emerald-400/40",
  VENCIDA: "bg-amber-500/10 text-amber-800 ring-1 ring-inset ring-amber-600/30 dark:text-amber-300 dark:ring-amber-400/40",
};

const ETIQUETAS = {
  EN_CURSO: "EN CURSO",
};

export default function EstadoReservaBadge({ estado, motivoCancelacion, esAdmin = false }) {
  const motivos = {
    USUARIO: esAdmin ? "CANCELADA POR USUARIO" : "CANCELASTE ESTA RESERVA",
    ADMINISTRACION: "CANCELADA POR ADMINISTRACIÓN",
    DESHABILITACION: "DESHABILITADA POR ADMINISTRACIÓN",
  };
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${ESTILOS[estado] ?? NEUTRO}`}
    >
      {estado === "CANCELADA" ? motivos[motivoCancelacion] ?? estado : ETIQUETAS[estado] ?? estado}
    </span>
  );
}
