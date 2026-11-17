package br.com.royal.pontos.ui;

import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.model.Movimento;
import br.com.royal.pontos.service.PainelService;
import br.com.royal.pontos.service.PontosService;
import br.com.royal.pontos.util.Formatos;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PainelView extends StackPane {

    private final Label kClientes = Ui.label("—", "kpi-valor"), kClientesExtra = Ui.label("", "kpi-extra");
    private final Label kEmitidos = Ui.label("—", "kpi-valor"), kEmitidosExtra = Ui.label("", "kpi-extra");
    private final Label kResgatados = Ui.label("—", "kpi-valor"), kResgatadosExtra = Ui.label("", "kpi-extra");
    private final Label kVencer = Ui.label("—", "kpi-valor"), kVencerExtra = Ui.label("", "kpi-extra");
    private final XYChart.Series<Number, Number> sEmitidos = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> sResgatados = new XYChart.Series<>();
    private final VBox topClientes = new VBox(4);
    private final TableView<Movimento> tabela = Tabelas.movimentos(true);

    public PainelView() {
        HBox kpis = new HBox(18,
                kpi(Icones.PESSOAS, "azul", "Clientes no programa", kClientes, kClientesExtra),
                kpi(Icones.TENDENCIA, "roxo", "Pontos emitidos no mês", kEmitidos, kEmitidosExtra),
                kpi(Icones.PRESENTE, "verde", "Pontos resgatados no mês", kResgatados, kResgatadosExtra),
                kpi(Icones.RELOGIO, "ouro", "A vencer em 7 dias", kVencer, kVencerExtra));

        NumberAxis x = new NumberAxis(0, 29, 7);
        x.setMinorTickVisible(false);
        x.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override public String toString(Number n) { return DateTimeFormatter.ofPattern("dd/MM").format(LocalDate.now().minusDays(29 - n.intValue())); }
            @Override public Number fromString(String s) { return 0; }
        });
        NumberAxis y = new NumberAxis();
        y.setForceZeroInRange(true);
        AreaChart<Number, Number> grafico = new AreaChart<>(x, y);
        grafico.setLegendVisible(true);
        grafico.setAnimated(false);
        grafico.setCreateSymbols(false);
        grafico.setPrefHeight(270);
        sEmitidos.setName("Pontos emitidos");
        sResgatados.setName("Pontos resgatados");
        grafico.getData().add(sEmitidos);
        grafico.getData().add(sResgatados);
        VBox cardGrafico = Ui.cardTitulo("Movimento de pontos", "Últimos 30 dias", grafico);
        HBox.setHgrow(cardGrafico, Priority.ALWAYS);

        VBox cardTop = Ui.cardTitulo("Maiores saldos", "Clientes com mais pontos disponíveis", topClientes);
        cardTop.setPrefWidth(340);
        cardTop.setMinWidth(300);

        tabela.setPrefHeight(330);
        VBox cardMov = Ui.cardTitulo("Últimas movimentações", null, tabela);

        VBox pagina = new VBox(20, kpis, new HBox(20, cardGrafico, cardTop), cardMov);
        pagina.getStyleClass().add("pagina");
        getChildren().add(Ui.rolagem(pagina));
        carregar();
    }

    private VBox kpi(String icone, String cor, String rotulo, Label valor, Label extra) {
        extra.setWrapText(true);
        HBox topo = new HBox(Ui.label(rotulo, "kpi-rotulo"), Ui.espaco(), Ui.iconeCirculo(icone, cor, 42));
        topo.setAlignment(Pos.TOP_LEFT);
        VBox v = Ui.card(topo, new VBox(4, valor, extra));
        v.setSpacing(6);
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    private void carregar() {
        Ui.async(() -> {
            PontosService.processarExpiracoes();
            return new Object[]{PainelService.carregar(), PontosService.ultimos(25)};
        }, r -> {
            PainelService.Resumo s = (PainelService.Resumo) r[0];
            @SuppressWarnings("unchecked") List<Movimento> movs = (List<Movimento>) r[1];
            kClientes.setText(Formatos.numero(s.clientes()));
            kClientesExtra.setText("+" + s.novosMes() + " novos este mês");
            kEmitidos.setText(Formatos.numero(s.emitidosMes()));
            kEmitidosExtra.setText(Formatos.moeda(s.vendasMes()) + " em compras pontuadas");
            kResgatados.setText(Formatos.numero(s.resgatadosMes()));
            kResgatadosExtra.setText(Formatos.numero(s.emCirculacao()) + " pts disponíveis com clientes");
            kVencer.setText(Formatos.numero(s.aVencer7()));
            kVencerExtra.setText(s.aVencer7() > 0 ? "Avise os clientes! 📲" : "Nenhum ponto vencendo");
            kVencerExtra.setStyle(s.aVencer7() > 0 ? "-fx-text-fill: #C27C00;" : "");

            sEmitidos.getData().clear();
            sResgatados.getData().clear();
            int dia = 0;
            for (var e : s.serie().entrySet()) {
                sEmitidos.getData().add(new XYChart.Data<>(dia, e.getValue()[0]));
                sResgatados.getData().add(new XYChart.Data<>(dia, e.getValue()[1]));
                dia++;
            }

            topClientes.getChildren().clear();
            if (s.top().isEmpty()) topClientes.getChildren().add(Ui.label("Ainda não há clientes com pontos.", "texto-suave"));
            int pos = 1;
            for (Cliente c : s.top()) topClientes.getChildren().add(linhaTop(pos++, c));
            tabela.getItems().setAll(movs);
        });
    }

    private HBox linhaTop(int pos, Cliente c) {
        Label n = Ui.nome(c.nome(), "texto-forte", 150);
        VBox info = new VBox(1, n, Ui.label(Formatos.telefone(c.telefone()), "texto-suave"));
        Label pts = Ui.badge(Formatos.pontos(c.saldo()), "ouro");
        HBox h = new HBox(12, Ui.label(pos + "º", "texto-roxo"), Ui.avatar(Formatos.iniciais(c.nome()), 34), info, Ui.espaco(), pts);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setStyle("-fx-padding: 7 4; -fx-cursor: hand;");
        HBox.setHgrow(info, Priority.ALWAYS);
        info.setMinWidth(0);
        h.setOnMouseClicked(e -> MainView.atual().abrirCliente(c.id()));
        return h;
    }

}
