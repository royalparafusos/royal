package br.com.royal.pontos.ui;

import br.com.royal.pontos.model.Movimento;
import br.com.royal.pontos.util.Formatos;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.util.Callback;

import java.util.function.Function;

/** Fábrica de tabelas padronizadas. */
public final class Tabelas {

    private Tabelas() {}

    public static <S, T> TableColumn<S, T> coluna(String titulo, Function<S, T> valor, double largura) {
        TableColumn<S, T> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleObjectProperty<>(valor.apply(d.getValue())));
        c.setPrefWidth(largura);
        c.setReorderable(false);
        return c;
    }

    /** Coluna que desenha um nó (badge, botão...) em cada linha. */
    public static <S> TableColumn<S, S> colunaNo(String titulo, Function<S, Node> desenho, double largura) {
        TableColumn<S, S> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(S item, boolean vazio) {
                super.updateItem(item, vazio);
                setText(null);
                setGraphic(vazio || item == null ? null : desenho.apply(item));
            }
        });
        c.setPrefWidth(largura);
        c.setReorderable(false);
        c.setSortable(false);
        return c;
    }

    public static <S> TableView<S> nova(String vazio) {
        TableView<S> t = new TableView<>();
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new Label(vazio));
        return t;
    }

    public static TableView<Movimento> movimentos(boolean comCliente) {
        TableView<Movimento> t = nova("Nenhuma movimentação ainda.");
        t.getColumns().add(coluna("DATA", m -> Formatos.dataHora(m.criadoEm()), 120));
        if (comCliente) t.getColumns().add(coluna("CLIENTE", Movimento::clienteNome, 170));
        t.getColumns().add(colunaNo("TIPO", m -> Ui.badge(m.tipoTexto(), corTipo(m)), 145));
        t.getColumns().add(coluna("DESCRIÇÃO", m -> m.descricao() + (m.cupom() != null ? "  ·  " + m.cupom() : ""), 300));
        t.getColumns().add(colunaNo("PONTOS", m -> {
            Label l = Ui.label((m.pontos() > 0 ? "+" : "") + Formatos.numero(m.pontos()), m.pontos() > 0 ? "texto-verde" : "texto-vermelho");
            if (m.estornado()) l.setStyle("-fx-strikethrough: true; -fx-opacity: 0.6;");
            return l;
        }, 90));
        t.getColumns().add(coluna("VALIDADE", m -> m.isLote() ? Formatos.data(m.expiraEm()) : "", 90));
        t.getColumns().add(colunaNo("SITUAÇÃO", m -> {
            String s = m.situacao();
            if (s.isEmpty()) return null;
            String cor = switch (s) { case "Ativo" -> "verde"; case "Parcial" -> "azul"; case "Estornado", "Expirado" -> "vermelho"; default -> "cinza"; };
            return Ui.badge(s, cor);
        }, 95));
        if (!comCliente) t.getColumns().add(coluna("USUÁRIO", m -> m.usuarioNome() == null ? "Sistema" : m.usuarioNome(), 110));
        return t;
    }

    static String corTipo(Movimento m) {
        return switch (m.tipo()) {
            case Movimento.CREDITO -> "verde";
            case Movimento.RESGATE_PRODUTO, Movimento.RESGATE_DESCONTO -> "roxo";
            case Movimento.EXPIRACAO -> "vermelho";
            case Movimento.ESTORNO -> "cinza";
            default -> "azul";
        };
    }

    public static <S> void aoDuploClique(TableView<S> t, java.util.function.Consumer<S> acao) {
        t.setRowFactory(tv -> {
            TableRow<S> r = new TableRow<>();
            r.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !r.isEmpty()) acao.accept(r.getItem()); });
            return r;
        });
    }
}
