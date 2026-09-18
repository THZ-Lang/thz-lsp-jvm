package thz.lang.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ThzWorkspaceService implements WorkspaceService {

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // noop
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // noop
    }

    @Override
    public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
        return CompletableFuture.completedFuture(Either.forLeft(List.of()));
    }
}
