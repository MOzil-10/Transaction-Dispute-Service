import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { request } from "@/services/http";
import { ApiError } from "@/services/apiError";
import { tokenStore } from "@/services/token";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("http request client", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns parsed JSON on success", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ id: 1, name: "ok" })));
    const data = await request<{ id: number; name: string }>("/api/v1/thing");
    expect(data).toEqual({ id: 1, name: "ok" });
  });

  it("attaches the Bearer token when authenticated", async () => {
    tokenStore.set("abc.def.ghi");
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    await request("/api/v1/account");

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer abc.def.ghi");
  });

  it("throws a structured ApiError using the backend message on failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            status: 400,
            error: "Bad Request",
            message: "A dispute has already been lodged",
            path: "/api/v1/disputes",
            timestamp: "2026-04-13T10:00:00Z",
          },
          400,
        ),
      ),
    );

    await expect(request("/api/v1/disputes", { method: "POST", body: {} })).rejects.toMatchObject({
      status: 400,
      message: "A dispute has already been lodged",
    });
  });

  it("clears the session and throws on 401", async () => {
    tokenStore.set("expired");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({}, 401)));

    await expect(request("/api/v1/account")).rejects.toBeInstanceOf(ApiError);
    expect(tokenStore.get()).toBeNull();
  });

  it("wraps network failures in an ApiError with status 0", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    await expect(request("/api/v1/account")).rejects.toMatchObject({ status: 0 });
  });
});