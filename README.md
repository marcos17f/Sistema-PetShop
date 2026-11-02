# Sistema do Pet Shop — MarcosLab

Site público + painel da loja (Next.js) e API (Spring Boot + PostgreSQL).

```
petshop-cleiton/
├── site/   → Next.js 15 (site, login e painel: agenda, clientes/pets, vacinas, lembretes, financeiro)
└── api/    → Spring Boot 3.5 / Java 21 LTS (REST, Flyway, PostgreSQL, Spring Security)
```

A API usa Java 21 (LTS) para compilar e rodar, inclusive no contêiner. Se a IDE estiver com outro JDK,
configure o projeto para o JDK 21 (no IntelliJ: *File → Project Structure → SDK*).

## 1. Rodar só o site (modo demonstração)

Sem a API configurada, o painel funciona com dados de exemplo em memória. É assim que se mostra para o cliente.

```bash
cd site
npm install
npm run dev
```

Abra http://localhost:3000 (site) e http://localhost:3000/painel (painel; no modo demonstração qualquer login entra).

## 2. Rodar com a API de verdade

```bash
cd api
docker compose up -d          # PostgreSQL em localhost:5432 (usuário/senha: petshop)
mvn test                      # roda os testes de integração (H2)
mvn spring-boot:run           # API em http://localhost:8080
```

No IntelliJ: *File → Open* na pasta `api` (abre como projeto Maven) e rode `PetshopApiApplication`.

Depois, no site, crie `site/.env.local`:

```
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_LOJA_SLUG=petshop-cleiton
```

Login do painel: usuário `loja`, senha `troque-esta-senha` (padrão de desenvolvimento).

> **Maven com erro `PKIX path validation failed`?** Algum antivírus/proxy está inspecionando o HTTPS.
> Rode com `MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"` para o Java usar os certificados do Windows.

### Variáveis de ambiente da API

| Variável | Padrão | Para quê |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | localhost / petshop / petshop | Banco PostgreSQL |
| `PAINEL_USUARIO` | `loja` | Usuário do painel |
| `PAINEL_SENHA` | `{noop}troque-esta-senha` | Senha. **Em produção use `{bcrypt}<hash>`** |
| `PAINEL_LOJA` | `petshop-cleiton` | Slug da loja que esse login acessa |
| `CORS_ORIGENS` | `http://localhost:3000` | Endereço do site (separe vários por vírgula) |
| `WHATSAPP_AUTOMATICO` | `false` | Liga o disparo automático às 18h |

## 3. Endpoints (todos com HTTP Basic)

Base: `/api/lojas/{slug}`

