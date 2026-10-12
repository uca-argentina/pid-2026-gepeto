import { detectarFormatoPatente, normalizarPatente } from "@/utils/patenteValidation";

/**
 * Cómo se reparte cada formato en la chapa. Las de auto van en una sola línea
 * con un espacio entre bloques; las de moto son casi cuadradas y van en dos
 * renglones, igual que en las chapas reales (Mercosur: "A12" / "3BCD";
 * anterior: "123" / "ABC").
 */
const DISENOS = {
  AUTO_MERCOSUR: { clase: "auto-mercosur", renglones: (p) => [`${p.slice(0, 2)} ${p.slice(2, 5)} ${p.slice(5)}`] },
  AUTO_ANTERIOR: { clase: "auto-anterior", renglones: (p) => [`${p.slice(0, 3)} ${p.slice(3)}`] },
  MOTO_MERCOSUR: { clase: "moto-mercosur", renglones: (p) => [p.slice(0, 3), p.slice(3)] },
  MOTO_ANTERIOR: { clase: "moto-anterior", renglones: (p) => [p.slice(0, 3), p.slice(3)] },
};

function BandaMercosur() {
  return (
    <span className="patente-banda">
      <span className="patente-mercosur">MERCOSUR</span>
      <span className="patente-pais">REPÚBLICA ARGENTINA</span>
      <span className="patente-bandera" />
    </span>
  );
}

/**
 * Representación gráfica de una patente argentina. Elige el diseño (Mercosur
 * o anterior, auto o moto) a partir del formato, con las mismas reglas que
 * valida el alta. Si la patente no coincide con ningún formato conocido, la
 * muestra igual en una chapa neutra, con la forma que corresponde al tipo.
 *
 * Para lectores de pantalla (y los tests) es una sola imagen con la patente
 * compacta como nombre: "Patente AB123CD".
 */
export default function PatenteVisual({ patente, tipo, tamano = "md", className = "" }) {
  const compacta = normalizarPatente(patente);
  const formato = detectarFormatoPatente(compacta);
  const diseno = DISENOS[formato];

  const clase = diseno ? diseno.clase : `generica ${tipo === "MOTO" ? "patente--moto" : ""}`;
  const renglones = diseno ? diseno.renglones(compacta) : [compacta || "—"];
  const esMercosur = formato === "AUTO_MERCOSUR" || formato === "MOTO_MERCOSUR";
  const esAnterior = formato === "AUTO_ANTERIOR" || formato === "MOTO_ANTERIOR";

  return (
    <span
      role="img"
      aria-label={`Patente ${compacta}`}
      title={compacta}
      data-formato={diseno ? diseno.clase : "generica"}
      className={`patente patente--${clase} patente--${tamano} ${className}`.replace(/\s+/g, " ").trim()}
    >
      {esMercosur && <BandaMercosur />}
      {esAnterior && <span className="patente-argentina">ARGENTINA</span>}
      <span className="patente-caracteres">
        {renglones.map((renglon) => (
          <span key={renglon} className="patente-renglon">{renglon}</span>
        ))}
      </span>
    </span>
  );
}