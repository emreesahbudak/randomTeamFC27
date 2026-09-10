import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TeamCrest } from "./TeamCrest";

describe("TeamCrest", () => {
  it("renders a placeholder when no team code is given", () => {
    render(<TeamCrest />);
    expect(screen.getByText("?")).toBeInTheDocument();
  });

  it("renders the first three letters of the team code, uppercased as given", () => {
    render(<TeamCrest code="SOL" colorHex="#e8b34d" />);
    expect(screen.getByText("SOL")).toBeInTheDocument();
  });

  it("truncates a longer code to three characters", () => {
    render(<TeamCrest code="LONGCODE" />);
    expect(screen.getByText("LON")).toBeInTheDocument();
  });
});
