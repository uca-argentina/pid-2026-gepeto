import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import DashboardIcon from "@/components/DashboardIcon";
import ReservasContent from "@/components/ReservasContent";

/**
 * Pantalla de entrada del visitante: el alta de reservas. El listado de lo ya
 * reservado vive en /dashboard-user/reservas y los vehículos en "Mis datos";
 * a las dos se llega desde el encabezado, sin pasar por el menú de cuenta.
 */
export default async function DashboardUserPage() {
  await requireAuth(["USER"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader visitor>
          {/*
            Va primero porque consultar lo ya reservado es lo más frecuente.
            Lleva a su propia pantalla en vez de bajar dentro de esta: el panel
            quedó para reservar y el listado tiene su lugar, así cada una hace
            una sola cosa.
          */}
          <Link href="/dashboard-user/reservas" className="dashboard-nav-link">
            <DashboardIcon name="calendar" />
            <span>Mis reservas</span>
          </Link>
          <Link href="/dashboard-user/perfil#mis-vehiculos" className="dashboard-nav-link">
            <DashboardIcon name="car" />
            <span>Mis vehículos</span>
          </Link>
        </DashboardHeader>
        <ReservasContent modo="user" mostrar="formulario" />
      </div>
    </div>
  );
}