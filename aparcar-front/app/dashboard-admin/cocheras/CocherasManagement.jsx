"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";

import api from "@/app/api";
import LogoutButton from "@/components/LogoutButton";
import DashboardHeader from "@/components/DashboardHeader";

const cocheraSchema = z.object({
  numero: z.string().min(1, "El número es obligatorio"),
  sector: z.string().min(1, "El sector es obligatorio"),
  tipo: z.enum(["AUTO", "MOTO", "ACCESIBLE", "CARGA"], {
    message: "Selecciona un tipo de cochera",
  }),
  estado: z.enum(["HABILITADA", "DESHABILITADA"], {
    message: "Selecciona un estado",
  }),
});

// Alta por planta: un sector y cuántas cocheras de cada tipo. Los números no
// se cargan acá: los genera el backend y vuelven en la respuesta.
const TIPOS_COCHERA = ["AUTO", "MOTO", "ACCESIBLE", "CARGA"];

const cantidadSchema = z
  .number({ message: "Ingresá una cantidad" })
  .int("Tiene que ser un número entero")
  .min(0, "No puede ser negativa");

const altaPorPlantaSchema = z.object({
  sector: z.string().trim().min(1, "El sector es obligatorio"),
  cantidades: z
    .object({
      AUTO: cantidadSchema,
      MOTO: cantidadSchema,
      ACCESIBLE: cantidadSchema,
      CARGA: cantidadSchema,
    })
    .refine((c) => Object.values(c).some((n) => n > 0), {
      message: "Indicá al menos un tipo con cantidad mayor a 0",
    }),
});

const cantidadesEnCero = () => ({ AUTO: 0, MOTO: 0, ACCESIBLE: 0, CARGA: 0 });

const OTRO_SECTOR = "__OTRO__";

// Agrupa las cocheras creadas por tipo, respetando el orden en que las
// devolvió el backend. Solo agrupa: los números se muestran tal cual llegan.
const agruparPorTipo = (cocherasCreadas) => {
  const grupos = new Map();
  cocherasCreadas.forEach((c) => {
    if (!grupos.has(c.tipo)) grupos.set(c.tipo, []);
    grupos.get(c.tipo).push(c.numero);
  });
  return Array.from(grupos.entries());
};

const inputClasses =
  "ui-input";

const labelClasses = "ui-label";

const TIPO_LABELS = {
  AUTO: "Auto",
  MOTO: "Moto",
  ACCESIBLE: "Accesible",
  CARGA: "Carga",
};

