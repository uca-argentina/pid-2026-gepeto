import Link from "next/link";

import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import ReservasContent from "@/components/ReservasContent";

/**
 * El listado de reservas del visitante, en pantalla propia.
 *
 * Antes vivía debajo del formulario de alta, en el panel: para mirar lo que ya
 * tenía reservado había que pasar de largo todo el formulario. Separarlas deja
 * cada pantalla con una sola tarea —el panel para reservar, esta para
 * consultar— y el acceso queda en el encabezado, a un clic desde cualquiera de
 * las dos.
 */
export default async function ReservasUserPage() {
  await requireAuth(["USER"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader visitor>
          <Link href="/dashboard-user" className="dashboard-back-link">
            ← Volver al panel
          </Link>
        </DashboardHeader>

        <ReservasContent modo="user" mostrar="listado" />
      </div>
    </div>
  );
}
