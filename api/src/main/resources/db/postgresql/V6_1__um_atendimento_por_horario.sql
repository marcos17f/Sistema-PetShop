-- Só PostgreSQL: um atendimento ativo por horário na loja, mesmo se duas pessoas salvarem ao mesmo tempo.
-- Cancelado não conta (libera o horário). A API também confere antes de salvar e responde 409 com mensagem clara.
CREATE UNIQUE INDEX ux_agendamento_horario_ativo
    ON agendamento (loja_id, data, hora)
    WHERE status <> 'CANCELADO';
