import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export interface User {
  id: number;
  email: string;
  roles: string[];
  createdAt: string;
  deletedAt?: string;
}

export interface UserUpdateRequest {
  email?: string;
  roles?: string[];
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly baseUrl = '/api/users';

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {}

  getCurrentUser(): Observable<User> {
    const userId = this.authService.getCurrentUserId();
    if (!userId) {
      throw new Error('Not authenticated');
    }
    return this.http.get<User>(`${this.baseUrl}/${userId}`);
  }

  getUserById(id: number): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/${id}`);
  }

  updateUser(id: number, request: UserUpdateRequest): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/${id}`, request);
  }
}