| Método | Caminho | O que faz |
|---|---|---|
| GET | `/tutores?busca=` | Lista/busca clientes (nome, WhatsApp ou nome do pet) |
| GET | `/tutores/{id}` | Ficha do cliente com os pets |
| POST | `/tutores` | Novo cliente |
| PUT / DELETE | `/tutores/{id}` | Edita o cliente / exclui: cadastro por engano é apagado; com atendimento concluído, apaga os dados pessoais (LGPD) e mantém os valores no financeiro |
| POST | `/tutores/{id}/pets` | Novo pet (pode informar o último banho) |
| PUT / DELETE | `/tutores/{id}/pets/{petId}` | Edita o pet (inclusive o último banho) / exclui pet cadastrado por engano (com atendimento concluído, não) |
| GET/POST | `/pets/{petId}/vacinas` | Carteira de vacinação |
| PUT / DELETE | `/pets/{petId}/vacinas/{id}` | Corrige / apaga vacina registrada errado |
| GET | `/agenda?data=yyyy-MM-dd` | Agenda do dia |
| POST | `/agenda` | Novo agendamento. **Intervalo mínimo entre atendimentos** (tela Configurações, padrão 40 min, de qualquer cliente); cancelado libera o horário |
| PUT / DELETE | `/agenda/{id}` | Edita (pet, serviço, data, hora, detalhe) / exclui agendamento lançado por engano. Concluído não é editado nem excluído |
| PATCH | `/agenda/{id}/status` | Muda status e, opcional, `valor` cobrado. Concluir sem valor usa o preço padrão do serviço. Banho concluído atualiza o "último banho" do pet |
| GET / PUT | `/config` | Configurações da loja (nome, WhatsApp, endereço, horários, intervalo da agenda, Instagram, link de avaliação) |
| GET | `/api/publico/lojas/{slug}` | **Sem login**: dados públicos da loja para o site (só leitura) |
| GET | `/lembretes?data=` | Lembretes do dia (agendamentos, reforço de vacina, retorno de banho) |
| POST | `/lembretes/enviados` | Marca lembrete como enviado |
| GET/POST | `/financeiro/categorias` | Categorias de custo/receita (as padrão vêm na migration) |
| PUT / DELETE | `/financeiro/categorias/{id}` | Renomeia / exclui categoria (só sem lançamentos) |
| GET | `/financeiro/lancamentos?mes=yyyy-MM` | Lançamentos do mês. Abrir o mês lança sozinho os custos fixos recorrentes |
| POST / PUT / DELETE | `/financeiro/lancamentos[/{id}]` | Cria, edita e exclui lançamento (o tipo vem da categoria) |
| GET | `/financeiro/atendimentos?mes=` | Atendimentos concluídos do mês (receita de serviço) |
| GET | `/financeiro/resumo?mes=` | Receita, custos fixos e variáveis, lucro, custo médio por atendimento, mês anterior e últimos 6 meses |
| GET | `/financeiro/historico` | Últimas 100 alterações no financeiro (só leitura) |
| GET / PUT | `/precos`, `/precos/{tipo}` | Preço padrão de cada serviço |

Erros seguem o padrão RFC 7807 (`detail` com a mensagem; `campos` nos erros de validação).

## 4. Lembretes por WhatsApp

- **Padrão (custo zero):** a tela *Lembretes* do painel lista as mensagens de amanhã; um clique abre o WhatsApp já com o texto e marca como enviado.
- **Automático (opcional):** implemente `NotificadorWhatsApp` com a WhatsApp Cloud API (template "utility", cerca de R$0,035 por mensagem fora da janela de 24h), marque com `@Primary` e ligue `WHATSAPP_AUTOMATICO=true`. Não use bibliotecas não oficiais: o número pode ser banido.

## 5. Financeiro

Tela **Painel → Financeiro**: escolha o mês e veja receita, custos fixos e variáveis, lucro (verde/vermelho),
despesas eventuais, atendimentos e **custo médio por atendimento** ((fixos + variáveis) ÷ atendimentos concluídos), comparando com o mês anterior.

- **Botão "+ Adicionar despesa"** no topo: escolhe fixa, variável ou eventual, categoria e valor.
- Despesas **fixas ou variáveis** marcadas como "Repete todo mês" são lançadas sozinhas no mês seguinte com o mesmo valor;
  é só ajustar (ex.: conta de luz). Excluir a cópia de um mês encerra a repetição. Eventuais nunca repetem.
- **Despesas eventuais** (equipamento, conserto da moto, reforma) entram no lucro, mas não no custo médio por
  atendimento, para uma compra pontual não distorcer quanto custa cada banho.
- **Receitas** = atendimentos concluídos (o valor vem do preço padrão e pode ser mudado na agenda) + receitas avulsas
  (ração, produtos).
- **Exportar para Excel** gera `financeiro-AAAA-MM.xlsx`, gerado no navegador e sem nome nem telefone de cliente:
  - **Painel** (dashboard): resumo do mês comparado com o anterior (lucro em verde/vermelho), "para onde foi o dinheiro"
    (% de cada tipo de despesa, com barras) e lucro dos últimos 6 meses com barras. As fórmulas puxam os totais das outras
    abas: se o contador corrigir um valor, o painel recalcula. As barras são `REPT("█")` (a biblioteca não grava gráfico nativo).
  - **Receitas, Custos fixos, Custos variáveis, Despesas eventuais**: um lançamento por linha, cabeçalho fixo e total.
- Valores sempre com 2 casas (`NUMERIC` no banco, `BigDecimal` na API); datas no fuso de Goiânia (`America/Sao_Paulo`).

