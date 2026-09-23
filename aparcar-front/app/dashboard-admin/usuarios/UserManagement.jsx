"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";

import api from "@/app/api";
import LogoutButton from "@/components/LogoutButton";
import DashboardHeader from "@/components/DashboardHeader";

const createUserSchema = z.object({
  nombre: z
    .string()
    .min(1, "El nombre es obligatorio")
    .max(100, "El nombre no puede superar los 100 caracteres"),

  documento: z
    .string()
    .min(1, "El documento es obligatorio"),

  email: z
    .string()
    .email("Ingresá un correo válido"),

  password: z
    .string()
    .min(8, "La contraseña debe tener al menos 8 caracteres")
    .max(100, "La contraseña no puede superar los 100 caracteres"),

  telefono: z.string().optional(),
});

const editUserSchema = z.object({
  nombre: z
    .string()
    .min(1, "El nombre es obligatorio")
    .max(100, "El nombre no puede superar los 100 caracteres"),

  documento: z
    .string()
    .min(1, "El documento es obligatorio"),

  telefono: z.string().optional(),

  authorities: z
    .array(z.enum(["USER", "ADMIN"]))
    .min(1, "El usuario debe tener al menos un rol"),
});

const inputClasses =
  "ui-input";

const labelClasses =
  "ui-label";

