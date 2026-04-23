import { describe, expect, it, vi } from 'vitest'
import { applyImageFallbackOnce } from '../src/utils/imageFallback'

describe('applyImageFallbackOnce', () => {
  it('should only apply fallback once', () => {
    const target = {
      dataset: {},
      onerror: vi.fn(),
      src: '/broken.png'
    }
    const event = { target }

    applyImageFallbackOnce(event, '/fallback.png')
    expect(target.dataset.fallbackApplied).toBe('1')
    expect(target.onerror).toBeNull()
    expect(target.src).toBe('/fallback.png')

    target.src = '/fallback.png'
    applyImageFallbackOnce(event, '/another.png')
    expect(target.src).toBe('/fallback.png')
  })
})