## 6. Segurança e LGPD

- **Histórico de alterações imutável:** todo lançamento criado/alterado/excluído, cada preço e cada valor cobrado em
  atendimento ficam registrados (usuário, data/hora, antes e depois). A API não tem rota para editar nem apagar o histórico,
  e no PostgreSQL um gatilho (`db/postgresql/V4_1__auditoria_imutavel.sql`) bloqueia `UPDATE`, `DELETE` e `TRUNCATE`
  mesmo por SQL direto. Testado em PostgreSQL 16.
- **Cada loja só vê os próprios dados:** toda consulta filtra pela loja do login; id de outra loja responde 404.
- **LGPD:** o histórico e o Excel não guardam nome, telefone nem CPF de cliente; CPF continua opcional.
- **Validação na API:** valores > 0 com no máximo 2 casas, datas entre 2000 e 2100, textos com tamanho máximo.
  Gravações simultâneas que colidem respondem 409 com mensagem clara (sem detalhes do banco).
- **Tentativas de login:** depois de 10 senhas erradas do mesmo endereço em 15 minutos, ele fica bloqueado por 15 minutos
  (resposta 429), mesmo acertando a senha. Configurável: `app.seguranca.max-tentativas` e `app.seguranca.bloqueio-minutos`.
  Atrás de proxy (Cloudflare/Nginx), ligue `server.forward-headers-strategy=native` para valer o IP do cliente.
- **Senha do painel em produção (bcrypt):** gere o hash e coloque em `PAINEL_SENHA` (nunca a senha em texto):
  ```bash
  docker run --rm httpd:2.4-alpine htpasswd -bnBC 12 "" 'SUA-SENHA-FORTE' | tr -d ':
'
  # PAINEL_SENHA={bcrypt}$2y$12$...   (o teste SenhaBcryptTest garante que esse formato funciona)
  ```
- **Backup diário:** `api/backup.sh` gera `backups/petshop-AAAA-MM-DD.sql.gz` (só o dono do servidor lê) e apaga os de mais
  de 30 dias. No cron: `0 3 * * * cd /caminho/api && ./backup.sh >> backups/backup.log 2>&1`. Restaurar:
  `gunzip -c backups/petshop-AAAA-MM-DD.sql.gz | docker compose exec -T postgres psql -U petshop -d petshop`
  (testado: restaura clientes, agenda, histórico e a trava do histórico). Guarde uma cópia fora do servidor.
- **Site:** cabeçalhos `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy` e `Permissions-Policy`; React escapa todo texto.
- **Dependências:** versões fixadas. `npm audit --omit=dev` ainda aponta um `postcss` interno do Next.js usado só na hora do
  build (processa o nosso CSS, não dados de usuário); some quando o Next 15 publicar a correção.

## 7. Publicar (opção gratuita)

- Site: Cloudflare Pages (`npm run build`), com `NEXT_PUBLIC_API_URL` apontando para a API.
- API + banco: Oracle Cloud Always Free (VM com Docker). Use o `Dockerfile` da pasta `api`.
- Domínio `.com.br`: cerca de R$40/ano no Registro.br.

## Pendências antes de entregar

- [ ] **Preencher os dados reais da loja** na tela **Painel → Configurações** (nome, WhatsApp, cidade, endereço, horários,
  Instagram, link de avaliação do Google). Hoje são de exemplo (fictícios).
- [ ] **Combinar o intervalo entre agendamentos** com o Cleiton (hoje 40 min): também na tela Configurações.
- [ ] Preços reais dos serviços (tela Financeiro → Preços dos serviços).
- [ ] Gerar a senha forte do painel com bcrypt (comando na seção 6) e colocar em `PAINEL_SENHA` no servidor.
- [ ] Agendar o `backup.sh` no cron do servidor e guardar cópia fora dele.
- [ ] Fotos reais da loja no site (com autorização dos tutores que aparecerem).
- [ ] Trocar o JDK da IDE para o 21 (*File → Project Structure → SDK*).
- [ ] Deixar o repositório do GitHub **privado** (*Settings → General → Danger Zone → Change visibility*).
