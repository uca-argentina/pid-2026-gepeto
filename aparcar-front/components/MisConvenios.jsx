"use client";

import DashboardIcon from "@/components/DashboardIcon";
import ConveniosCredenciales from "@/components/ConveniosCredenciales";
import useConvenios from "@/hooks/useConvenios";

export default function MisConvenios({ documento }) {
  const convenios = useConvenios({ guardar: true, revision: documento });
  return (
    <section id="mis-convenios" aria-labelledby="mis-convenios-titulo" className="ui-card profile-form">
      <div className="profile-section-heading">
        <span className="profile-section-icon"><DashboardIcon name="convenios" /></span>
        <div><h2 id="mis-convenios-titulo">Mis convenios</h2><p>Verificá y guardá tus credenciales para usarlas en tus próximas reservas.</p></div>
      </div>
      <ConveniosCredenciales modelo={convenios} guardar />
    </section>
  );
}
