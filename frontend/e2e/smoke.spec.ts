import { expect, test } from "@playwright/test";

// Shell-only smoke. Full Operator→Tenant→Owner→Member→200/403 is the
// manual checklist in docs/local-dev.md (I5 acceptance).
test.describe("minimal console smoke", () => {
  test.skip(!process.env.E2E_BASE_URL, "Set E2E_BASE_URL after starting the Vite app.");

  test("loads the SPA shell", async ({ page }) => {
    await page.goto("/");

    await expect(
      page.getByRole("heading", { level: 1, name: "Thin-slice console" }),
    ).toBeVisible();
    await expect(page.getByText("RoseCloud IAM")).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Operator setup" }),
    ).toBeVisible();
  });
});
