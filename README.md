# Royal Pontos — Programa de Fidelidade da Royal Parafusos e EPIs

Programa de pontos para o balcão (JavaFX, Windows) + página para o cliente consultar o saldo pelo celular.

**O que ele faz**
- Cada compra vira pontos (regra configurável: "a cada R$ X = Y pontos", compra mínima).
- Pontos vencem em **60 dias** (configurável). Os resgates usam primeiro os pontos que vencem antes.
- Resgate por **produtos do catálogo**, por **desconto**, ou pelos dois (você escolhe em Configurações).
- Catálogo de prêmios cadastrado à mão **ou** importado dos produtos do Bling.
- Vendas lançadas no balcão **ou** importadas automaticamente do Bling (pelo CPF do cliente).
- Login com **Administrador** e **Atendente**.
- Mensagens prontas no WhatsApp (pontos ganhos, saldo, comprovante de resgate).
- Painel com gráfico, maiores saldos, pontos a vencer e extrato completo com estorno.

---

## 1. Rodar pela primeira vez (modo demonstração)

O programa já funciona sozinho, guardando os dados no próprio computador. Bom para testar.

1. Instale o **Java 21 (JDK)**: https://adoptium.net (Temurin 21 LTS, Windows x64, `.msi`). Na instalação, marque "Set JAVA_HOME".
2. Instale o **Maven**: o jeito mais fácil é instalar o **IntelliJ IDEA Community** (gratuito, já vem com Maven):
   https://www.jetbrains.com/idea/download
3. **Pelo IntelliJ:** *File › Open* › escolha a pasta `royal-pontos` › aguarde baixar as dependências ›
   abra `src/main/java/br/com/royal/pontos/Launcher.java` e clique no ▶ verde.
   **Ou pelo terminal** (se tiver o Maven instalado): dê dois cliques em `executar.bat`.
4. Primeiro acesso: usuário **admin**, senha **admin**. O programa pede para você criar uma senha nova.

> Os dados do modo demonstração ficam em `C:\Users\<você>\RoyalPontos\dados`.

## 2. Gerar o programa com ícone (.exe)

