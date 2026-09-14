package pe.com.comparadorprecios.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Blender
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Toys
import androidx.compose.ui.graphics.vector.ImageVector

/** Mismos ids que CATEGORIAS en lib/productIdentification.ts. `id = null` = la IA decide. */
data class ProductCategory(val id: String?, val label: String, val icon: ImageVector)

val AUTO_CATEGORY = ProductCategory(null, "Automático", Icons.Filled.AutoAwesome)

val PRODUCT_CATEGORIES = listOf(
    AUTO_CATEGORY,
    ProductCategory("abarrotes", "Abarrotes", Icons.Filled.ShoppingBasket),
    ProductCategory("bebidas", "Bebidas", Icons.Filled.LocalDrink),
    ProductCategory("limpieza", "Limpieza", Icons.Filled.CleaningServices),
    ProductCategory("cuidado_personal", "Cuidado personal", Icons.Filled.Face),
    ProductCategory("tecnologia", "Tecnología", Icons.Filled.Headphones),
    ProductCategory("electrohogar", "Electrohogar", Icons.Filled.Blender),
    ProductCategory("hogar", "Hogar", Icons.Filled.Chair),
    ProductCategory("ferreteria", "Ferretería", Icons.Filled.Build),
    ProductCategory("moda", "Moda", Icons.Filled.Checkroom),
    ProductCategory("juguetes", "Juguetes", Icons.Filled.Toys),
    ProductCategory("otros", "Otros", Icons.Filled.Category),
)

fun categoryLabel(categoria: String): String =
    PRODUCT_CATEGORIES.firstOrNull { it.id == categoria }?.label ?: categoria
