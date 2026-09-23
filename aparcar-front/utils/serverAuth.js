import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { jwtDecode } from "jwt-decode";

export async function requireAuth(allowedRoles = []) {
  const cookieStore = await cookies();
  const token = cookieStore.get("JWT")?.value;

  if (!token) {
    redirect("/login");
  }

  let decoded;
  try {
    decoded = jwtDecode(token);
  } catch (error) {
    redirect("/login");
  }

  // El backend emite el claim "authorities" como string separado por comas
  // (ej: "USER,ADMIN"), no un "role" único.
  const roles = decoded.authorities
    ? decoded.authorities.split(',').map((r) => r.trim()).filter(Boolean)
    : [];

  if (allowedRoles.length > 0 && !allowedRoles.some((r) => roles.includes(r))) {
    redirect("/unauthorized");
  }

  return { ...decoded, roles };
}
