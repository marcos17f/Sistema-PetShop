-- Só PostgreSQL: clientes que aceitaram promoções antes da V11 ficaram sem o link pessoal de descadastro.
-- gen_random_uuid() é aleatório de forma criptográfica (não dá para adivinhar o link de outra pessoa).
UPDATE tutor
SET token_descadastro = replace(gen_random_uuid()::text, '-', '')
WHERE consentimento_marketing_em IS NOT NULL AND token_descadastro IS NULL;
