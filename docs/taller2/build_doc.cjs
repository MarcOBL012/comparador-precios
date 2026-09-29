const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, ImageRun,
  Table, TableRow, TableCell, WidthType, ShadingType, BorderStyle,
  AlignmentType, ExternalHyperlink, PageOrientation,
} = require("docx");

const PAGE_WIDTH = 12240; // US Letter DXA
const PAGE_HEIGHT = 15840;
const MARGIN = 720; // 0.5"
const CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;

const COLOR = {
  accent: "1E4E8C",
  text: "1A1A1A",
  muted: "555555",
  ruleLight: "D9D9D9",
};

function h(text, size = 22) {
  return new Paragraph({
    keepNext: true,
    spacing: { before: 200, after: 80 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: COLOR.accent, space: 2 } },
    children: [new TextRun({ text, bold: true, color: COLOR.accent, size })],
  });
}

function p(runs, opts = {}) {
  return new Paragraph({
    spacing: { after: 80, ...(opts.spacing || {}) },
    children: Array.isArray(runs) ? runs : [new TextRun(runs)],
    ...opts,
  });
}

function bullet(text, boldLead) {
  const children = boldLead
    ? [new TextRun({ text: boldLead, bold: true, size: 20 }), new TextRun({ text: " " + text, size: 20 })]
    : [new TextRun({ text, size: 20 })];
  return new Paragraph({
    bullet: { level: 0 },
    spacing: { after: 60 },
    children,
  });
}

function codeRun(text) {
  return new TextRun({ text, font: "Consolas", size: 18, color: "5B3A7A" });
}

function fieldRow(label, cells) {
  return new TableRow({
    children: [
      new TableCell({
        width: { size: 2600, type: WidthType.DXA },
        shading: { type: ShadingType.CLEAR, fill: "F2F2F2" },
        margins: { top: 60, bottom: 60, left: 100, right: 100 },
        children: [new Paragraph({ children: [new TextRun({ text: label, bold: true, size: 19 })] })],
      }),
      new TableCell({
        width: { size: CONTENT_WIDTH - 2600, type: WidthType.DXA },
        margins: { top: 60, bottom: 60, left: 100, right: 100 },
        children: cells,
      }),
    ],
  });
}

const titleBlock = [
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 20 },
    children: [new TextRun({ text: "Universidad Nacional de Ingeniería", size: 18, color: COLOR.muted })],
  }),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 20 },
    children: [new TextRun({ text: "Desarrollo de un Sistema Integrado — 2026-2 — Taller 2", size: 18, color: COLOR.muted })],
  }),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 40 },
    children: [new TextRun({
      text: "Asistente de captura por firmeza e iluminación — Comparador de Precios",
      bold: true, size: 32, color: COLOR.accent,
    })],
  }),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 200 },
    children: [new TextRun({ text: "Documento técnico · Integrantes: [completar] · Repositorio: github.com/MarcOBL012/comparador-precios", size: 16, italics: true, color: COLOR.muted })],
  }),
];

