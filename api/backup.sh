#!/bin/sh
# Backup diário do banco do pet shop (rodar no servidor, na pasta api).
# Agendar no cron às 3h:   0 3 * * * cd /caminho/api && ./backup.sh >> backups/backup.log 2>&1
# Restaurar um backup:      gunzip -c backups/petshop-AAAA-MM-DD.sql.gz | docker compose exec -T postgres psql -U petshop -d petshop
set -eu

PASTA="${BACKUP_PASTA:-backups}"
DIAS="${BACKUP_DIAS:-30}"   # quantos dias de backup manter
mkdir -p "$PASTA"
ARQUIVO="$PASTA/petshop-$(date +%F).sql.gz"

# --clean: o arquivo apaga e recria as tabelas ao restaurar; o gatilho do histórico vem junto.
docker compose exec -T postgres pg_dump -U petshop -d petshop --clean --if-exists | gzip > "$ARQUIVO.tmp"
mv "$ARQUIVO.tmp" "$ARQUIVO"   # só troca quando o dump terminou inteiro
chmod 600 "$ARQUIVO"           # dados de clientes: só o dono do servidor lê

find "$PASTA" -name 'petshop-*.sql.gz' -mtime +"$DIAS" -delete
echo "$(date '+%F %T') backup ok: $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1))"
