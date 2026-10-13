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
          {/*
            Va primero porque el listado es lo que más se consulta de esta
            pantalla y queda debajo del formulario: sin este atajo hay que
            desplazarse hasta el final para ver las reservas propias.
            El desplazamiento suave y el margen bajo el encabezado ya salen de
            `scroll-behavior` y `scroll-padding-top`, definidos en globals.css.
          */}
          <Link href="#mis-reservas" className="dashboard-nav-link">
            <DashboardIcon name="calendar" />
            <span>Mis reservas</span>
          </Link>
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