import { request } from "./http";
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/types/api";

export const authService = {
  login(body: LoginRequest): Promise<AuthResponse> {
    return request<AuthResponse>("/api/v1/auth/login", { method: "POST", body, auth: false });
  },
  register(body: RegisterRequest): Promise<AuthResponse> {
    return request<AuthResponse>("/api/v1/auth/register", { method: "POST", body, auth: false });
  },
};