export default function UserManagement() {
  const [users, setUsers] = useState([]);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [editingUser, setEditingUser] = useState(null);

  const {
    register: registerCreate,
    handleSubmit: handleCreateSubmit,
    reset: resetCreate,
    formState: {
      errors: createErrors,
      isSubmitting: isCreating,
    },
  } = useForm({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      nombre: "",
      documento: "",
      email: "",
      password: "",
      telefono: "",
    },
  });

  const {
    register: registerEdit,
    handleSubmit: handleEditSubmit,
    reset: resetEdit,
    formState: {
      errors: editErrors,
      isSubmitting: isEditing,
    },
  } = useForm({
    resolver: zodResolver(editUserSchema),
    defaultValues: {
      nombre: "",
      documento: "",
      telefono: "",
      authorities: [],
    },
  });

  const loadUsers = async () => {
    try {
      setLoadingUsers(true);

      const response = await api.get("/api/v1/usuarios");

      setUsers(response.data);
    } catch (error) {
      toast.error(
        error.response?.data?.message ||
          "No se pudieron cargar los usuarios."
      );
    } finally {
      setLoadingUsers(false);
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const onCreateUser = async (data) => {
    try {
      await api.post("/register", {
        nombre: data.nombre,
        documento: data.documento,
        email: data.email,
        password: data.password,
        telefono: data.telefono || null,
      });

      toast.success("Usuario creado correctamente");

      resetCreate();

      await loadUsers();
    } catch (error) {
      toast.error(
        error.response?.data?.message ||
          "No se pudo crear el usuario."
      );
    }
  };

  const startEditing = (user) => {
    setEditingUser(user);

    resetEdit({
      nombre: user.nombre,
      documento: user.documento || "",
      telefono: user.telefono || "",
      authorities: user.authorities || [],
    });
  };

  const cancelEditing = () => {
    setEditingUser(null);

    resetEdit({
      nombre: "",
      documento: "",
      telefono: "",
      authorities: [],
    });
  };

  const onEditUser = async (data) => {
    if (!editingUser) {
      return;
    }

    try {
      await api.put(`/api/v1/usuarios/${editingUser.id}`, {
        nombre: data.nombre,
        documento: data.documento,
        telefono: data.telefono || null,
        authorities: data.authorities,
      });

      toast.success("Usuario actualizado correctamente");

      cancelEditing();

      await loadUsers();
    } catch (error) {
      toast.error(
        error.response?.data?.message ||
          "No se pudo actualizar el usuario."
      );
    }
  };

  const activateUser = async (user) => {
    try {
      await api.post("/users/activate", {
        email: user.email,
      });

      toast.success("Usuario activado correctamente");

      await loadUsers();
    } catch (error) {
      toast.error(
        error.response?.data?.message ||
          "No se pudo activar el usuario."
      );
    }
  };

  const deleteUser = async (user) => {
    const confirmed = window.confirm(
      `¿Seguro que querés eliminar a ${user.nombre}?`
    );

    if (!confirmed) {
      return;
    }

    try {
      await api.delete("/users", {
        data: {
          email: user.email,
        },
      });

      toast.success("Usuario eliminado correctamente");

      if (editingUser?.id === user.id) {
        cancelEditing();
      }

      await loadUsers();
    } catch (error) {
      toast.error(
        error.response?.data?.message ||
          "No se pudo eliminar el usuario."
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
            Gestión de usuarios
          </h1>

          <p className="mt-2 text-sm text-ink/60">
            Administrá las cuentas de AparcAR. Cada cuenta es un visitante: el
            mismo registro sirve para iniciar sesión y para reservar.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
          <div className="xl:col-span-1">
            <div className="ui-card p-6">
              <h2 className="text-xl font-bold text-ink">
                Nuevo usuario
              </h2>

              <p className="mt-1 mb-6 text-sm text-ink/60">
                Alta administrativa: acá elegís vos la contraseña. Nace con rol
                USER y el rol se cambia editando la cuenta.
              </p>

              <form
                className="space-y-4"
                onSubmit={handleCreateSubmit(onCreateUser)}
              >
                <div>
                  <label
                    htmlFor="nombre"
                    className={labelClasses}
                  >
                    Nombre
                  </label>

                  <input
                    id="nombre"
                    {...registerCreate("nombre")}
                    className={inputClasses}
                    placeholder="Nombre completo"
                  />

                  {createErrors.nombre && (
                    <p className="mt-1 text-sm text-red-500">
                      {createErrors.nombre.message}
                    </p>
                  )}
                </div>

                <div>
                  <label
                    htmlFor="documento"
                    className={labelClasses}
                  >
                    Documento
                  </label>

                  <input
                    id="documento"
                    {...registerCreate("documento")}
                    className={inputClasses}
                    placeholder="DNI / documento"
                  />

                  {createErrors.documento && (
                    <p className="mt-1 text-sm text-red-500">
                      {createErrors.documento.message}
                    </p>
                  )}
                </div>

                <div>
                  <label
                    htmlFor="email"
                    className={labelClasses}
                  >
                    Email
                  </label>

                  <input
                    id="email"
                    type="email"
                    {...registerCreate("email")}
                    className={inputClasses}
                    placeholder="usuario@aparcar.com"
                  />

                  {createErrors.email && (
                    <p className="mt-1 text-sm text-red-500">
                      {createErrors.email.message}
                    </p>
                  )}
                </div>

                <div>
                  <label
                    htmlFor="password"
                    className={labelClasses}
                  >
                    Contraseña
                  </label>

                  <input
                    id="password"
                    type="password"
                    {...registerCreate("password")}
                    className={inputClasses}
                    placeholder="Mínimo 8 caracteres"
                  />

                  {createErrors.password && (
                    <p className="mt-1 text-sm text-red-500">
                      {createErrors.password.message}
                    </p>
                  )}
                </div>

                <div>
                  <label
                    htmlFor="telefono"
                    className={labelClasses}
                  >
                    Teléfono
                  </label>

                  <input
                    id="telefono"
                    {...registerCreate("telefono")}
                    className={inputClasses}
                    placeholder="Opcional"
                  />
                </div>

                <button
                  type="submit"
                  disabled={isCreating}
                  className="ui-primary flex w-full justify-center px-3 py-3 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {isCreating
                    ? "Creando..."
                    : "Crear usuario"}
                </button>
              </form>
            </div>
          </div>

          <div className="xl:col-span-2">
            {editingUser && (
              <div className="mb-8 ui-card p-6">
                <div className="mb-6 flex items-start justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-bold text-ink">
                      Editar usuario
                    </h2>

                    <p className="mt-1 text-sm text-ink/60">
                      {editingUser.email}
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={cancelEditing}
                    className="rounded-lg px-3 py-2 text-sm font-medium text-ink/60 transition-colors hover:bg-ink/5 hover:text-ink"
                  >
                    Cancelar
                  </button>
                </div>

                <form
                  className="space-y-5"
                  onSubmit={handleEditSubmit(onEditUser)}
                >
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div>
                      <label
                        htmlFor="edit-nombre"
                        className={labelClasses}
                      >
                        Nombre
                      </label>

                      <input
                        id="edit-nombre"
                        {...registerEdit("nombre")}
                        className={inputClasses}
                      />

                      {editErrors.nombre && (
                        <p className="mt-1 text-sm text-red-500">
                          {editErrors.nombre.message}
                        </p>
                      )}
                    </div>

                    <div>
                      <label
                        htmlFor="edit-documento"
                        className={labelClasses}
                      >
                        Documento
                      </label>

                      <input
                        id="edit-documento"
                        {...registerEdit("documento")}
                        className={inputClasses}
                      />

                      {editErrors.documento && (
                        <p className="mt-1 text-sm text-red-500">
                          {editErrors.documento.message}
                        </p>
                      )}
                    </div>

                    <div>
                      <label
                        htmlFor="edit-telefono"
                        className={labelClasses}
                      >
                        Teléfono
                      </label>

                      <input
                        id="edit-telefono"
                        {...registerEdit("telefono")}
                        className={inputClasses}
                        placeholder="Opcional"
                      />
                    </div>
                  </div>

                  <div>
                    <span className={labelClasses}>
                      Roles
                    </span>

                    <div className="mt-2 flex flex-wrap gap-4">
                      <label className="flex cursor-pointer items-center gap-2 text-sm text-ink/70">
                        <input
                          type="checkbox"
                          value="USER"
                          {...registerEdit("authorities")}
                          className="h-4 w-4 rounded border-ink/20 bg-surface accent-accent"
                        />

                        USER
                      </label>

                      <label className="flex cursor-pointer items-center gap-2 text-sm text-ink/70">
                        <input
                          type="checkbox"
                          value="ADMIN"
                          {...registerEdit("authorities")}
                          className="h-4 w-4 rounded border-ink/20 bg-surface accent-accent"
                        />

                        ADMIN
                      </label>
                    </div>

                    {editErrors.authorities && (
                      <p className="mt-1 text-sm text-red-500">
                        {editErrors.authorities.message}
                      </p>
                    )}
                  </div>

                  <button
                    type="submit"
                    disabled={isEditing}
                    className="ui-primary px-5 py-3 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {isEditing
                      ? "Guardando..."
                      : "Guardar cambios"}
                  </button>
                </form>
              </div>
            )}

            <div className="overflow-hidden ui-card">
              <div className="border-b border-ink/10 px-6 py-5">
                <h2 className="text-xl font-bold text-ink">
                  Usuarios
                </h2>

                <p className="mt-1 text-sm text-ink/60">
                  Cuentas registradas en el sistema.
                </p>
              </div>

              {loadingUsers ? (
                <div className="p-8 text-center text-sm text-ink/60">
                  Cargando usuarios...
                </div>
              ) : users.length === 0 ? (
                <div className="p-8 text-center text-sm text-ink/60">
                  No hay usuarios registrados.
                </div>
              ) : (
                <div className="responsive-table-scroll overflow-x-auto">
                  <table role="table" className="responsive-table min-w-full divide-y divide-ink/10">
                    <thead role="rowgroup" className="bg-surface">
                      <tr role="row">
                        <th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Usuario
                        </th>

                        <th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Documento
                        </th>

                        <th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Teléfono
                        </th>

                        <th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Roles
                        </th>

                        <th scope="col" role="columnheader" className="px-6 py-3 text-right text-xs font-semibold uppercase tracking-wider text-ink/50">
                          Acciones
                        </th>
                      </tr>
                    </thead>

                    <tbody role="rowgroup" className="divide-y divide-ink/10">
                      {users.map((user) => (
                        <tr role="row"
                          key={user.id}
                          className="transition-colors hover:bg-accent/5"
                        >
                          <td role="cell" data-label="Usuario" className="whitespace-nowrap px-6 py-4">
                            <p className="font-medium text-ink">
                              {user.nombre}
                            </p>

                            <p className="text-sm text-ink/60">
                              {user.email}
                            </p>
                          </td>

                          <td role="cell" data-label="Documento" className="whitespace-nowrap px-6 py-4 text-sm text-ink/70">
                            {user.documento || "—"}
                          </td>

                          <td role="cell" data-label="Teléfono" className="whitespace-nowrap px-6 py-4 text-sm text-ink/70">
                            {user.telefono || "—"}
                          </td>

                          <td role="cell" data-label="Roles" className="px-6 py-4">
                            <div className="flex flex-wrap gap-2">
                              {user.authorities?.map(
                                (authority) => (
                                  <span
                                    key={authority}
                                    className="rounded-full bg-accent/10 px-2.5 py-1 text-xs font-medium text-link ring-1 ring-inset ring-accent/20"
                                  >
                                    {authority}
                                  </span>
                                )
                              )}
                            </div>
                          </td>

                          <td role="cell" data-label="Acciones" className="px-6 py-4">
                            <div className="flex justify-end gap-2">
                              {!user.isActive && (
                                <button
                                  type="button"
                                  onClick={() =>
                                    activateUser(user)
                                  }
                                  className="ui-primary rounded-lg px-3 py-2 text-xs font-semibold transition-colors"
                                >
                                  Activar
                                </button>
                              )}

                              <button
                                type="button"
                                onClick={() =>
                                  startEditing(user)
                                }
                                className="rounded-lg bg-ink/5 px-3 py-2 text-xs font-semibold text-ink ring-1 ring-inset ring-ink/15 transition-colors hover:bg-ink/10"
                              >
                                Editar
                              </button>

                              <button
                                type="button"
                                onClick={() =>
                                  deleteUser(user)
                                }
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
