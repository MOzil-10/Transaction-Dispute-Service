import { request } from "./http";
import type { AccountResponse } from "@/types/api";

export const accountService = {
  /** Read-only overview of the authenticated customer and their primary account. */
  overview(): Promise<AccountResponse> {
    return request<AccountResponse>("/api/v1/account");
  },
};