/** Ilustración decorativa: no representa disponibilidad ni reservas reales. */
export default function ParkingIllustration() {
  return (
    <div className="parking-illustration" aria-hidden="true">
      <div className="parking-caption"><span className="parking-symbol">P</span><span>UN LUGAR PARA VOS</span><span>↗</span></div>
      <div className="parking-plan">
        <div className="parking-row">
          {["01", "02", "03", "04"].map((space, index) => (
            <div key={space} className={`parking-space${index === 1 ? " parking-space-selected" : ""}`}>
              <span>{space}</span>
              {index === 1 ? <span className="parking-check">✓</span> : index !== 3 && <div className="parked-car" />}
            </div>
          ))}
        </div>
        <div className="parking-lane"><span>ENTRADA</span><span>→</span><i /><span>→</span></div>
        <div className="parking-row parking-row-bottom">
          {["05", "06", "07", "08"].map((space, index) => (
            <div key={space} className="parking-space"><span>{space}</span>{index !== 1 && <div className="parked-car" />}</div>
          ))}
        </div>
      </div>
      <div className="parking-note"><span className="parking-note-icon">✓</span><div><strong>Menos vueltas.</strong><span>Más tranquilidad al llegar.</span></div></div>
      <div className="parking-footnote"><span>Estacionar, simplificado.</span><span>APARCAR / 01</span></div>
    </div>
  );
}
