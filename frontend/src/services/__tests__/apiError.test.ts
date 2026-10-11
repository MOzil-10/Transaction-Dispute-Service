import { describe, it, expect } from "vitest";
import { ApiError } from "@/services/apiError";
import type { ErrorResponse } from "@/types/api";

describe("ApiError", () => {
  it("captures message, status and payload", () => {
    const payload: ErrorResponse = {
      status: 400,
      error: "Bad Request",
      message: "A dispute has already been lodged",
      path: "/api/v1/disputes",
      timestamp: "2026-04-13T10:00:00Z",
    };
    const err = new ApiError("A dispute has already been lodged", 400, payload);

    expect(err).toBeInstanceOf(Error);
    expect(err.name).toBe("ApiError");
    expect(err.message).toBe("A dispute has already been lodged");
    expect(err.status).toBe(400);
    expect(err.payload).toBe(payload);
  });

  it("works without a payload", () => {
    const err = new ApiError("Network down", 0);
    expect(err.status).toBe(0);
    expect(err.payload).toBeUndefined();
  });
});