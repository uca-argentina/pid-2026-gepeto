import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import LogoutButton from "@/components/LogoutButton";
import VisitantesContent from "../VisitantesContent";
import DashboardHeader from "@/components/DashboardHeader";

/**
 * El alta de visitante, que antes era lo que abría el dashboard.
 *
 * Pasó a pantalla propia cuando el panel principal se volvió la ocupación de
 * cocheras: sigue siendo la acción del día a día en portería, pero se llega
 * desde el botón "Nuevo visitante" del encabezado.
 */
export default async function VisitantesAdminPage() {
  await requireAuth(["ADMIN"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader actions={<LogoutButton />}>
          <Link href="/dashboard-admin" className="dashboard-back-link">
            ← Volver al panel
          </Link>
        </DashboardHeader>

        <VisitantesContent />
      </div>
    </div>
  );
}
