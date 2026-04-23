import { describe, expect, it } from 'vitest'
import { mergeSearchPage } from '../src/utils/searchPagination'

describe('mergeSearchPage', () => {
  it('should replace items on first page and append items on later pages', () => {
    const page1 = mergeSearchPage([{ id: 1 }], [{ id: 2 }, { id: 3 }], 1, 3)
    expect(page1.items).toEqual([{ id: 2 }, { id: 3 }])
    expect(page1.hasMore).toBe(true)
    expect(page1.nextPage).toBe(2)

    const page2 = mergeSearchPage(page1.items, [{ id: 4 }, { id: 5 }], 2, 3)
    expect(page2.items).toEqual([{ id: 2 }, { id: 3 }, { id: 4 }, { id: 5 }])
    expect(page2.hasMore).toBe(true)
    expect(page2.nextPage).toBe(3)
  })

  it('should mark hasMore false on last page', () => {
    const result = mergeSearchPage([{ id: 1 }], [{ id: 2 }], 3, 3)
    expect(result.items).toEqual([{ id: 1 }, { id: 2 }])
    expect(result.hasMore).toBe(false)
    expect(result.nextPage).toBe(4)
  })
})