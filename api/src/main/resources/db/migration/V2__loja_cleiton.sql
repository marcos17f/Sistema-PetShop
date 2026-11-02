-- [Trocar nome e WhatsApp pelos dados reais da loja]
INSERT INTO loja (id, slug, nome, whatsapp) VALUES (1, 'petshop-cleiton', '[Nome do Pet Shop]', NULL);

ALTER TABLE loja ALTER COLUMN id RESTART WITH 100;
