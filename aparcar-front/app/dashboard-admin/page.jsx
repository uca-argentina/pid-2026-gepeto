import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import PanelOperativo from "./PanelOperativo";
import DashboardHeader from "@/components/DashboardHeader";
import DashboardIcon from "@/components/DashboardIcon";
import ParkingHeading from "@/components/ParkingHeading";

export default async function DashboardAdminPage() {
  await requireAuth(["ADMIN"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader>
          <Link href="/dashboard-admin/dashboard" className="dashboard-nav-link">
            <DashboardIcon name="dashboard" />
            <span>Dashboard</span>
          </Link>
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
          <Link href="/dashboard-admin/tarifas" className="dashboard-nav-link">
            <DashboardIcon name="tarifas" />
            <span>Gestionar tarifas</span>
          </Link>
        </DashboardHeader>

        <ParkingHeading />
        <PanelOperativo />
      </div>
    </div>
  );
}
