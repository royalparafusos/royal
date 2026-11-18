package br.com.royal.pontos.util;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Gerador simples de planilhas Excel (.xlsx), sem bibliotecas externas. */
public final class Xlsx {

    /** Estilos: 0 = normal, 1 = cabeçalho, 2 = número com separador de milhar, 3 = número em destaque. */
    public static final int NORMAL = 0, CABECALHO = 1, NUMERO = 2, DESTAQUE = 3;

    public static final class Aba {
        final String nome;
        final List<Object[]> linhas = new ArrayList<>();
        double[] larguras = new double[0];

        Aba(String nome) { this.nome = nome; }

        public Aba larguras(double... l) { larguras = l; return this; }

        /** Valores: String, Number ou null. A primeira linha é tratada como cabeçalho. */
        public Aba linha(Object... valores) { linhas.add(valores); return this; }
    }

    private final List<Aba> abas = new ArrayList<>();

    public Aba aba(String nome) {
        Aba a = new Aba(nome.length() > 31 ? nome.substring(0, 31) : nome);
        abas.add(a);
        return a;
    }

    public void salvar(Path arquivo) throws Exception {
        Files.createDirectories(arquivo.getParent());
        Path tmp = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream z = new ZipOutputStream(os)) {
            put(z, "[Content_Types].xml", contentTypes());
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(z, "xl/workbook.xml", workbook());
            put(z, "xl/_rels/workbook.xml.rels", workbookRels());
            put(z, "xl/styles.xml", STYLES);
            for (int i = 0; i < abas.size(); i++) put(z, "xl/worksheets/sheet" + (i + 1) + ".xml", sheet(abas.get(i)));
        }
        Files.move(tmp, arquivo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void put(ZipOutputStream z, String nome, String conteudo) throws Exception {
        z.putNextEntry(new ZipEntry(nome));
        z.write(conteudo.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private String contentTypes() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= abas.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < abas.size(); i++) {
            sb.append("<sheet name=\"").append(esc(abas.get(i).nome)).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private String workbookRels() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < abas.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i + 1).append(".xml\"/>");
        }
        int n = abas.size() + 1;
        sb.append("<Relationship Id=\"rId").append(n)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    private String sheet(Aba a) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        if (a.larguras.length > 0) {
            sb.append("<cols>");
            for (int i = 0; i < a.larguras.length; i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(a.larguras[i]).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        int maxCols = 0;
        for (int r = 0; r < a.linhas.size(); r++) {
            Object[] linha = a.linhas.get(r);
            maxCols = Math.max(maxCols, linha.length);
            sb.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < linha.length; c++) {
                Object v = linha[c];
                if (v == null) continue;
                String ref = coluna(c) + (r + 1);
                if (v instanceof Number n && r > 0) {
                    int estilo = (v instanceof Double || v instanceof Float || v instanceof java.math.BigDecimal) ? NORMAL : NUMERO;
                    if (c == 3 && a.nome.equals("Saldos")) estilo = DESTAQUE;
                    sb.append("<c r=\"").append(ref).append("\" s=\"").append(estilo).append("\"><v>").append(n).append("</v></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(r == 0 ? " s=\"1\"" : "")
                            .append("><is><t xml:space=\"preserve\">").append(esc(String.valueOf(v))).append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        if (a.linhas.size() > 1 && maxCols > 0) {
            sb.append("<autoFilter ref=\"A1:").append(coluna(maxCols - 1)).append(a.linhas.size()).append("\"/>");
        }
        return sb.append("</worksheet>").toString();
    }

    private static String coluna(int i) {
        StringBuilder s = new StringBuilder();
        i++;
        while (i > 0) { int m = (i - 1) % 26; s.insert(0, (char) ('A' + m)); i = (i - 1) / 26; }
        return s.toString();
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                default -> { if (ch >= 0x20 || ch == '\t' || ch == '\n') b.append(ch); }
            }
        }
        return b.toString();
    }

    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><color rgb=\"FF1E36B0\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1E36B0\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"4\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
            + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"3\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>"
            + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";

    private Xlsx() {}

    public static Xlsx nova() { return new Xlsx(); }
}
