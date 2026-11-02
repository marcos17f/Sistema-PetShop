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
| `AGENDA_INTERVALO_MINUTOS` | `40` | Minutos mínimos entre um atendimento e outro (no site: `intervaloAgendaMinutos` em `site/src/lib/loja.ts`) |

## 3. Endpoints (todos com HTTP Basic)

Base: `/api/lojas/{slug}`

| Método | Caminho | O que faz |
|---|---|---|
| GET | `/tutores?busca=` | Lista/busca clientes (nome, WhatsApp ou nome do pet) |
| GET | `/tutores/{id}` | Ficha do cliente com os pets |
| POST | `/tutores` | Novo cliente |
| POST | `/tutores/{id}/pets` | Novo pet |
| GET/POST | `/pets/{petId}/vacinas` | Carteira de vacinação |
| GET | `/agenda?data=yyyy-MM-dd` | Agenda do dia |
| POST | `/agenda` | Novo agendamento. **Intervalo mínimo entre atendimentos** (padrão 40 min, de qualquer cliente); cancelado libera o horário |
| PATCH | `/agenda/{id}/status` | Muda status e, opcional, `valor` cobrado. Concluir sem valor usa o preço padrão do serviço. Banho concluído atualiza o "último banho" do pet |
| GET | `/lembretes?data=` | Lembretes do dia (agendamentos, reforço de vacina, retorno de banho) |
| POST | `/lembretes/enviados` | Marca lembrete como enviado |
| GET/POST | `/financeiro/categorias` | Categorias de custo/receita (as padrão vêm na migration) |
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
- **Site:** cabeçalhos `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy` e `Permissions-Policy`; React escapa todo texto.
- **Dependências:** versões fixadas. `npm audit --omit=dev` ainda aponta um `postcss` interno do Next.js usado só na hora do
  build (processa o nosso CSS, não dados de usuário); some quando o Next 15 publicar a correção.

## 7. Publicar (opção gratuita)

- Site: Cloudflare Pages (`npm run build`), com `NEXT_PUBLIC_API_URL` apontando para a API.
- API + banco: Oracle Cloud Always Free (VM com Docker). Use o `Dockerfile` da pasta `api`.
- Domínio `.com.br`: cerca de R$40/ano no Registro.br.

## Pendências antes de entregar

- [ ] **Trocar os dados de exemplo (fictícios)** pelos reais do Cleiton: nome, WhatsApp, endereço, horários e Instagram em
  `site/src/lib/loja.ts`; nome/WhatsApp da loja e preços dos serviços numa migration nova (`V7__...`), nunca editando a V3.
- [ ] **Trocar o intervalo entre agendamentos** (hoje 40 min, provisório; combinar com o Cleiton, ex.: 30 min): variável
  `AGENDA_INTERVALO_MINUTOS` na API **e** `intervaloAgendaMinutos` em `site/src/lib/loja.ts` (os dois com o mesmo valor).
  Os preços também podem ser mudados pela tela Financeiro → Preços dos serviços.
- [ ] Limitar tentativas de login na API (hoje não há bloqueio após senhas erradas) antes de publicar.
- [ ] Fotos reais da loja no site (com autorização dos tutores que aparecerem).
- [ ] Senha forte com bcrypt em produção.
- [ ] Link das avaliações do Google (perfil da empresa) no site.
- [ ] Backup diário do PostgreSQL.
