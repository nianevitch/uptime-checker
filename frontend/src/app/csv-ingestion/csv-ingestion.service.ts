import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CsvFileSummary {
  id: string;
  fileName: string;
  columnCount: number;
  rowCount: number;
  ingestedAt: string;
  ingestedBy: string;
}

export interface CsvFileData {
  id: string;
  fileName: string;
  headers: string[];
  rows: { [key: string]: string }[];
}

export interface MergeRequest {
  rowIndices?: number[]; // Deprecated: use rowIds instead
  rowIds?: string[]; // Preferred: stable across index shifts
  mergedRow: { [key: string]: string };
  useCustomIdentity?: boolean;
  mergeStrategy?: 'manual' | 'merge-delete' | 'auto-merge';
}

export interface CsvIngestionResponse {
  id: string | null;
  message: string;
  success: boolean;
}

@Injectable({ providedIn: 'root' })
export class CsvIngestionService {
  private readonly baseUrl = '/api/csv/ingest';

  constructor(private readonly http: HttpClient) {}

  list(): Observable<CsvFileSummary[]> {
    return this.http.get<CsvFileSummary[]>(this.baseUrl);
  }

  upload(file: File): Observable<CsvIngestionResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<CsvIngestionResponse>(this.baseUrl, formData);
  }

  uploadMultiple(files: File[]): Observable<CsvIngestionResponse[]> {
    const uploads = files.map(file => this.upload(file));
    // Return all uploads as an array
    return new Observable(observer => {
      const results: CsvIngestionResponse[] = [];
      let completed = 0;
      
      uploads.forEach((upload$, index) => {
        upload$.subscribe({
          next: response => {
            results[index] = response;
            completed++;
            if (completed === files.length) {
              observer.next(results);
              observer.complete();
            }
          },
          error: error => {
            results[index] = {
              id: null,
              message: error.error?.message || `Failed to upload ${files[index].name}`,
              success: false
            };
            completed++;
            if (completed === files.length) {
              observer.next(results);
              observer.complete();
            }
          }
        });
      });
    });
  }

  getFileData(id: string): Observable<CsvFileData> {
    return this.http.get<CsvFileData>(`${this.baseUrl}/${id}`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  mergeRows(id: string, request: MergeRequest): Observable<{
    mergedRow: { [key: string]: string };
    mergedRowIndex: number;
    affectedRows: { [key: string]: { [key: string]: string } };
    deletedRowIndices: number[];
  }> {
    return this.http.put<{
      mergedRow: { [key: string]: string };
      mergedRowIndex: number;
      affectedRows: { [key: string]: { [key: string]: string } };
      deletedRowIndices: number[];
    }>(`${this.baseUrl}/${id}/merge`, request);
  }

  recalculateDuplicateStatus(id: string): Observable<CsvFileData> {
    return this.http.post<CsvFileData>(`${this.baseUrl}/${id}/recalculate-status`, {});
  }

  updateSingleField(id: string, rowIndex: number, fieldName: string, fieldValue: string): Observable<{
    updatedRow: { [key: string]: string };
    updatedRowIndex: number;
    affectedRows: { [key: string]: { [key: string]: string } }; // Keys are strings in JSON
    resolvedDuplicateIndices: number[];
    hasResolvedDuplicates: boolean;
  }> {
    return this.http.put<{
      updatedRow: { [key: string]: string };
      updatedRowIndex: number;
      affectedRows: { [key: string]: { [key: string]: string } }; // Keys are strings in JSON
      resolvedDuplicateIndices: number[];
      hasResolvedDuplicates: boolean;
    }>(`${this.baseUrl}/${id}/update-field`, {
      rowIndex,
      fieldName,
      fieldValue
    });
  }

  updateRowsAndRecalculateStatus(id: string, rows: { [key: string]: string }[]): Observable<CsvFileData> {
    return this.http.put<CsvFileData>(`${this.baseUrl}/${id}/update-rows`, { rows });
  }

  autoMergeAllDuplicates(id: string, duplicateGroups: Array<{ rowIds?: string[]; rowIndices?: number[] }>, mergeStrategy: string): Observable<{
    totalGroups: number;
    processedGroups: number;
    successfulGroups: number;
    failedGroups: number;
    errors?: string[];
  }> {
    return this.http.post<{
      totalGroups: number;
      processedGroups: number;
      successfulGroups: number;
      failedGroups: number;
      errors?: string[];
    }>(`${this.baseUrl}/${id}/auto-merge-all`, {
      duplicateGroups,
      mergeStrategy
    });
  }
}


