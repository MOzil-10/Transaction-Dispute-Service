import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Badge } from "@/components/ui/Badge";

describe("Badge", () => {
  it("renders its children", () => {
    render(<Badge tone="green">Auto-approved</Badge>);
    expect(screen.getByText("Auto-approved")).toBeInTheDocument();
  });

  it("applies tone-specific classes", () => {
    render(<Badge tone="red">Rejected</Badge>);
    const el = screen.getByText("Rejected");
    expect(el.className).toContain("text-red-700");
  });
});