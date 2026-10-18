"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";
import AtajosJornada from "@/components/AtajosJornada";
import CotizacionReserva from "@/components/CotizacionReserva";
import ModalCodigoAcceso from "@/components/ModalCodigoAcceso";
import PatenteVisual from "@/components/PatenteVisual";
import ReservasListado from "@/components/ReservasListado";
import AvisoListaEspera from "@/components/AvisoListaEspera";
import NotificacionesReservas from "@/components/NotificacionesReservas";
import RepeticionReserva, { contarRepeticiones, limiteRepeticion } from "@/components/RepeticionReserva";
import useCotizacion from "@/hooks/useCotizacion";
import useDisponibilidad from "@/hooks/useDisponibilidad";
import { useAutoRefresco } from "@/hooks/useAutoRefresco";
import { tieneCodigoVigente } from "@/utils/codigoAcceso";
import {
  PASO_MINUTOS,
  redondearAlBloqueLocal,
  formatearRango,
  franjaPorDefecto,
} from "@/utils/franjaHoraria";

const reservaSchema = z
  .object({
    patente: z.string().min(1, "Ingresá una patente"),
    cocheraId: z.string().optional(),
    listaEspera: z.boolean().optional(),
    desde: z.string().min(1, "Elegí desde cuándo"),
    hasta: z.string().min(1, "Elegí hasta cuándo"),
  })
  .refine((d) => d.listaEspera || Boolean(d.cocheraId), {
    message: "Selecciona una cochera", path: ["cocheraId"],
  })
  .refine((d) => !d.desde || !d.hasta || d.hasta > d.desde, {
    message: "El fin tiene que ser posterior al inicio",
    path: ["hasta"],
  });

const inputClasses =
  "ui-input";
const labelClasses = "ui-label";

