cd src/frontend/tests
call npm ci
call npx playwright install --with-deps
call npx playwright test
