package io.github.mojri.hesabyar.rust

// Search domain of the RustBridge façade. See RustBridgeCore for the split.

/** Structured transaction search over the Rust core. */
internal interface RustBridgeSearch : RustBridgeCore {
  /** Filters transactions by a structured query. Empty response when Rust is unavailable. */
  fun searchTransactionsSync(
    transactions: List<Transaction>,
    query: SearchQuery
  ): SearchResponse =
    rustCallSync(
      SearchResponse(results = emptyList(), totalCount = 0L, totalAmount = 0L)
    ) { HesabyarCore.searchTransactions(transactions, query) }
}
