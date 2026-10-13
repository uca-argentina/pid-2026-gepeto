import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import PanelOperativo from "./PanelOperativo";
import DashboardHeader from "@/components/DashboardHeader";
import DashboardIcon from "@/components/DashboardIcon";
import ParkingHeading from "@/components/ParkingHeading";

/**
 * El panel de operación, compartido por el administrador y el empleado.
 *
 * Los dos ven la misma pantalla de ocupación; lo que cambia es el menú. El
 * empleado opera (reservas, accesos, alta de visitantes) pero no configura:
 * tarifas, cuentas, estado de las cocheras y series recurrentes quedan fuera.
 *
 * Ocultar los accesos es solo comodidad: cada una de esas pantallas valida el
 * rol por su cuenta y el backend rechaza igual los pedidos. Escribir la URL a
 * mano no sirve de nada.
 */
export default async function DashboardAdminPage() {
  const { roles } = await requireAuth(["ADMIN", "EMPLEADO"]);
  const esAdmin = roles.includes("ADMIN");

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader>
          {esAdmin && (
          <Link href="/dashboard-admin/dashboard" className="dashboard-nav-link">
            <DashboardIcon name="dashboard" />
            <span>Dashboard</span>
          </Link>
          )}
          <Link href="/dashboard-admin/accesos" className="dashboard-nav-link">
            <DashboardIcon name="qr" />
            <span>Control de acceso</span>
          </Link>
          <Link
            href="/dashboard-admin/visitantes"
            className="dashboard-nav-link"
          >
            <DashboardIcon name="user-plus" />
            <span>Nuevo visitante</span>
          </Link>
          <Link
            href="/dashboard-admin/cocheras"
            className="dashboard-nav-link"
          >
            <DashboardIcon name="parking" />
            <span>Gestionar cocheras</span>
          </Link>
          <Link
            href="/dashboard-admin/reservas"
            className="dashboard-nav-link"
          >
            <DashboardIcon name="calendar" />
            <span>Ver reservas</span>
          </Link>
          <Link href="/dashboard-admin/recurrentes" className="dashboard-nav-link">
            <DashboardIcon name="calendar" />
            <span>Mis reservas recurrentes</span>
          </Link>
          <Link
            href="/dashboard-admin/usuarios"
            className="dashboard-nav-link"
          >
            <DashboardIcon name="users" />
            <span>Gestionar usuarios</span>
          </Link>
          )}
          {esAdmin && (
          <Link href="/dashboard-admin/tarifas" className="dashboard-nav-link">
            <DashboardIcon name="tarifas" />
            <span>Gestionar tarifas</span>
          </Link>
          )}
        </DashboardHeader>

        <ParkingHeading />
        <PanelOperativo esAdmin={esAdmin} />
      </div>
    </div>
  );
}
