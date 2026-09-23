import Brand from "@/components/Brand";
import ParkingIllustration from "@/components/ParkingIllustration";

export default function AuthLayout({ children, wide = false }) {
  return (
    <main className={`auth-layout${wide ? " auth-layout-wide" : ""}`}>
      <aside className="auth-story">
        <Brand inverse />
        <div className="auth-story-content">
          <p className="eyebrow">TU LUGAR, ANTES DE LLEGAR</p>
          <h2>Estacionar puede<br />ser más simple.</h2>
          <p>Tus vehículos, tus reservas y la tranquilidad de tener un lugar. Todo en un mismo espacio.</p>
          <ParkingIllustration />
        </div>
        <p className="auth-story-footer">AparcAR · Gestión de estacionamientos</p>
      </aside>
      <div className="auth-content">
        <div className="auth-mobile-brand"><Brand /></div>
        {children}
        <p className="auth-footer">AparcAR · Proyecto Integral de Desarrollo, UCAio.</p>
      </div>
    </main>
  );
}
