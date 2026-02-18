import { test, expect } from '@playwright/test'

test('로그인 페이지 노출', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByText('업무 일정 관리 로그인')).toBeVisible()
})
