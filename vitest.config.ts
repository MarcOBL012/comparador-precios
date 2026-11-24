import { defineConfig } from 'vitest/config';

// Los archivos de prueba corren uno tras otro: las pruebas de integración comparten una misma base
// y ahora procesan eventos de verdad, así que dos archivos a la vez podrían reclamar entregas ajenas.
export default defineConfig({
  test: { fileParallelism: false },
});
