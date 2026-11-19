package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.model.Usuario;
import br.com.royal.pontos.service.Config;
import br.com.royal.pontos.service.UsuarioService;
import br.com.royal.pontos.util.Formatos;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ConfiguracoesView extends StackPane {

    private final TextField reais = Ui.campoNumero("1,00", true);
    private final TextField pontos = Ui.campoNumero("1", false);
    private final TextField minimo = Ui.campoNumero("0,00", true);
    private final TextField validade = Ui.campoNumero("60", false);
    private final Label exemplo = Ui.label("", "texto-roxo");
    private final RadioButton rProdutos = new RadioButton("Somente produtos do catálogo");
    private final RadioButton rDesconto = new RadioButton("Somente desconto em compras");
    private final RadioButton rAmbos = new RadioButton("Produtos e desconto (o cliente escolhe)");
    private final TextField descPontos = Ui.campoNumero("100", false);
    private final TextField descReais = Ui.campoNumero("5,00", true);
    private final TextField compraMinResgate = Ui.campoNumero("40,00", true);
    private final CheckBox renovaValidade = new CheckBox("Renovar a validade a cada compra (os pontos vencem X dias após a última compra)");
    private final TextField loja = Ui.campoTexto("Nome da loja");
    private final TextField link = Ui.campoTexto("https://royalpontos.netlify.app");
    private final TableView<Usuario> usuarios = Tabelas.nova("Nenhum usuário.");
    private final Button salvarBtn = Ui.botao("Salvar configurações", "btn-primario", Icones.CHECK);

    public ConfiguracoesView() {
        reais.setText(Formatos.valorTexto(Config.decimal(Config.REGRA_REAIS)));
        pontos.setText(String.valueOf(Config.inteiro(Config.REGRA_PONTOS)));
        minimo.setText(Formatos.valorTexto(Config.decimal(Config.VALOR_MINIMO)));
        validade.setText(String.valueOf(Config.inteiro(Config.VALIDADE_DIAS)));
        descPontos.setText(String.valueOf(Config.inteiro(Config.DESCONTO_PONTOS)));
        descReais.setText(Formatos.valorTexto(Config.decimal(Config.DESCONTO_REAIS)));
        compraMinResgate.setText(Formatos.valorTexto(Config.decimal(Config.RESGATE_COMPRA_MINIMA)));
        renovaValidade.setSelected(Config.bool(Config.VALIDADE_RENOVA));
        renovaValidade.setWrapText(true);
        renovaValidade.selectedProperty().addListener((o, a, n) -> atualizarExemplo());
        compraMinResgate.setPrefWidth(180);
        loja.setText(Config.get(Config.LOJA_NOME));
        link.setText(Config.get(Config.LINK_CONSULTA));
        ToggleGroup g = new ToggleGroup();
        rProdutos.setToggleGroup(g);
        rDesconto.setToggleGroup(g);
        rAmbos.setToggleGroup(g);
        switch (Config.get(Config.MODO_RESGATE)) {
            case "PRODUTOS" -> rProdutos.setSelected(true);
            case "DESCONTO" -> rDesconto.setSelected(true);
            default -> rAmbos.setSelected(true);
        }
        for (TextField t : new TextField[]{reais, pontos, minimo, validade}) {
            t.setPrefWidth(180);
            t.textProperty().addListener((o, a, n) -> atualizarExemplo());
        }
        descPontos.setPrefWidth(150);
        descReais.setPrefWidth(150);

        // ---- regra
        HBox regraLinha = new HBox(18, Ui.campo("A cada (R$ em compras)", reais), Ui.campo("O cliente ganha (pontos)", pontos));
        HBox outros = new HBox(18, Ui.campo("Compra mínima para pontuar (R$)", minimo), Ui.campo("Validade dos pontos (dias)", validade));
        VBox ex = new VBox(2, Ui.label("Simulação", "texto-suave"), exemplo);
        ex.getStyleClass().add("previa-box");
        VBox cardRegra = Ui.cardTitulo("Regra de pontuação", "Vale para o balcão e para as vendas importadas do Bling", regraLinha, outros, renovaValidade, ex);

        // ---- resgate
        HBox descLinha = new HBox(18, Ui.campo("Pontos", descPontos), Ui.campo("Valem (R$ de desconto)", descReais));
        descLinha.disableProperty().bind(rProdutos.selectedProperty());
        VBox cardResgate = Ui.cardTitulo("Forma de resgate", "Escolha como o cliente pode usar os pontos",
                new VBox(10, rAmbos, rProdutos, rDesconto),
                Ui.campo("Compra mínima no dia do resgate (R$) — 0 = sem mínimo", compraMinResgate),
                Ui.label("Conversão para desconto", "card-titulo"), descLinha);

        // ---- loja
        Label dicaLink = Ui.label("Endereço onde você publicou a página de consulta (pasta consulta-cliente). "
                + "Ele vai junto nas mensagens de WhatsApp.", "texto-suave");
        dicaLink.setWrapText(true);
        VBox cardLoja = Ui.cardTitulo("Loja e consulta do cliente", null,
                Ui.campo("Nome da loja (aparece nas mensagens)", loja), Ui.campo("Link da página de consulta do cliente", link), dicaLink);

        Button salvar = salvarBtn;
        salvar.getStyleClass().add("btn-grande");
        salvar.setOnAction(e -> salvar());

        // ---- usuários
        usuarios.getColumns().add(Tabelas.colunaNo("NOME", u -> {
            HBox h = new HBox(10, Ui.avatar(Formatos.iniciais(u.nome()), 30), Ui.label(u.nome(), "texto-forte"));
            h.setAlignment(Pos.CENTER_LEFT);
            return h;
        }, 220));
        usuarios.getColumns().add(Tabelas.coluna("LOGIN", Usuario::login, 120));
        usuarios.getColumns().add(Tabelas.colunaNo("PERFIL", u -> Ui.badge(u.perfilTexto(), u.isAdmin() ? "roxo" : "azul"), 120));
        usuarios.getColumns().add(Tabelas.colunaNo("SITUAÇÃO", u -> Ui.badge(u.ativo() ? "Ativo" : "Inativo", u.ativo() ? "verde" : "cinza"), 100));
        usuarios.getColumns().add(Tabelas.colunaNo("", u -> {
            Button b = Ui.botao("Editar", "btn-link", null);
            b.setOnAction(e -> editarUsuario(u));
            return b;
        }, 80));
        usuarios.setPrefHeight(230);
        Button novoUsuario = Ui.botao("Novo usuário", "btn-secundario", Icones.ADD_PESSOA);
        novoUsuario.setOnAction(e -> editarUsuario(null));
        HBox cabUsuarios = new HBox(new VBox(2, Ui.label("Usuários do sistema", "card-titulo"),
                Ui.label("Atendentes lançam compras e resgates. Administradores também mudam regras, prêmios e estornam.", "card-sub")),
                Ui.espaco(), novoUsuario);
        cabUsuarios.setAlignment(Pos.CENTER_LEFT);
        VBox cardUsuarios = Ui.card(cabUsuarios, usuarios);

        // ---- banco
        Button conexao = Ui.botao("Configurar conexão", "btn-secundario", Icones.NUVEM);
        conexao.setOnAction(e -> { if (ConexaoDialog.abrir()) App.reiniciarConexao(); });
        HBox banco = new HBox(14, Ui.iconeCirculo(Db.isNuvem() ? Icones.NUVEM : Icones.COMPUTADOR, Db.isNuvem() ? "verde" : "ouro", 44),
                new VBox(2, Ui.label(Db.isNuvem() ? "Conectado ao Supabase (nuvem)" : "Banco local — modo demonstração", "texto-forte"),
                        Ui.label(Db.isNuvem() ? "Dados compartilhados entre computadores e com a página do cliente."
                                : "Os dados ficam só neste computador. Para a consulta do cliente funcionar, conecte ao Supabase.", "texto-suave")),
                Ui.espaco(), conexao);
        banco.setAlignment(Pos.CENTER_LEFT);
        VBox cardBanco = Ui.cardTitulo("Banco de dados", null, banco);

        Button verificar = Ui.botao("Verificar atualizações", "btn-secundario", Icones.SYNC);
        verificar.setOnAction(e -> MainView.atual().verificarAtualizacao(true));
        HBox atualiz = new HBox(14, Ui.iconeCirculo(Icones.SYNC, "azul", 44),
                new VBox(2, Ui.label("Versão instalada: " + br.com.royal.pontos.service.Atualizador.versaoEmUso(), "texto-forte"),
                        Ui.label("O programa procura atualizações sozinho toda vez que é aberto e se atualiza automaticamente.", "texto-suave")),
                Ui.espaco(), verificar);
        atualiz.setAlignment(Pos.CENTER_LEFT);
        VBox cardAtualiz = Ui.cardTitulo("Atualizações", null, atualiz);

        Button backupAgora = Ui.botao("Fazer backup agora", "btn-secundario", Icones.CHECK);
        backupAgora.setOnAction(e -> MainView.atual().fazerBackup(true));
        Button abrirPasta = Ui.botao("Abrir pasta", "btn-link", null);
        abrirPasta.setOnAction(e -> {
            try {
                java.nio.file.Files.createDirectories(br.com.royal.pontos.service.BackupService.pasta());
                br.com.royal.pontos.App.abrirLink(br.com.royal.pontos.service.BackupService.pasta().toUri().toString());
            } catch (Exception erroPasta) { Ui.erro(erroPasta.getMessage()); }
        });
        Label textoBackup = Ui.label("O programa salva sozinho, ao abrir e a cada 30 minutos, a planilha \"Saldos Royal Pontos.xlsx\" com o saldo atual "
                + "e os 2 saldos anteriores de cada cliente, mais todos os lançamentos. Pasta: " + br.com.royal.pontos.service.BackupService.pasta(), "texto-suave");
        textoBackup.setWrapText(true);
        HBox backupLinha = new HBox(14, Ui.iconeCirculo(Icones.CARTEIRA, "verde", 44), textoBackup, Ui.espaco(), new VBox(6, backupAgora, abrirPasta));
        backupLinha.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textoBackup, Priority.ALWAYS);
        VBox cardBackup = Ui.cardTitulo("Backup em Excel", null, backupLinha);

        HBox linha1 = new HBox(20, cardRegra, cardResgate);
        HBox.setHgrow(cardRegra, Priority.ALWAYS);
        HBox.setHgrow(cardResgate, Priority.ALWAYS);
        cardRegra.setMaxWidth(Double.MAX_VALUE);
        cardResgate.setMaxWidth(Double.MAX_VALUE);
        HBox barraSalvar = new HBox(salvar);
        barraSalvar.setAlignment(Pos.CENTER_RIGHT);

        VBox pagina = new VBox(20, linha1, cardLoja, barraSalvar, cardUsuarios, cardBanco, cardBackup, cardAtualiz);
        pagina.getStyleClass().add("pagina");
        getChildren().add(Ui.rolagem(pagina));
        atualizarExemplo();
        carregarUsuarios();
    }

    private void atualizarExemplo() {
        BigDecimal r = Formatos.valor(reais.getText());
        int p;
        try { p = Integer.parseInt(pontos.getText().trim()); } catch (Exception e) { p = 0; }
        if (r == null || r.signum() <= 0 || p <= 0) { exemplo.setText("Preencha a regra para ver a simulação."); return; }
        BigDecimal compra = new BigDecimal("150.00");
        long pts = compra.divide(r, 0, RoundingMode.FLOOR).longValue() * p;
        exemplo.setText("Compra de R$ 150,00 → " + Formatos.pontos(pts) + "  ·  válidos por " + validade.getText() + " dias"
                + (renovaValidade.isSelected() ? " após a última compra" : ""));
    }

    private void salvar() {
        BigDecimal r = Formatos.valor(reais.getText()), min = Formatos.valor(minimo.getText()), dr = Formatos.valor(descReais.getText());
        BigDecimal minResg = Formatos.valor(compraMinResgate.getText());
        try {
            int p = Integer.parseInt(pontos.getText().trim());
            int v = Integer.parseInt(validade.getText().trim());
            int dp = Integer.parseInt(descPontos.getText().trim());
            if (r == null || r.signum() <= 0 || p <= 0) throw new IllegalArgumentException("Regra de pontuação inválida.");
            if (v < 1) throw new IllegalArgumentException("A validade precisa ser de pelo menos 1 dia.");
            if (dp <= 0 || dr == null || dr.signum() <= 0) throw new IllegalArgumentException("Conversão para desconto inválida.");
            java.util.Map<String, String> valores = new java.util.LinkedHashMap<>();
            valores.put(Config.REGRA_REAIS, r.toPlainString());
            valores.put(Config.REGRA_PONTOS, String.valueOf(p));
            valores.put(Config.VALOR_MINIMO, (min == null ? BigDecimal.ZERO : min).toPlainString());
            valores.put(Config.VALIDADE_DIAS, String.valueOf(v));
            valores.put(Config.MODO_RESGATE, rProdutos.isSelected() ? "PRODUTOS" : rDesconto.isSelected() ? "DESCONTO" : "AMBOS");
            valores.put(Config.DESCONTO_PONTOS, String.valueOf(dp));
            valores.put(Config.DESCONTO_REAIS, dr.toPlainString());
            valores.put(Config.RESGATE_COMPRA_MINIMA, (minResg == null ? BigDecimal.ZERO : minResg).toPlainString());
            valores.put(Config.VALIDADE_RENOVA, String.valueOf(renovaValidade.isSelected()));
            valores.put(Config.LOJA_NOME, loja.getText().trim());
            valores.put(Config.LINK_CONSULTA, link.getText().trim());
            salvarBtn.setDisable(true);
            salvarBtn.setText("Salvando...");
            Ui.async(() -> { Config.setVarios(valores); return true; },
                    ok -> Ui.toast("Configurações salvas. A nova validade vale para os próximos lançamentos."),
                    () -> { salvarBtn.setDisable(false); salvarBtn.setText("Salvar configurações"); });
        } catch (NumberFormatException e) {
            Ui.erro("Preencha todos os números corretamente.");
        } catch (Exception e) {
            Ui.erro(e.getMessage());
        }
    }

    private void carregarUsuarios() {
        Ui.async(UsuarioService::listar, l -> usuarios.getItems().setAll(l));
    }

    private void editarUsuario(Usuario u) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(u == null ? "Novo usuário" : "Editar usuário");
        d.setHeaderText(u == null ? "Novo usuário do sistema" : "Editar " + u.nome());
        d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());
        TextField nome = Ui.campoTexto("Nome");
        TextField login = Ui.campoTexto("login");
        PasswordField senha = new PasswordField();
        senha.setPromptText(u == null ? "Senha inicial" : "Deixe em branco para manter");
        ComboBox<String> perfil = new ComboBox<>();
        perfil.getItems().addAll("Atendente", "Administrador");
        CheckBox ativo = new CheckBox("Usuário ativo");
        Label erro = Ui.label("", "erro-texto");
        if (u != null) {
            nome.setText(u.nome());
            login.setText(u.login());
            perfil.setValue(u.perfilTexto());
            ativo.setSelected(u.ativo());
        } else {
            perfil.setValue("Atendente");
            ativo.setSelected(true);
        }
        VBox box = new VBox(12, Ui.campo("Nome", nome), Ui.campo("Login", login), Ui.campo(u == null ? "Senha" : "Nova senha", senha),
                Ui.campo("Perfil", perfil), ativo, erro);
        box.setPrefWidth(380);
        d.getDialogPane().setContent(box);
        ButtonType ok = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        d.getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                UsuarioService.salvar(u == null ? null : u.id(), nome.getText(), login.getText(), senha.getText(),
                        "Administrador".equals(perfil.getValue()) ? "ADMIN" : "ATENDENTE", ativo.isSelected());
            } catch (Exception ex) {
                erro.setText(ex.getMessage());
                e.consume();
            }
        });
        if (d.showAndWait().filter(b -> b == ok).isPresent()) {
            Ui.toast("Usuário salvo");
            carregarUsuarios();
        }
    }
}
