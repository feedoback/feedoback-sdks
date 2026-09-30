import { expect, test } from "@playwright/test";

/**
 * Drives the built package in a real browser against a faithful widget stub.
 * Proves the whole chain: the SDK injects the script with the right tag, the
 * pre-boot identity queue is replayed on load, and the imperative API from
 * useFeedback() reaches the widget after boot.
 */
test.beforeEach(async ({ page }) => {
  await page.goto("/");
});

test("injects widget.js with the project key and version", async ({ page }) => {
  const script = page.locator('script[data-feedoback-sdk][data-project="pk_e2e"]');
  await expect(script).toHaveCount(1);
  await expect(script).toHaveAttribute("data-version", "1.0.0");
  await expect(script).toHaveAttribute("src", /\/widget\.js$/);
});

test("loads the widget and replays the queued visitor and context", async ({ page }) => {
  const launcher = page.locator("#feedoback-stub-launcher");
  await expect(launcher).toBeVisible();
  await expect(launcher).toHaveAttribute("data-project", "pk_e2e");
  // Identity and context were set on first render, before the async script ran;
  // the widget replays them from the queue on boot.
  await expect(launcher).toHaveAttribute("data-visitor", "usr_42");
  await expect(launcher).toHaveAttribute("data-context", JSON.stringify({ plan: "pro" }));
});

test("mounts exactly one widget under Strict Mode", async ({ page }) => {
  await expect(page.locator("#feedoback-stub-launcher")).toHaveCount(1);
  await expect(page.locator('script[data-feedoback-sdk]')).toHaveCount(1);
});

test("useFeedback drives the widget after boot", async ({ page }) => {
  const launcher = page.locator("#feedoback-stub-launcher");
  await expect(launcher).toBeVisible();
  await expect(launcher).toHaveAttribute("data-open", "false");

  await page.locator("#btn-open").click();
  await expect(launcher).toHaveAttribute("data-open", "true");

  await page.locator("#btn-feedback").click();
  await expect(launcher).toHaveAttribute("data-mode", "picker");

  await page.locator("#btn-record").click();
  await expect(launcher).toHaveAttribute("data-mode", "recording");
});

test("destroy() removes the widget", async ({ page }) => {
  await expect(page.locator("#feedoback-stub-launcher")).toBeVisible();
  await page.locator("#btn-destroy").click();
  await expect(page.locator("#feedoback-stub-launcher")).toHaveCount(0);
});
