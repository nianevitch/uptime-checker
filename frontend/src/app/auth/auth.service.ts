import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  userId: number;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenKey = 'uptime_token';
  private readonly apiBase = '/api/auth';

  constructor(private readonly http: HttpClient) {}

  login(payload: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiBase}/login`, payload).pipe(
      tap(res => {
        this.storeToken(res.token);
        localStorage.setItem('uptime_user', JSON.stringify({ userId: res.userId, email: res.email }));
      })
    );
  }

  register(payload: RegisterRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiBase}/register`, payload).pipe(
      tap(res => {
        this.storeToken(res.token);
        localStorage.setItem('uptime_user', JSON.stringify({ userId: res.userId, email: res.email }));
      })
    );
  }

  logout(): Observable<void> {
    this.clearToken();
    return this.http.post<void>(`${this.apiBase}/logout`, {});
  }

  get token(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  private storeToken(token: string): void {
    localStorage.setItem(this.tokenKey, token);
  }

  clearToken(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem('uptime_user');
  }

  getCurrentUserId(): number | null {
    const stored = localStorage.getItem('uptime_user');
    if (stored) {
      const user = JSON.parse(stored);
      return user.userId || null;
    }
    return null;
  }

  getCurrentUserEmail(): string | null {
    const stored = localStorage.getItem('uptime_user');
    if (stored) {
      const user = JSON.parse(stored);
      return user.email || null;
    }
    return null;
  }

  isAuthenticated(): boolean {
    return !!this.token;
  }
}

