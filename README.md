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
NEXT_PUBLIC_SITE_URL=http://localhost:3001   # no ar: o endereço do site (sitemap, robots e dados do Google)
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
| `LIMITE_WHATSAPP_MES` | `1000` | Mensagens do plano/franquia por mês: avisa em 80% e 100% |
| `LIMITE_BANCO_MB` | `1000` | Espaço do banco contratado: avisa em 80% e 95% |
| `WHATSAPP_TOKEN` | vazio | Token da WhatsApp Cloud API (segredo: só aqui, nunca no banco/tela/Git). Vazio = modo simulado |
| `WHATSAPP_PHONE_NUMBER_ID` | vazio | Id do número na Meta (WhatsApp → Configuração da API) |
| `WHATSAPP_API_URL` | `https://graph.facebook.com/v23.0` | Versão da Graph API (atualizar quando a Meta descontinuar a versão) |
| `WHATSAPP_TEMPLATE_LEMBRETE` | `lembrete_pet` | Nome do modelo de lembrete aprovado na Meta |
| `WHATSAPP_TEMPLATE_CONFIRMACAO` | `confirmacao_agendamento` | Modelo da confirmação do agendamento online |
| `WHATSAPP_APP_SECRET` | vazio | Segredo do app na Meta: confere a assinatura do webhook (sem ele, o webhook recusa tudo) |
| `WHATSAPP_VERIFY_TOKEN` | vazio | Token que você inventa e informa na Meta ao cadastrar o webhook |
| `WHATSAPP_TEMPLATE_AVALIACAO` | `avaliacao_atendimento` | Modelo do pedido de avaliação no Google |
| `CAMPANHAS_LIMITE_DIA` | `200` | Mensagens de campanha por dia (a Meta limita números novos a ~250/dia); o resto continua às 10h do dia seguinte |
| `SITE_URL` | `http://localhost:3001` | Endereço público do site (vai no link da resposta rápida) |

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
| GET | `/api/publico/lojas/{slug}/servicos` e `/horarios?data=` | **Sem login**: serviços com preço e horários livres do dia |
| GET / POST | `/campanhas`, `/campanhas/publico` | Campanhas (só para quem aceitou promoções), relatório e tamanho do público |
| PUT | `/tutores/{id}/promocoes` | Registra o aceite de promoções feito na loja ou tira da lista (vai para o histórico) |
| POST | `/api/publico/descadastro` | **Sem login**: link "não quero mais receber" (mesma resposta sempre) |
| POST | `/api/publico/lojas/{slug}/agendamentos` | **Sem login**: agendamento online (limite de 5/h por endereço, campo contra robôs, aceite LGPD) |
| GET / POST | `/api/publico/whatsapp/webhook` | Webhook da Meta (verificação + mensagens recebidas, só com assinatura válida) |
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

## 4. WhatsApp

Tela **Painel → WhatsApp**: modo (simulado ou oficial), lembretes automáticos às 18h, resposta rápida, mensagem de teste,
"rodar lembretes de amanhã agora" e as últimas mensagens (guardadas por 90 dias, LGPD).

- **Modo simulado (padrão, custo zero):** sem `WHATSAPP_TOKEN`, nada sai. Cada mensagem fica registrada na tela com o botão
  **Abrir no WhatsApp** para a loja mandar por clique. A tela *Lembretes* continua funcionando igual.
