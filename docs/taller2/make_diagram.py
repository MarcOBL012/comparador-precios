import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib.font_manager import FontProperties

# Paleta neutra: azul (entrada), morado (procesamiento), naranja (decision), verde (accion), gris (adaptacion)
COLORS = {
    "entrada": "#2E5FA3",
    "proceso": "#6A4C93",
    "decision": "#C46210",
    "accion": "#1E7B4D",
    "adapt": "#4A4A4A",
}

fig, ax = plt.subplots(figsize=(10, 7.2))
ax.set_xlim(0, 10)
ax.set_ylim(0, 10)
ax.axis("off")

bold = FontProperties(weight="bold", size=11)
small = FontProperties(size=9)
stage_font = FontProperties(weight="bold", size=9.5)


def box(x, y, w, h, text, color, subtitle=None):
    patch = FancyBboxPatch(
        (x, y), w, h,
        boxstyle="round,pad=0.02,rounding_size=0.12",
        linewidth=1.4, edgecolor=color, facecolor=color, alpha=0.12,
    )
    ax.add_patch(patch)
    edge = FancyBboxPatch(
        (x, y), w, h,
        boxstyle="round,pad=0.02,rounding_size=0.12",
        linewidth=1.6, edgecolor=color, facecolor="none",
    )
    ax.add_patch(edge)
    cy = y + h / 2 + (0.14 if subtitle else 0)
    ax.text(x + w / 2, cy, text, ha="center", va="center", fontproperties=bold, color=color, wrap=True)
    if subtitle:
        ax.text(x + w / 2, y + h / 2 - 0.24, subtitle, ha="center", va="center", fontproperties=small, color="#333333")


def arrow(x1, y1, x2, y2, color="#555555"):
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle="-|>", mutation_scale=16,
                                  linewidth=1.5, color=color, shrinkA=2, shrinkB=2))


def stage_label(x, y, text, color):
    ax.text(x, y, text, ha="left", va="center", fontproperties=stage_font, color=color)


# --- Fila 1: ENTRADA FISICA ---
stage_label(0.15, 9.35, "ENTRADA FÍSICA", COLORS["entrada"])
box(0.5, 8.2, 3.6, 0.95, "Acelerómetro", COLORS["entrada"], "TYPE_ACCELEROMETER · magnitud de aceleración")
box(5.9, 8.2, 3.6, 0.95, "Sensor de luz", COLORS["entrada"], "TYPE_LIGHT · nivel de iluminación (lux)")

# --- Fila 2: PROCESAMIENTO ---
stage_label(0.15, 7.15, "PROCESAMIENTO", COLORS["proceso"])
box(1.7, 6.0, 6.6, 0.95, "CaptureConditionsMonitor",
    COLORS["proceso"], "Varianza de aceleración (ventana móvil) → Firmeza  ·  Lux vs. umbral → Iluminación")

arrow(2.3, 8.2, 4.2, 6.95)
arrow(7.7, 8.2, 5.8, 6.95)

# --- Fila 3: DECISION ---
stage_label(0.15, 5.35, "DECISIÓN", COLORS["decision"])
box(1.7, 4.2, 6.6, 0.95, "CaptureAdvisor.decide(firmeza, iluminación)",
    COLORS["decision"], "Combina AMBAS entradas: temblando → espera; firme+oscuro → flash; firme+claro → listo")
arrow(5.0, 6.0, 5.0, 5.15)

# --- Fila 4: ACCION FISICA ---
stage_label(0.15, 3.55, "ACCIÓN FÍSICA", COLORS["accion"])
box(0.5, 2.4, 3.6, 0.95, "Flash / torch", COLORS["accion"], "camera.cameraControl.enableTorch(...)")
box(5.9, 2.4, 3.6, 0.95, "Vibración", COLORS["accion"], "Haptics.vibrateReady() · pulso de 40 ms")
arrow(3.8, 4.2, 2.3, 3.35)
arrow(6.2, 4.2, 7.7, 3.35)

# --- Fila 5: ADAPTACION ---
stage_label(0.15, 1.75, "ADAPTACIÓN (UI en vivo)", COLORS["adapt"])
box(1.7, 0.6, 6.6, 0.95, "Banner en ScanScreen",
    COLORS["adapt"], "\"Sostén firme…\" / \"Poca luz: flash activado…\" — cambia solo, sin recargar pantalla")
arrow(2.3, 2.4, 4.2, 1.55)
arrow(7.7, 2.4, 5.8, 1.55)

plt.tight_layout()
plt.savefig("diagrama_flujo.png", dpi=200, bbox_inches="tight", facecolor="white")
print("ok")
