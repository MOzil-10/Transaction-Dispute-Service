import { request } from "./http";
import type { MandateType, Page, TransactionResponse } from "@/types/api";

export interface TransactionQuery {
  page?: number;
  size?: number;
  mandateType?: MandateType | "";
  fromDate?: string; // yyyy-MM-dd
  toDate?: string; // yyyy-MM-dd
}

export const transactionService = {
  list(page = 0, size = 10): Promise<Page<TransactionResponse>> {
    return request<Page<TransactionResponse>>(`/api/v1/transactions?page=${page}&size=${size}`);
  },
  search(q: TransactionQuery): Promise<Page<TransactionResponse>> {
    const params = new URLSearchParams();
    params.set("page", String(q.page ?? 0));
    params.set("size", String(q.size ?? 10));
    if (q.mandateType) params.set("mandateType", q.mandateType);
    if (q.fromDate) params.set("fromDate", q.fromDate);
    if (q.toDate) params.set("toDate", q.toDate);
    return request<Page<TransactionResponse>>(`/api/v1/transactions/search?${params.toString()}`);
  },
};