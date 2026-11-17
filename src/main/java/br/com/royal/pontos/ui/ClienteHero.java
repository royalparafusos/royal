package br.com.royal.pontos.ui;

import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.util.Formatos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.time.LocalDate;

/** Cartão de destaque do cliente com o saldo de pontos (estilo programas de fidelidade). */
public class ClienteHero extends StackPane {

    public ClienteHero(Cliente c, int aVencer, LocalDate proxima, Node... acoes) {
        getStyleClass().add("hero");

        Label nome = Ui.label(c.nome(), "hero-nome");
        Label dados = Ui.label("CPF " + Formatos.cpf(c.cpf()) + "   ·   " + Formatos.telefone(c.telefone()), "hero-rotulo");
        HBox pontos = new HBox(8, Ui.label(Formatos.numero(c.saldo()), "hero-pontos"), Ui.label("pontos", "hero-pts"));
        pontos.setAlignment(Pos.BASELINE_LEFT);

        javafx.scene.layout.FlowPane chips = new javafx.scene.layout.FlowPane(10, 8);
        if (aVencer > 0 && proxima != null) {
            chips.getChildren().add(chip(Icones.RELOGIO, Formatos.numero(aVencer) + " pts vencem em até 30 dias"));
        } else if (proxima != null) {
            chips.getChildren().add(chip(Icones.RELOGIO, "Próximo vencimento: " + Formatos.data(proxima)));
        }
        if (c.criadoEm() != null) chips.getChildren().add(chip(Icones.ESTRELA, "Cliente desde " + Formatos.data(c.criadoEm().toLocalDate())));

        VBox esq = new VBox(6, Ui.label("SALDO DISPONÍVEL", "hero-rotulo"), pontos, new VBox(2, nome, dados), chips);
        esq.setSpacing(8);
        HBox.setHgrow(esq, Priority.ALWAYS);

        VBox dir = new VBox(10, acoes);
        dir.setAlignment(Pos.CENTER_RIGHT);

        HBox linha = new HBox(20, esq, dir);
        linha.setAlignment(Pos.CENTER_LEFT);

        Circle d1 = new Circle(140); d1.getStyleClass().add("hero-decoracao");
        Circle d2 = new Circle(80); d2.getStyleClass().add("hero-decoracao");
        d1.setManaged(false); d2.setManaged(false);
        javafx.scene.layout.Pane deco = new javafx.scene.layout.Pane(d1, d2);
        deco.setManaged(false);
        deco.setMouseTransparent(true);
        getChildren().addAll(deco, linha);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(44);
        clip.setArcHeight(44);
        layoutBoundsProperty().addListener((o, a, b) -> {
            clip.setWidth(b.getWidth()); clip.setHeight(b.getHeight());
            d1.setCenterX(b.getWidth() - 30); d1.setCenterY(-20);
            d2.setCenterX(b.getWidth() - 250); d2.setCenterY(b.getHeight() + 10);
        });
        deco.setClip(clip);
    }

    private HBox chip(String icone, String texto) {
        Label l = new Label(texto);
        l.setMinWidth(Region.USE_PREF_SIZE);
        HBox h = new HBox(6, Ui.icone(icone, 13, "icone-branco"), l);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("hero-chip");
        return h;
    }
}
