"use client";

import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";

import api from "@/app/api";
import {
  agruparOcupacion,
  diaEnPalabras,
  diaRelativo,
  etiquetaDeGrupo,
  hoy,
  sumarDias,
} from "@/utils/ocupacion";

/**
 * Panel de ocupación de cocheras para el admin. Es la vista con la que abre el
 * dashboard.
 *
 * Dos formas de mirar lo mismo: por tipo de cochera o por piso. Cada grupo es
 * un desplegable y, al abrirlo, muestra al costado **todas** las cocheras de
 * ese grupo — no solo las reservadas — con lo que hay ocupado ese día, y desde
 * ahí mismo se cancela una reserva o se habilita y deshabilita la cochera, sin
 * tener que ir a otra pantalla.
 *
 * Los grupos no están escritos acá: salen de las cocheras que devuelve el
 * backend, así que un piso nuevo o un tipo nuevo aparecen solos.
 */

const PESTANAS = [
  { clave: "tipo", etiqueta: "Por tipo de cochera" },
  { clave: "sector", etiqueta: "Por piso" },
];

export default function OcupacionCocheras({ refreshKey = 0 }) {
  const [dia, setDia] = useState(hoy);
  const [agrupadoPor, setAgrupadoPor] = useState("tipo");
  const [abierto, setAbierto] = useState(null);
  const [soloReservadas, setSoloReservadas] = useState(false);
  const [cocheras, setCocheras] = useState([]);
  const [reservas, setReservas] = useState([]);
  const [cargando, setCargando] = useState(true);
  // Mientras una acción está en vuelo se deshabilitan sus botones, para que dos
  // clics seguidos no manden dos veces la misma cancelación.
  const [enCurso, setEnCurso] = useState(null);
  // Se usa para volver a pedir los datos después de una acción propia.
  const [recarga, setRecarga] = useState(0);

  // Las dos pestañas y todos los días se calculan sobre los mismos datos, así
  // que se piden una sola vez: cambiar de pestaña o de día no vuelve a la red.
  useEffect(() => {
    let vigente = true;
    setCargando(true);

    Promise.all([api.get("/api/v1/cocheras"), api.get("/api/v1/reservas")])
      .then(([c, r]) => {
        if (!vigente) return;
        setCocheras(c.data);
        setReservas(r.data);
      })
      .catch(() => {
        if (vigente) toast.error("No se pudo cargar la ocupación de cocheras.");
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });

    return () => {
      vigente = false;
    };
  }, [refreshKey, recarga]);

  const grupos = useMemo(
    () => agruparOcupacion(cocheras, reservas, dia, agrupadoPor),
    [cocheras, reservas, dia, agrupadoPor]
  );

  // Al cambiar de pestaña las claves son otras (AUTO vs "Planta Baja"), así que
  // lo que estaba abierto ya no existe.
  const cambiarPestana = (clave) => {
    setAgrupadoPor(clave);
    setAbierto(null);
  };

  const cancelarReserva = async (cochera, reserva) => {
    const confirmado = window.confirm(
      `¿Cancelar la reserva de ${reserva.patente} en la cochera ${cochera.numero} (${reserva.horario.completo})?`
    );
    if (!confirmado) return;

    setEnCurso(reserva.id);
    try {
      await api.post(`/api/v1/reservas/${reserva.id}/cancelar`);
      toast.success("Reserva cancelada correctamente");
      setRecarga((n) => n + 1);
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo cancelar la reserva.");
    } finally {
      setEnCurso(null);
    }
  };

  const cambiarEstado = async (cochera) => {
    const deshabilitando = cochera.estado === "HABILITADA";
    const vigentes = cochera.reservas.filter((r) => r.cancelable).length;

    // Se avisa cuántas reservas se van a cancelar, no "puede que haya alguna":
    // acá ya se sabe cuántas hay, así que el aviso puede ser exacto. Y cuando
    // no hay ninguna, no se molesta con una advertencia que no aplica.
    const aviso = !deshabilitando
      ? `¿Habilitar la cochera ${cochera.numero}?`
      : vigentes > 0
        ? `Deshabilitar la cochera ${cochera.numero} va a cancelar ${vigentes} ${
            vigentes === 1 ? "reserva confirmada" : "reservas confirmadas"
          } de este día, y cualquier otra a futuro. ¿Confirmás?`
        : `¿Deshabilitar la cochera ${cochera.numero}? No va a poder reservarse hasta que la habilites de nuevo.`;

    if (!window.confirm(aviso)) return;

    setEnCurso(cochera.id);
    try {
      // El PUT pide la cochera entera, no solo el estado.
      await api.put(`/api/v1/cocheras/${cochera.id}`, {
        numero: cochera.numero,
        sector: cochera.sector,
        tipo: cochera.tipo,
        estado: deshabilitando ? "DESHABILITADA" : "HABILITADA",
      });
      toast.success(
        deshabilitando
          ? `Cochera ${cochera.numero} deshabilitada`
          : `Cochera ${cochera.numero} habilitada`
      );
      setRecarga((n) => n + 1);
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo actualizar la cochera.");
    } finally {
      setEnCurso(null);
    }
  };

  const grupoAbierto = grupos.find((g) => g.clave === abierto) ?? null;
  const visibles = grupoAbierto
    ? soloReservadas
      ? grupoAbierto.cocheras.filter((c) => c.ocupada)
      : grupoAbierto.cocheras
    : [];

  const relativo = diaRelativo(dia);
  const totalOcupadas = grupos.reduce((n, g) => n + g.ocupadas, 0);
  const totalCocheras = grupos.reduce((n, g) => n + g.total, 0);

  return (
    <section className="ocupacion" aria-label="Ocupación de cocheras">
      {/* El día que se está mirando, y cómo moverse entre días. */}
      <div className="ocupacion-banner">
        <button
          type="button"
          className="ocupacion-nav"
          onClick={() => setDia(sumarDias(dia, -1))}
          aria-label="Día anterior"
        >
          ←
        </button>

        <div className="ocupacion-fecha">
          <p className="ocupacion-fecha-dia">
            {relativo && <span className="ocupacion-fecha-relativo">{relativo}</span>}
            <span className="ocupacion-fecha-texto">{diaEnPalabras(dia)}</span>
          </p>
          <p className="ocupacion-fecha-resumen">
            {cargando
              ? "Cargando…"
              : `${totalOcupadas} de ${totalCocheras} cocheras ocupadas`}
          </p>
        </div>

        <div className="ocupacion-banner-acciones">
          {/* Solo aparece cuando sirve para algo: si ya estás en hoy, no. */}
          {relativo !== "Hoy" && (
            <button type="button" className="ocupacion-hoy" onClick={() => setDia(hoy())}>
              Hoy
            </button>
          )}
          <button
            type="button"
            className="ocupacion-nav"
            onClick={() => setDia(sumarDias(dia, 1))}
            aria-label="Día siguiente"
          >
            →
          </button>
        </div>
      </div>

      <div className="ocupacion-tabs" role="tablist" aria-label="Cómo agrupar las cocheras">
        {PESTANAS.map(({ clave, etiqueta }) => (
          <button
            key={clave}
            type="button"
            role="tab"
            id={`ocupacion-tab-${clave}`}
            aria-selected={agrupadoPor === clave}
            aria-controls="ocupacion-grupos"
            className={`ocupacion-tab ${agrupadoPor === clave ? "is-activa" : ""}`}
            onClick={() => cambiarPestana(clave)}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      <div className="ocupacion-cuerpo">
        {/* Los desplegables, pegados uno abajo del otro. */}
        <div
          className="ocupacion-grupos"
          id="ocupacion-grupos"
          role="tabpanel"
          aria-labelledby={`ocupacion-tab-${agrupadoPor}`}
        >
          {grupos.length === 0 && !cargando && (
            <p className="ocupacion-vacio">Todavía no hay cocheras cargadas.</p>
          )}

          {grupos.map((g) => {
            const activo = abierto === g.clave;
            return (
              <button
                key={g.clave}
                type="button"
                className={`ocupacion-grupo ${activo ? "is-abierto" : ""}`}
                aria-expanded={activo}
                onClick={() => setAbierto(activo ? null : g.clave)}
              >
                <span className="ocupacion-grupo-nombre">
                  {etiquetaDeGrupo(g.clave, agrupadoPor)}
                  {g.deshabilitadas > 0 && (
                    <span className="ocupacion-grupo-aviso">
                      {g.deshabilitadas} fuera de servicio
                    </span>
                  )}
                </span>
                <span
                  className={`ocupacion-grupo-contador ${g.ocupadas > 0 ? "is-ocupada" : ""}`}
                >
                  {g.ocupadas}/{g.total}
                </span>
                <span className="ocupacion-grupo-flecha" aria-hidden="true">
                  ›
                </span>
              </button>
            );
          })}
        </div>

        {/* El detalle, al costado en pantalla ancha y abajo en angosta. */}
        <div className="ocupacion-detalle">
          {!grupoAbierto ? (
            <p className="ocupacion-detalle-ayuda">
              Elegí {agrupadoPor === "tipo" ? "un tipo de cochera" : "un piso"} para ver sus
              cocheras.
            </p>
          ) : (
            <>
              <div className="ocupacion-detalle-cabecera">
                <h3 className="ocupacion-detalle-titulo">
                  {etiquetaDeGrupo(grupoAbierto.clave, agrupadoPor)}
                  <span className="ocupacion-detalle-cuenta">
                    {grupoAbierto.ocupadas} de {grupoAbierto.total} ocupadas
                  </span>
                </h3>

                <button
                  type="button"
                  className={`ocupacion-filtro ${soloReservadas ? "is-activo" : ""}`}
                  aria-pressed={soloReservadas}
                  onClick={() => setSoloReservadas((v) => !v)}
                >
                  Solo reservadas
                </button>
              </div>

              {visibles.length === 0 ? (
                <p className="ocupacion-detalle-ayuda">
                  {soloReservadas
                    ? "No hay ninguna cochera reservada en este grupo, este día."
                    : "Este grupo no tiene cocheras."}
                </p>
              ) : (
                <ul className="ocupacion-lista">
                  {visibles.map((c) => {
                    const deshabilitada = c.estado === "DESHABILITADA";
                    const trabajando = enCurso === c.id;
                    return (
                      <li
                        key={c.id}
                        className={`ocupacion-cochera ${deshabilitada ? "is-deshabilitada" : ""} ${
                          c.ocupada ? "is-ocupada" : ""
                        }`}
                      >
                        <div className="ocupacion-cochera-cabecera">
                          <span className="ocupacion-cochera-numero">{c.numero}</span>
                          <span className="ocupacion-cochera-estado">
                            {deshabilitada
                              ? "Fuera de servicio"
                              : c.ocupada
                                ? `${c.reservas.length} ${c.reservas.length === 1 ? "reserva" : "reservas"}`
                                : "Libre"}
                          </span>

                          <button
                            type="button"
                            className="ocupacion-accion"
                            onClick={() => cambiarEstado(c)}
                            disabled={trabajando}
                          >
                            {deshabilitada ? "Habilitar" : "Deshabilitar"}
                          </button>
                        </div>

                        {c.reservas.length > 0 && (
                          <ul className="ocupacion-reservas">
                            {c.reservas.map((r) => (
                              <li key={r.id} className="ocupacion-item">
                                <div className="ocupacion-item-cabecera">
                                  <span className="ocupacion-item-patente">{r.patente}</span>
                                  <span className="ocupacion-item-modalidad">{r.modalidad}</span>
                                </div>
                                <div className="ocupacion-item-datos">
                                  {/* El title trae la franja completa: en el
                                      renglón se muestra solo la parte que cae
                                      en este día. */}
                                  <span
                                    className={`ocupacion-item-horario ${
                                      r.horario.cruzaElDia ? "cruza" : ""
                                    }`}
                                    title={r.horario.completo}
                                  >
                                    {r.horario.texto}
                                  </span>
                                  <span className="ocupacion-item-email">{r.email}</span>

                                  {/* Una reserva ya terminada o cancelada no se
                                      cancela: el botón no tendría qué hacer. */}
                                  {r.cancelable && (
                                    <button
                                      type="button"
                                      className="ocupacion-accion is-peligro"
                                      onClick={() => cancelarReserva(c, r)}
                                      disabled={enCurso === r.id}
                                    >
                                      Cancelar
                                    </button>
                                  )}
                                </div>
                              </li>
                            ))}
                          </ul>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}
