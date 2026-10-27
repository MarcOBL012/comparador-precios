package pe.com.comparadorprecios.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import pe.com.comparadorprecios.data.NearbyStore
import pe.com.comparadorprecios.data.StoreRoute
import pe.com.comparadorprecios.location.LatLng

/**
 * Mapa de OpenStreetMap con tu posición, las tiendas cercanas y la ruta a la elegida.
 * Tocar el pin de otra tienda la selecciona (y recalcula la ruta).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun StoreMap(
    origin: LatLng,
    stores: List<NearbyStore>,
    selected: Int,
    route: StoreRoute?,
    routeColor: Color,
    onSelectStore: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            // Dentro de una lista con scroll: mientras el dedo está en el mapa, el mapa manda.
            setOnTouchListener { view, _ ->
                view.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()
            val you = GeoPoint(origin.lat, origin.lng)

            val routePoints = route?.puntos?.mapNotNull { p -> if (p.size >= 2) GeoPoint(p[0], p[1]) else null }.orEmpty()
            if (routePoints.size >= 2) {
                map.overlays.add(
                    Polyline(map).apply {
                        setPoints(routePoints)
                        outlinePaint.color = routeColor.toArgb()
                        outlinePaint.strokeWidth = 14f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        infoWindow = null
                    }
                )
            }

            stores.forEachIndexed { index, store ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(store.lat, store.lng)
                        title = store.nombre
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        alpha = if (index == selected) 1f else 0.55f
                        setOnMarkerClickListener { _, _ ->
                            onSelectStore(index)
                            true
                        }
                    }
                )
            }

            map.overlays.add(
                Marker(map).apply {
                    position = you
                    title = "Tú"
                    icon = youAreHereDot()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> true }
                }
            )

            val focus = buildList {
                add(you)
                add(GeoPoint(stores[selected].lat, stores[selected].lng))
                addAll(routePoints)
            }
            map.post {
                if (map.width > 0 && map.height > 0) {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(focus).increaseByScale(1.35f), false, 48)
                    if (map.zoomLevelDouble > 18.0) map.controller.setZoom(18.0)
                }
            }
            map.invalidate()
        },
    )
}

/** Punto azul "estás aquí", como en las apps de mapas que la gente ya conoce. */
private fun youAreHereDot() = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(AndroidColor.parseColor("#1A73E8"))
    setStroke(6, AndroidColor.WHITE)
    setSize(44, 44)
}
