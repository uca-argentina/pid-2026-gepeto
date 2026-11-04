"use client";

import { useEffect, useId, useState } from "react";
import api from "@/app/api";
import { formatearPuntos, plural, TIPO_MOVIMIENTO_ETIQUETA } from "@/utils/puntos";

const dosDigitos = (n) => String(n).padStart(2, "0");

const fechaYHora = (valor) => {
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) return "";
  return `${dosDigitos(fecha.getDate())}/${dosDigitos(fecha.getMonth() + 1)}/${fecha.getFullYear()} `
    + `${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}`;
};

/**
 * La billetera de puntos del visitante: saldo, racha, historial y cómo se
 * suman. Solo lee; los puntos se ganan al ingresar y se usan al reservar.
 */
export default function BilleteraPuntos() {
  const id = useId();
  const [billetera, setBilletera] = useState(null);
  const [movimientos, setMovimientos] = useState([]);
  const [error, setError] = useState(false);
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    let vigente = true;
    const consultar = async () => {
      try {
        const [b, m] = await Promise.all([
          api.get("/api/v1/puntos/billetera"),
          api.get("/api/v1/puntos/movimientos"),
        ]);
        if (!vigente) return;
        setBilletera(b.data);
        setMovimientos(Array.isArray(m.data) ? m.data : []);
        setError(false);
      } catch {
        if (vigente) setError(true);
      }
    };
    consultar();
    return () => { vigente = false; };
  }, [intento]);

  const cargar = () => setIntento((valor) => valor + 1);

  if (error && !billetera) {
    return (
      <div className="ui-card puntos-aviso" role="alert">
        <p>No se pudieron cargar tus puntos.</p>
        <button type="button" className="profile-secondary" onClick={cargar}>Reintentar</button>
      </div>
    );
  }
  if (!billetera) return <p role="status" className="text-sm text-ink/60">Cargando tus puntos…</p>;

  const usables = Math.min(billetera.saldo, billetera.maxPuntosCanjeables);
  const descuentoDisponible = Math.floor(usables / billetera.puntosPorPorcentaje);
  // Los días ya hechos del ciclo actual: el día del bonus cuenta el ciclo completo.
  const enCiclo = billetera.rachaDias - billetera.diasParaBonus;

  return (
    <div className="puntos">
      <div className="puntos-resumen">
        <section className="ui-card puntos-tarjeta" aria-labelledby={`${id}-saldo`}>
          <h2 id={`${id}-saldo`} className="puntos-tarjeta-titulo">Tu saldo</h2>
          <p className="puntos-saldo">{billetera.saldo} <span>{billetera.saldo === 1 ? "punto" : "puntos"}</span></p>
          <p className="puntos-nota">
            {descuentoDisponible > 0
              ? `Te alcanzan para hasta un ${descuentoDisponible} % de descuento en tu próxima reserva.`
              : `Con ${billetera.puntosPorPorcentaje * 10} puntos ya tenés un 10 % de descuento.`}
          </p>
        </section>

        <section className="ui-card puntos-tarjeta" aria-labelledby={`${id}-racha`}>
          <h2 id={`${id}-racha`} className="puntos-tarjeta-titulo">Racha</h2>
          <p className={`puntos-racha ${billetera.rachaActual > 0 ? "is-activa" : ""}`}>
            <svg aria-hidden="true" viewBox="0 0 24 24" className="puntos-llama">
              <path d="M12 2c1 3.5 5 6 5 11a5 5 0 0 1-10 0c0-2.4 1.2-4 2.5-5.3C9.7 10 11 11 12 11c-.5-3 0-6 0-9Z" />
            </svg>
            <span>{plural(billetera.rachaActual, "día seguido", "días seguidos")}</span>
          </p>
          <ol className="puntos-ciclo" aria-hidden="true">
            {Array.from({ length: billetera.rachaDias }, (_, i) => (
              <li key={i} className={i < enCiclo ? "is-hecho" : ""} />
            ))}
          </ol>
          <div
            role="progressbar"
            aria-label="Avance hacia el próximo bonus de racha"
            aria-valuemin={0}
            aria-valuemax={billetera.rachaDias}
            aria-valuenow={enCiclo}
            aria-valuetext={`${enCiclo} de ${billetera.rachaDias} días`}
            className="sr-only"
          />
          <p className="puntos-nota">
            {billetera.diasParaBonus === 1
              ? `Mañana, si ingresás, sumás el bonus de +${billetera.rachaBonus}.`
              : `Te faltan ${billetera.diasParaBonus} días seguidos para el bonus de +${billetera.rachaBonus}.`}
            {" "}Mejor racha: {plural(billetera.mejorRacha, "día", "días")}.
          </p>
        </section>
      </div>

      <section className="ui-card puntos-seccion" aria-labelledby={`${id}-como`}>
        <h2 id={`${id}-como`}>Cómo sumar y usar puntos</h2>
        <ul className="puntos-reglas">
          <li>Sumás puntos cuando ingresás con una reserva, según la hora en que empieza. Cancelar o no presentarse no suma.</li>
          <li>Las reservas de menos de {billetera.duracionMinimaMin} minutos no suman, y como mucho suman {plural(billetera.maxAcreditacionesDia, "reserva", "reservas")} por día.</li>
          <li>Cada {billetera.rachaDias} días seguidos con un ingreso sumás un bonus de +{billetera.rachaBonus}. Si salteás un día, la racha vuelve a empezar.</li>
          <li>Al reservar, cada 100 puntos son un 10 % de descuento, hasta un {billetera.descuentoMaxPct} %. Si cancelás antes de usarla, te los devolvemos; si vence sin ingreso, no.</li>
        </ul>
        <div className="puntos-tabla">
          <table>
            <caption className="sr-only">Puntos por franja horaria de inicio</caption>
            <thead>
              <tr><th scope="col">Franja</th><th scope="col">Horario de inicio</th><th scope="col">Puntos</th></tr>
            </thead>
            <tbody>
              {billetera.franjas.map((franja) => (
                <tr key={franja.clave}>
                  <th scope="row">{franja.nombre}</th>
                  <td>{franja.inicio} a {franja.fin}</td>
                  <td><span className="puntos-chip">+{franja.puntos}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="ui-card puntos-seccion" aria-labelledby={`${id}-historial`}>
        <h2 id={`${id}-historial`}>Historial</h2>
        {movimientos.length === 0 ? (
          <p className="puntos-nota">Todavía no tenés movimientos. Reservá e ingresá para empezar a sumar.</p>
        ) : (
          <ul className="puntos-movimientos">
            {movimientos.map((movimiento) => (
              <li key={movimiento.id}>
                <div>
                  <p className="puntos-movimiento-tipo">{TIPO_MOVIMIENTO_ETIQUETA[movimiento.tipo] ?? movimiento.tipo}</p>
                  <p className="puntos-nota">{movimiento.descripcion}</p>
                  <p className="puntos-fecha">{fechaYHora(movimiento.fecha)}</p>
                </div>
                <p className={`puntos-movimiento-valor ${movimiento.puntos < 0 ? "is-negativo" : ""}`}>
                  {formatearPuntos(movimiento.puntos)}
                  <span className="sr-only"> puntos</span>
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
