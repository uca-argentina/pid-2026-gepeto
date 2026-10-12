import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import DashboardIcon from "@/components/DashboardIcon";
import ReservasContent from "@/components/ReservasContent";

/**
 * Pantalla de entrada del visitante: lo primero que ve es el alta de reservas
 * (con su listado). Los vehículos se gestionan desde "Mis datos"; el acceso
 * directo del encabezado lleva a esa sección sin pasar por el menú de cuenta.
 */
export default async function DashboardUserPage() {
  await requireAuth(["USER"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader visitor>
          <Link href="/dashboard-user/perfil#mis-vehiculos" className="dashboard-nav-link">
            <DashboardIcon name="car" />
            <span>Mis vehículos</span>
          </Link>
        </DashboardHeader>
        <ReservasContent modo="user" />
      </div>
    </div>
  );
}