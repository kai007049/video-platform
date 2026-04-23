import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { appendSeenIds, replaceRecommendationBatch } from '../src/utils/recommendationRefresh'

const getMock = vi.fn((url, config) => ({ url, config }))
const getRecommendedMock = vi.fn()
const getVideoListMock = vi.fn()
const getHotListMock = vi.fn()
const pushMock = vi.fn()

vi.mock('../src/api/request', () => {
  return {
    default: {
      get: getMock
    }
  }
})

vi.mock('../src/api/video', () => ({
  getRecommended: (...args) => getRecommendedMock(...args),
  getVideoList: (...args) => getVideoListMock(...args),
  getHotList: (...args) => getHotListMock(...args)
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ query: { tab: 'recommend' } })
}))

vi.mock('../src/components/SkeletonScreen.vue', () => ({
  default: defineComponent({
    name: 'SkeletonScreenStub',
    template: '<div class="skeleton-stub"></div>'
  })
}))

import { getRecommended } from '../src/api/video'
import Home from '../src/views/Home.vue'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

async function flushPromises(times = 4) {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve()
    await nextTick()
  }
}

describe('recommendation refresh helpers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    global.IntersectionObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
    global.ResizeObserver = class {
      observe() {}
      disconnect() {}
    }
  })

  it('should append unique seen ids and replace current batch', () => {
    const seen = appendSeenIds([], [{ id: 1 }, { id: 2 }, { id: 2 }, { id: 3 }])
    expect(seen).toEqual([1, 2, 3])

    const next = replaceRecommendationBatch(
      [{ id: 1, title: 'old-1' }, { id: 2, title: 'old-2' }],
      [{ id: 4, title: 'new-4' }, { id: 5, title: 'new-5' }]
    )
    expect(next).toEqual([
      { id: 4, title: 'new-4' },
      { id: 5, title: 'new-5' }
    ])
  })

  it('should ignore invalid ids when collecting seen items', () => {
    const seen = appendSeenIds([1], [{ id: 1 }, { id: null }, {}, { id: 3 }])
    expect(seen).toEqual([1, 3])
  })

  it('should pass excludeVideoIds to recommended api params', () => {
    const result = getRecommended(1, 12, [10, 11, 12])
    expect(result.config.params).toEqual({
      page: 1,
      size: 12,
      excludeVideoIds: '10,11,12'
    })
  })

  it('should keep seenRecommendedIds when loading more after refresh', async () => {
    getRecommendedMock
      .mockResolvedValueOnce({
        records: Array.from({ length: 16 }, (_, index) => ({ id: index + 1, title: `video-${index + 1}` })),
        current: 1,
        pages: 3
      })
      .mockResolvedValueOnce({
        records: Array.from({ length: 16 }, (_, index) => ({ id: index + 101, title: `refresh-${index + 101}` })),
        current: 1,
        pages: 3
      })
      .mockResolvedValueOnce({
        records: Array.from({ length: 16 }, (_, index) => ({ id: index + 201, title: `more-${index + 201}` })),
        current: 2,
        pages: 3
      })

    const wrapper = mount(Home)
    await flushPromises()

    const buttons = wrapper.findAll('button')
    await buttons.find((button) => button.text() === '换一批').trigger('click')
    await flushPromises()

    await buttons.find((button) => button.text() === '加载更多').trigger('click')
    await flushPromises()

    expect(getRecommendedMock).toHaveBeenNthCalledWith(1, 1, 16, [])
    expect(getRecommendedMock).toHaveBeenNthCalledWith(
      2,
      1,
      16,
      Array.from({ length: 16 }, (_, index) => index + 1)
    )
    expect(getRecommendedMock).toHaveBeenNthCalledWith(
      3,
      2,
      16,
      Array.from({ length: 32 }, (_, index) => index < 16 ? index + 1 : index + 85)
    )
  })

  it('should block loadMore while refreshRecommendBatch is in flight', async () => {
    const initialBatch = {
      records: Array.from({ length: 16 }, (_, index) => ({ id: index + 1, title: `video-${index + 1}` })),
      current: 1,
      pages: 3
    }
    const refreshRequest = deferred()

    getRecommendedMock
      .mockResolvedValueOnce(initialBatch)
      .mockImplementationOnce(() => refreshRequest.promise)

    const wrapper = mount(Home)
    await flushPromises()

    const buttons = wrapper.findAll('button')
    await buttons.find((button) => button.text() === '换一批').trigger('click')
    await flushPromises()

    await buttons.find((button) => button.text() === '加载更多').trigger('click')
    await flushPromises()

    expect(getRecommendedMock).toHaveBeenCalledTimes(2)

    refreshRequest.resolve({
      records: Array.from({ length: 16 }, (_, index) => ({ id: index + 101, title: `refresh-${index + 101}` })),
      current: 1,
      pages: 3
    })
    await flushPromises()
  })
})
