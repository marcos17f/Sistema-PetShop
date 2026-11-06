-- Dados reais da A Arca Pet (06/11/2026). Só troca o que ainda era exemplo, para não apagar o que o dono já salvou
-- na tela Configurações.
UPDATE loja SET
    cidade = 'Aparecida de Goiânia – GO',
    endereco = 'Rua 8, Quadra 055, Lote 36, Sala 2 – Jardim Tiradentes',
    instagram = 'aarcapet.oficial'
WHERE id = 1;
-- [PREENCHER] WhatsApp e horário reais: tira o número fictício; a tela Configurações exige o WhatsApp ao salvar
UPDATE loja SET whatsapp = NULL WHERE id = 1 AND whatsapp = '5562999990000';
UPDATE loja SET horario_texto = '[PREENCHER] horário de funcionamento'
WHERE id = 1 AND horario_texto = 'Segunda a sexta, 8h às 18h · Sábado, 8h às 12h';
