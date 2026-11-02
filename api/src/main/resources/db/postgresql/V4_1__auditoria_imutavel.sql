-- Só PostgreSQL (pasta db/{vendor}): o histórico não pode ser alterado nem apagado,
-- nem por SQL direto com o usuário da aplicação. Para remover a trava é preciso derrubar o gatilho,
-- o que exige acesso de dono do banco e fica registrado no log do PostgreSQL.
CREATE FUNCTION auditoria_imutavel() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'O histórico de alterações não pode ser alterado nem apagado';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_auditoria_sem_alteracao
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_imutavel();

CREATE TRIGGER tg_auditoria_sem_truncate
    BEFORE TRUNCATE ON auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_imutavel();
