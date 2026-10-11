import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Logo } from "@/components/layout/Logo";

describe("Logo", () => {
  it("shows the DisputeX brand name (not the old Aster Bank name)", () => {
    render(<Logo />);
    expect(screen.getByText("DisputeX")).toBeInTheDocument();
    expect(screen.queryByText(/aster/i)).not.toBeInTheDocument();
  });
});