"use client";

/**
 * Los puntitos: uno por falta permitida, rojos los ya usados.
 *
 * Son tantos como el máximo configurado, no siempre tres: si el administrador
 * sube el límite a cinco, aparecen cinco. Un número fijo mentiría sobre cuánto
 * margen queda.
 *
 * Cuando hay más faltas que puntitos —el caso en que la cuenta se bloquea, que
 * ocurre justamente al superar el máximo— se agrega un punto extra. Sin eso, la
 * cuarta falta sobre un máximo de tres se vería igual que la tercera, que es
 * exactamente el momento en que algo cambió.
 *
 * El dibujo es decorativo: el dato va en el texto de al lado y en el
 * `aria-label`, para que no dependa de ver colores.
 */
export default function PuntosFaltas({ cantidad = 0, maximo = 3 }) {
  const total = Math.max(maximo, cantidad);
  const puntos = Array.from({ length: total }, (_, i) => i < cantidad);

  return (
    <p
      className="falta-puntos"
      role="img"
      aria-label={`${cantidad} de ${maximo} faltas permitidas`}
    >
      {puntos.map((usado, i) => (
        <span
          key={i}
          aria-hidden="true"
          className={[
            "falta-punto",
            usado ? "falta-punto-usado" : "",
            // El que se pasa del máximo se marca aparte: no es "una falta más",
            // es la que bloqueó la cuenta.
            i >= maximo ? "falta-punto-excedido" : "",
          ].filter(Boolean).join(" ")}
        />
      ))}
    </p>
  );
}