- **Modo oficial (WhatsApp Cloud API da Meta):** defina `WHATSAPP_TOKEN` e `WHATSAPP_PHONE_NUMBER_ID` no servidor e reinicie.
  Passo a passo: [Meta for Developers](https://developers.facebook.com/) → criar app *Business* → produto **WhatsApp** →
  usar o **número de teste** (manda grátis para até 5 números cadastrados) para validar → depois registrar o número da loja e
  gerar um **token permanente** (usuário do sistema no Gerenciador de Negócios). Nunca use bibliotecas não oficiais
  (whatsapp-web.js, Baileys...): o número é banido.
- **Modelo para aprovar na Meta** (WhatsApp → Modelos de mensagem):

  | Nome | Categoria | Idioma | Corpo |
  |---|---|---|---|
  | `lembrete_pet` | Utilidade | Português (BR) | `Lembrete de {{1}}: {{2}} Responda esta mensagem para confirmar ou remarcar.` |

  | `confirmacao_agendamento` | Utilidade | Português (BR) | `Agendamento recebido em {{1}}: {{2}}. Responda se precisar mudar algo.` |
  | `avaliacao_atendimento` | Utilidade | Português (BR) | `Obrigado pela visita à {{1}}! Se puder, avalie nosso atendimento: {{2}} Sua opinião ajuda muito.` |
  | `promocao_loja` (exemplo) | **Marketing** | Português (BR) | `Novidade da {{1}}: {{2}} Para não receber mais promoções: {{3}}` |

  `{{1}}` = nome da loja; `{{2}}` = texto (ex.: "Olá, Ana! Amanhã é dia da vacina V10 do Thor." ou "banho do Thor em 05/11 às 10:00").
- **Webhook (mensagens recebidas):** na Meta, *WhatsApp → Configuração → Webhook*: URL `https://SUA-API/api/publico/whatsapp/webhook`,
  token de verificação = `WHATSAPP_VERIFY_TOKEN`, assinar o campo `messages`. Quem escreve vira cliente (origem WhatsApp ou
  Instagram, se citar) e, com a **resposta rápida** ligada, recebe o link do agendamento online (uma vez a cada 24 h).
- **Custos:** a Meta cobra por mensagem de modelo (lembrete = utilidade; campanha = marketing) e as regras mudaram em
  2025/2026 (franquia mensal grátis de mensagens de atendimento, janela grátis de 72 h para quem vem de anúncio
  clique-para-WhatsApp). Confira a tabela atual em
  [Preços da WhatsApp Business Platform](https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing)
  antes de ligar o envio automático. Para começar sem custo: modo simulado (envio por clique) + número de teste da Meta.

## 5. Agendamento online (página /agendar)

O cliente escolhe o serviço (com preço "a partir de"), o dia e **só os horários livres** (funcionamento da loja, intervalo
entre atendimentos, 1 h de antecedência, até 60 dias, domingo fechado), informa nome, WhatsApp, pet e se quer **busca de
moto** (com endereço). Aceite LGPD obrigatório; promoções só se marcar.

- Cai no painel como **Aguardando**, com os selos *Agendou online* e *Busca de moto*; confirmação no WhatsApp
  (simulada sem a API oficial: aparece na tela WhatsApp para mandar por clique).
- Cliente do mesmo WhatsApp é reaproveitado (pet pelo nome), **sem revelar nem sobrescrever** dados de ninguém.
- Proteções: mesma trava/intervalo da agenda, limite por endereço, campo invisível contra robôs, validação de tudo.
- Divulgação: em **Configurações → Divulgação** há o link da bio (`/agendar?origem=instagram`) e o link do WhatsApp com
  "Vim pelo Instagram"; quem chega por eles fica com a origem certa no cadastro.

## 6. Campanhas, avaliação no Google e SEO

- **Campanhas (Painel → Campanhas):** vão **somente** para clientes da base que aceitaram promoções (no agendamento online
  ou pedindo na loja: ficha do cliente → *Aceitou promoções*), com a data do aceite registrada. Não existe campo para números
  avulsos: impossível disparar para lista comprada. Toda mensagem leva o link pessoal **"não quero mais receber"**
  (`/sair?t=...`, token aleatório); quem sai fica com a data registrada e é pulado nos envios pendentes. Limite por dia
  (`CAMPANHAS_LIMITE_DIA`) e continuação automática às 10h; relatório com enviadas, simuladas, falhas, pendentes e puladas.
  Para alcançar gente nova: Google, Instagram e anúncios — nunca listas de números.
- **Avaliação no Google:** ligue em *WhatsApp → Pedir avaliação no Google* (precisa do link em Configurações). Depois do
  atendimento concluído, o cliente recebe o pedido com o link, no máximo 1 vez a cada 90 dias.
- **SEO local:** a página inicial tem dados estruturados `PetStore` (nome, endereço, telefone, horário e Instagram das
  Configurações), `sitemap.xml` e `robots.txt` (painel, login e `/sair` fora do Google). Defina `NEXT_PUBLIC_SITE_URL`
  no site publicado. Cadastre a loja no **Perfil da Empresa no Google** com o mesmo nome, endereço e telefone.

## 7. Financeiro

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

## 8. Segurança e LGPD

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

## 9. Saúde do sistema (avisos)

Tela **Painel → Saúde do sistema** e faixa no topo do painel: o sistema confere sozinho e avisa **antes** de algo parar,
sempre dizendo o que fazer:

| Área | Avisa quando |
|---|---|
| WhatsApp | mensagens do mês em 80%/100% do plano (`LIMITE_WHATSAPP_MES`) → hora de um plano maior; falhas de envio; token recusado pela Meta; lembretes automáticos ligados no modo simulado |
| Banco de dados | espaço em 80%/95% de `LIMITE_BANCO_MB` (só PostgreSQL) |
| Servidor | memória ainda ocupada depois da coleta de lixo ≥ 85% (sem alarme falso de pico); 5+ erros 5xx na última hora |
| Segurança | senha do painel sem bcrypt; endereços bloqueados por senha errada nas últimas 24 h |

**Monitor de queda (grátis):** cadastre `https://SEU-DOMINIO-DA-API/actuator/health` no [UptimeRobot](https://uptimerobot.com/)
(ou similar) para receber e-mail/WhatsApp se o sistema cair. A rota responde só `{"status":"UP"}` (sem detalhes); o resto
do actuator fica bloqueado.

## 10. Publicar (opção gratuita)

- Site: Cloudflare Pages (`npm run build`), com `NEXT_PUBLIC_API_URL` apontando para a API.
- API + banco: Oracle Cloud Always Free (VM com Docker). Use o `Dockerfile` da pasta `api`.
- Domínio `.com.br`: cerca de R$40/ano no Registro.br.

## Pendências antes de entregar

- [ ] **Preencher os dados reais da loja** na tela **Painel → Configurações** (nome, WhatsApp, cidade, endereço, horários,
  Instagram, link de avaliação do Google). Hoje são de exemplo (fictícios).
- [ ] **Combinar o intervalo entre agendamentos** com o Cleiton (hoje 40 min): também na tela Configurações.
- [ ] Preços reais dos serviços (tela Financeiro → Preços dos serviços).
- [ ] Gerar a senha forte do painel com bcrypt (comando na seção 8) e colocar em `PAINEL_SENHA` no servidor.
- [ ] Agendar o `backup.sh` no cron do servidor e guardar cópia fora dele.
- [ ] Fotos reais da loja no site (com autorização dos tutores que aparecerem).
- [ ] Trocar o JDK da IDE para o 21 (*File → Project Structure → SDK*).
- [ ] Deixar o repositório do GitHub **privado** (*Settings → General → Danger Zone → Change visibility*).
