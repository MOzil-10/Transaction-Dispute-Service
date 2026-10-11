import type { ErrorResponse } from "@/types/api";

/** A normalised error thrown by the API client with the structured backend payload when available. */
export class ApiError extends Error {
  status: number;
  payload?: ErrorResponse;

  constructor(message: string, status: number, payload?: ErrorResponse) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.payload = payload;
  }
}