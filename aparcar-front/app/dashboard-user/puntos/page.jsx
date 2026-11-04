import Link from "next/link";

import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import BilleteraPuntos from "@/components/BilleteraPuntos";

/** Los puntos del visitante: saldo, racha, historial y cómo se suman. */
export default async function PuntosUserPage() {
  await requireAuth(["USER"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader visitor>
          <Link href="/dashboard-user" className="dashboard-back-link">
            ← Volver al panel
          </Link>
        </DashboardHeader>

        <h1 className="puntos-titulo">Mis puntos</h1>
        <BilleteraPuntos />
      </div>
    </div>
  );
}
