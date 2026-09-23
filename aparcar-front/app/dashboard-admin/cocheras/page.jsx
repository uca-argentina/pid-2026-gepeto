import { requireAuth } from "@/utils/serverAuth";
import CocherasManagement from "./CocherasManagement";

export default async function CocherasPage() {
  await requireAuth(["ADMIN"]);

  return <CocherasManagement />;
}