import { describe, expect, it } from 'vitest'
import { mergeSearchPage } from '../src/utils/searchPagination.js'

describe('mergeSearchPage', () => {
  it('deduplicates items by id across pages while preserving pagination metadata', () => {
    const existingItems = [
      { id: 1, title: 'First' },
      { id: 2, title: 'Second' }
    ]
    const incomingItems = [
      { id: 2, title: 'Second duplicate from next page' },
      { id: 3, title: 'Third' }
    ]

    const result = mergeSearchPage(existingItems, incomingItems, 2, 4)

    expect(result).toEqual({
      items: [
        { id: 1, title: 'First' },
        { id: 2, title: 'Second' },
        { id: 3, title: 'Third' }
      ],
      hasMore: true,
      nextPage: 3
    })
  })
})