export default function CocherasManagement() {
  const [cocheras, setCocheras] = useState([]);
  const [loadingCocheras, setLoadingCocheras] = useState(true);
  const solicitudCocheras = useRef(0);
  const [editingCochera, setEditingCochera] = useState(null);

  const [filtroSector, setFiltroSector] = useState("");
  const [filtroTipo, setFiltroTipo] = useState("TODOS");
  const [filtroEstado, setFiltroEstado] = useState("TODOS");
  const [filtroFecha, setFiltroFecha] = useState("");

  // Sectores reales para los dropdowns (filtro, alta, edición y lote). Se
  // piden con su propio endpoint, sin depender del listado filtrado de la
  // tabla, para que la lista de opciones no se achique cuando el usuario
  // filtra por otra cosa.
  const [sectoresDisponibles, setSectoresDisponibles] = useState([]);

  // Si el sector elegido en el alta/edición es "otro" (no está en la lista),
  // se muestra un input de texto libre en vez del dropdown.
  const [sectorCreateEsNuevo, setSectorCreateEsNuevo] = useState(false);
  const [sectorEditEsNuevo, setSectorEditEsNuevo] = useState(false);
  const [sectorLoteEsNuevo, setSectorLoteEsNuevo] = useState(false);

  // Lo que devolvió el backend en la última alta por planta, para mostrarle
  // al admin exactamente qué números quedaron asignados.
  const [resultadoLote, setResultadoLote] = useState(null);

  const {
    register: registerCreate,
    handleSubmit: handleCreateSubmit,
    reset: resetCreate,
    setValue: setValueCreate,
    formState: { errors: createErrors, isSubmitting: isCreating },
  } = useForm({
    resolver: zodResolver(cocheraSchema),
    defaultValues: { numero: "", sector: "", tipo: "AUTO", estado: "HABILITADA" },
  });

  const {
    register: registerEdit,
    handleSubmit: handleEditSubmit,
    reset: resetEdit,
    setValue: setValueEdit,
    formState: { errors: editErrors, isSubmitting: isEditing },
  } = useForm({
    resolver: zodResolver(cocheraSchema),
    defaultValues: { numero: "", sector: "", tipo: "AUTO", estado: "HABILITADA" },
  });

  const {
    register: registerLote,
    control: controlLote,
    handleSubmit: handleLoteSubmit,
    reset: resetLote,
    setValue: setValueLote,
    formState: { errors: loteErrors, isSubmitting: isCreandoLote },
  } = useForm({
    resolver: zodResolver(altaPorPlantaSchema),
    defaultValues: { sector: "", cantidades: cantidadesEnCero() },
  });

  // Total a crear, para el texto del botón y para deshabilitarlo con todo en
  // 0. Un valor vacío o negativo no suma: el schema lo marca al enviar.
  const cantidadesLote = useWatch({ control: controlLote, name: "cantidades" });
  const totalLote = TIPOS_COCHERA.reduce((total, tipo) => {
    const n = cantidadesLote?.[tipo];
    return Number.isFinite(n) && n > 0 ? total + n : total;
  }, 0);

  const [sectoresCargados, setSectoresCargados] = useState(false);

  const cargarSectores = async () => {
    try {
      const response = await api.get("/api/v1/cocheras/sectores");
      setSectoresDisponibles(response.data);
    } catch {
      // No es critico: si falla, los dropdowns de sector quedan vacios (y
      // caen a texto libre) pero el resto de la pantalla sigue funcionando.
    } finally {
      setSectoresCargados(true);
    }
  };

  // Solo se bloquea en modo "sector nuevo" cuando YA se confirmo que no hay
  // ningun sector (instalacion nueva) — no en el instante inicial, cuando
  // sectoresDisponibles todavia esta vacio simplemente porque el pedido no
  // termino. Nunca se vuelve a false sola, eso lo decide el usuario.
  useEffect(() => {
    if (sectoresCargados && sectoresDisponibles.length === 0) {
      setSectorCreateEsNuevo(true);
      setSectorLoteEsNuevo(true);
    }
  }, [sectoresCargados, sectoresDisponibles.length]);

  const loadCocheras = useCallback(async () => {
    const solicitud = ++solicitudCocheras.current;
    try {
      setLoadingCocheras(true);

      const params = {};
      if (filtroSector.trim()) params.sector = filtroSector.trim();
      if (filtroTipo !== "TODOS") params.tipo = filtroTipo;
      if (filtroEstado !== "TODOS") params.estado = filtroEstado;
      // El filtro sigue siendo por día, pero la disponibilidad se calcula por
      // rango: se pregunta por el día entero, de 00:00 a 00:00 del siguiente.
      // Así "disponible el 24" significa "sin ninguna reserva ese día", que es
      // lo que significaba antes de que existieran las franjas.
      if (filtroFecha) {
        params.desde = `${filtroFecha}T00:00`;
        params.hasta = `${filtroFecha}T23:59`;
      }

      const response = await api.get("/api/v1/cocheras", { params });

      if (solicitud === solicitudCocheras.current) setCocheras(response.data);
    } catch (error) {
      if (solicitud === solicitudCocheras.current) {
        toast.error(
          error.response?.data?.message || "No se pudieron cargar las cocheras."
        );
      }
    } finally {
      if (solicitud === solicitudCocheras.current) setLoadingCocheras(false);
    }
  }, [filtroSector, filtroTipo, filtroEstado, filtroFecha]);

  useEffect(() => {
    cargarSectores();
  }, []);

  // Carga inicial y filtros inmediatos; una respuesta anterior no debe
  // reemplazar el resultado de los filtros actuales.
  useEffect(() => {
    loadCocheras();
    return () => { solicitudCocheras.current += 1; };
  }, [loadCocheras]);

  const refrescarTodo = async () => {
    await Promise.all([loadCocheras(), cargarSectores()]);
  };

  const onSectorSelectChange = (e, setValueForm, setEsNuevo, registerOnChange) => {
    if (e.target.value === OTRO_SECTOR) {
      setEsNuevo(true);
      setValueForm("sector", "", { shouldValidate: false });
    } else {
      registerOnChange(e);
    }
  };

  const onCreateCochera = async (data) => {
    try {
      await api.post("/api/v1/cocheras", data);

      toast.success("Cochera creada correctamente");

      resetCreate();
      setSectorCreateEsNuevo(false);

      await refrescarTodo();
    } catch (error) {
      toast.error(
        error.response?.data?.message || "No se pudo crear la cochera."
      );
    }
  };

  const onCrearPorPlanta = async (data) => {
    setResultadoLote(null);

    // Solo viajan los tipos con algo para crear; el backend ignora igual los
    // que vienen en 0, pero así el pedido dice exactamente lo que se pidió.
    const cantidades = Object.fromEntries(
      TIPOS_COCHERA.filter((tipo) => data.cantidades[tipo] > 0).map((tipo) => [
        tipo,
        data.cantidades[tipo],
      ])
    );

    try {
      const response = await api.post("/api/v1/cocheras/alta-por-planta", {
        sector: data.sector.trim(),
        cantidades,
      });

      const creadas = response.data;
      toast.success(
        `Se ${creadas.length === 1 ? "creó 1 cochera" : `crearon ${creadas.length} cocheras`}`
      );
      setResultadoLote(creadas);

      resetLote({ sector: "", cantidades: cantidadesEnCero() });
      setSectorLoteEsNuevo(false);

      await refrescarTodo();
    } catch (error) {
      // El backend es todo-o-nada: si algo falla, no crea ninguna. Su mensaje
      // se muestra tal cual y el formulario queda como estaba para corregir.
      toast.error(
        error.response?.data?.message || "No se pudieron crear las cocheras. No se creó ninguna."
      );
    }
  };

  const startEditing = (cochera) => {
    setEditingCochera(cochera);
    setSectorEditEsNuevo(!sectoresDisponibles.includes(cochera.sector));

    resetEdit({
      numero: cochera.numero,
      sector: cochera.sector,
      tipo: cochera.tipo,
      estado: cochera.estado,
    });
  };

  const cancelEditing = () => {
    setEditingCochera(null);
    setSectorEditEsNuevo(false);

    resetEdit({ numero: "", sector: "", tipo: "AUTO", estado: "HABILITADA" });
  };

  const onEditCochera = async (data) => {
    if (!editingCochera) {
      return;
    }

    if (
      editingCochera.estado === "HABILITADA" &&
      data.estado === "DESHABILITADA"
    ) {
      const confirmed = window.confirm(
        "Esta cochera tiene reservas confirmadas a futuro, se van a cancelar automáticamente al deshabilitarla. ¿Confirmás?"
      );

      if (!confirmed) {
        return;
      }
    }

    try {
      await api.put(`/api/v1/cocheras/${editingCochera.id}`, data);

      toast.success("Cochera actualizada correctamente");

      cancelEditing();

      await refrescarTodo();
    } catch (error) {
      toast.error(
        error.response?.data?.message || "No se pudo actualizar la cochera."
      );
    }
  };

  const deleteCochera = async (cochera) => {
    const confirmed = window.confirm(
      `¿Seguro que querés eliminar la cochera ${cochera.numero}?`
    );

    if (!confirmed) {
      return;
    }

    try {
      await api.delete(`/api/v1/cocheras/${cochera.id}`);

      toast.success("Cochera eliminada correctamente");

      if (editingCochera?.id === cochera.id) {
        cancelEditing();
      }

      await refrescarTodo();
    } catch (error) {
      toast.error(
        error.response?.data?.message || "No se pudo eliminar la cochera."
      );
    }
  };

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader actions={<LogoutButton />}>
          <Link
            href="/dashboard-admin"
            className="dashboard-back-link"
          >
            ← Volver al panel
          </Link>
        </DashboardHeader>

        <div className="mb-8">
          <h1 className="text-3xl font-extrabold tracking-tight text-ink">
            Gestión de cocheras
          </h1>

          <p className="mt-2 text-sm text-ink/60">
            Administrá las cocheras del predio: número, sector, tipo y estado.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
          <div className="xl:col-span-1">
            <div className="ui-card p-6">
              <h2 className="text-xl font-bold text-ink">Nueva cochera</h2>

              <p className="mt-1 mb-6 text-sm text-ink/60">
                Cargá una cochera nueva en el predio.
              </p>

              <form className="space-y-4" onSubmit={handleCreateSubmit(onCreateCochera)}>
                <div>
                  <label htmlFor="numero" className={labelClasses}>
                    Número
                  </label>

                  <input
                    id="numero"
                    {...registerCreate("numero")}
                    className={inputClasses}
                    placeholder="A-01"
                  />

                  {createErrors.numero && (
                    <p className="mt-1 text-sm text-red-500">
                      {createErrors.numero.message}
                    </p>
                  )}
                </div>

                <div>
                  <label htmlFor="sector" className={labelClasses}>
                    Sector
                  </label>

                  {sectorCreateEsNuevo ? (
                    <>
                      <input
                        id="sector"
                        {...registerCreate("sector")}
                        className={inputClasses}
                        placeholder="Nombre del sector nuevo"
                      />
                      {sectoresDisponibles.length > 0 && (
                        <button
                          type="button"
                          onClick={() => {
                            setSectorCreateEsNuevo(false);
                            setValueCreate("sector", "", { shouldValidate: false });
                          }}
                          className="mt-1 text-xs font-medium text-link hover:text-brand"
                        >
                          ‹ Elegir de la lista
                        </button>
                      )}
                    </>
                  ) : (
                    (() => {
                      const { onChange, ...sectorField } = registerCreate("sector");
                      return (
                        <select
                          id="sector"
                          {...sectorField}
                          onChange={(e) =>
                            onSectorSelectChange(e, setValueCreate, setSectorCreateEsNuevo, onChange)
                          }
                          className={inputClasses}
                          defaultValue=""
                        >
                          <option value="" disabled>
                            Seleccioná un sector
                          </option>
                          {sectoresDisponibles.map((sector) => (
                            <option key={sector} value={sector}>
                              {sector}
                            </option>
                          ))}
                          <option value={OTRO_SECTOR}>+ Otro (sector nuevo)</option>
                        </select>
                      );
                    })()
                  )}

                  {createErrors.sector && (
                    <p className="mt-1 text-sm text-red-500">
                      {createErrors.sector.message}
                    </p>
                  )}
                </div>

                <div>
                  <label htmlFor="tipo" className={labelClasses}>
                    Tipo
                  </label>

                  <select id="tipo" {...registerCreate("tipo")} className={inputClasses}>
                    <option value="AUTO">Auto</option>
                    <option value="MOTO">Moto</option>
                    <option value="ACCESIBLE">Accesible</option>
                    <option value="CARGA">Carga</option>
                  </select>

                  {createErrors.tipo && (
                    <p className="mt-1 text-sm text-red-500">
                      {createErrors.tipo.message}
                    </p>
                  )}
                </div>

                <div>
                  <label htmlFor="estado" className={labelClasses}>
                    Estado
                  </label>

                  <select id="estado" {...registerCreate("estado")} className={inputClasses}>
                    <option value="HABILITADA">Habilitada</option>
                    <option value="DESHABILITADA">Deshabilitada</option>
                  </select>

                  {createErrors.estado && (
                    <p className="mt-1 text-sm text-red-500">
                      {createErrors.estado.message}
                    </p>
                  )}
                </div>

                <button
                  type="submit"
                  disabled={isCreating}
                  className="ui-primary flex w-full justify-center px-3 py-3 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {isCreating ? "Creando..." : "Crear cochera"}
                </button>
              </form>
            </div>

            <div className="mt-8 ui-card p-6">
              <h2 className="text-xl font-bold text-ink">Alta por planta</h2>

              <p className="mt-1 mb-6 text-sm text-ink/60">
                Elegí un sector e indicá cuántas cocheras crear de cada tipo. Los
                números se asignan solos, a continuación de los que ya existen.
              </p>

              <form className="space-y-4" onSubmit={handleLoteSubmit(onCrearPorPlanta)}>
                <div>
                  <label htmlFor="lote-sector" className={labelClasses}>
                    Sector de la planta
                  </label>

                  {sectorLoteEsNuevo ? (
                    <>
                      <input
                        id="lote-sector"
                        {...registerLote("sector")}
                        className={inputClasses}
                        placeholder="Nombre del sector nuevo"
                      />
                      {sectoresDisponibles.length > 0 && (
                        <button
                          type="button"
                          onClick={() => {
                            setSectorLoteEsNuevo(false);
                            setValueLote("sector", "", { shouldValidate: false });
                          }}
                          className="mt-1 text-xs font-medium text-link hover:text-brand"
                        >
                          ‹ Elegir de la lista
                        </button>
                      )}
                    </>
                  ) : (
                    (() => {
                      const { onChange, ...sectorField } = registerLote("sector");
                      return (
                        <select
                          id="lote-sector"
                          {...sectorField}
                          onChange={(e) =>
                            onSectorSelectChange(e, setValueLote, setSectorLoteEsNuevo, onChange)
                          }
                          className={inputClasses}
                          defaultValue=""
                        >
                          <option value="" disabled>
                            Seleccioná un sector
                          </option>
                          {sectoresDisponibles.map((sector) => (
                            <option key={sector} value={sector}>
                              {sector}
                            </option>
                          ))}
                          <option value={OTRO_SECTOR}>+ Otro (sector nuevo)</option>
                        </select>
                      );
                    })()
                  )}

                  {loteErrors.sector && (
                    <p className="mt-1 text-sm text-red-500">
                      {loteErrors.sector.message}
                    </p>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-3">
                  {TIPOS_COCHERA.map((tipo) => (
                    <div key={tipo}>
                      <label htmlFor={`lote-cantidad-${tipo}`} className={labelClasses}>
                        {TIPO_LABELS[tipo]}
                      </label>

                      <input
                        id={`lote-cantidad-${tipo}`}
                        type="number"
                        min={0}
                        step={1}
                        inputMode="numeric"
                        {...registerLote(`cantidades.${tipo}`, { valueAsNumber: true })}
                        className={inputClasses}
                      />

                      {loteErrors.cantidades?.[tipo] && (
                        <p className="mt-1 text-xs text-red-500">
                          {loteErrors.cantidades[tipo].message}
                        </p>
                      )}
                    </div>
                  ))}
                </div>

                {loteErrors.cantidades?.root && (
                  <p className="text-sm text-red-500">{loteErrors.cantidades.root.message}</p>
                )}

                <button
                  type="submit"
                  disabled={isCreandoLote || totalLote === 0}
                  className="ui-primary flex w-full justify-center px-3 py-3 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {isCreandoLote
                    ? "Creando..."
                    : totalLote === 0
                      ? "Indicá cuántas crear"
                      : `Crear ${totalLote} ${totalLote === 1 ? "cochera" : "cocheras"}`}
                </button>
              </form>

              {resultadoLote && (
                <div
                  role="status"
                  className="mt-6 rounded-xl border border-accent/20 bg-accent/5 p-4"
                >
                  <p className="text-sm font-semibold text-ink">
                    {resultadoLote.length === 1
                      ? "Se creó 1 cochera"
                      : `Se crearon ${resultadoLote.length} cocheras`}
                    {resultadoLote[0]?.sector && ` en ${resultadoLote[0].sector}`}:
                  </p>

                  <ul className="mt-2 space-y-1 text-sm text-ink/70">
                    {agruparPorTipo(resultadoLote).map(([tipo, numeros]) => (
                      <li key={tipo}>
                        <span className="font-medium text-ink">{TIPO_LABELS[tipo] ?? tipo}:</span>{" "}
                        {numeros.join(", ")}
                      </li>
                    ))}
                  </ul>

                  <button
                    type="button"
                    onClick={() => setResultadoLote(null)}
                    className="mt-3 text-xs font-medium text-link hover:text-brand"
                  >
                    Ocultar
                  </button>
                </div>
              )}
            </div>
          </div>

          <div className="xl:col-span-2">
            {editingCochera && (
              <div className="mb-8 ui-card p-6">
                <div className="mb-6 flex items-start justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-bold text-ink">Editar cochera</h2>
                    <p className="mt-1 text-sm text-ink/60">{editingCochera.numero}</p>
                  </div>

                  <button
                    type="button"
                    onClick={cancelEditing}
                    className="rounded-lg px-3 py-2 text-sm font-medium text-ink/60 transition-colors hover:bg-ink/5 hover:text-ink"
                  >
                    Cancelar
                  </button>
                </div>

                <form className="space-y-5" onSubmit={handleEditSubmit(onEditCochera)}>
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div>
                      <label htmlFor="edit-numero" className={labelClasses}>
                        Número
                      </label>

                      <input id="edit-numero" {...registerEdit("numero")} className={inputClasses} />

                      {editErrors.numero && (
                        <p className="mt-1 text-sm text-red-500">{editErrors.numero.message}</p>
                      )}
                    </div>

                    <div>
                      <label htmlFor="edit-sector" className={labelClasses}>
                        Sector
                      </label>

                      {sectorEditEsNuevo ? (
                        <>
                          <input id="edit-sector" {...registerEdit("sector")} className={inputClasses} />
                          {sectoresDisponibles.length > 0 && (
                            <button
                              type="button"
                              onClick={() => {
                                setSectorEditEsNuevo(false);
                                setValueEdit("sector", "", { shouldValidate: false });
                              }}
                              className="mt-1 text-xs font-medium text-link hover:text-brand"
                            >
                              ‹ Elegir de la lista
                            </button>
                          )}
                        </>
                      ) : (
                        (() => {
                          const { onChange, ...sectorField } = registerEdit("sector");
                          return (
                            <select
                              id="edit-sector"
                              {...sectorField}
                              onChange={(e) =>
                                onSectorSelectChange(e, setValueEdit, setSectorEditEsNuevo, onChange)
                              }
                              className={inputClasses}
                            >
                              {sectoresDisponibles.map((sector) => (
                                <option key={sector} value={sector}>
                                  {sector}
                                </option>
                              ))}
                              <option value={OTRO_SECTOR}>+ Otro (sector nuevo)</option>
                            </select>
                          );
                        })()
                      )}

                      {editErrors.sector && (
                        <p className="mt-1 text-sm text-red-500">{editErrors.sector.message}</p>
                      )}
                    </div>

                    <div>
                      <label htmlFor="edit-tipo" className={labelClasses}>
                        Tipo
                      </label>

                      <select id="edit-tipo" {...registerEdit("tipo")} className={inputClasses}>
                        <option value="AUTO">Auto</option>
                        <option value="MOTO">Moto</option>
                        <option value="ACCESIBLE">Accesible</option>
                        <option value="CARGA">Carga</option>
                      </select>
                    </div>

                    <div>
                      <label htmlFor="edit-estado" className={labelClasses}>
                        Estado
                      </label>

                      <select id="edit-estado" {...registerEdit("estado")} className={inputClasses}>
                        <option value="HABILITADA">Habilitada</option>
                        <option value="DESHABILITADA">Deshabilitada</option>
                      </select>
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={isEditing}
                    className="ui-primary px-5 py-3 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {isEditing ? "Guardando..." : "Guardar cambios"}
                  </button>
                </form>
              </div>
            )}

            <div className="overflow-hidden ui-card">
              <div className="border-b border-ink/10 px-6 py-5">
                <h2 className="text-xl font-bold text-ink">Cocheras</h2>
                <p className="mt-1 text-sm text-ink/60">Cocheras registradas en el predio.</p>

                <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                  <select
                    value={filtroSector || "TODOS"}
                    onChange={(e) => setFiltroSector(e.target.value === "TODOS" ? "" : e.target.value)}
                    className={inputClasses}
                  >
                    <option value="TODOS">Todos los sectores</option>
                    {sectoresDisponibles.map((sector) => (
                      <option key={sector} value={sector}>
                        {sector}
                      </option>
                    ))}
                  </select>

                  <select
                    value={filtroTipo}
                    onChange={(e) => setFiltroTipo(e.target.value)}
                    className={inputClasses}
                  >
                    <option value="TODOS">Todos los tipos</option>
                    <option value="AUTO">Auto</option>
                    <option value="MOTO">Moto</option>
                    <option value="ACCESIBLE">Accesible</option>
                    <option value="CARGA">Carga</option>
                  </select>

                  <select
                    value={filtroEstado}
                    onChange={(e) => setFiltroEstado(e.target.value)}
                    className={inputClasses}
                  >
                    <option value="TODOS">Todos los estados</option>
                    <option value="HABILITADA">Habilitada</option>
                    <option value="DESHABILITADA">Deshabilitada</option>
                  </select>

                  <div>
                    <input
                      type="date"
                      aria-label="Filtrar por fecha"
                      value={filtroFecha}
                      onChange={(e) => setFiltroFecha(e.target.value)}
                      className={inputClasses}
                    />
                    {filtroFecha && (
                      <button
                        type="button"
                        onClick={() => setFiltroFecha("")}
                        className="mt-1 text-xs font-medium text-link hover:text-brand"
                      >
                        Quitar filtro de fecha
                      </button>
                    )}
                  </div>
                </div>
              </div>

              {loadingCocheras ? (
                <div className="p-8 text-center text-sm text-ink/60">
                  Cargando cocheras...
                </div>
              ) : cocheras.length === 0 ? (
                <div className="p-8 text-center text-sm text-ink/60">
                  No hay cocheras que coincidan con los filtros.
                </div>
              ) : (
                <div className="responsive-table-scroll overflow-x-auto">
                  <table role="table" className="responsive-table min-w-full divide-y divide-ink/10">
                    <thead role="rowgroup" className="bg-surface">
                      <tr role="row">
                        <th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Número
                        </th>
                        <th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Sector
                        </th>
                        <th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Tipo
                        </th>
                        <th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Estado
                        </th>
                        <th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                          {filtroFecha ? `Disponibilidad (${filtroFecha})` : "Disponibilidad"}
                        </th>
                        <th scope="col" role="columnheader" className="px-6 py-3 text-right text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Acciones
                        </th>
                      </tr>
                    </thead>

                    <tbody role="rowgroup" className="divide-y divide-ink/10">
                      {cocheras.map((cochera) => (
                        <tr role="row" key={cochera.id} className="transition-colors hover:bg-accent/5">
                          <td role="cell" data-label="Número" className="whitespace-nowrap px-6 py-4 font-medium text-ink">
                            {cochera.numero}
                          </td>

                          <td role="cell" data-label="Sector" className="whitespace-nowrap px-6 py-4 text-sm text-ink/70">
                            {cochera.sector}
                          </td>

                          <td role="cell" data-label="Tipo" className="whitespace-nowrap px-6 py-4">
                            <span className="rounded-full bg-accent/10 px-2.5 py-1 text-xs font-medium text-link ring-1 ring-inset ring-accent/20">
                              {TIPO_LABELS[cochera.tipo]}
                            </span>
                          </td>

                          <td role="cell" data-label="Estado" className="whitespace-nowrap px-6 py-4">
                            {cochera.estado === "HABILITADA" ? (
                              <span className="rounded-full bg-accent/10 px-2.5 py-1 text-xs font-medium text-link ring-1 ring-inset ring-accent/20">
                                Habilitada
                              </span>
                            ) : (
                              <span className="rounded-full bg-ink/5 px-2.5 py-1 text-xs font-medium text-ink/50 ring-1 ring-inset ring-ink/10">
                                Deshabilitada
                              </span>
                            )}
                          </td>

                          <td role="cell" data-label={filtroFecha ? `Disponibilidad (${filtroFecha})` : "Disponibilidad"} className="whitespace-nowrap px-6 py-4">
                            {cochera.disponibleEnFecha === true && (
                              <span className="rounded-full bg-accent/10 px-2.5 py-1 text-xs font-medium text-link ring-1 ring-inset ring-accent/20">
                                Libre
                              </span>
                            )}
                            {cochera.disponibleEnFecha === false && (
                              <span className="rounded-full bg-red-50 px-2.5 py-1 text-xs font-medium text-red-600 ring-1 ring-inset ring-red-200 dark:bg-red-500/10 dark:text-red-300 dark:ring-red-400/20 dark:border-red-400/25 dark:hover:bg-red-500/20">
                                Ocupada
                              </span>
                            )}
                            {(cochera.disponibleEnFecha === null || cochera.disponibleEnFecha === undefined) && (
                              <span className="text-xs text-ink/40">—</span>
                            )}
                          </td>

                          <td role="cell" data-label="Acciones" className="px-6 py-4">
                            <div className="flex justify-end gap-2">
                              <button
                                type="button"
                                onClick={() => startEditing(cochera)}
                                className="rounded-lg bg-ink/5 px-3 py-2 text-xs font-semibold text-ink ring-1 ring-inset ring-ink/15 transition-colors hover:bg-ink/10"
                              >
                                Editar
                              </button>

                              <button
                                type="button"
                                onClick={() => deleteCochera(cochera)}
                                className="rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-600 ring-1 ring-inset ring-red-200 transition-colors hover:bg-red-100 dark:bg-red-500/10 dark:text-red-300 dark:ring-red-400/20 dark:border-red-400/25 dark:hover:bg-red-500/20"
                              >
                                Eliminar
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}