import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import LogoutButton from "@/components/LogoutButton";
import PanelOperativo from "./PanelOperativo";
import DashboardHeader from "@/components/DashboardHeader";
import DashboardIcon from "@/components/DashboardIcon";

export default async function DashboardAdminPage() {
  await requireAuth(["ADMIN"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader actions={<LogoutButton />}>
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
          <Link
            href="/dashboard-admin/usuarios"
            className="dashboard-nav-link"
          >
            <DashboardIcon name="users" />
            <span>Gestionar usuarios</span>
          </Link>
        </DashboardHeader>

        <PanelOperativo />
      </div>
    </div>
  );
}
