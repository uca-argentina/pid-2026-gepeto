export default function Brand({ inverse = false }) {
  return (
    <span className={`brand${inverse ? " brand-inverse" : ""}`}>
      {/* El recorte por CSS conserva el logo original y elimina su margen blanco. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/Logo.jpeg" alt="" aria-hidden="true" width="1533" height="688" />
      <span className="sr-only">AparcAR</span>
    </span>
  );
}
