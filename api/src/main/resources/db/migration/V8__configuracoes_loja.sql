-- Configurações da loja editáveis no painel (antes ficavam fixas no código do site).
ALTER TABLE loja ADD COLUMN cidade VARCHAR(80);
ALTER TABLE loja ADD COLUMN endereco VARCHAR(200);
ALTER TABLE loja ADD COLUMN horario_texto VARCHAR(120);
ALTER TABLE loja ADD COLUMN instagram VARCHAR(60);
ALTER TABLE loja ADD COLUMN link_avaliacao VARCHAR(300);
-- Agenda: intervalo mínimo entre atendimentos e horário de funcionamento (domingo fechado)
ALTER TABLE loja ADD COLUMN intervalo_agenda_minutos INTEGER DEFAULT 40 NOT NULL;
ALTER TABLE loja ADD COLUMN abre TIME DEFAULT '08:00' NOT NULL;
ALTER TABLE loja ADD COLUMN fecha TIME DEFAULT '18:00' NOT NULL;
ALTER TABLE loja ADD COLUMN sabado_abre TIME DEFAULT '08:00';
ALTER TABLE loja ADD COLUMN sabado_fecha TIME DEFAULT '12:00';

-- [Valores de exemplo: o Cleiton troca pela tela Configurações]
UPDATE loja SET cidade = 'Goiânia – GO', endereco = 'Rua Exemplo, 123 · Centro',
    horario_texto = 'Segunda a sexta, 8h às 18h · Sábado, 8h às 12h', instagram = 'petshopdocleiton'
WHERE id = 1;
