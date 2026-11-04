import Link from "next/link";

import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import SancionesConfig from "./SancionesConfig";

/**
 * Los números del régimen de faltas.
 *
 * Solo ADMIN: son configuración del sistema, no operación. El empleado aplica
 * faltas pero no decide cuántas hacen falta para bloquear una cuenta ni cuánto
 * dura el bloqueo.
 */
export default async function SancionesPage() {
  await requireAuth(["ADMIN"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader>
          <Link href="/dashboard-admin" className="dashboard-back-link">
            ← Volver al panel
          </Link>
        </DashboardHeader>

        <SancionesConfig />
      </div>
    </div>
  );
}
