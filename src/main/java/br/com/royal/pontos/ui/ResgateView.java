package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.model.Premio;
import br.com.royal.pontos.service.Config;
import br.com.royal.pontos.service.PontosService;
import br.com.royal.pontos.service.PremioService;
import br.com.royal.pontos.util.Formatos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ResgateView extends StackPane {

    private final ClienteBusca busca = new ClienteBusca(this::clienteMudou);
    private final StackPane heroArea = new StackPane();
    private final FlowPane catalogo = new FlowPane(18, 18);
    private final VBox areaDesconto = new VBox(16);
    private final StackPane areaConteudo = new StackPane();
    private final TextField filtro = Ui.campoTexto("Buscar prêmio");
    private List<Premio> premios = List.of();
    private Cliente cliente;

    public ResgateView(String cpfInicial) {
        VBox cardBusca = Ui.cardTitulo("Identifique o cliente", null, busca);
        cardBusca.setPrefWidth(470);
        cardBusca.setMinWidth(440);
        HBox.setHgrow(heroArea, Priority.ALWAYS);
        HBox topo = new HBox(20, cardBusca, heroArea);

        // ---- alternância Produtos / Desconto
        ToggleGroup g = new ToggleGroup();
        ToggleButton tProdutos = new ToggleButton("Trocar por produtos");
        ToggleButton tDesconto = new ToggleButton("Trocar por desconto");
        tProdutos.setToggleGroup(g);
        tDesconto.setToggleGroup(g);
        HBox segmento = new HBox(4);
        segmento.getStyleClass().add("segmento");
        segmento.setMaxWidth(Region.USE_PREF_SIZE);
        if (Config.aceitaProdutos()) segmento.getChildren().add(tProdutos);
        if (Config.aceitaDesconto()) segmento.getChildren().add(tDesconto);

        HBox filtroBox = new HBox(8, Ui.icone(Icones.BUSCA, 16), filtro);
        filtroBox.setAlignment(Pos.CENTER_LEFT);
        filtro.setPrefWidth(260);
        filtro.textProperty().addListener((o, a, n) -> desenharCatalogo());
        HBox barra = new HBox(16, segmento, Ui.espaco(), filtroBox);
        barra.setAlignment(Pos.CENTER_LEFT);

        VBox produtos = new VBox(catalogo);
        g.selectedToggleProperty().addListener((o, a, n) -> {
            if (n == null) { (a != null ? a : tProdutos).setSelected(true); return; }
            boolean prod = n == tProdutos;
            filtroBox.setVisible(prod);
            areaConteudo.getChildren().setAll(prod ? produtos : areaDesconto);
        });

        VBox pagina = new VBox(20, topo, barra, areaConteudo);
        pagina.getStyleClass().add("pagina");
        getChildren().add(Ui.rolagem(pagina));

        (Config.aceitaProdutos() ? tProdutos : tDesconto).setSelected(true);
        clienteMudou(null);
        Ui.async(() -> PremioService.listar(false, ""), l -> { premios = l; desenharCatalogo(); });
        if (cpfInicial != null) busca.definirCpf(cpfInicial); else busca.focar();
    }

    private void clienteMudou(Cliente c) {
        cliente = c;
        if (c == null) {
            VBox vazio = Ui.card(Ui.iconeCirculo(Icones.CARTEIRA, "roxo", 56),
                    Ui.label("Identifique o cliente", "card-titulo"),
                    Ui.label("O saldo e os prêmios disponíveis aparecem aqui.", "texto-suave"));
            vazio.setAlignment(Pos.CENTER);
            heroArea.getChildren().setAll(vazio);
        } else {
            Ui.async(() -> new Object[]{PontosService.aVencer(c.id(), 30), PontosService.proximaExpiracao(c.id())},
                    r -> heroArea.getChildren().setAll(new ClienteHero(c, (Integer) r[0], (LocalDate) r[1])));
        }
        desenharCatalogo();
        desenharDesconto();
    }

    // ------------------------------------------------------------------ produtos

    private void desenharCatalogo() {
        String f = filtro.getText() == null ? "" : filtro.getText().trim().toLowerCase();
        catalogo.getChildren().clear();
        for (Premio p : premios) {
            if (!p.disponivel() || (!f.isEmpty() && !p.nome().toLowerCase().contains(f))) continue;
            catalogo.getChildren().add(cardPremio(p));
        }
        if (catalogo.getChildren().isEmpty()) {
            catalogo.getChildren().add(Ui.label(premios.isEmpty()
                    ? "Nenhum prêmio cadastrado. Cadastre em \"Catálogo de prêmios\"." : "Nenhum prêmio encontrado.", "texto-suave"));
        }
    }

    private VBox cardPremio(Premio p) {
        Label nome = Ui.label(p.nome(), "premio-nome");
        nome.setWrapText(true);
        nome.setMinHeight(38);
        nome.setMaxWidth(186);
        HBox pts = new HBox(4, Ui.label(Formatos.numero(p.pontos()), "premio-pontos"), Ui.label("pts", "premio-pts"));
        pts.setAlignment(Pos.BASELINE_LEFT);
        Node estoque = p.semControleEstoque() ? Ui.badge("Disponível", "verde")
                : Ui.badge(p.estoque() + " em estoque", p.estoque() <= 3 ? "ouro" : "verde");

        Button resgatar = Ui.botao("Resgatar", "btn-primario", null);
        resgatar.setMaxWidth(Double.MAX_VALUE);
        Label falta = Ui.label("", "premio-falta");
        if (cliente == null) {
            resgatar.setDisable(true);
            falta.setText("Identifique o cliente");
        } else if (cliente.saldo() < p.pontos()) {
            resgatar.setDisable(true);
            falta.setText("Faltam " + Formatos.pontos(p.pontos() - cliente.saldo()));
        } else {
            falta.setText("✓ Saldo suficiente");
            falta.setStyle("-fx-text-fill: #0E8A55;");
        }
        resgatar.setOnAction(e -> resgatarProduto(p));

        VBox card = new VBox(10, Ui.imagemPremio(p.imagem(), 186, 130), nome, new HBox(pts, Ui.espaco(), estoque), falta, resgatar);
        card.getStyleClass().add("premio-card");
        card.setPrefWidth(210);
        card.setMaxWidth(210);
        return card;
    }

    private void resgatarProduto(Premio p) {
        int max = cliente.saldo() / p.pontos();
        if (!p.semControleEstoque()) max = Math.min(max, p.estoque());
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Confirmar resgate");
        d.setHeaderText("Resgatar \"" + p.nome() + "\"");
        d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());
        Spinner<Integer> qtd = new Spinner<>(1, Math.max(1, max), 1);
        qtd.setEditable(true);
        qtd.setPrefWidth(110);
        Label total = Ui.label("", "texto-roxo");
        Label restante = Ui.label("", "texto-suave");
        Runnable atualizar = () -> {
            int t = p.pontos() * qtd.getValue();
            total.setText("Total: " + Formatos.pontos(t));
            restante.setText("Saldo após o resgate: " + Formatos.pontos(cliente.saldo() - t));
        };
        qtd.valueProperty().addListener((o, a, n) -> atualizar.run());
        atualizar.run();
        CompraDoDia compra = new CompraDoDia();
        VBox dados = new VBox(6, Ui.label("Cliente: " + cliente.nome(), "texto-forte"), Ui.campo("Quantidade", qtd), total, restante);
        if (compra.exigida()) dados.getChildren().add(compra.caixa());
        HBox linha = new HBox(14, Ui.imagemPremio(p.imagem(), 90, 70), dados);
        linha.setAlignment(Pos.CENTER_LEFT);
        d.getDialogPane().setContent(linha);
        ButtonType ok = new ButtonType("Confirmar resgate", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        d.getDialogPane().lookupButton(ok).disableProperty().bind(compra.invalida());
        if (d.showAndWait().filter(b -> b == ok).isEmpty()) return;
        long cid = cliente.id();
        int quantidade = qtd.getValue();
        BigDecimal valorCompra = compra.valor();
        Ui.async(() -> new Object[]{PontosService.resgatarProduto(cid, p.id(), quantidade, valorCompra), PremioService.listar(false, "")}, res -> {
            premios = castLista(res[1]);
            comprovante("Resgate confirmado!", quantidade + "x " + p.nome(), (PontosService.Resultado) res[0],
                    "Entregue o produto ao cliente e guarde o código do comprovante.");
            busca.recarregar();
        });
    }

    // ------------------------------------------------------------------ desconto

    private void desenharDesconto() {
        int bloco = Math.max(1, Config.inteiro(Config.DESCONTO_PONTOS));
        BigDecimal reais = Config.decimal(Config.DESCONTO_REAIS);
        Label regra = Ui.label("Cada " + Formatos.pontos(bloco) + " = " + Formatos.moeda(reais) + " de desconto", "card-titulo");

        if (cliente == null || cliente.saldo() < bloco) {
            String msg = cliente == null ? "Identifique o cliente para gerar um cupom de desconto."
                    : "O cliente precisa de pelo menos " + Formatos.pontos(bloco) + " (faltam " + Formatos.pontos(bloco - cliente.saldo()) + ").";
            areaDesconto.getChildren().setAll(Ui.card(new HBox(14, Ui.iconeCirculo(Icones.TAG, "ouro", 48), new VBox(4, regra, Ui.label(msg, "texto-suave")))));
            return;
        }
        int maxBlocos = cliente.saldo() / bloco;
        Slider slider = new Slider(1, maxBlocos, maxBlocos);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setPrefWidth(420);
        slider.setDisable(maxBlocos == 1);
        Label pts = Ui.label("", "previa-pontos");
        Label valor = Ui.label("", "pagina-titulo");
        valor.setStyle("-fx-text-fill: #0E8A55;");
        Runnable atualizar = () -> {
            int b = (int) Math.round(slider.getValue());
            pts.setText(Formatos.numero((long) b * bloco) + " pts");
            valor.setText("= " + Formatos.moeda(PontosService.valorDesconto(b * bloco)) + " de desconto");
        };
        slider.valueProperty().addListener((o, a, n) -> atualizar.run());
        atualizar.run();

        Button gerar = Ui.botao("Gerar cupom de desconto", "btn-ouro", Icones.TAG);
        gerar.getStyleClass().add("btn-grande");
        CompraDoDia compra = new CompraDoDia();
        gerar.disableProperty().bind(compra.invalida());
        gerar.setOnAction(e -> {
            int usar = (int) Math.round(slider.getValue()) * bloco;
            if (!Ui.confirmar("Gerar cupom", "Usar " + Formatos.pontos(usar) + " de " + cliente.nome() + " para gerar "
                    + Formatos.moeda(PontosService.valorDesconto(usar)) + " de desconto?")) return;
            long cid = cliente.id();
            BigDecimal valorCompra = compra.valor();
            gerar.disableProperty().unbind();
            gerar.setDisable(true);
            Ui.async(() -> PontosService.resgatarDesconto(cid, usar, valorCompra), r -> {
                comprovante("Cupom gerado!", "Desconto de " + Formatos.moeda(r.valor()), r,
                        "Aplique o desconto no caixa agora e anote o código do cupom na venda.");
                busca.recarregar();
            }, () -> { gerar.setDisable(false); gerar.disableProperty().bind(compra.invalida()); });
        });
        VBox previa = new VBox(4, Ui.label("Quantos pontos usar?", "texto-suave"), pts, valor);
        previa.getStyleClass().add("previa-box");
        VBox card = Ui.card(new HBox(14, Ui.iconeCirculo(Icones.TAG, "ouro", 48), new VBox(4, regra,
                Ui.label("O cliente tem " + Formatos.pontos(cliente.saldo()) + " — pode gerar até "
                        + Formatos.moeda(PontosService.valorDesconto(maxBlocos * bloco)) + " de desconto.", "texto-suave"))),
                slider, previa);
        if (compra.exigida()) card.getChildren().add(compra.caixa());
        card.getChildren().add(gerar);
        card.setMaxWidth(640);
        areaDesconto.getChildren().setAll(card);
    }

    /** Campo "valor da compra de hoje", exigido quando há compra mínima para resgatar. */
    private static final class CompraDoDia {
        private final BigDecimal minimo = PontosService.compraMinimaResgate();
        private final TextField campo = Ui.campoNumero("0,00", true);
        private final javafx.beans.property.BooleanProperty invalida = new javafx.beans.property.SimpleBooleanProperty(false);

        CompraDoDia() {
            campo.setPrefWidth(160);
            if (exigida()) {
                invalida.set(true);
                campo.textProperty().addListener((o, a, n) -> {
                    BigDecimal v = Formatos.valor(n);
                    invalida.set(v == null || v.compareTo(minimo) < 0);
                });
            }
        }

        boolean exigida() { return minimo.signum() > 0; }

        javafx.beans.property.BooleanProperty invalida() { return invalida; }

        BigDecimal valor() { return exigida() ? Formatos.valor(campo.getText()) : null; }

        VBox caixa() {
            Label dica = Ui.label("Resgate só com compra a partir de " + Formatos.moeda(minimo) + ".", "texto-suave");
            dica.setWrapText(true);
            return new VBox(4, Ui.campo("Valor da compra de hoje (R$)", campo), dica);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Premio> castLista(Object o) { return (List<Premio>) o; }

    // ------------------------------------------------------------------ comprovante

    private void comprovante(String titulo, String item, PontosService.Resultado r, String instrucao) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(titulo);
        d.setHeaderText(null);
        d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());
        Cliente c = cliente;
        Label t = Ui.label(titulo, "pagina-titulo");
        t.setStyle("-fx-text-fill: #0E8A55;");
        Label codigo = Ui.label(r.codigo(), "codigo");
        Label instr = Ui.label(instrucao, "texto-suave");
        instr.setWrapText(true);
        Button copiar = Ui.botao("Copiar código", "btn-secundario", null);
        copiar.setOnAction(e -> {
            var cc = new javafx.scene.input.ClipboardContent();
            cc.putString(r.codigo());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            Ui.toast("Código copiado");
        });
        Button whats = Ui.botao("Enviar comprovante no WhatsApp", "btn-verde", Icones.CHAT);
        whats.setOnAction(e -> Ui.abrirWhatsApp(c.telefone(), "Olá, " + Formatos.primeiroNome(c.nome()) + "! 🎁\n"
                + "Seu resgate na *" + Config.get(Config.LOJA_NOME) + "* foi confirmado: *" + item + "*.\n"
                + "Código: *" + r.codigo() + "*\nPontos usados: " + Formatos.pontos(r.pontos())
                + "\nSaldo restante: *" + Formatos.pontos(r.saldoAtual()) + "*\nObrigado por ser cliente Royal! 👑"));
        VBox box = new VBox(12, Ui.iconeCirculo(Icones.CHECK, "verde", 56), t, Ui.label(item + " · " + c.nome(), "texto-forte"),
                Ui.label("Pontos usados: " + Formatos.pontos(r.pontos()) + "   ·   Saldo restante: " + Formatos.pontos(r.saldoAtual()), "texto-suave"),
                new HBox(10, codigo, copiar), instr, whats);
        ((HBox) box.getChildren().get(4)).setAlignment(Pos.CENTER_LEFT);
        box.setPrefWidth(460);
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().add(new ButtonType("Concluir", ButtonBar.ButtonData.OK_DONE));
        d.showAndWait();
        Ui.toast("Resgate registrado no extrato do cliente");
    }
}
