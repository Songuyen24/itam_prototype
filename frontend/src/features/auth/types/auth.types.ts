export interface LoginPayload {
  email: string;
  password: string;
}

export interface AuthUser {
  id: number;
  email: string;
  fullName: string;
  role: string;
  roleName?: string;
  departmentName?: string;
  accountStatus?: string;
}

export interface LoginResponse {
  token: string;
  type: string;
  user: AuthUser;
}

export type CurrentUserResponse = AuthUser;

export interface RolePreset {
  email: string;
  label: string;
  description: string;
  role: string;
}
