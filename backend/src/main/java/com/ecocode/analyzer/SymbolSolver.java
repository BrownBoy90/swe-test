package com.ecocode.analyzer;

/** JavaProjectParser resolves calls using JavaParser's type solver. Unknown calls stay unknown. */
public class SymbolSolver {
    public void resolveSymbols(ProjectModel model) {
        // Deliberately do not guess from a global name/arity index.
    }
}
