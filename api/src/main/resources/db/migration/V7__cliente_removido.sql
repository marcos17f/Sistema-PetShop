-- Cliente excluído que já tinha atendimento concluído: os dados pessoais são apagados (LGPD),
-- mas o registro fica para os valores continuarem no financeiro. Cliente sem atendimento concluído é apagado de verdade.
ALTER TABLE tutor ADD COLUMN removido_em TIMESTAMP WITH TIME ZONE;
