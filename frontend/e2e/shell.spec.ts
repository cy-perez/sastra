import { expect, test } from '@playwright/test';

/**
 * Estas si necesitan navegador. Si falla con "Executable doesn't exist", falta
 * el paso explicito: npx playwright install chromium
 */
test.describe('cascaron del sitio', () => {
  test('el enlace de salto lleva el foco al contenido', async ({ page }) => {
    await page.goto('/');
    await page.keyboard.press('Tab');

    const skip = page.getByRole('link', { name: 'Saltar al contenido' });
    await expect(skip).toBeFocused();

    await page.keyboard.press('Enter');
    await expect(page.locator('#contenido')).toBeFocused();
  });

  test('el conmutador de tema cambia el modo y lo recuerda', async ({ page }) => {
    await page.goto('/');
    const root = page.locator('html');
    await expect(root).toHaveAttribute('data-tema', 'claro');

    await page.getByRole('button', { name: 'Cambiar a modo oscuro' }).click();
    await expect(root).toHaveAttribute('data-tema', 'oscuro');

    // Al recargar, el servidor ya lo sabe por la cookie: llega oscuro de origen.
    await page.reload();
    await expect(root).toHaveAttribute('data-tema', 'oscuro');
  });

  test('el selector de idioma cambia el texto y lo recuerda', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Compra y vende');

    await page.getByLabel('Idioma').selectOption('en');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Buy and sell');

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });

  test('hay un solo h1 en la pagina', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
  });
});