const doc = new Document({
  styles: {
    default: { document: { run: { font: "Calibri", size: 20, color: COLOR.text } } },
  },
  sections: [
    {
      properties: {
        page: {
          size: { width: PAGE_WIDTH, height: PAGE_HEIGHT },
          margin: { top: MARGIN, bottom: MARGIN, left: MARGIN, right: MARGIN },
        },
      },
      children: [
        ...titleBlock,

        h("1. Descripción del sistema"),
        p([new TextRun({
          text:
            "Comparador de Precios es una app Android (Kotlin + Compose + CameraX) que fotografía el empaque de un producto, lo identifica con IA (Gemini) y compara su precio entre tiendas peruanas. " +
            "Para este taller se añadió un subsistema nuevo, independiente del trabajo del Taller 1 (cámara, cola sin conexión, contexto de red/batería): un asistente de captura que combina dos sensores físicos del teléfono " +
            "para decidir, en tiempo real y sin intervención del usuario, si conviene activar el flash y cuándo el teléfono está en condiciones de tomar una foto nítida.",
          size: 20,
        })]),

        h("2. Entradas físicas utilizadas"),
        new Table({
          width: { size: CONTENT_WIDTH, type: WidthType.DXA },
          columnWidths: [2600, CONTENT_WIDTH - 2600],
          rows: [
            fieldRow("Acelerómetro", [p([new TextRun({ text: "Sensor.TYPE_ACCELEROMETER. Se usa la magnitud del vector de aceleración (√x²+y²+z²) para estimar cuánto se mueve el teléfono.", size: 19 })])]),
            fieldRow("Sensor de luz", [p([new TextRun({ text: "Sensor.TYPE_LIGHT. Entrega el nivel de iluminación ambiental en lux frente a la cámara.", size: 19 })])]),
          ],
        }),
        p([new TextRun({
          text: "Las dos entradas se capturan simultáneamente mientras la pantalla de escaneo está abierta (SensorManager.SENSOR_DELAY_UI) y participan juntas en una sola decisión: ninguna alcanza para decidir por sí sola (ver sección 3).",
          size: 19, italics: true, color: COLOR.muted,
        })], { spacing: { before: 80, after: 120 } }),

        h("3. Procesamiento y lógica de decisión"),
        p([
          new TextRun({ text: "Procesamiento — ", bold: true, size: 20 }),
          new TextRun({
            text: "el valor instantáneo del acelerómetro nunca es cero (arrastra ~9.8 m/s² de gravedad), así que la firmeza no se mide con el valor puntual sino con la ",
            size: 20,
          }),
          new TextRun({ text: "varianza", bold: true, size: 20 }),
          new TextRun({ text: " de la magnitud en una ventana móvil de 12 muestras (~0.7 s): un valor bajo indica pulso firme, uno alto indica temblor o movimiento. La luz se compara contra un umbral fijo (30 lux) para clasificarla en clara/oscura.", size: 20 }),
        ], { spacing: { after: 100 } }),
        p([
          new TextRun({ text: "Decisión — ", bold: true, size: 20 }),
          new TextRun({ text: "la tabla siguiente es la función pura ", size: 20 }),
          codeRun("CaptureAdvisor.decide(firmeza, iluminación)"),
          new TextRun({ text: ", sin dependencias de Android (se prueba con JUnit puro). Nótese que las dos entradas se combinan: con poca luz, si el teléfono tiembla NO se activa el flash (el flash no corrige el movimiento, solo produce una foto oscura y además borrosa); primero se exige firmeza.", size: 20 }),
        ], { spacing: { after: 120 } }),
        new Table({
          width: { size: CONTENT_WIDTH, type: WidthType.DXA },
          columnWidths: [2400, 2400, CONTENT_WIDTH - 4800],
          rows: [
            new TableRow({
              tableHeader: true,
              children: ["Firmeza", "Iluminación", "Decisión (CaptureAction)"].map((t, i) => new TableCell({
                width: { size: i === 2 ? CONTENT_WIDTH - 4800 : 2400, type: WidthType.DXA },
                shading: { type: ShadingType.CLEAR, fill: COLOR.accent },
                margins: { top: 60, bottom: 60, left: 100, right: 100 },
                children: [new Paragraph({ children: [new TextRun({ text: t, bold: true, color: "FFFFFF", size: 19 })] })],
              })),
            }),
            ...[
              ["Temblando (SHAKING)", "Cualquiera", "WAIT_STEADY — espera, sin flash, banner \"Sostén firme…\""],
              ["Firme (STEADY)", "Baja (LOW)", "READY_DARK — activa flash + vibra"],
              ["Firme (STEADY)", "Buena (BRIGHT)", "READY_BRIGHT — vibra, sin flash"],
            ].map((row) => new TableRow({
              children: row.map((t, i) => new TableCell({
                width: { size: i === 2 ? CONTENT_WIDTH - 4800 : 2400, type: WidthType.DXA },
                margins: { top: 50, bottom: 50, left: 100, right: 100 },
                children: [new Paragraph({ children: [new TextRun({ text: t, size: 18 })] })],
              })),
            })),
          ],
        }),

        h("4. Acción física ejecutada"),
        bullet("prende/apaga el flash del teléfono según la decisión (CameraX CameraControl).", "Flash (torch):"),
        bullet("un pulso háptico de 40 ms cuando el sistema pasa a un estado \"listo\" (READY_DARK o READY_BRIGHT), avisando sin necesidad de mirar la pantalla.", "Vibración:"),
        bullet("un aviso de texto (\"Sostén firme…\" / \"Poca luz: flash activado…\") que aparece y desaparece solo, sin recargar la pantalla, reflejando el cambio de entorno en vivo.", "Adaptación visual:"),

        h("5. Diagrama de flujo y componentes principales"),
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 60, after: 60 },
          children: [new ImageRun({
            type: "png",
            data: fs.readFileSync(__dirname + "/diagrama_flujo.png"),
            transformation: { width: 620, height: 446 },
          })],
        }),

        h("6. Ubicación del código relevante"),
        new Table({
          width: { size: CONTENT_WIDTH, type: WidthType.DXA },
          columnWidths: [3600, CONTENT_WIDTH - 3600],
          rows: [
            ["Entrada + procesamiento (sensores)", "android/app/.../capture/CaptureConditionsMonitor.kt"],
            ["Decisión (lógica pura, testeada)", "android/app/.../capture/CaptureConditions.kt"],
            ["Test unitario de la decisión", "android/app/src/test/.../capture/CaptureAdvisorTest.kt"],
            ["Actuador: vibración", "android/app/.../capture/Haptics.kt"],
            ["Actuador: flash + banner de adaptación", "android/app/.../ui/ScanScreen.kt"],
            ["Permiso declarado", "android/app/src/main/AndroidManifest.xml (VIBRATE)"],
          ].map(([a, b]) => new TableRow({
            children: [
              new TableCell({
                width: { size: 3600, type: WidthType.DXA },
                shading: { type: ShadingType.CLEAR, fill: "F2F2F2" },
                margins: { top: 60, bottom: 60, left: 100, right: 100 },
                children: [new Paragraph({ children: [new TextRun({ text: a, size: 18 })] })],
              }),
              new TableCell({
                width: { size: CONTENT_WIDTH - 3600, type: WidthType.DXA },
                margins: { top: 60, bottom: 60, left: 100, right: 100 },
                children: [new Paragraph({ children: [new TextRun({ text: b, font: "Consolas", size: 18, color: "5B3A7A" })] })],
              }),
            ],
          })),
        }),
        p([new TextRun({
          text: "Prueba: ./gradlew :app:testDebugUnitTest (JVM, sin emulador) · Demo física: cámara con poca luz — mover el teléfono muestra el aviso de firmeza; al detenerlo, se activa el flash y vibra; al iluminar el ambiente, el flash se apaga solo.",
          italics: true, size: 18, color: COLOR.muted,
        })], { spacing: { before: 120 } }),
      ],
    },
  ],
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(__dirname + "/Taller2_AsistenteCaptura_DocumentoTecnico.docx", buf);
  console.log("ok");
});
