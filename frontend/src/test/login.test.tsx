import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { LoginPage } from '../components/LoginPage'

test('로그인 화면 렌더', () => {
  render(
    <BrowserRouter>
      <LoginPage />
    </BrowserRouter>
  )
  expect(screen.getByText('업무 일정 관리 로그인')).toBeInTheDocument()
})
