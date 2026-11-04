import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import ProfileSettings from "@/components/ProfileSettings";
import MiPerfilContent from "../MiPerfilContent";
import MisAmonestaciones from "@/components/MisAmonestaciones";

export default async function PerfilUserPage() {
  await requireAuth(["USER"]);
  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader visitor>
          <Link href="/dashboard-user" className="dashboard-back-link">← Volver al panel</Link>
        </DashboardHeader>
        {/* La gestión de vehículos es parte del perfil del visitante: va
            dentro de "Mis datos", después de los datos personales. */}
        <ProfileSettings>
          <MiPerfilContent />
          {/* Al final del perfil, como se pidió: las faltas vigentes y el
              historial de incidencias, que es permanente. */}
          <MisAmonestaciones />
        </ProfileSettings>
      </div>
    </div>
  );
}