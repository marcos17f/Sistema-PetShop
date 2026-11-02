-- Agendamento online pelo cliente e busca de moto; consentimento de marketing (LGPD) para as campanhas.
ALTER TABLE agendamento ADD COLUMN canal VARCHAR(20) DEFAULT 'PAINEL' NOT NULL;
ALTER TABLE agendamento ADD COLUMN busca_moto BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE agendamento ADD COLUMN endereco_busca VARCHAR(200);

-- Quando o cliente aceitou receber promoções (null = não aceitou). Só quem aceitou recebe campanha.
ALTER TABLE tutor ADD COLUMN consentimento_marketing_em TIMESTAMP WITH TIME ZONE;
