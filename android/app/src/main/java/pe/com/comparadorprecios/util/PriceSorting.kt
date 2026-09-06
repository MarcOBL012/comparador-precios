package pe.com.comparadorprecios.util

import pe.com.comparadorprecios.data.StoreResult

/**
 * Orden para mostrar (spec: lista ordenada por precio ascendente;
 * tiendas sin resultado como "no disponible", no se omiten).
 *
 * - `encontrado` primero, por `precio` ascendente.
 * - Luego `no_encontrado`, luego `error`, preservando su orden relativo.
 * - Nunca asume cuántas tiendas hay (Plan 2b puede agregar más).
 */
object PriceSorting {
    fun sortForDisplay(tiendas: List<StoreResult>): List<StoreResult> {
        val found = tiendas.filter { it.estado == "encontrado" }.sortedBy { it.precio ?: Double.MAX_VALUE }
        val notFound = tiendas.filter { it.estado == "no_encontrado" }
        val errors = tiendas.filter { it.estado != "encontrado" && it.estado != "no_encontrado" }
        return found + notFound + errors
    }
}
