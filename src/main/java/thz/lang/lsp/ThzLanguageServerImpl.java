package thz.lang.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.*;

import thz.lang.fachada.ThzCompilerFacade;
import thz.lang.governanca.AuditorGovernanca;
import thz.lang.governanca.RelatorioAuditoria;
import thz.lang.ir.GeradorIr;
import thz.lang.ir.IrPrograma;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ThzLanguageServerImpl implements LanguageServer, LanguageClientAware {

    private final ThzTextDocumentService textDocumentService = new ThzTextDocumentService(this);
    private final ThzWorkspaceService workspaceService = new ThzWorkspaceService();

    private LanguageClient client;
    private boolean lintEstrito = false;

    // Cache de documentos abertos: uri -> conteúdo
    private final ConcurrentHashMap<String, String> documentos = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(TextDocumentSyncKind.Incremental));
        capabilities.setHoverProvider(true);

        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(List.of(".", " ", ":"));
        capabilities.setCompletionProvider(completionOptions);

        capabilities.setDocumentSymbolProvider(true);
        capabilities.setDefinitionProvider(true);
        capabilities.setDocumentFormattingProvider(true);

        InitializeResult result = new InitializeResult(capabilities);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    public LanguageClient getClient() {
        return client;
    }

    public boolean isLintEstrito() {
        return lintEstrito;
    }

    public void setLintEstrito(boolean lintEstrito) {
        this.lintEstrito = lintEstrito;
    }

    // ---- Endpoints Customizados (THZ Extensões) ----

    @JsonRequest("thz/audit")
    public CompletableFuture<Map<String, Object>> audit(Map<String, Object> params) {
        String uri = (String) params.get("uri");
        String fonte = obterDocumento(uri);
        if (fonte == null) return CompletableFuture.completedFuture(Map.of("error", "Documento não encontrado: " + uri));
        try {
            ThzCompilerFacade.ResultadoAnalise r = ThzCompilerFacade.analisar(fonte, lintEstrito);
            if (r.ast() == null) return CompletableFuture.completedFuture(Map.of("error", "Não foi possível auditar programa com erros sintáticos."));
            RelatorioAuditoria relatorio = AuditorGovernanca.auditar(r.ast());
            String markdown = AuditorGovernanca.gerarMarkdownGovernanca(relatorio);
            return CompletableFuture.completedFuture(Map.of("markdown", markdown));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Map.of("error", e.getMessage() != null ? e.getMessage() : "Erro desconhecido"));
        }
    }

    @JsonRequest("thz/ir")
    public CompletableFuture<Map<String, Object>> ir(Map<String, Object> params) {
        String uri = (String) params.get("uri");
        String fonte = obterDocumento(uri);
        if (fonte == null) return CompletableFuture.completedFuture(Map.of("error", "Documento não encontrado: " + uri));
        try {
            ThzCompilerFacade.ResultadoAnalise r = ThzCompilerFacade.analisar(fonte, lintEstrito);
            if (r.ast() == null) return CompletableFuture.completedFuture(Map.of("error", "Não foi possível gerar IR de programa com erros sintáticos."));
            IrPrograma ir = GeradorIr.baixarParaIr(r.ast());
            String json = GeradorIr.serializarIrJson(ir);
            return CompletableFuture.completedFuture(Map.of("text", json));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Map.of("error", e.getMessage() != null ? e.getMessage() : "Erro desconhecido"));
        }
    }

    @JsonRequest("thz/llvm")
    public CompletableFuture<Map<String, Object>> llvm(Map<String, Object> params) {
        String uri = (String) params.get("uri");
        String fonte = obterDocumento(uri);
        if (fonte == null) return CompletableFuture.completedFuture(Map.of("error", "Documento não encontrado: " + uri));
        try {
            ThzCompilerFacade.ResultadoAnalise r = ThzCompilerFacade.analisar(fonte, lintEstrito);
            if (r.ast() == null) return CompletableFuture.completedFuture(Map.of("error", "Não foi possível gerar LLVM IR de programa com erros sintáticos."));
            String llvm = GeradorIr.emitirLlvm(r.ast());
            return CompletableFuture.completedFuture(Map.of("text", llvm));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Map.of("error", e.getMessage() != null ? e.getMessage() : "Erro desconhecido"));
        }
    }

    // ---- Helpers compartilhados ----

    public void atualizarDocumento(String uri, String texto) {
        documentos.put(uri, texto);
    }

    public void removerDocumento(String uri) {
        documentos.remove(uri);
    }

    public String obterDocumento(String uri) {
        return documentos.get(uri);
    }

    /**
     * Pipeline de análise completo via ThzCompilerFacade.
     */
    public ResultadoAnalise analisar(String fonte) {
        ThzCompilerFacade.ResultadoAnalise r = ThzCompilerFacade.analisar(fonte, lintEstrito);

        List<DiagnosticoLsp> diagnosticos = r.diagnosticos().stream()
                .map(d -> new DiagnosticoLsp(d.linha(), d.coluna(), d.mensagem(), d.origem()))
                .toList();

        List<SimboloLsp> simbolos = r.simbolos().stream()
                .map(s -> new SimboloLsp(s.nome(), s.categoria(), s.detalhe(), s.linha(), s.coluna(), s.container()))
                .toList();

        return new ResultadoAnalise(r.ast(), diagnosticos, simbolos);
    }

    /**
     * Converte diagnósticos internos para o formato LSP.
     */
    public List<Diagnostic> paraLspDiagnostics(List<DiagnosticoLsp> diagnosticos, String fonte) {
        List<Diagnostic> result = new ArrayList<>();
        String[] linhas = fonte.split("\\r?\\n", -1);

        for (DiagnosticoLsp d : diagnosticos) {
            int line = Math.max(0, d.linha - 1);
            int col = Math.max(0, d.coluna - 1);
            String conteudo = (line < linhas.length) ? linhas[line] : "";
            int fim = col;
            while (fim < conteudo.length() && isIdentChar(conteudo.charAt(fim))) fim++;
            if (fim == col) fim = col + 1;

            Diagnostic diag = new Diagnostic();
            diag.setSeverity(DiagnosticSeverity.Error);
            diag.setRange(new Range(
                    new Position(line, col),
                    new Position(line, fim)
            ));
            diag.setMessage(d.mensagem);
            diag.setSource("thz-" + d.origem);
            result.add(diag);
        }
        return result;
    }

    /**
     * Hover: resolve símbolo sob cursor via ThzCompilerFacade.
     */
    public HoverResult obterHover(String fonte, int linha, int coluna) {
        ThzCompilerFacade.HoverInfo h = ThzCompilerFacade.obterHover(fonte, linha, coluna);
        if (h == null) return null;
        Range range = new Range(
                new Position(h.linha() - 1, h.colunaInicio() - 1),
                new Position(h.linha() - 1, h.colunaFim() - 1)
        );
        return new HoverResult(h.conteudo(), range);
    }

    /**
     * Formatação canônica via ThzCompilerFacade.
     */
    public String formatar(String fonte) {
        ThzCompilerFacade.ResultadoFormatacao r = ThzCompilerFacade.formatar(fonte);
        return r.alterou() ? r.resultado() : null;
    }

    // ---- Helpers ----

    private boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // ---- Records internos ----

    public record DiagnosticoLsp(int linha, int coluna, String mensagem, String origem) {}
    public record SimboloLsp(String nome, String categoria, String detalhe, int linha, int coluna, String container) {}
    public record ResultadoAnalise(thz.lang.ast.ProgramaAst ast, List<DiagnosticoLsp> diagnosticos, List<SimboloLsp> simbolos) {}
    public record HoverResult(String conteudo, Range range) {}
}