Dê dois cliques em `gerar-executavel.bat`. Ele cria a pasta `dist\Royal Pontos\` com o `Royal Pontos.exe`
(já leva o Java junto — pode copiar a pasta para outro computador da loja sem instalar nada).
Crie um atalho do `.exe` na área de trabalho.

## 3. Colocar na nuvem (necessário para o cliente consultar pelo celular)

Usamos o **Supabase** (plano gratuito).

1. Crie a conta em https://supabase.com › **New project** › região **South America (São Paulo)** › anote a senha do banco.
2. No projeto: **SQL Editor › New query** › cole todo o conteúdo de `supabase/schema.sql` › **Run**.
3. Clique em **Connect** (topo da página) › aba **Session pooler**. Anote: *host*, *port* (5432), *database* (postgres) e *user* (`postgres.xxxxx`).
4. No Royal Pontos, na tela de login, clique em **"Banco: este computador"** (ou em Configurações › Banco de dados) ›
   escolha **Na nuvem — Supabase** › preencha host, porta, banco, usuário e a senha do banco › **Testar conexão** › **Salvar**.
5. Faça isso em cada computador da loja: todos passam a ver os mesmos dados.

> Os dados do modo demonstração **não** são levados para a nuvem. Se já cadastrou clientes de verdade no modo local, me avise que preparo uma migração.

## 4. Página de consulta do cliente

Arquivo: `consulta-cliente/index.html` (funciona no celular; o cliente digita o CPF e os 4 últimos dígitos do celular).

1. No Supabase: **Project Settings › API Keys** › copie a **Project URL** e a chave **anon / publishable**
   (*nunca* use a chave `service_role` / `secret`).
2. Abra o `index.html` no Bloco de Notas e preencha no topo:
   ```js
   const SUPABASE_URL = "https://SEU-PROJETO.supabase.co";
   const SUPABASE_KEY = "sua-chave-anon";
   const WHATSAPP_LOJA = "5538999999999"; // opcional
   ```
3. Publique de graça: entre em https://app.netlify.com/drop e **arraste a pasta `consulta-cliente`**. Você recebe um link
   (pode trocar o nome em *Site settings*, ex.: `royalpontos.netlify.app`).
4. No programa: **Configurações › Link da página de consulta** › cole o link. Ele passa a ir junto nas mensagens de WhatsApp.

Segurança: a página só acessa uma função (`consultar_pontos`) que devolve o saldo quando CPF **e** final do celular conferem.
As tabelas ficam bloqueadas para acesso externo.

## 5. Integração com o Bling

Menu **Integração Bling** (só administrador). O passo a passo está na própria tela:
1. No Bling: *Central de Extensões › Área do integrador › Criar aplicativo* (API). Escopos: Pedidos de Venda, Contatos, Produtos.
2. Copie Client ID e Client Secret para o programa › **Autorizar no Bling** › aceite › copie o `code=...` do endereço › **Conectar**.
3. **Importar vendas**: escolha o período e clique em importar. Cada pedido entra uma vez só (não duplica).
   Pontos vão para o cliente cujo **CPF** está no contato do pedido. Pedidos cancelados (situação 12) são ignorados.
4. Opcional: importar automaticamente ao abrir o programa e cadastrar sozinho os clientes do Bling que têm CPF e celular.
5. **Produtos como prêmios**: em *Prêmios › Importar do Bling*. O custo em pontos = preço × "pontos por real" (padrão 20).

> Teste a integração primeiro com um período curto (1 dia) e confira o resultado no extrato.

## 6. Atualizações automáticas

Depois da primeira instalação, você **não precisa mais reinstalar** nada:
- Toda vez que o Royal Pontos abre, ele consulta se há versão nova publicada no GitHub.
- Se houver, baixa em segundo plano e mostra o botão verde **"Versão X pronta — reiniciar"** no topo.
- Se você não clicar, na próxima vez que abrir o programa ele já abre na versão nova.
- A versão em uso aparece no rodapé do menu e em Configurações › Atualizações.

Como uma versão nova é publicada: o número em `Versao.java` sobe e o código é enviado ao repositório
`royalparafusos/royal`. O GitHub compila e publica a Release sozinho (`.github/workflows/publicar.yml`).
Seus dados (clientes, pontos, configurações) não são afetados pelas atualizações.

## 7. Como os pontos funcionam

| Situação | O que acontece |
|---|---|
| Compra | Gera um "lote" de pontos com validade (data da compra + 60 dias) |
| Resgate | Consome primeiro os lotes que vencem antes |
| Vencimento | Ao abrir o programa/painel, os lotes vencidos são zerados e aparecem no extrato como "Expirados" |
| Estorno (admin) | Botão direito no extrato do cliente. Compra: retira os pontos que sobraram. Resgate: devolve os pontos (e o estoque) |
| Ajuste (admin) | Na ficha do cliente: adiciona ou remove pontos com motivo (ex.: bônus de aniversário) |

Mudar a validade ou a regra em Configurações vale para os próximos lançamentos.

## 8. Estrutura do projeto

```
royal-pontos/
├── pom.xml                         dependências (JavaFX 21, PostgreSQL, H2, Gson)
├── executar.bat / gerar-executavel.bat
├── src/main/java/br/com/royal/pontos/
│   ├── App.java, Launcher.java     início do programa
│   ├── db/                         conexão e criação das tabelas
│   ├── model/                      Cliente, Movimento, Premio, Usuario
│   ├── service/                    regras: pontos, clientes, prêmios, Bling, painel
│   └── ui/                         telas (login, painel, compra, resgate, clientes, prêmios, Bling, configurações)
├── src/main/resources/css/app.css  visual (cores, cartões, botões)
├── supabase/schema.sql             banco na nuvem + função de consulta do cliente
└── consulta-cliente/index.html     página do cliente
```
