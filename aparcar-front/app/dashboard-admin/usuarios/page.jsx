import { requireAuth } from "@/utils/serverAuth";
import UserManagement from "./UserManagement";

export default async function UsuariosPage() {
  await requireAuth(["ADMIN"]);

  return <UserManagement />;
}