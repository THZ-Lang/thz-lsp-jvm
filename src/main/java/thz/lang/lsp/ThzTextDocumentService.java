package thz.lang.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

import thz.lang.governanca.AuditorGovernanca;
import thz.lang.governanca.RelatorioAuditoria;
import thz.lang.lexico.SintaxeEnxuta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ThzTextDocumentService implements TextDocumentService {

    private final ThzLanguageServerImpl server;

    public ThzTextDocumentService(ThzLanguageServerImpl server) {
        this.server = server;
    }

    // ---- Lifecycle ----

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getTextDocument().getText();
        server.atualizarDocumento(uri, text);
        validar(uri);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
            server.atualizarDocumento(uri, change.getText());
        }
        validar(uri);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        if (params.getText() != null) {
            server.atualizarDocumento(uri, params.getText());
        }
        validar(uri);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        server.removerDocumento(uri);
    }

    // ---- Validação ----

    private void validar(String uri) {
        String fonte = server.obterDocumento(uri);
        if (fonte == null) return;

        ThzLanguageServerImpl.ResultadoAnalise r = server.analisar(fonte);
        List<Diagnostic> diags = server.paraLspDiagnostics(r.diagnosticos(), fonte);

        for (SintaxeEnxuta.DiagnosticoIndentacao erro : SintaxeEnxuta.validarIndentacao(fonte)) {
            Diagnostic diag = new Diagnostic();
            diag.setSeverity(DiagnosticSeverity.Error);
            diag.setRange(new Range(
                    new Position(erro.linha() - 1, Math.max(0, erro.coluna() - 1)),
                    new Position(erro.linha() - 1, Math.max(1, erro.coluna()))
            ));
            diag.setMessage(erro.mensagem());
            diag.setSource("thz-indentacao");
            diags.add(diag);
        }

        // G4 — pendências de governança como warnings
        if (r.ast() != null) {
            try {
                RelatorioAuditoria auditoria = AuditorGovernanca.auditar(r.ast());
                if (auditoria.metricas().pendencias() != null) {
                    for (String p : auditoria.metricas().pendencias()) {
                        if (diags.stream().anyMatch(d -> d.getMessage().equals(p))) continue;
                        Diagnostic diag = new Diagnostic();
                        diag.setSeverity(DiagnosticSeverity.Warning);
                        diag.setRange(new Range(new Position(0, 0), new Position(0, 1)));
                        diag.setMessage("[Governança] " + p);
                        diag.setSource("thz-governanca");
                        diags.add(diag);
                    }
                }
            } catch (Exception ignored) {}
        }

        PublishDiagnosticsParams params = new PublishDiagnosticsParams();
        params.setUri(uri);
        params.setDiagnostics(diags);
        server.getClient().publishDiagnostics(params);
    }

    // ---- Hover ----

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        String fonte = server.obterDocumento(uri);
        if (fonte == null) return CompletableFuture.completedFuture(null);

        int linha = params.getPosition().getLine() + 1;
        int coluna = params.getPosition().getCharacter() + 1;

        ThzLanguageServerImpl.HoverResult h = server.obterHover(fonte, linha, coluna);
        if (h == null) return CompletableFuture.completedFuture(null);

        MarkupContent content = new MarkupContent();
        content.setKind(MarkupKind.MARKDOWN);
        content.setValue(h.conteudo());

        Hover hover = new Hover();
        hover.setContents(content);
        hover.setRange(h.range());
        return CompletableFuture.completedFuture(hover);
    }

    // ---- Completion ----

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        List<CompletionItem> items = new ArrayList<>();

        String[] sintaxeEnxuta = {
                "programa Nome:", "biblioteca Nome:", "estrutura Nome:",
                "regra Nome:", "funcao nome() -> Tipo:", "operacao nome() -> Tipo:",
                "procedimento nome():", "se condicao:", "senao:", "enquanto condicao:",
                "para item de inicio ate fim:", "nome := valor", "retorne valor",
                "exige condicao", "garante condicao"
        };
        for (String forma : sintaxeEnxuta) {
            CompletionItem item = new CompletionItem();
            item.setLabel(forma);
            item.setKind(CompletionItemKind.Snippet);
            item.setDetail("sintaxe canônica enxuta THZ-LANG");
            items.add(item);
        }

        String[] keywords = {
                "PROGRAMA", "VISUAL", "NEGOCIO", "ARQUITETURA", "BIBLIOTECA", "EXTENSAO", "FERRAMENTA", "TESTE", "TELA",
                "PIPELINE_DADOS", "FONTE_ENTRADA", "DESTINO_SAIDA", "TRANSFORMACAO",
                "FIM_PROGRAMA", "FIM_BIBLIOTECA", "FIM_EXTENSAO", "FIM_FERRAMENTA", "FIM_TESTE", "FIM_TELA",
                "FIM_PIPELINE", "FIM_FONTE", "FIM_DESTINO", "FIM_TRANSFORMACAO", "FIM_VETORIZAR",
                "METADADOS_ARQUITETURA", "FIM_METADADOS",
                "ESTRUTURA", "FIM_ESTRUTURA", "ENUMERACAO", "FIM_ENUMERACAO",
                "REGRA_NEGOCIO", "FIM_REGRA_NEGOCIO", "PROCEDIMENTO", "FUNCAO", "FIM_FUNCAO", "FIM_OPERACAO", "INICIO", "FIM",
                "EXIGE", "GARANTE", "INVARIANTE", "FALHAR_COM",
                "CONTRATO_ENTRADA", "FIM_CONTRATO_ENTRADA", "CONTRATO_SAIDA", "FIM_CONTRATO_SAIDA",
                "VARIAVEL", "RETORNE", "RETORNAR", "EXIBA", "OPERACAO",
                "SE", "ENTAO", "SENAO", "ENQUANTO", "FACA", "FIM_SE", "FIM_ENQUANTO",
                "VERDADEIRO", "FALSO", "NULO", "E", "OU", "NAO",
                "VETORIZAR_PARA", "EM", "PASSO_SIMD", "PARA", "PASSO", "DE", "ATE",
                "CRIAR", "LER", "FIM_PARA",
                "USAR_BLOCO_MEMORIA", "FIM_BLOCO_MEMORIA", "LAYOUT_COLUNAR",
                "STREAMING", "LOTE", "CONECTOR", "FORMATO",
                "IMPORTAR", "CASO_RESULTADO", "ESCOLHA", "CASO", "FIM_CASO", "FIM_ESCOLHA", "SUCESSO", "ERRO", "FALHA",
                "TENTE", "CAPTURE", "FIM_TENTE",
                "VERSAO_LINGUAGEM", "IDEMPOTENTE", "CHAVE_IDEMPOTENCIA",
                "SISTEMA", "MODULO", "DOMINIO", "SUBDOMINIO", "CAMADA", "VERSAO", "AUTOR", "RESPONSAVEL",
                "SLO_LATENCIA_MS", "SLO_LATENCIA_MAXIMA", "CONFORMIDADE", "CRITICIDADE",
                "IDENTIFICADOR_REGRA", "RASTREIO_REQUISITO", "DESCRICAO"
        };

        for (String kw : keywords) {
            CompletionItem item = new CompletionItem();
            item.setLabel(kw);
            item.setKind(CompletionItemKind.Keyword);
            item.setDetail("THZ-LANG keyword");
            items.add(item);
        }

        String[] tipos = {
                "INTEIRO", "INTEIRO32", "INTEIRO64", "NATURAL8", "NATURAL16", "NATURAL32", "NATURAL64",
                "DECIMAL(12, 2)", "DECIMAL(12, 4)", "MONETARIO(BRL)", "MONETARIO(USD)", "MONETARIO(EUR)",
                "TEXTO", "LOGICO", "UUID", "DATA", "DATA_HORA", "FATIA[Item]", "RESULTADO[T, E]", "LISTA"
        };
        for (String tipo : tipos) {
            CompletionItem item = new CompletionItem();
            item.setLabel(tipo);
            item.setKind(CompletionItemKind.TypeParameter);
            item.setDetail("tipo THZ");
            items.add(item);
        }

        return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        return CompletableFuture.completedFuture(unresolved);
    }

    // ---- Document Symbols ----

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        String fonte = server.obterDocumento(uri);
        if (fonte == null) return CompletableFuture.completedFuture(List.of());

        ThzLanguageServerImpl.ResultadoAnalise r = server.analisar(fonte);
        List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();

        for (ThzLanguageServerImpl.SimboloLsp s : r.simbolos()) {
            SymbolKind kind = switch (s.categoria()) {
                case "programa" -> SymbolKind.Package;
                case "estrutura" -> SymbolKind.Struct;
                case "campo" -> SymbolKind.Field;
                case "enumeracao" -> SymbolKind.Enum;
                case "membro-enum" -> SymbolKind.EnumMember;
                case "regra" -> SymbolKind.Module;
                case "operacao" -> SymbolKind.Method;
                case "parametro" -> SymbolKind.Variable;
                case "variavel" -> SymbolKind.Variable;
                default -> SymbolKind.Variable;
            };

            DocumentSymbol symbol = new DocumentSymbol();
            symbol.setName(s.nome());
            symbol.setDetail(s.detalhe() != null ? s.detalhe() : s.container() != null ? s.container() : "");
            symbol.setKind(kind);
            symbol.setRange(new Range(
                    new Position(s.linha() - 1, s.coluna() - 1),
                    new Position(s.linha() - 1, s.coluna() - 1 + s.nome().length())
            ));
            symbol.setSelectionRange(symbol.getRange());
            result.add(Either.forRight(symbol));
        }

        return CompletableFuture.completedFuture(result);
    }

    // ---- Definition ----

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        String uri = params.getTextDocument().getUri();
        String fonte = server.obterDocumento(uri);
        if (fonte == null) return CompletableFuture.completedFuture(Either.forLeft(List.of()));

        int linha = params.getPosition().getLine() + 1;
        int coluna = params.getPosition().getCharacter() + 1;

        String[] linhas = fonte.split("\\r?\\n", -1);
        if (linha - 1 >= linhas.length) return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        String conteudo = linhas[linha - 1];
        int idx = Math.max(0, coluna - 1);
        while (idx > 0 && isIdentChar(conteudo.charAt(idx - 1))) idx--;
        int fim = idx;
        while (fim < conteudo.length() && isIdentChar(conteudo.charAt(fim))) fim++;
        String palavra = conteudo.substring(idx, fim);
        if (palavra.isBlank()) return CompletableFuture.completedFuture(Either.forLeft(List.of()));

        ThzLanguageServerImpl.ResultadoAnalise r = server.analisar(fonte);
        for (ThzLanguageServerImpl.SimboloLsp s : r.simbolos()) {
            if (s.nome().equals(palavra)) {
                Location loc = new Location(uri, new Range(
                        new Position(s.linha() - 1, s.coluna() - 1),
                        new Position(s.linha() - 1, s.coluna() - 1 + s.nome().length())
                ));
                return CompletableFuture.completedFuture(Either.forLeft(List.of(loc)));
            }
        }
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
    }

    // ---- Formatting ----

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        String uri = params.getTextDocument().getUri();
        String fonte = server.obterDocumento(uri);
        if (fonte == null) return CompletableFuture.completedFuture(List.of());
        if (!SintaxeEnxuta.validarIndentacao(fonte).isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String formatado = server.formatar(fonte);
        if (formatado == null || formatado.equals(fonte)) return CompletableFuture.completedFuture(List.of());

        int lastLine = fonte.split("\\r?\\n", -1).length - 1;
        int lastCol = 0;
        String[] linhas = fonte.split("\\r?\\n", -1);
        if (linhas.length > 0) {
            lastCol = linhas[linhas.length - 1].length();
        }

        TextEdit edit = new TextEdit();
        edit.setRange(new Range(new Position(0, 0), new Position(lastLine, lastCol)));
        edit.setNewText(formatado);

        return CompletableFuture.completedFuture(List.of(edit));
    }

    // ---- References ----

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        String uri = params.getTextDocument().getUri();
        String fonte = server.obterDocumento(uri);
        if (fonte == null) return CompletableFuture.completedFuture(List.of());

        String palavra = obterPalavraNaPosicao(fonte, params.getPosition().getLine() + 1, params.getPosition().getCharacter() + 1);
        if (palavra.isBlank()) return CompletableFuture.completedFuture(List.of());

        List<Location> locs = encontrarOcorrencias(uri, fonte, palavra);
        return CompletableFuture.completedFuture(locs);
    }

    // ---- Rename ----

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        String uri = params.getTextDocument().getUri();
        String fonte = server.obterDocumento(uri);
        if (fonte == null) return CompletableFuture.completedFuture(null);

        String palavraAntiga = obterPalavraNaPosicao(fonte, params.getPosition().getLine() + 1, params.getPosition().getCharacter() + 1);
        if (palavraAntiga.isBlank()) return CompletableFuture.completedFuture(null);

        List<Location> ocorrencias = encontrarOcorrencias(uri, fonte, palavraAntiga);
        List<TextEdit> edits = new ArrayList<>();
        for (Location loc : ocorrencias) {
            TextEdit edit = new TextEdit();
            edit.setRange(loc.getRange());
            edit.setNewText(params.getNewName());
            edits.add(edit);
        }

        WorkspaceEdit we = new WorkspaceEdit();
        we.setChanges(Map.of(uri, edits));
        return CompletableFuture.completedFuture(we);
    }

    // ---- Helpers ----

    private String obterPalavraNaPosicao(String fonte, int linha, int coluna) {
        String[] linhas = fonte.split("\\r?\\n", -1);
        if (linha - 1 >= linhas.length) return "";
        String conteudo = linhas[linha - 1];
        int idx = Math.max(0, coluna - 1);
        while (idx > 0 && isIdentChar(conteudo.charAt(idx - 1))) idx--;
        int fim = idx;
        while (fim < conteudo.length() && isIdentChar(conteudo.charAt(fim))) fim++;
        return conteudo.substring(idx, fim);
    }

    private List<Location> encontrarOcorrencias(String uri, String fonte, String palavra) {
        List<Location> locs = new ArrayList<>();
        String[] linhas = fonte.split("\\r?\\n", -1);
        for (int l = 0; l < linhas.length; l++) {
            String lineContent = linhas[l];
            int index = lineContent.indexOf(palavra);
            while (index >= 0) {
                boolean inicioValido = index == 0 || !isIdentChar(lineContent.charAt(index - 1));
                boolean fimValido = (index + palavra.length() == lineContent.length()) || !isIdentChar(lineContent.charAt(index + palavra.length()));
                if (inicioValido && fimValido) {
                    Location loc = new Location(uri, new Range(
                            new Position(l, index),
                            new Position(l, index + palavra.length())
                    ));
                    locs.add(loc);
                }
                index = lineContent.indexOf(palavra, index + 1);
            }
        }
        return locs;
    }

    private boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
