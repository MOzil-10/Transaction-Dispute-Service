import { request } from "./http";
import type { DisputeRequest, DisputeResponse, Page } from "@/types/api";

export const disputeService = {
  list(page = 0, size = 20): Promise<Page<DisputeResponse>> {
    return request<Page<DisputeResponse>>(`/api/v1/disputes?page=${page}&size=${size}`);
  },
  submit(body: DisputeRequest): Promise<DisputeResponse> {
    return request<DisputeResponse>("/api/v1/disputes", { method: "POST", body });
  },
};