import { describe, it, expect, vi, beforeEach } from 'vitest';

const { generateTextMock, objectMock } = vi.hoisted(() => ({
  generateTextMock: vi.fn(),
  objectMock: vi.fn((config: unknown) => ({ __schemaConfig: config })),
}));

vi.mock('ai', () => ({
  generateText: generateTextMock,
  Output: { object: objectMock },
}));

import { identifyProduct } from '../lib/identifyProduct';
import { ProductIdentificationSchema } from '../lib/productIdentification';

const VALID_IDENTIFICATION = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.92,
};

describe('identifyProduct', () => {
  beforeEach(() => {
    generateTextMock.mockReset();
    objectMock.mockClear();
  });

  it('llama a generateText con el modelo, la imagen y el schema correctos', async () => {
    generateTextMock.mockResolvedValue({ output: VALID_IDENTIFICATION });

    const result = await identifyProduct('data:image/jpeg;base64,ABC123');

    expect(result).toEqual(VALID_IDENTIFICATION);
    expect(objectMock).toHaveBeenCalledWith({ schema: ProductIdentificationSchema });

    const callArgs = generateTextMock.mock.calls[0][0];
    expect(callArgs.model.modelId).toBe('gemini-3.6-flash');
    expect(callArgs.messages).toEqual([
      {
        role: 'user',
        content: [
          { type: 'text', text: expect.any(String) },
          { type: 'image', image: 'data:image/jpeg;base64,ABC123' },
        ],
      },
    ]);
  });

  it('incluye la categoría elegida por la persona como pista en el prompt', async () => {
    generateTextMock.mockResolvedValue({ output: VALID_IDENTIFICATION });

    await identifyProduct('data:image/jpeg;base64,ABC123', 'tecnologia');
    const withChoice = generateTextMock.mock.calls[0][0].messages[0].content[0].text;
    await identifyProduct('data:image/jpeg;base64,ABC123');
    const withoutChoice = generateTextMock.mock.calls[1][0].messages[0].content[0].text;

    expect(withChoice).toContain('categoría "tecnologia"');
    expect(withoutChoice).not.toContain('la persona indicó');
  });

  it('propaga el error si generateText falla', async () => {
    generateTextMock.mockRejectedValue(new Error('timeout de red'));

    await expect(identifyProduct('data:image/jpeg;base64,ABC123')).rejects.toThrow('timeout de red');
  });
});
