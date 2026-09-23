"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useForm, useFieldArray } from "react-hook-form";
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

const loteSchema = z.object({
  cocheras: z.array(cocheraSchema).min(1, "Agregá al menos una cochera"),
});

const OTRO_SECTOR = "__OTRO__";

const filaVacia = () => ({ numero: "", sector: "", tipo: "AUTO", estado: "HABILITADA" });

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
    formState: { errors: loteErrors, isSubmitting: isCreandoLote },
  } = useForm({
    resolver: zodResolver(loteSchema),
    defaultValues: { cocheras: [filaVacia()] },
  });

  const {
    fields: filasLote,
    append: agregarFila,
    remove: quitarFila,
  } = useFieldArray({ control: controlLote, name: "cocheras" });

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
      if (filtroFecha) params.fecha = filtroFecha;

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

  const onCrearLote = async (data) => {
    try {
      const response = await api.post("/api/v1/cocheras/bulk", data.cocheras);

      toast.success(`${response.data.length} cocheras creadas correctamente`);

      resetLote({ cocheras: [filaVacia()] });

      await refrescarTodo();
    } catch (error) {
      // El backend es todo-o-nada: si algo del lote falla, no crea ninguna y
      // devuelve un mensaje que dice exactamente cual numero/fila fallo y
      // por que. Se lo mostramos tal cual, no lo reinterpretamos.
      toast.error(
        error.response?.data?.message || "No se pudo crear el lote de cocheras. No se creó ninguna."
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
              <h2 className="text-xl font-bold text-ink">Alta en lote</h2>

              <p className="mt-1 mb-6 text-sm text-ink/60">
                Cargá varias cocheras a la vez. Si alguna fila tiene un error,
                no se crea ninguna del lote.
              </p>

              <form className="space-y-4" onSubmit={handleLoteSubmit(onCrearLote)}>
                <div className="space-y-3">
                  {filasLote.map((fila, index) => (
                    <div
                      key={fila.id}
                      className="space-y-2 rounded-xl border border-ink/10 p-3"
                    >
                      <input
                        {...registerLote(`cocheras.${index}.numero`)}
                        className={inputClasses}
                        placeholder="A-01"
                        aria-label={`Número fila ${index + 1}`}
                      />
                      {loteErrors.cocheras?.[index]?.numero && (
                        <p className="text-xs text-red-500">
                          {loteErrors.cocheras[index].numero.message}
                        </p>
                      )}

                      {sectoresDisponibles.length === 0 ? (
                        <input
                          {...registerLote(`cocheras.${index}.sector`)}
                          className={inputClasses}
                          placeholder="Sector"
                          aria-label={`Sector fila ${index + 1}`}
                        />
                      ) : (
                        <select
                          {...registerLote(`cocheras.${index}.sector`)}
                          className={inputClasses}
                          defaultValue=""
                          aria-label={`Sector fila ${index + 1}`}
                        >
                          <option value="" disabled>
                            Seleccioná un sector
                          </option>
                          {sectoresDisponibles.map((sector) => (
                            <option key={sector} value={sector}>
                              {sector}
                            </option>
                          ))}
                        </select>
                      )}
                      {loteErrors.cocheras?.[index]?.sector && (
                        <p className="text-xs text-red-500">
                          {loteErrors.cocheras[index].sector.message}
                        </p>
                      )}

                      <div className="grid grid-cols-2 gap-2">
                        <select
                          {...registerLote(`cocheras.${index}.tipo`)}
                          className={inputClasses}
                          aria-label={`Tipo fila ${index + 1}`}
                        >
                          <option value="AUTO">Auto</option>
                          <option value="MOTO">Moto</option>
                          <option value="ACCESIBLE">Accesible</option>
                          <option value="CARGA">Carga</option>
                        </select>

                        <select
                          {...registerLote(`cocheras.${index}.estado`)}
                          className={inputClasses}
                          aria-label={`Estado fila ${index + 1}`}
                        >
                          <option value="HABILITADA">Habilitada</option>
                          <option value="DESHABILITADA">Deshabilitada</option>
                        </select>
                      </div>

                      <button
                        type="button"
                        onClick={() => quitarFila(index)}
                        disabled={filasLote.length === 1}
                        className="text-xs font-semibold text-red-600 hover:text-red-700 disabled:cursor-not-allowed disabled:opacity-40"
                      >
                        Quitar fila
                      </button>
                    </div>
                  ))}
                </div>

                <div className="flex flex-wrap gap-3">
                  <button
                    type="button"
                    onClick={() => agregarFila(filaVacia())}
                    className="rounded-xl bg-ink/5 px-4 py-2.5 text-sm font-semibold text-ink ring-1 ring-inset ring-ink/15 transition-colors hover:bg-ink/10"
                  >
                    + Agregar fila
                  </button>

                  <button
                    type="submit"
                    disabled={isCreandoLote}
                    className="ui-primary flex-1 px-4 py-2.5 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {isCreandoLote
                      ? "Creando..."
                      : `Crear ${filasLote.length} ${filasLote.length === 1 ? "cochera" : "cocheras"}`}
                  </button>
                </div>
              </form>
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
