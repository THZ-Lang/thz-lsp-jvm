# ADR-001: Suporte a Documentos COBOL e Isolamento de Diagnósticos no LSP Server

- **Status:** Aceito
- **Data:** 2026-10-11
- **Decisores:** Time de Arquitetura THZ-LANG & Lucas Thomaz
- **Contexto:** Ecossistema THZ-LANG (v0.5.0), módulo `thz-lsp-jvm` e extensão `thz-vscode`

---

## 1. Contexto e Problema

A extensão `thz-vscode` registra o identificador de linguagem `thz-cobol` e o associa ao Language Server para prover inteligência de código em arquivos legados (`.cbl`, `.cob`, `.cpy`).

Contudo, o serviço de documentos do LSP (`ThzTextDocumentService.java`) submetia indistintamente todo documento aberto à pipeline do compilador THZ (`server.analisar(fonte)`) e ao validador de indentação canônica de 4 espaços (`SintaxeEnxuta.validarIndentacao(fonte)`).

Como consequência:
1. Em arquivos COBOL puros como `CALC_JUROS.cbl`, a palavra-chave `PROGRAM-ID` era lida como a palavra reservada `PROGRAM` do dialeto internacional `[en-US]`, disparando erro léxico falso: `[Erro Léxico] Palavra-chave 'PROGRAM' pertence ao dialeto [en-US] e não pode ser usada em arquivo configurado para [pt-BR]`.
2. As regras de indentação rígidas da sintaxe enxuta THZ geravam diagnósticos de erro em colunas COBOL.
3. Operações de hover, formatação e navegação falhavam ou tentavam manipular a sintaxe COBOL como se fosse código THZ.

---

## 2. Decisão Arquitetural

1. **Integração do Módulo `thz-lang-cobol` no LSP:**
   - Adicionada inclusão de build e dependência direta do módulo de interoperabilidade COBOL no `thz-lsp-jvm`.

2. **Isolamento de Pipeline por Tipo de Documento:**
   - Introduzido predicado `isArquivoCobol(uri)` para identificar extensões `.cbl`, `.cob` e `.cpy`.
   - Documentos COBOL são desviados para `validarCobol(uri, fonte)`, que utiliza `CobolProcedureParser` e `CopybookParser` dedicados, emitindo diagnósticos apenas para erros reais de sintaxe COBOL.
   - Desabilitada a validação de indentação (`thz-indentacao`) e dialeto THZ para esses arquivos.

3. **Inteligência de Código para COBOL:**
   - Implementado suporte em `documentSymbol` para arquivos COBOL, extraindo o programa (`SymbolKind.Package`) e seus parágrafos (`SymbolKind.Method`) para visualização de outline e navegação no editor.
   - Preservada a integridade de formatação (evitando substituição indevida do fonte original por formatador THZ).

---

## 3. Consequências

- **Fim dos Falsos Positivos:** Arquivos `.cbl` válidos abrem no editor sem nenhum sublinhado vermelho indevido ou erro léxico de dialeto.
- **Suporte de Primeira Classe:** Navegação estrutural e verificação de sanidade ativas para COBOL dentro do ecossistema.
- **Zero Regressão:** Testes do LSP cobrindo sintaxe enxuta, formatação e autocompletion continuam com 100% de aprovação.
