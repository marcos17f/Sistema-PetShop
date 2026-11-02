import type { Metadata } from "next";
import { AgendarOnline } from "./AgendarOnline";

export const metadata: Metadata = {
  title: "Agendar online",
  description: "Escolha o serviço, o dia e o horário livre. Confirmação pelo WhatsApp.",
};

export default function Agendar() {
  return <AgendarOnline />;
}
