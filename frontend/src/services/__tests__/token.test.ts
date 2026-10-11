import { describe, it, expect, beforeEach } from "vitest";
import { tokenStore } from "@/services/token";

describe("tokenStore", () => {
  beforeEach(() => localStorage.clear());

  it("stores and retrieves the token", () => {
    tokenStore.set("my.jwt");
    expect(tokenStore.get()).toBe("my.jwt");
  });

  it("stores and retrieves the user", () => {
    tokenStore.setUser({ customerId: 42, email: "a@b.co.za" });
    expect(tokenStore.getUser()).toEqual({ customerId: 42, email: "a@b.co.za" });
  });

  it("clear() removes token and user", () => {
    tokenStore.set("my.jwt");
    tokenStore.setUser({ customerId: 1, email: "x@y.co.za" });
    tokenStore.clear();
    expect(tokenStore.get()).toBeNull();
    expect(tokenStore.getUser()).toBeNull();
  });
});