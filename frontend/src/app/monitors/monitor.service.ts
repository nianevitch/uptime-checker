import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CheckResult {
  id: number;
  httpCode?: number;
  errorMessage?: string;
  responseTimeMs?: number;
  checkedAt: string;
}

export interface Ping {
  id: number;
  ownerId?: number;
  label?: string;
  url: string;
  frequencyMinutes: number;
  nextCheckAt?: string;
  inProgress: boolean;
  createdAt: string;
  updatedAt: string;
  recentResults: CheckResult[];
}

export interface PingPayload {
  url: string;
  label?: string;
  frequencyMinutes: number;
}

@Injectable({ providedIn: 'root' })
export class PingService {
  private readonly baseUrl = '/api/pings';

  constructor(private readonly http: HttpClient) {}

  list(): Observable<Ping[]> {
    return this.http.get<Ping[]>(this.baseUrl);
  }

  create(payload: PingPayload): Observable<Ping> {
    return this.http.post<Ping>(this.baseUrl, payload);
  }

  update(id: number, payload: PingPayload): Observable<Ping> {
    return this.http.put<Ping>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  execute(id: number): Observable<CheckResult> {
    return this.http.post<CheckResult>('/api/checks/execute', { pingId: id });
  }
}

