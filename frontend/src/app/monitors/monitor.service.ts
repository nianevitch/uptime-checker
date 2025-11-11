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

export interface Monitor {
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

export interface MonitorPayload {
  url: string;
  label?: string;
  frequencyMinutes: number;
}

@Injectable({ providedIn: 'root' })
export class MonitorService {
  private readonly baseUrl = '/api/monitors';

  constructor(private readonly http: HttpClient) {}

  list(): Observable<Monitor[]> {
    return this.http.get<Monitor[]>(this.baseUrl);
  }

  create(payload: MonitorPayload): Observable<Monitor> {
    return this.http.post<Monitor>(this.baseUrl, payload);
  }

  update(id: number, payload: MonitorPayload): Observable<Monitor> {
    return this.http.put<Monitor>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  execute(id: number): Observable<CheckResult> {
    return this.http.post<CheckResult>('/api/checks/execute', { monitorId: id });
  }
}

