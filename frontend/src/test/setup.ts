import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Unmount and clean up the DOM after every test to keep them isolated.
afterEach(() => {
  cleanup();
});