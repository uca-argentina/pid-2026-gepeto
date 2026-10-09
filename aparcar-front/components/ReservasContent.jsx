"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";
import AtajosJornada from "@/components/AtajosJornada";
import CotizacionReserva from "@/components/CotizacionReserva";
import PatenteVisual from "@/components/PatenteVisual";
import ReservasListado from "@/components/ReservasListado";
import useCotizacion from "@/hooks/useCotizacion";
import {
  PASO_MINUTOS,
  redondearAlBloqueLocal,
  formatearRango,
  franjaPorDefecto,
} from "@/utils/franjaHoraria";

const reservaSchema = z
  .object({
    patente: z.string().min(1, "Ingresá una patente"),
    cocheraId: z.string().min(1, "Selecciona una cochera"),
    desde: z.string().min(1, "Elegí desde cuándo"),
    hasta: z.string().min(1, "Elegí hasta cuándo"),
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
}) {
  const esAdmin = modo === "admin";

  const [visitantes, setVisitantes] = useState([]);
  const [vehiculos, setVehiculos] = useState([]);
  const [cocheras, setCocheras] = useState([]);
  const [reservas, setReservas] = useState([]);
  const [loadingReservas, setLoadingReservas] = useState(true);
  const [errorReservas, setErrorReservas] = useState(false);

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
    defaultValues: { ...franjaPorDefecto(), patente: "" },
  });

  const patente = watch("patente");
  const cocheraId = watch("cocheraId");
  const desde = watch("desde");
  const hasta = watch("hasta");
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

  // Para un visitante, /disponibles ya viene filtrado por el backend según su
  // perfil. El admin, en cambio, recibe todas las cocheras (reserva en nombre
  // de otros), así que las ACCESIBLE se ocultan acá cuando el dueño del
  // vehículo no declaró discapacidad: ofrecerlas solo lleva a un rechazo.
  // La comparación es estricta con `false` para no ocultar nada si el dato no
  // viene (visitante no encontrado todavía o un backend anterior al campo).
  const ocultarAccesibles = esAdmin && visitanteEncontrado?.tieneDiscapacidad === false;
  const cocherasOfrecidas = useMemo(
    () => (ocultarAccesibles ? cocheras.filter((c) => c.tipo !== "ACCESIBLE") : cocheras),
    [cocheras, ocultarAccesibles]
  );
  const hayAccesiblesOcultas = cocherasOfrecidas.length < cocheras.length;
  const cocheraElegida = cocherasOfrecidas.find((c) => c.id === cocheraId);
  const cotizacion = useCotizacion(cocheraElegida?.tipo ?? vehiculoEncontrado?.tipo, desde, hasta);

  const cargarReservas = async () => {
    setLoadingReservas(true);
    setErrorReservas(false);
    try {
      // El backend ya devuelve todas las reservas si sos ADMIN, y solo las
      // propias si sos visitante. Acá no hay nada que filtrar.
      const res = await api.get("/api/v1/reservas");
      setReservas(res.data);
    } catch {
      setErrorReservas(true);
      toast.error("No se pudieron cargar las reservas.");
    } finally {
      setLoadingReservas(false);
    }
  };

  // GET /api/v1/vehiculos devuelve el catálogo completo para el ADMIN y solo
  // los propios para un visitante, así que la misma llamada sirve en los dos
  // modos. Se re-llama al enfocar el campo de patente (además de al montar)
  // porque el vehículo puede haberse cargado recién en otra pestaña o
  // pantalla (el visitante los gestiona desde "Mis datos").
  const cargarCatalogos = () => {
    api
      .get("/api/v1/vehiculos")
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
  }, [refreshKey]);

  useEffect(() => {
    let vigente = true;
    setValue("cocheraId", "");
    setCocheras([]);
    if (!rangoValido || !vehiculoEncontrado) return;

    // Solo las cocheras libres durante TODA la franja pedida.
    api
      // Se pregunta por el bloque y no por el horario a medio escribir.
      .get("/api/v1/cocheras/disponibles", {
        params: {
          desde: redondearAlBloqueLocal(desde),
          hasta: redondearAlBloqueLocal(hasta),
          tipoVehiculo: vehiculoEncontrado.tipo,
        },
      })
      .then((res) => { if (vigente) setCocheras(res.data); })
      .catch(() => { if (vigente) toast.error("No se pudieron cargar las cocheras disponibles."); });
    return () => { vigente = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [desde, hasta, rangoValido, vehiculoEncontrado]);

  const onSubmit = async (data) => {
    if (!vehiculoEncontrado) {
      toast.error("No se encontró ningún vehículo con esa patente.");
      return;
    }

    if (esAdmin && !visitanteEncontrado) {
      toast.error("No se encontró el visitante dueño de ese vehículo.");
      return;
    }

    if (!cotizacion.lista || !cocheraElegida || !rangoValido) return;

    try {
      await api.post("/api/v1/reservas", {
        // Solo el admin puede reservar en nombre de otro; para un visitante el
        // backend ignora este campo y usa su propia cuenta.
        visitanteId: esAdmin ? visitanteEncontrado.id : undefined,
        vehiculoId: vehiculoEncontrado.id,
        cocheraId: data.cocheraId,
        // Cinturón: al pasar del campo al botón se dispara el blur que ya lo
        // acomoda, pero el valor también puede venir de un atajo o de un reset,
        // y el backend rechaza lo que no caiga en la grilla de 15 minutos.
        desde: redondearAlBloqueLocal(data.desde),
        hasta: redondearAlBloqueLocal(data.hasta),
        precioEsperado: cotizacion.datos.total,
      });
      toast.success("Reserva creada correctamente");
      reset({ patente: "", cocheraId: "", ...franjaPorDefecto() });
      setCocheras([]);
      cargarReservas();
      onOcupacionCambiada?.();
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo crear la reserva.");
      cotizacion.recargar();
    }
  };

  const cancelarReserva = async (reserva) => {
    const confirmado = window.confirm(
      `¿Seguro que querés cancelar la reserva de la cochera ${reserva.cochera?.numero} (${formatearRango(reserva.desde, reserva.hasta)})?`
    );
    if (!confirmado) {
      return;
    }

    try {
      await api.post(`/api/v1/reservas/${reserva.id}/cancelar`);
      toast.success("Reserva cancelada correctamente");
      await cargarReservas();
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
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight text-ink mb-2">Nueva reserva</h1>
        <p className="text-sm text-ink/60">
          {esAdmin
            ? "Ingresá la patente, elegí desde y hasta cuándo, y una cochera libre en esa franja."
            : "Elegí uno de tus vehículos, desde y hasta cuándo, y una cochera libre en esa franja."}
        </p>
      </div>

      <div className="ui-card p-8">
        {sinVehiculos ? (
          <p className="text-sm text-ink/60">
            Cargá al menos un vehículo en{" "}
            <Link href="/dashboard-user/perfil#mis-vehiculos" className="font-semibold text-link underline-offset-2 hover:underline">
              &quot;Mis datos&quot;
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
                disabled={!vehiculoEncontrado || !rangoValido}
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
              {vehiculoEncontrado && rangoValido && cocherasOfrecidas.length === 0 && (
                <p className="mt-1 text-sm text-ink/50">
                  No hay cocheras libres durante toda esa franja.
                </p>
              )}
              {hayAccesiblesOcultas && (
                <p className="mt-1 text-sm text-ink/50">
                  No se muestran las cocheras accesibles: {visitanteEncontrado.nombre} no tiene
                  declarada discapacidad en su perfil.
                </p>
              )}
            </div>
          </div>

          <CotizacionReserva cotizacion={cotizacion} />

          <div>
            <button
              type="submit"
              disabled={isSubmitting || !cocheraElegida || !cotizacion.lista || !rangoValido}
              className="ui-primary group relative flex w-full justify-center px-3 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? "Reservando..." : "Confirmar reserva"}
            </button>
          </div>
        </form>
        )}
      </div>
      </div>

      <ReservasListado
        modo={modo}
        reservas={reservas}
        cargando={loadingReservas}
        error={errorReservas}
        onReintentar={cargarReservas}
        onCancelar={cancelarReserva}
      />
    </div>
  );
}
