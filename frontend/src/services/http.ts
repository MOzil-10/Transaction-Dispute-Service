import { ApiError } from "./apiError";
import { tokenStore } from "./token";
import type { ErrorResponse } from "@/types/api";

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  auth?: boolean;
  signal?: AbortSignal;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, auth = true, signal } = opts;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth) {
    const token = tokenStore.get();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch {
    throw new ApiError("Unable to reach the server. Check your connection.", 0);
  }

  if (res.status === 401) {
    // Token invalid/expired - clear and surface a clean error.
    tokenStore.clear();
    throw new ApiError("Your session has expired. Please sign in again.", 401);
  }

  const isJson = res.headers.get("content-type")?.includes("application/json");
  const data = isJson ? await res.json().catch(() => undefined) : undefined;

  if (!res.ok) {
    const payload = data as ErrorResponse | undefined;
    throw new ApiError(payload?.message ?? `Request failed (${res.status})`, res.status, payload);
  }

  return data as T;
}