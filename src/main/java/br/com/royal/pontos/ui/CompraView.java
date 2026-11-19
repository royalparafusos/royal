package br.com.royal.pontos.ui;

import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.model.Movimento;
import br.com.royal.pontos.service.Config;
import br.com.royal.pontos.service.PontosService;
import br.com.royal.pontos.util.Formatos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CompraView extends StackPane {

    private final ClienteBusca busca = new ClienteBusca(this::clienteMudou);
    private final TextField valor = Ui.campoNumero("0,00", true);
    private final TextField descricao = Ui.campoTexto("Nº do cupom / nota (opcional)");
    private final Label previaPts = Ui.label("0", "previa-pontos");
    private final Label previaInfo = Ui.label("", "texto-suave");
    private final Button confirmar = Ui.botao("Confirmar e creditar pontos", "btn-primario", Icones.CHECK);
    private final VBox etapa2 = new VBox(16);
    private final VBox hoje = new VBox(2);

    public CompraView(String cpfInicial) {
        VBox card1 = Ui.cardTitulo("1. Identifique o cliente", "Digite o CPF — a busca é automática", busca);

        valor.getStyleClass().add("campo-grande");
        valor.textProperty().addListener((o, a, n) -> atualizarPrevia());
        valor.setOnAction(e -> confirmar());
        HBox valorBox = new HBox(10, Ui.label("R$", "pagina-titulo"), valor);
        valorBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(valor, Priority.ALWAYS);

        VBox previa = new VBox(2, Ui.label("O cliente vai ganhar", "texto-suave"),
                new HBox(8, previaPts, Ui.label("pontos", "texto-roxo")), previaInfo);
        ((HBox) previa.getChildren().get(1)).setAlignment(Pos.BASELINE_LEFT);
        previa.getStyleClass().add("previa-box");

        confirmar.getStyleClass().add("btn-grande");
        confirmar.setMaxWidth(Double.MAX_VALUE);
        confirmar.setOnAction(e -> confirmar());

        etapa2.getChildren().addAll(Ui.campo("Valor da compra", valorBox), Ui.campo("Descrição", descricao), previa, confirmar);
        VBox card2 = Ui.cardTitulo("2. Valor da compra", null, etapa2);

        VBox esquerda = new VBox(20, card1, card2);
        HBox.setHgrow(esquerda, Priority.ALWAYS);

        // ---- lateral
        VBox regra = Ui.cardTitulo("Regra atual", null,
                linhaRegra(Icones.CARTEIRA, "A cada " + Formatos.moeda(Config.decimal(Config.REGRA_REAIS)), Config.inteiro(Config.REGRA_PONTOS) + " ponto(s)"),
                linhaRegra(Icones.RELOGIO, "Validade dos pontos", Config.inteiro(Config.VALIDADE_DIAS) + " dias"
                        + (Config.bool(Config.VALIDADE_RENOVA) ? " após a última compra" : "")),
                linhaRegra(Icones.TAG, "Compra mínima", Formatos.moeda(Config.decimal(Config.VALOR_MINIMO))));
        VBox cardHoje = Ui.cardTitulo("Lançados hoje", null, hoje);
        VBox direita = new VBox(20, regra, cardHoje);
        direita.setPrefWidth(330);
        direita.setMinWidth(300);

        esquerda.setMinWidth(420);
        HBox linha = new HBox(20, esquerda, direita);
        linha.getStyleClass().add("pagina");
        getChildren().add(Ui.rolagem(linha));
        clienteMudou(null);
        carregarHoje();
        if (cpfInicial != null) busca.definirCpf(cpfInicial); else busca.focar();
    }

    private HBox linhaRegra(String icone, String rotulo, String valor) {
        HBox h = new HBox(12, Ui.iconeCirculo(icone, "roxo", 36), Ui.label(rotulo, "texto-suave"), Ui.espaco(), Ui.label(valor, "texto-forte"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private void clienteMudou(Cliente c) {
        etapa2.setDisable(c == null);
        if (c != null) javafx.application.Platform.runLater(valor::requestFocus);
        atualizarPrevia();
    }

    private void atualizarPrevia() {
        BigDecimal v = Formatos.valor(valor.getText());
        int pts = PontosService.calcular(v);
        previaPts.setText(Formatos.numero(pts));
        if (v != null && v.signum() > 0 && pts == 0) {
            previaInfo.setText("Valor abaixo do mínimo para pontuar.");
        } else {
            previaInfo.setText(pts > 0 ? "Válidos até " + Formatos.data(PontosService.validadePara(LocalDate.now())) : "Informe o valor da compra");
        }
        confirmar.setDisable(pts <= 0 || busca.cliente() == null);
    }

    private void confirmar() {
        Cliente c = busca.cliente();
        BigDecimal v = Formatos.valor(valor.getText());
        if (c == null || v == null || PontosService.calcular(v) <= 0 || confirmar.isDisabled()) return;
        String desc = descricao.getText();
        confirmar.setDisable(true);
        confirmar.setText("Lançando...");
        Ui.async(() -> PontosService.creditar(c.id(), v, desc), r -> {
            sucesso(c, r);
            busca.recarregar();
            carregarHoje();
        }, () -> { confirmar.setText("Confirmar e creditar pontos"); atualizarPrevia(); });
    }

    private void sucesso(Cliente c, PontosService.Resultado r) {
        Label titulo = Ui.label("+" + Formatos.numero(r.pontos()) + " pontos creditados!", "pagina-titulo");
        titulo.setStyle("-fx-text-fill: #0E8A55;");
        Label info = Ui.label(Formatos.primeiroNome(c.nome()) + " agora tem " + Formatos.pontos(r.saldoAtual())
                + ". Pontos válidos até " + Formatos.data(r.validade()) + ".", "texto-forte");
        info.setWrapText(true);
        Button whats = Ui.botao("Avisar cliente no WhatsApp", "btn-verde", Icones.CHAT);
        whats.setOnAction(e -> Ui.abrirWhatsApp(c.telefone(), mensagem(c, r)));
        Button novo = Ui.botao("Novo lançamento", "btn-primario", Icones.MAIS);
        novo.setOnAction(e -> MainView.atual().ir(MainView.Tela.COMPRA));
        Button mesmo = Ui.botao("Outra compra deste cliente", "btn-secundario", null);
        mesmo.setOnAction(e -> {
            valor.clear();
            descricao.clear();
            busca.recarregar();
            etapa2.getChildren().setAll(etapa2Original);
        });
        FlowPane botoes = new FlowPane(10, 10, whats, novo, mesmo);
        VBox box = new VBox(12, Ui.iconeCirculo(Icones.CHECK, "verde", 52), titulo, info, botoes);
        box.getStyleClass().add("sucesso-box");
        etapa2Original = etapa2.getChildren().toArray(new javafx.scene.Node[0]);
        etapa2.getChildren().setAll(box);
        Ui.toast("Pontos creditados para " + Formatos.primeiroNome(c.nome()));
    }

    private javafx.scene.Node[] etapa2Original;

    private String mensagem(Cliente c, PontosService.Resultado r) {
        String link = Config.get(Config.LINK_CONSULTA);
        return "Olá, " + Formatos.primeiroNome(c.nome()) + "! 🎉\n"
                + "Sua compra na *" + Config.get(Config.LOJA_NOME) + "* rendeu *" + Formatos.pontos(r.pontos()) + "*.\n"
                + "Saldo atual: *" + Formatos.pontos(r.saldoAtual()) + "* (estes pontos valem até " + Formatos.data(r.validade()) + ").\n"
                + "Junte pontos e troque por produtos! 👑"
                + (link.isBlank() ? "" : "\n\nConsulte seu saldo: " + link);
    }

    private void carregarHoje() {
        Ui.async(PontosService::creditosDeHoje, lista -> {
            hoje.getChildren().clear();
            if (lista.isEmpty()) hoje.getChildren().add(Ui.label("Nenhum lançamento hoje.", "texto-suave"));
            for (Movimento m : lista) {
                Label n = Ui.nome(m.clienteNome(), "texto-forte", 160);
                VBox info = new VBox(1, n, Ui.label(m.criadoEm().toLocalTime().toString().substring(0, 5) + " · " + Formatos.moeda(m.valor()), "texto-suave"));
                HBox h = new HBox(10, Ui.avatar(Formatos.iniciais(m.clienteNome()), 32), info, Ui.espaco(), Ui.label("+" + Formatos.numero(m.pontos()), "texto-verde"));
                h.setAlignment(Pos.CENTER_LEFT);
                h.setStyle("-fx-padding: 6 0;");
                hoje.getChildren().add(h);
            }
        });
    }
}
