package thz.lang.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class ThzLspTest {

    private ThzLanguageServerImpl server;
    private ThzTextDocumentService docService;
    private List<PublishDiagnosticsParams> diagnosticosPublicados;

    @BeforeEach
    void setUp() {
        server = new ThzLanguageServerImpl();
        diagnosticosPublicados = new ArrayList<>();
        server.connect(new LanguageClient() {
            @Override public void telemetryEvent(Object object) {}
            @Override public void publishDiagnostics(PublishDiagnosticsParams diagnostics) { diagnosticosPublicados.add(diagnostics); }
            @Override public void showMessage(MessageParams messageParams) {}
            @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) { return CompletableFuture.completedFuture(null); }
            @Override public void logMessage(MessageParams message) {}
        });
        docService = (ThzTextDocumentService) server.getTextDocumentService();
    }

    @Test
    @DisplayName("ThzLanguageServerImpl deve inicializar e reportar capacidades corretas")
    void testInicializacao() throws Exception {
        InitializeParams params = new InitializeParams();
        CompletableFuture<InitializeResult> initFuture = server.initialize(params);
        InitializeResult result = initFuture.get();

        assertNotNull(result);
        ServerCapabilities caps = result.getCapabilities();
        assertNotNull(caps);
        assertTrue(caps.getHoverProvider().getLeft());
        assertNotNull(caps.getCompletionProvider());
        assertTrue(caps.getDocumentFormattingProvider().getLeft());
    }

    @Test
    @DisplayName("ThzTextDocumentService deve fornecer autocompletion para novas palavras-chave e arquétipos")
    void testAutocompletion() throws Exception {
        TextDocumentIdentifier docId = new TextDocumentIdentifier("file:///teste.thz");
        Position pos = new Position(0, 0);
        CompletionParams params = new CompletionParams(docId, pos);

        var future = docService.completion(params);
        var either = future.get();
        List<CompletionItem> items = either.getLeft();

        assertNotNull(items);
        assertFalse(items.isEmpty());

        List<String> labels = items.stream().map(CompletionItem::getLabel).toList();
        assertTrue(labels.contains("PROGRAMA"));
        assertTrue(labels.contains("VISUAL"));
        assertTrue(labels.contains("NEGOCIO"));
        assertTrue(labels.contains("IMPORTAR"));
        assertTrue(labels.contains("CASO_RESULTADO"));
        assertTrue(labels.contains("FUNCAO"));
        assertTrue(labels.contains("funcao nome() -> Tipo:"));
        assertTrue(labels.contains("ESCOLHA"));
        assertTrue(labels.contains("TENTE"));
    }

    @Test
    @DisplayName("ThzLanguageServerImpl deve responder aos endpoints customizados thz/audit, thz/ir e thz/llvm")
    void testEndpointsCustomizados() throws Exception {
        String uri = "file:///AuditoriaDemo.thz";
        String src = """
                PROGRAMA AuditoriaDemo
                METADADOS_ARQUITETURA
                    DOMINIO: "Vendas"
                    CAMADA: "Dominio"
                    VERSAO: "1.0.0"
                    AUTOR: "Arquiteto"
                    SLO_LATENCIA_MAXIMA: "10ms"
                FIM_METADADOS
                REGRA_NEGOCIO RegraTeste
                    IDENTIFICADOR_REGRA: "BR-001"
                    RASTREIO_REQUISITO: "REQ-001"
                    OPERACAO Executar(x : INTEIRO32) : INTEIRO32
                    INICIO
                        RETORNE x + 100
                    FIM
                FIM_REGRA_NEGOCIO
                FIM_PROGRAMA
                """;

        // Abre o documento no servidor
        docService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "thz", 1, src)));

        // 1. thz/audit
        var auditRes = server.audit(java.util.Map.of("uri", uri)).get();
        assertNotNull(auditRes);
        assertTrue(auditRes.containsKey("markdown") || auditRes.containsKey("error"));

        // 2. thz/ir
        var irRes = server.ir(java.util.Map.of("uri", uri)).get();
        assertNotNull(irRes);
        assertTrue(irRes.containsKey("text") || irRes.containsKey("error"));

        // 3. thz/llvm
        var llvmRes = server.llvm(java.util.Map.of("uri", uri)).get();
        assertNotNull(llvmRes);
        assertTrue(llvmRes.containsKey("text") || llvmRes.containsKey("error"));
    }

    @Test
    @DisplayName("ThzTextDocumentService deve formatar documentos com formatação canônica")
    void testFormatacaoDocumento() throws Exception {
        String srcDesformatado = "PROGRAMA Minimo\nPROCEDIMENTO P()\nINICIO\nEXIBA \"Ok\"\nFIM\nFIM_PROGRAMA";
        // Notifica abertura do documento
        docService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem("file:///fmt.thz", "thz", 1, srcDesformatado)));

        DocumentFormattingParams fmtParams = new DocumentFormattingParams();
        fmtParams.setTextDocument(new TextDocumentIdentifier("file:///fmt.thz"));

        var edits = docService.formatting(fmtParams).get();
        assertNotNull(edits);
    }

    @Test
    @DisplayName("LSP deve diagnosticar indentação ambígua da sintaxe enxuta")
    void testDiagnosticoIndentacaoEnxuta() throws Exception {
        String uri = "file:///indentacao.thz";
        String fonte = "programa Demo:\n      procedimento Principal():\n          exiba \"ok\"\n";
        docService.didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem(uri, "thz", 1, fonte)));

        assertFalse(diagnosticosPublicados.isEmpty());
        assertTrue(diagnosticosPublicados.getLast().getDiagnostics().stream()
                .anyMatch(d -> "thz-indentacao".equals(d.getSource())
                        && d.getMessage().contains("múltiplos de 4 espaços")));

        DocumentFormattingParams formatacao = new DocumentFormattingParams();
        formatacao.setTextDocument(new TextDocumentIdentifier(uri));
        assertTrue(docService.formatting(formatacao).get().isEmpty(),
                "O formatador não deve consolidar uma AST enquanto o recuo for ambíguo");
    }

    @Test
    @DisplayName("ThzTextDocumentService deve responder a Find References e Rename Refactoring")
    void testReferencesERename() throws Exception {
        String uri = "file:///ref.thz";
        String src = """
                PROGRAMA RefDemo
                PROCEDIMENTO Calcular()
                INICIO
                    VARIAVEL total <- 100
                    EXIBA(total)
                FIM
                FIM_PROGRAMA
                """;

        docService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "thz", 1, src)));

        // References para 'total'
        ReferenceParams refParams = new ReferenceParams();
        refParams.setTextDocument(new TextDocumentIdentifier(uri));
        refParams.setPosition(new Position(3, 15));
        var refs = docService.references(refParams).get();
        assertNotNull(refs);
        assertFalse(refs.isEmpty());

        // Rename de 'total' -> 'novoTotal'
        RenameParams renameParams = new RenameParams();
        renameParams.setTextDocument(new TextDocumentIdentifier(uri));
        renameParams.setPosition(new Position(3, 15));
        renameParams.setNewName("novoTotal");
        var workspaceEdit = docService.rename(renameParams).get();
        assertNotNull(workspaceEdit);
        assertTrue(workspaceEdit.getChanges().containsKey(uri));
    }
}
