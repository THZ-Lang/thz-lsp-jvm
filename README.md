# thz-lsp-jvm — Servidor LSP Java 25 (LSP4J)

Servidor de Protocolo de Linguagem oficial (**Language Server Protocol - LSP**) para a linguagem THZ-LANG, desenvolvido em Java 25 com a biblioteca Eclipse LSP4J. Fornece inteligência de código e diagnósticos estáticos em tempo real para o VS Code, Antigravity IDE e qualquer cliente compatível com LSP.

---

## 🌟 Funcionalidades Suportadas

| Recurso LSP | Status | Descrição |
|---|---|---|
| `textDocument/didOpen` | ✅ Ativo | Validação sintática e semântica imediata na abertura |
| `textDocument/didChange` | ✅ Ativo | Análise incremental e diagnósticos em tempo real com precisão `[Linha L:C]` |
| `textDocument/didSave` | ✅ Ativo | Revalidação completa e formatação canônica opcional |
| `textDocument/hover` | ✅ Ativo | Inspeção de tipos, contratos `EXIGE`/`GARANTE`, docstrings e stdlib |
| `textDocument/completion`| ✅ Ativo | Autocomplete contextual para sintaxe canônica e dual moderna (`var`, `fn`, etc.) |
| `textDocument/documentSymbol` | ✅ Ativo | Árvore de símbolos e outline estruturado do arquivo |
| `textDocument/definition` | ✅ Ativo | Navegação e *Go-to-Definition* para estruturas e funções |
| `textDocument/formatting` | ✅ Ativo | Formatação canônica idempotente |
| `thz/audit` | ✅ Custom | Comando customizado para exibir auditoria de governança viva |
| `thz/ir` | ✅ Custom | Visualização do código intermediário THZ-IR e LLVM IR |

---

## 🚀 Compilação

A partir da raiz do repositório:
```bash
# Compila o UberJAR do servidor LSP
./gradlew :thz-lsp-jvm:shadowJar
```

Gera o arquivo `target/thz-lsp.jar` e `JVM/thz-lsp-jvm/build/libs/thz-lsp-0.4.0.jar`.

---

## 🛠️ Execução

### Modo stdio (utilizado por IDEs como VS Code / Antigravity):
```bash
java -jar target/thz-lsp.jar --stdio
```

---

## 🔌 Integração com VS Code

A extensão oficial [`Extensions/thz-lsp-vscode`](../../Extensions/thz-lsp-vscode) consome este servidor Java. O empacotamento automatizado via `scripts/build-vsix.ps1` ou `scripts/build-vsix.sh` inclui este servidor compilado no artefato final `.vsix`.

---

## 📦 Dependência do Core

```kotlin
implementation("thz.lang:thz-core:0.4.0")
```
Resolvido via Gradle Composite Build a partir de `../thz-core-jvm`.
