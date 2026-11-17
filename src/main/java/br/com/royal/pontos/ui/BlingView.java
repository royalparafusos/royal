package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.service.BlingService;
import br.com.royal.pontos.service.Config;
import br.com.royal.pontos.util.Formatos;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;

public class BlingView extends StackPane {

    private final Label status = new Label();
    private final TextArea log = new TextArea();

    public BlingView() {
        // ---- conexão
        TextField clientId = Ui.campoTexto("Client ID do aplicativo");
        clientId.setText(Config.get(Config.BLING_CLIENT_ID));
        PasswordField secret = new PasswordField();
        secret.setPromptText("Client Secret do aplicativo");
        secret.setText(Config.get(Config.BLING_CLIENT_SECRET));
        TextField codigo = Ui.campoTexto("Cole aqui o código (ou o link inteiro) que aparece após autorizar");

        Button abrir = Ui.botao("1. Autorizar no Bling", "btn-secundario", Icones.NUVEM);
        abrir.setOnAction(e -> Ui.tentar(() -> {
            salvarCredenciais(clientId, secret);
            App.abrirLink(BlingService.urlAutorizacao());
        }));
        Button conectar = Ui.botao("2. Conectar", "btn-primario", Icones.CHECK);
        conectar.setOnAction(e -> {
            salvarCredenciais(clientId, secret);
            String c = codigo.getText();
            if (c.isBlank()) { Ui.erro("Cole o código de autorização."); return; }
            Ui.async(() -> { BlingService.conectarComCodigo(c); return true; }, ok -> {
                codigo.clear();
                atualizarStatus();
                Ui.toast("Bling conectado com sucesso!");
            });
        });

        Label passos = Ui.label("""
                Como conectar (uma vez só):
                1) No Bling, acesse Central de Extensões › Área do integrador › Criar aplicativo (tipo API).
                2) Em "Link de redirecionamento", use qualquer endereço seu (ex.: https://royalparafusos.com.br).
                3) Marque os escopos: Pedidos de Venda, Contatos e Produtos (leitura).
                4) Copie o Client ID e o Client Secret para cá, clique em "Autorizar", aceite no navegador
                   e copie o "code=..." que aparece no endereço da página. Cole abaixo e clique em "Conectar".""", "texto-suave");
        passos.setWrapText(true);

        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(12);
        g.add(Ui.campo("Client ID", clientId), 0, 0);
        g.add(Ui.campo("Client Secret", secret), 1, 0);
        g.add(Ui.campo("Código de autorização", codigo), 0, 1, 2, 1);
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(50);
        g.getColumnConstraints().addAll(cc, cc);

        HBox titulo = new HBox(12, new VBox(2, Ui.label("Conexão com o Bling", "card-titulo"), Ui.label("API v3 · OAuth 2.0", "card-sub")), Ui.espaco(), status);
        titulo.setAlignment(Pos.CENTER_LEFT);
        VBox cardConexao = Ui.card(titulo, passos, g, new HBox(10, abrir, conectar));

        // ---- importação de vendas
        DatePicker de = new DatePicker(LocalDate.now().minusDays(7));
        DatePicker ate = new DatePicker(LocalDate.now());
        Button importar = Ui.botao("Importar vendas agora", "btn-ouro", Icones.SYNC);
        log.setEditable(false);
        log.setPrefRowCount(12);
        log.getStyleClass().add("log");
        log.setWrapText(true);
        importar.setOnAction(e -> {
            if (!BlingService.conectado()) { Ui.erro("Conecte o Bling primeiro."); return; }
            if (de.getValue() == null || ate.getValue() == null || de.getValue().isAfter(ate.getValue())) { Ui.erro("Período inválido."); return; }
            log.clear();
            importar.setDisable(true);
            LocalDate d1 = de.getValue(), d2 = ate.getValue();
            Ui.async(() -> BlingService.importarVendas(d1, d2, msg -> Platform.runLater(() -> log.appendText(msg + "\n"))),
                    r -> Ui.toast(r.creditados() + " venda(s) pontuada(s) · " + Formatos.pontos(r.pontos())),
                    () -> importar.setDisable(false));
        });

        CheckBox auto = new CheckBox("Importar vendas novas automaticamente ao abrir o programa");
        auto.setSelected(Config.bool(Config.BLING_AUTO_IMPORTAR));
        auto.selectedProperty().addListener((o, a, n) -> Config.set(Config.BLING_AUTO_IMPORTAR, String.valueOf(n)));
        CheckBox autoCad = new CheckBox("Cadastrar automaticamente no programa os clientes do Bling com CPF e celular");
        autoCad.setSelected(Config.bool(Config.BLING_AUTO_CADASTRO));
        autoCad.selectedProperty().addListener((o, a, n) -> Config.set(Config.BLING_AUTO_CADASTRO, String.valueOf(n)));
        TextField ignorar = Ui.campoTexto("12");
        ignorar.setText(Config.get(Config.BLING_IGNORAR_SITUACOES));
        ignorar.setPrefWidth(120);
        ignorar.focusedProperty().addListener((o, a, n) -> { if (!n) Config.set(Config.BLING_IGNORAR_SITUACOES, ignorar.getText().trim()); });

        String ultima = Config.get(Config.BLING_ULTIMA_IMPORTACAO);
        Label info = Ui.label("Os pontos são creditados pelo CPF do contato do pedido, usando a mesma regra do balcão.\n"
                + "Cada pedido é importado só uma vez (não duplica). Última importação até: "
                + (ultima.isBlank() ? "nunca" : Formatos.data(LocalDate.parse(ultima))) + ".", "texto-suave");
        info.setWrapText(true);

        HBox periodo = new HBox(14, Ui.campo("De", de), Ui.campo("Até", ate), Ui.campo("Ignorar situações (IDs)", ignorar));
        periodo.setAlignment(Pos.BOTTOM_LEFT);
        HBox acoes = new HBox(importar);
        VBox cardVendas = Ui.cardTitulo("Importar vendas", "Pedidos de venda do Bling viram pontos para o cliente",
                info, periodo, auto, autoCad, acoes, log);

        // ---- conversão de produtos
        TextField conv = Ui.campoNumero("20", true);
        conv.setText(Config.get(Config.BLING_PONTOS_POR_REAL).replace('.', ','));
        conv.setPrefWidth(100);
        conv.focusedProperty().addListener((o, a, n) -> {
            if (!n && Formatos.valor(conv.getText()) != null) Config.set(Config.BLING_PONTOS_POR_REAL, Formatos.valor(conv.getText()).toPlainString());
        });
        HBox convLinha = new HBox(10, Ui.label("R$ 1,00 do preço do produto =", "texto-forte"), conv, Ui.label("pontos", "texto-forte"));
        convLinha.setAlignment(Pos.CENTER_LEFT);
        Label exemplo = Ui.label("Ex.: com 20 pontos por real, um produto de R$ 25,00 custa 500 pontos no catálogo. "
                + "Para importar produtos, vá em Catálogo de prêmios › Importar do Bling.", "texto-suave");
        exemplo.setWrapText(true);
        VBox cardConv = Ui.cardTitulo("Produtos do Bling como prêmios", "Conversão do preço de venda em pontos", convLinha, exemplo);

        VBox pagina = new VBox(20, cardConexao, cardVendas, cardConv);
        pagina.getStyleClass().add("pagina");
        getChildren().add(Ui.rolagem(pagina));
        atualizarStatus();
    }

    private void salvarCredenciais(TextField id, PasswordField secret) {
        Config.set(Config.BLING_CLIENT_ID, id.getText().trim());
        Config.set(Config.BLING_CLIENT_SECRET, secret.getText().trim());
    }

    private void atualizarStatus() {
        boolean ok = BlingService.conectado();
        status.setText(ok ? "● Conectado" : "● Não conectado");
        status.getStyleClass().setAll("label", "badge", ok ? "badge-verde" : "badge-cinza");
    }
}
