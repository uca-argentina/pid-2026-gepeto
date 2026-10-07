import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import ProfileSettings from "@/components/ProfileSettings";
import MiPerfilContent from "../MiPerfilContent";

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
        </ProfileSettings>
      </div>
    </div>
  );
}