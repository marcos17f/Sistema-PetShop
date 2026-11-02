import type { Metadata } from "next";
import { Sair } from "./Sair";

export const metadata: Metadata = { title: "Sair da lista de promoções", robots: { index: false } };

export default function Pagina() {
  return <Sair />;
}
