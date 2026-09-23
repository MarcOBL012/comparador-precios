Esta carpeta existe solo para que Vercel encuentre un "Output Directory"
durante el build. Este proyecto no sirve archivos estáticos: todo el backend
son funciones serverless en `api/` (ver ../CLAUDE.md). No borrar esta carpeta
sin antes cambiar la config de Output Directory en el dashboard de Vercel,
o el build vuelve a fallar con "No Output Directory named public found".