// Lo usan los dos dashboards, pero no hacen lo mismo:
//
// - modo "admin": busca la patente en el catálogo completo y reserva a nombre
//   de cualquier visitante ya registrado. Ve todas las reservas del sistema.
// - modo "user": elige entre sus propias patentes y reserva a su nombre. Ve
//   solo sus reservas.
// - modo "recurrentes": el ADMIN reserva únicamente para sus vehículos y ve
//   sus propias series desde una pantalla separada de las reservas de visitantes.
//
// La separación es real, no cosmética: el backend ignora el visitanteId que
// mande un USER y usa su cuenta, y filtra el listado por dueño. Lo de acá es
// para que la pantalla no ofrezca lo que el backend después va a rechazar.
//
// `onOcupacionCambiada` es opcional: el dashboard-admin lo usa para refrescar
// la cuadrícula de ocupación, que vive en un componente hermano. Se dispara
// tanto al crear una reserva como al cancelarla, porque las dos cosas cambian
// qué cocheras están libres.
export default function ReservasContent({
  modo = "user",
  onOcupacionCambiada,
  refreshKey = 0,
  layout = "apilado",
  // Qué mitad del componente se dibuja. El panel del visitante muestra solo el
  // alta y el listado vive en su propia pantalla, para que ver las reservas no
  // obligue a pasar de largo el formulario.
  mostrar = "todo",
}) {
  const muestraFormulario = mostrar !== "listado";
  const muestraListado = mostrar !== "formulario";
  const esAdmin = modo === "admin";
  const recurrente = modo === "recurrentes";

  const [visitantes, setVisitantes] = useState([]);
  const [vehiculos, setVehiculos] = useState([]);
  const [diasSemana, setDiasSemana] = useState([]);
  const [repetirHasta, setRepetirHasta] = useState("");
  const [reservas, setReservas] = useState([]);
  const [loadingReservas, setLoadingReservas] = useState(true);
  const [errorReservas, setErrorReservas] = useState(false);
  // La reserva cuyo código de acceso (QR) se está mostrando.
  const [reservaCodigo, setReservaCodigo] = useState(null);
  const [codigoRecienCreado, setCodigoRecienCreado] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(reservaSchema),
    // La reserva arranca por defecto en este momento; el "hasta" se elige.
    defaultValues: { ...franjaPorDefecto(), patente: "", cocheraId: "", listaEspera: false },
  });

  const patente = watch("patente");
  const cocheraId = watch("cocheraId");
  const desde = watch("desde");
  const hasta = watch("hasta");
  const listaEspera = watch("listaEspera");
  const rangoValido = Boolean(desde && hasta && hasta > desde);

  const vehiculoEncontrado = useMemo(() => {
    const normalizada = patente?.trim().toUpperCase();
    if (!normalizada) return null;
    return vehiculos.find((v) => v.patente === normalizada) || null;
  }, [patente, vehiculos]);

  // Solo el admin necesita saber de quién es el vehículo: el visitante reserva
  // para sí mismo y el backend resuelve a nombre de quién va.
  const visitanteEncontrado = useMemo(() => {
    if (!esAdmin || !vehiculoEncontrado) return null;
    return visitantes.find((v) => v.id === vehiculoEncontrado.visitanteId) || null;
  }, [esAdmin, vehiculoEncontrado, visitantes]);

  const disponibilidad = useDisponibilidad(vehiculoEncontrado?.tipo, desde, hasta, recurrente);
  const cocheras = disponibilidad.cocheras;

  // Para un visitante, /disponibles ya viene filtrado por el backend según su
  // perfil. El admin, en cambio, recibe todas las cocheras (reserva en nombre
  // de otros), así que las ACCESIBLE se ocultan acá cuando el dueño del
  // vehículo no tiene verificada su discapacidad: ofrecerlas solo lleva a un rechazo.
  // La comparación es estricta con `false` para no ocultar nada si el dato no
  // viene (visitante no encontrado todavía o un backend anterior al campo).
  const ocultarAccesibles = esAdmin && visitanteEncontrado?.tieneDiscapacidad === false;
  const cocherasOfrecidas = useMemo(
    () => (ocultarAccesibles ? cocheras.filter((c) => c.tipo !== "ACCESIBLE") : cocheras),
    [cocheras, ocultarAccesibles]
  );
  const hayAccesiblesOcultas = cocherasOfrecidas.length < cocheras.length;
  const cocheraElegida = cocherasOfrecidas.find((c) => c.id === cocheraId);
  const puedeEsperar = disponibilidad.lista && cocherasOfrecidas.length === 0 && !recurrente;
  const enEspera = puedeEsperar && listaEspera;
  const cantidadRepeticiones = contarRepeticiones(desde, repetirHasta, diasSemana);
  const diaInicial = desde ? new Date(`${desde.slice(0, 10)}T12:00:00`).getDay() || 7 : null;
  const repeticionValida = !recurrente || (cantidadRepeticiones > 0 && cantidadRepeticiones <= 90
    && repetirHasta <= limiteRepeticion(desde) && diasSemana.includes(diaInicial));
  const cotizacion = useCotizacion(cocheraElegida?.tipo ?? vehiculoEncontrado?.tipo, desde, hasta);

  // Lo último que llegó, para no reescribir el estado con datos idénticos. Es
  // una referencia porque solo sirve para comparar, no se dibuja.
  const ultimasReservas = useRef("");

  /**
   * Trae el listado de reservas.
   *
   * El refresco automático pasa `silencioso`, y eso evita las tres cosas que lo
   * harían visible: prender el estado de carga (que reemplaza la lista por un
   * esqueleto), avisar de errores (un microcorte dejaría un cartel cada veinte
   * segundos), y reescribir el estado cuando no cambió nada.
   */
  const cargarReservas = async ({ silencioso = false } = {}) => {
    // Sin listado en pantalla no hay nada que traer. Corta acá y no en cada
    // llamador, que son varios: la carga inicial, el alta, la cancelación y el
    // refresco automático.
    if (!muestraListado) return;

    if (!silencioso) {
      setLoadingReservas(true);
      setErrorReservas(false);
    }
    try {
      // El endpoint de recurrentes limita el listado a las series del admin
      // autenticado; la pantalla operativa conserva el listado general.
      const res = await api.get(recurrente ? "/api/v1/reservas/recurrentes" : "/api/v1/reservas");
      const huella = JSON.stringify(res.data);
      if (huella !== ultimasReservas.current) {
        ultimasReservas.current = huella;
        setReservas(res.data);
      }
      if (!silencioso) setErrorReservas(false);
    } catch (error) {
      if (silencioso) throw error;
      setErrorReservas(true);
      toast.error("No se pudieron cargar las reservas.");
    } finally {
      if (!silencioso) setLoadingReservas(false);
    }
  };

  // El listado cambia por fuera de esta pantalla: a un visitante se le puede
  // confirmar una reserva que estaba en lista de espera, y el admin ve entrar
  // reservas de otros mientras mira. Solo se refresca el listado, nunca los
  // catálogos que alimentan el formulario, para no tocar nada de lo que la
  // persona pueda estar completando.
  useAutoRefresco(() => cargarReservas({ silencioso: true }), { activo: muestraListado });

  // GET /api/v1/vehiculos devuelve el catálogo completo para el ADMIN y solo
  // los propios para un visitante. En recurrentes se pide /mios para que el
  // admin sólo elija vehículos propios. Se recarga al enfocar la patente
  // porque el vehículo puede haberse cargado recién en otra pestaña o
  // pantalla (el visitante los gestiona desde "Mis datos").
  const cargarCatalogos = () => {
    api
      .get(recurrente ? "/api/v1/vehiculos/mios" : "/api/v1/vehiculos")
      .then((res) => setVehiculos(res.data))
      .catch(() => toast.error("No se pudieron cargar los vehículos."));

    if (!esAdmin) return;

    api
      .get("/api/v1/visitantes")
      .then((res) => setVisitantes(res.data))
      .catch(() => toast.error("No se pudieron cargar los visitantes."));
  };

  useEffect(() => {
    cargarCatalogos();
    cargarReservas();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshKey, modo]);

  useEffect(() => {
    setValue("cocheraId", "");
    setValue("listaEspera", false);
  }, [disponibilidad.solicitud, vehiculoEncontrado?.id, ocultarAccesibles, setValue]);

  const onSubmit = async (data) => {
    if (!vehiculoEncontrado) {
      toast.error("No se encontró ningún vehículo con esa patente.");
      return;
    }

    if (esAdmin && !visitanteEncontrado) {
      toast.error("No se encontró el visitante dueño de ese vehículo.");
      return;
    }

    if (!cotizacion.lista || (!cocheraElegida && !enEspera) || !rangoValido || !repeticionValida) return;

    try {
      const res = await api.post(recurrente ? "/api/v1/reservas/recurrentes" : "/api/v1/reservas", {
        // Sólo el modo operativo reserva en nombre de otro. Visitantes y
        // recurrentes usan la cuenta autenticada.
        visitanteId: esAdmin ? visitanteEncontrado.id : undefined,
        vehiculoId: vehiculoEncontrado.id,
        cocheraId: enEspera ? null : data.cocheraId,
        ...(enEspera ? { listaEspera: true, tipoCochera: vehiculoEncontrado.tipo } : {}),
        ...(recurrente ? { diasSemana, repetirHasta } : {}),
        // Cinturón: al pasar del campo al botón se dispara el blur que ya lo
        // acomoda, pero el valor también puede venir de un atajo o de un reset,
        // y el backend rechaza lo que no caiga en la grilla de 15 minutos.
        desde: redondearAlBloqueLocal(data.desde),
        hasta: redondearAlBloqueLocal(data.hasta),
        precioEsperado: cotizacion.datos.total,
      });
      toast.success(recurrente ? `${res.data.length} reservas recurrentes creadas correctamente`
        : res.data?.estado === "LISTA_ESPERA" ? "Reserva agregada a la lista de espera"
        : "Reserva creada correctamente");
      // El visitante se lleva el QR en el momento: es lo que va a mostrar en
      // la entrada.
      if (!esAdmin && !recurrente && tieneCodigoVigente(res?.data)) {
        setCodigoRecienCreado(true);
        setReservaCodigo(res.data);
      }
      reset({ patente: "", cocheraId: "", listaEspera: false, ...franjaPorDefecto() });
      setDiasSemana([]);
      setRepetirHasta("");
      disponibilidad.recargar();
      cargarReservas();
      onOcupacionCambiada?.();
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo crear la reserva.");
      cotizacion.recargar();
      disponibilidad.recargar();
    }
  };

  const cancelarReserva = async (reserva) => {
    const confirmado = window.confirm(
      `¿Seguro que querés cancelar la reserva ${reserva.estado === "LISTA_ESPERA" ? "en lista de espera" : `de la cochera ${reserva.cochera?.numero}`} (${formatearRango(reserva.desde, reserva.hasta)})?`
    );
    if (!confirmado) {
      return;
    }

    try {
      await api.post(`/api/v1/reservas/${reserva.id}/cancelar`);
      toast.success("Reserva cancelada correctamente");
      await cargarReservas();
      disponibilidad.recargar();
      onOcupacionCambiada?.();
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo cancelar la reserva.");
    }
  };

  const sinVehiculos = !esAdmin && vehiculos.length === 0;

  // En dos columnas el alta queda a la izquierda y el listado a la derecha; el
  // dashboard del visitante los sigue viendo apilados, que es lo que entra en
  // una pantalla angosta sin pelear con el resto de su panel.
  const enColumnas = layout === "columnas";

  return (
    <div className={enColumnas ? "reservas-columnas" : "mx-auto max-w-3xl space-y-10"}>
      <div className="space-y-10">
      <NotificacionesReservas onActualizadas={cargarReservas} onVerCodigo={esAdmin ? undefined : (reserva) => {
        setCodigoRecienCreado(false);
        setReservaCodigo(reserva);
      }} />
      {muestraFormulario && (<>
      <div>
        {recurrente
          ? <h2 className="text-xl font-bold text-ink mb-2">Nueva reserva recurrente</h2>
          : <h1 className="text-3xl font-extrabold tracking-tight text-ink mb-2">Nueva reserva</h1>}
        <p className="text-sm text-ink/60">
          {recurrente
            ? "Elegí uno de tus vehículos, la primera franja y los días de la semana en que vas a reservar."
            : esAdmin
            ? "Ingresá la patente, elegí desde y hasta cuándo, y una cochera libre en esa franja."
            : "Elegí uno de tus vehículos, desde y hasta cuándo, y una cochera libre en esa franja."}
        </p>
      </div>

      <div className="ui-card p-8">
        {sinVehiculos ? (
          <p className="text-sm text-ink/60">
            Cargá al menos un vehículo en{" "}
            <Link href={recurrente ? "#mis-vehiculos" : "/dashboard-user/perfil#mis-vehiculos"} className="font-semibold text-link underline-offset-2 hover:underline">
              {recurrente ? "Mis vehículos" : '"Mis datos"'}
            </Link>{" "}
            para poder reservar.
          </p>
        ) : (
        <form className="space-y-6" onSubmit={handleSubmit(onSubmit)}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* La patente ocupa el ancho completo para que `Desde` y `Hasta`
                caigan juntos en la fila siguiente: son un par, y separarlos
                obliga a leer el formulario en zigzag. */}
            <div className="sm:col-span-2">
              <label className={labelClasses} htmlFor="reserva-patente">Patente</label>
              {esAdmin ? (
                <>
                  <input
                    id="reserva-patente"
                    list="patentes-registradas"
                    {...register("patente")}
                    onFocus={cargarCatalogos}
                    className={`${inputClasses} uppercase`}
                    placeholder="ABC123 / AB123CD"
                  />
                  <datalist id="patentes-registradas">
                    {vehiculos.map((v) => (
                      <option key={v.id} value={v.patente} />
                    ))}
                  </datalist>
                </>
              ) : (
                <select
                  id="reserva-patente"
                  {...register("patente")}
                  onFocus={cargarCatalogos}
                  className={inputClasses}
                >
                  <option value="">Seleccioná un vehículo</option>
                  {vehiculos.map((v) => (
                    <option key={v.id} value={v.patente}>
                      {v.patente} — {v.tipo}
                    </option>
                  ))}
                </select>
              )}
              {errors.patente && <p className="mt-1 text-sm text-red-500">{errors.patente.message}</p>}

              {esAdmin && patente && !vehiculoEncontrado && (
                <p className="mt-1 text-sm text-ink/50">
                  No hay ningún vehículo registrado con esa patente.
                </p>
              )}
              {/* Un <option> no puede llevar la chapa dibujada: se muestra
                  debajo del desplegable la del vehículo elegido. */}
              {!esAdmin && vehiculoEncontrado && (
                <div className="reserva-patente-elegida">
                  <PatenteVisual patente={vehiculoEncontrado.patente} tipo={vehiculoEncontrado.tipo} tamano="sm" />
                </div>
              )}
              {esAdmin && vehiculoEncontrado && visitanteEncontrado && (
                <p className="mt-1 text-sm text-link">
                  {visitanteEncontrado.nombre} — {vehiculoEncontrado.tipo}
                </p>
              )}
            </div>

            <div>
              <label className={labelClasses} htmlFor="reserva-desde">Desde</label>
              {/* step de 15 min: las flechas del campo se mueven de a un
                  bloque. Lo que se escribe se redondea al bloque más cercano
                  **al salir del campo**, no en cada tecla: escribir los minutos
                  pasa por estados intermedios —tipear el "3" de ":30" deja
                  ":03"— y acomodarlos en el momento los borraba, así que no se
                  podía escribir ningún minuto. */}
              <input
                id="reserva-desde"
                type="datetime-local"
                step={PASO_MINUTOS * 60}
                {...register("desde", {
                  onBlur: (e) =>
                    setValue("desde", redondearAlBloqueLocal(e.target.value), {
                      shouldValidate: true,
                    }),
                })}
                className={inputClasses}
              />
              {errors.desde && <p className="mt-1 text-sm text-red-500">{errors.desde.message}</p>}
            </div>

            <div>
              <label className={labelClasses} htmlFor="reserva-hasta">Hasta</label>
              <input
                id="reserva-hasta"
                type="datetime-local"
                step={PASO_MINUTOS * 60}
                min={desde}
                {...register("hasta", {
                  onBlur: (e) =>
                    setValue("hasta", redondearAlBloqueLocal(e.target.value), {
                      shouldValidate: true,
                    }),
                })}
                className={inputClasses}
              />
              {errors.hasta && <p className="mt-1 text-sm text-red-500">{errors.hasta.message}</p>}
              {/* Se avisa al instante: si se espera al submit, el error que
                  aparece es el de la cochera (que quedó deshabilitada), y el
                  problema real —la franja dada vuelta— queda invisible. */}
              {desde && hasta && hasta <= desde && !errors.hasta && (
                <p className="mt-1 text-sm text-red-500">El fin tiene que ser posterior al inicio</p>
              )}
            </div>

            <div className="sm:col-span-2">
              <AtajosJornada
                idPrefijo="reserva"
                desde={desde}
                hasta={hasta}
                onChange={(h) => setValue("hasta", h, { shouldValidate: true })}
              />
            </div>

            <div className="sm:col-span-2">
              <label className={labelClasses} htmlFor="reserva-cocheraId">Cochera</label>
              <select
                id="reserva-cocheraId"
                {...register("cocheraId")}
                className={inputClasses}
                disabled={!vehiculoEncontrado || !rangoValido || !disponibilidad.lista || cocherasOfrecidas.length === 0}
              >
                <option value="">
                  {vehiculoEncontrado && rangoValido
                    ? "Seleccioná una cochera"
                    : esAdmin
                    ? "Ingresá primero una patente válida y la franja"
                    : "Elegí primero un vehículo y la franja"}
                </option>
                {cocherasOfrecidas.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.numero} — {c.sector} ({c.tipo})
                  </option>
                ))}
              </select>
              {errors.cocheraId && <p className="mt-1 text-sm text-red-500">{errors.cocheraId.message}</p>}
              {disponibilidad.cargando && <p className="mt-1 text-sm text-ink/50">Consultando disponibilidad…</p>}
              {disponibilidad.error && <div className="mt-1 text-sm text-red-500" role="alert">
                <p>{disponibilidad.error}</p>
                <button type="button" className="font-semibold underline" onClick={disponibilidad.recargar}>Reintentar disponibilidad</button>
              </div>}
              {disponibilidad.lista && cocherasOfrecidas.length === 0 && (
                <p className="mt-1 text-sm text-ink/50">
                  No hay cocheras libres durante toda esa franja.
                </p>
              )}
              {puedeEsperar && <AvisoListaEspera id="reserva-lista-espera" registro={register("listaEspera")} tipoCochera={vehiculoEncontrado?.tipo} />}
              {hayAccesiblesOcultas && (
                <p className="mt-1 text-sm text-ink/50">
                  No se muestran las cocheras accesibles: {visitanteEncontrado.nombre} no tiene
                  verificado su certificado de discapacidad.
                </p>
              )}
            </div>
          </div>

          {recurrente && <RepeticionReserva desde={desde}
            diasSemana={diasSemana} onDias={setDiasSemana} repetirHasta={repetirHasta} onHasta={setRepetirHasta}
            cantidad={cantidadRepeticiones} />}

          <CotizacionReserva cotizacion={cotizacion} />

          <div>
            <button
              type="submit"
              disabled={isSubmitting || (!cocheraElegida && !enEspera) || !cotizacion.lista || !rangoValido || !repeticionValida}
              className="ui-primary group relative flex w-full justify-center px-3 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? "Reservando..." : enEspera ? "Ingresar a lista de espera" : recurrente ? "Confirmar reservas recurrentes" : "Confirmar reserva"}
            </button>
          </div>
        </form>
        )}
      </div>
      </>)}
      </div>

      {muestraListado && (
      <ReservasListado
        modo={esAdmin ? "admin" : "user"}
        reservas={reservas}
        cargando={loadingReservas}
        error={errorReservas}
        onReintentar={cargarReservas}
        onCancelar={cancelarReserva}
        onVerCodigo={esAdmin ? undefined : (reserva) => {
          setCodigoRecienCreado(false);
          setReservaCodigo(reserva);
        }}
      />
      )}

      {reservaCodigo && (
        <ModalCodigoAcceso
          reserva={reservaCodigo}
          titulo={codigoRecienCreado ? "Reserva confirmada: tu código de acceso" : "Tu código de acceso"}
          onClose={() => setReservaCodigo(null)}
        />
      )}
    </div>
  );
}
