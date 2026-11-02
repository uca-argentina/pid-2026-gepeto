import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
const { get, post, put, borrar } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), borrar: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get, post, put, delete: borrar } }));
import ConveniosCredenciales from "@/components/ConveniosCredenciales";
import useConvenios from "@/hooks/useConvenios";

const catalogo = [
  { id: "uca", nombre: "UCA", formatoCredencial: "UCA-XXX", descuentoPct: 15 },
  { id: "nacion", nombre: "Club Nación", formatoCredencial: "NACION-XXX", descuentoPct: 10 },
];
let guardadas;
function Formulario(props) {
  const modelo = useConvenios(props);
  return <><ConveniosCredenciales modelo={modelo} guardar={props.guardar} />
    <output data-testid="seleccion">{JSON.stringify(modelo.credenciales)}</output></>;
}
beforeEach(() => {
  vi.resetAllMocks();
  guardadas = [];
  get.mockImplementation((url) => Promise.resolve({ data: url.endsWith("/credenciales") ? guardadas : catalogo }));
  post.mockImplementation((url, body) => Promise.resolve({ data: body }));
  put.mockImplementation((url, body) => Promise.resolve({ data: body }));
  borrar.mockResolvedValue({});
});

it("solo incorpora credenciales verificadas y descarta la validación al editar", async () => {
  const user = userEvent.setup();
  render(<Formulario />);
  await user.type(await screen.findByLabelText("Credencial UCA"), "UCA-123");
  expect(screen.getByTestId("seleccion")).toHaveTextContent("[]");
  await user.click(screen.getAllByRole("button", { name: "Verificar" })[0]);
  expect(await screen.findByText("Credencial validada para esta reserva.")).toBeInTheDocument();
  expect(screen.getByTestId("seleccion")).toHaveTextContent('"credencial":"UCA-123"');
  expect(post).toHaveBeenCalledWith("/api/v1/convenios/verificar", { convenioId: "uca", credencial: "UCA-123" });
  await user.type(screen.getByLabelText("Credencial UCA"), "4");
  expect(screen.getByTestId("seleccion")).toHaveTextContent("[]");
});

it("permite guardar y eliminar una credencial desde Mis datos", async () => {
  const user = userEvent.setup();
  render(<Formulario guardar />);
  await user.type(await screen.findByLabelText("Credencial UCA"), "UCA-123");
  await user.click(screen.getAllByRole("button", { name: "Verificar y guardar" })[0]);
  expect(await screen.findByText("Credencial validada y guardada en Mis datos.")).toBeInTheDocument();
  expect(put).toHaveBeenCalledWith("/api/v1/convenios/credenciales", { convenioId: "uca", credencial: "UCA-123" });
  await user.click(screen.getByRole("button", { name: "Eliminar" }));
  await waitFor(() => expect(screen.getByLabelText("Credencial UCA")).toHaveValue(""));
  expect(borrar).toHaveBeenCalledWith("/api/v1/convenios/credenciales/uca");
  expect(screen.getByTestId("seleccion")).toHaveTextContent("[]");
});

it("recupera credenciales guardadas como verificadas para la reserva", async () => {
  guardadas = [{ convenioId: "uca", credencial: "UCA-123" }];
  render(<Formulario visitanteId="v1" />);
  expect(await screen.findByLabelText("Credencial UCA")).toHaveValue("UCA-123");
  expect(screen.getByLabelText("Credencial UCA")).toHaveAttribute("readonly");
  expect(screen.getByText("Credencial validada y guardada en Mis datos.")).toBeInTheDocument();
  expect(get).toHaveBeenCalledWith("/api/v1/convenios/credenciales", { params: { visitanteId: "v1" } });
});

it("ignora una verificación pendiente del documento anterior", async () => {
  let resolver;
  post.mockImplementation(() => new Promise((resolve) => { resolver = resolve; }));
  const user = userEvent.setup();
  const { rerender } = render(<Formulario documento="40123123" />);
  await user.type(await screen.findByLabelText("Credencial UCA"), "UCA-123");
  await user.click(screen.getAllByRole("button", { name: "Verificar" })[0]);
  expect(post).toHaveBeenCalledWith("/api/v1/convenios/verificar", {
    convenioId: "uca", credencial: "UCA-123", documento: "40123123",
  });
  rerender(<Formulario documento="40123999" />);
  await act(async () => resolver({ data: { credencial: "UCA-123" } }));
  expect(screen.getByTestId("seleccion")).toHaveTextContent("[]");
  expect(screen.getByLabelText("Credencial UCA")).toHaveValue("");
});

it("muestra el rechazo del documento sin aplicar el descuento", async () => {
  post.mockRejectedValue({ response: { data: { message: "La credencial no coincide con tu documento." } } });
  render(<Formulario />);
  fireEvent.change(await screen.findByLabelText("Credencial UCA"), { target: { value: "UCA-000" } });
  fireEvent.click(screen.getAllByRole("button", { name: "Verificar" })[0]);
  expect(await screen.findByRole("alert")).toHaveTextContent("La credencial no coincide con tu documento.");
  expect(screen.getByTestId("seleccion")).toHaveTextContent("[]");
  expect(within(screen.getByRole("group")).getByText(/No se acumulan/)).toBeInTheDocument();
});
